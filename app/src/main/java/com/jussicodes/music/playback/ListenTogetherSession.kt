package com.jussicodes.music.playback

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import com.jussicodes.music.constants.userIdKye
import com.jussicodes.music.data.ListenTogetherApi
import com.jussicodes.music.data.LTRoom
import com.jussicodes.music.data.LTSnapshot
import com.jussicodes.music.data.buildListenTogetherInviteUrl
import com.jussicodes.music.data.parseListenTogetherInvitation
import com.jussicodes.music.extensions.toMediaItemList
import com.jussicodes.music.utils.PlayerExpandBus
import com.jussicodes.music.utils.dataStore
import com.rcmiku.ncmapi.model.Song
import com.rcmiku.ncmapi.utils.CookieProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 一起听（listen together）会话状态机（移植自 MeiloX ListenTogetherStore，适配 jussichords
 * 直连 NCM 官方端点 + 1.4.1 Media3）。
 *
 * - 无长连接：NCM 服务端是状态中枢，本端 1 秒轮询 /sync/playlist/get 拉快照，
 *   用「列表签名 + 命令签名」diff，没变化不动播放器；
 * - 本地播放事件（切歌/播放/暂停/拖动/队列变更）带单调递增 clientSeq 上报，
 *   远端命令带 serverSeq，双序号去重排序；
 * - 应用远端状态时置 [applyingRemote] + 1s 抑制窗口，防止「应用别人切歌 →
 *   触发本地回调 → 又报回去」的回环；
 * - 心跳（/heartbeat）随 1s 监控循环每 5 个 tick（tick==1 || tick%5==0）发一次，
 *   房间状态每 5 tick 复检一次（对齐 MeiloX；timeSpan 仍随响应解析但不再驱动间隔）。
 */
@OptIn(UnstableApi::class)
object ListenTogetherSession : Player.Listener {

    data class UiState(
        val isLoading: Boolean = false,
        val room: LTRoom? = null,
        val isHost: Boolean = false,
        val reconnecting: Boolean = false,
        val inviteUrl: String? = null,
        val error: String? = null,
        val notice: String? = null,
        val lastSyncMs: Long = 0L
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private const val TAG = "LT-Debug"

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    @Volatile
    private var player: Player? = null
    @Volatile
    private var appContext: Context? = null
    private var monitorJob: Job? = null
    private var playlistReportJob: Job? = null
    private var actionJob: Job? = null

    private var clientSeq = 0L
    /** 队列 REPLACE 的 version 计数，与命令 clientSeq 分开，避免两套序号交替递增。 */
    private var playlistVersion = 0L
    private var localUid: Long = 0L
    private var formerSongId: Long = 0L
    @Volatile
    private var applyingRemote = false
    @Volatile
    private var suppressReportsUntilMs = 0L
    private var lastPlaylistSig: String? = null
    private var lastCommandSig: String? = null
    /**
     * 本端最近一次成功上报的播放命令序号（clientSeq）+ 目标歌，用于**回声识别**：
     * 轮询回来的 playCommand 若 clientSeq 与之相等，说明那就是我自己刚发的那条，
     * 不能当成「远端指令」回灌到本地播放器（否则本地操作会被自己 1s 前的
     * 旧命令撤销，典型症状：切歌单后点第 5 首歌却自动播第 1 首）。
     */
    private var lastOwnCommandSeq = 0L
    private var lastOwnCommandTargetId = 0L
    private var consecutiveFailures = 0

    // ------------------------------------------------------------------
    // 播放器接线（PlaybackService onCreate / onDestroy）
    // ------------------------------------------------------------------

    fun attach(value: Player, context: Context) {
        if (player === value) return
        player?.removeListener(this)
        player = value
        appContext = context.applicationContext
        value.addListener(this)
        formerSongId = currentSongId() ?: 0L
        // MeiloX 原样：attach 时无条件 refresh()（不按登录态 gating）。
        refresh()
    }

    // MeiloX detachPlayer 原样：只摘该播放器监听 + 停监控；房间/序号/本地 uid 保留，
    // 下次 attach 由 refresh() 重新对账。残留的 playlistReportJob 到期会因 player==null 直接 return。
    fun detach(value: Player) {
        if (player !== value) return
        value.removeListener(this)
        player = null
        appContext = null
        monitorJob?.cancel()
        monitorJob = null
    }

    // ------------------------------------------------------------------
    // 用户动作
    // ------------------------------------------------------------------

    fun refresh() {
        if (actionJob?.isActive == true) return
        actionJob = scope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                // MeiloX 原样：status/snapshot 拉取失败不当「不在房」清会话，直接抛给 catch → error。
                val status = ListenTogetherApi.status().getOrThrow()
                val room = status.room
                if (status.inRoom != true || room == null) {
                    clearSession()
                } else {
                    loadLocalUid()
                    establish(room)
                    val snapshot = ListenTogetherApi.snapshot(room.id).getOrThrow()
                    applyRemote(snapshot, initial = true)
                    sendHeartbeat()
                    startMonitoring()
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _state.value = _state.value.copy(isLoading = false, error = error.message)
            }
        }
    }

    fun create() {
        if (actionJob?.isActive == true) return
        actionJob = scope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, notice = null)
            var establishedRoomId: String? = null
            try {
                if (!CookieProvider.isLoggedIn()) {
                    _state.value = _state.value.copy(isLoading = false, error = "请先登录网易云账号")
                    return@launch
                }
                val activePlayer = player ?: error("播放器未就绪")
                val songId = currentSongId()
                if (songId == null || songId <= 0L) {
                    _state.value = _state.value.copy(isLoading = false, error = "请先播放一首歌曲再创建房间")
                    return@launch
                }
                val room = ListenTogetherApi.create().getOrThrow()
                loadLocalUid()
                establish(room)
                establishedRoomId = room.id
                Log.d(TAG, "create ok roomId=${room.id} isHost=${_state.value.isHost} localUid=$localUid")
                reportPlaylist()
                reportCommand("GOTO", songId, songId, player?.playWhenReady ?: true)
                sendHeartbeat()
                startMonitoring()
                _state.value = _state.value.copy(isLoading = false)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                // establish 之后的失败（队列上报被业务拒绝 / 心跳失败）不能只写 error：
                // 房间已在服务端存在、UI 显示「已进房N 人」，但 startMonitoring 没跑到 →
                // 没有轮询、没有心跳，服务端最终判掉线。回滚：结束服务端房间 + 清会话。
                rollbackAfterEstablish(establishedRoomId, error)
            }
        }
    }

    /** 从官方分享链接文本进房。 */
    fun join(invitationText: String) {
        if (actionJob?.isActive == true) return
        actionJob = scope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, notice = null)
            var establishedRoomId: String? = null
            try {
                val (roomId, inviterId) = parseListenTogetherInvitation(invitationText)
                    ?: throw IllegalStateException("邀请链接无效")
                val check = ListenTogetherApi.checkRoom(roomId).getOrThrow()
                if (!check.first) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = check.second?.takeIf { it.isNotBlank() } ?: "房间不可加入"
                    )
                    return@launch
                }
                val room = ListenTogetherApi.accept(roomId, inviterId).getOrThrow()
                loadLocalUid()
                establish(room)
                establishedRoomId = room.id
                Log.d(TAG, "join ok roomId=${room.id} inviterId=$inviterId isHost=${_state.value.isHost} localUid=$localUid")
                // 初始同步失败直接抛 → 下方 catch 回滚（不进监测，不展开播放页）。
                applyRemote(ListenTogetherApi.snapshot(room.id).getOrThrow(), initial = true)
                sendHeartbeat()
                startMonitoring()
                // 接收端进房成功：自动展开全屏播放界面，落到 host 当前那首歌。
                // host（自己重进自己的房间）不展开，保持现状。
                if (!_state.value.isHost) PlayerExpandBus.expand()
                _state.value = _state.value.copy(isLoading = false)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                rollbackAfterEstablish(establishedRoomId, error)
            }
        }
    }

    /**
     * establish 之后失败的统一回滚。
     *
     * 旧实现只写 error：房间已在服务端存在、UI 显示「已进房 N 人」，但
     * startMonitoring() 永远没执行 → 无轮询、无心跳，服务端最终判掉线，
     * 本机却以为还在房里（房间状态永远停在半建立）。
     *
     * @param roomId 本次动作已 establish 的房间；为 null 表示失败发生在 establish 之前
     * （例如链接无效 / checkRoom 不过），此时只清 error，不动服务端房间。
     */
    private fun rollbackAfterEstablish(roomId: String?, error: Throwable) {
        if (roomId != null) {
            // 结束服务端房间（失败也不阻断本地清理），避免留下一个谁都不同步的僵尸房。
            scope.launch { runCatching { ListenTogetherApi.end(roomId) } }
        }
        clearSession()
        _state.value = _state.value.copy(isLoading = false, error = error.message)
        Log.w(TAG, "rollback room=$roomId reason=${error.message}")
    }

    /**
     * 由私信「一起听」卡片进房：[join] 的卡片变体，直接吃 roomId + inviterId。
     * 调用方先自行 [ListenTogetherApi.checkRoom] 判房间是否存活，此处只负责
     * accept + establish + 拉快照 + 心跳 + 监测；成功返回 true（调用方据此展开播放界面）。
     * 非 suspend 前提（登录态 / 播放器就绪）不满足或 accept 失败时返回 false。
     */
    suspend fun joinCard(roomId: String, inviterId: String): Boolean {
        if (!CookieProvider.isLoggedIn()) return false
        if (player == null) return false
        val room = ListenTogetherApi.accept(roomId, inviterId).getOrNull() ?: return false
        loadLocalUid()
        establish(room)
        Log.d(TAG, "joinCard ok roomId=${room.id} inviterId=$inviterId isHost=${_state.value.isHost} localUid=$localUid")
        val snapshot = runCatching { ListenTogetherApi.snapshot(room.id).getOrThrow() }
            .getOrNull()
        // applyRemote 现在会抛（targetId 缺失 / 目标不在列表 / 分片拉详情失败）：失败不 crash 进房流程，
        // 置 reconnecting 交给监控循环计数，随后 startMonitoring 的每 tick 快照会继续重试同步。
        if (snapshot != null) {
            runCatching { applyRemote(snapshot, initial = true) }
                .onFailure { _state.value = _state.value.copy(reconnecting = true) }
        }
        // 初始心跳失败不阻断进房：置 reconnecting，交给监控循环的 catch 计数。
        runCatching { sendHeartbeat() }
            .onFailure { _state.value = _state.value.copy(reconnecting = true) }
        startMonitoring()
        _state.value = _state.value.copy(isLoading = false)
        return true
    }

    fun end() {
        if (actionJob?.isActive == true) return
        val room = _state.value.room ?: return
        actionJob = scope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            runCatching { ListenTogetherApi.end(room.id) }
            clearSession("已离开一起听房间")
            _state.value = _state.value.copy(isLoading = false)
        }
    }

    fun dismissError() {
        _state.value = _state.value.copy(error = null)
    }

    fun dismissNotice() {
        _state.value = _state.value.copy(notice = null)
    }

    // ------------------------------------------------------------------
    // 会话内部
    // ------------------------------------------------------------------

    private suspend fun loadLocalUid() {
        val ctx = appContext ?: return
        localUid = ctx.dataStore.data.first()[userIdKye] ?: 0L
    }

    private fun establish(room: LTRoom) {
        // 换房间才归零命令序号：refresh()（每次 attach，即每次 Service 重建）在
        // 已在房时会重新走 establish —— 无条件 clientSeq=0 会让重进后的命令序号
        // 倒退，服务端可能当旧序丢弃，表现为「重开 App 后一起听彻底不同步」。
        // 同一房间重进保留计数；真换房间（room.id 不同）才从头开始。
        if (_state.value.room?.id != room.id) {
            clientSeq = 0L
            playlistVersion = 0L
        }
        lastPlaylistSig = null
        lastCommandSig = null
        consecutiveFailures = 0
        suppressReportsUntilMs = SystemClock.elapsedRealtime() + 1000L
        _state.value = UiState(
            room = room,
            isHost = room.creatorId == localUid,
            // MeiloX buildInvitationUrl 原样：无歌可播 / 取不到 inviter 时 inviteUrl 为 null。
            inviteUrl = buildListenTogetherInviteUrl(
                roomId = room.id,
                inviterId = localUid.takeIf { it > 0 } ?: room.creatorId,
                songId = currentSongId()
            ),
            lastSyncMs = System.currentTimeMillis()
        )
    }

    private fun clearSession(notice: String? = null) {
        monitorJob?.cancel()
        monitorJob = null
        playlistReportJob?.cancel()
        playlistReportJob = null
        clientSeq = 0L
        playlistVersion = 0L
        localUid = 0L
        applyingRemote = false
        lastPlaylistSig = null
        lastCommandSig = null
        lastOwnCommandSeq = 0L
        lastOwnCommandTargetId = 0L
        consecutiveFailures = 0
        _state.value = UiState(notice = notice)
    }

    private fun startMonitoring() {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            var tick = 0
            while (isActive && _state.value.room != null) {
                tick++
                try {
                    val room = _state.value.room ?: break
                    // MeiloX 原样：快照拉取失败直接抛 → catch 计数（连 2 次置 Reconnecting），
                    // 不再 getOrNull 静默吞掉。
                    val snapshot = ListenTogetherApi.snapshot(room.id).getOrThrow()
                    applyRemote(snapshot, initial = tick == 1)

                    // MeiloX 原样：每 5 tick（5s）同时复检房间状态 + 发心跳，失败同样抛给 catch 计数。
                    if (tick == 1 || tick % 5 == 0) {
                        refreshRoomStatus()
                        sendHeartbeat()
                    }

                    consecutiveFailures = 0
                    _state.value = _state.value.copy(
                        lastSyncMs = System.currentTimeMillis(),
                        reconnecting = false
                    )
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    consecutiveFailures++
                    if (consecutiveFailures >= 2) {
                        _state.value = _state.value.copy(reconnecting = true)
                    }
                }
                delay(1000)
            }
        }
    }

    private fun updateInviteUrl() {
        val room = _state.value.room ?: return
        // MeiloX updateInvitationUrl 原样：无歌时 buildInvitationUrl 返回 null，inviteUrl 随之清空。
        _state.value = _state.value.copy(
            inviteUrl = buildListenTogetherInviteUrl(
                roomId = room.id,
                inviterId = localUid.takeIf { it > 0 } ?: room.creatorId,
                songId = currentSongId()
            )
        )
    }

    private suspend fun refreshRoomStatus() {
        val expected = _state.value.room ?: return
        // MeiloX 原样：status 拉取失败直接抛 → 监控循环 catch 计数（连 2 次置 Reconnecting）。
        val status = ListenTogetherApi.status().getOrThrow()
        if (status.inRoom != true || status.room == null) {
            clearSession("房间已结束")
            return
        }
        if (status.room.id != expected.id) {
            clearSession("你已在另一个一起听房间")
            return
        }
        // MeiloX 原样：复检时同步刷新 isHost 判定与 inviteUrl（无歌时 inviteUrl 为 null）。
        _state.value = _state.value.copy(
            room = status.room,
            isHost = status.room.creatorId == localUid,
            inviteUrl = buildListenTogetherInviteUrl(
                roomId = status.room.id,
                inviterId = localUid.takeIf { it > 0 } ?: status.room.creatorId,
                songId = currentSongId()
            )
        )
    }

    /** 应用远端快照（签名 diff：列表/命令都没变则直接返回，不动播放器）。 */
    private suspend fun applyRemote(snapshot: LTSnapshot, initial: Boolean) {
        val room = _state.value.room ?: return
        val activePlayer = player ?: return
        val playlistSig = snapshot.playMode.orEmpty() + "|" + snapshot.songIds.joinToString(",")
        val commandSig = "${snapshot.serverSeq}|${snapshot.clientSeq}|${snapshot.commandType}|" +
            "${snapshot.targetSongId}|${snapshot.progressMs}|${snapshot.isPlaying}"
        val playlistChanged = playlistSig != lastPlaylistSig
        val commandChanged = commandSig != lastCommandSig
        if (!initial && !playlistChanged && !commandChanged) return

        // 回声识别：playCommand 的 clientSeq 等于我刚上报的那条 seq（且目标歌一致）
        // → 这不是别人的指令，而是我自己 1s 前的操作被服务端回显。
        // 若照样 seekTo(targetIndex)，本地刚做的选择会被自己的旧命令撤销：
        //   切歌单 → setMediaItems 把 index 重置为 0 → 发出 GOTO(#1)
        //   → 用户点第 5 首（此刻在 1s 抑制窗口内，GOTO(#5) 被丢弃）
        //   → 下一 tick 读到 GOTO(#1) → seekTo(#1) → 自动播第一首，每秒重复。
        // 所以「我发的、我本地已经执行过的」目标不再回灌；只落签名继续同步。
        val ownEcho = snapshot.clientSeq == lastOwnCommandSeq && lastOwnCommandSeq > 0L &&
            (snapshot.targetSongId ?: 0L) == lastOwnCommandTargetId
        if (ownEcho && !playlistChanged) {
            lastCommandSig = commandSig
            Log.d(TAG, "applyRemote skip own echo clientSeq=${snapshot.clientSeq} target=${snapshot.targetSongId}")
            return
        }
        // ownEcho && playlistChanged（我自己刚 REPLACE 的队列被回显）：队列要应用，
        // 但落点必须用**本地当前那首**，不能用回显里的旧 target（那正是「点第 5 首
        // 却被拉回第 1 首」的来源）—— 所以 ownEcho 时把 snapshot.targetSongId 置空，
        // 让下面的 fallback 走 currentSongId()。
        val remoteTarget = if (ownEcho) null else snapshot.targetSongId

        // 空快照（远端 data:{} / 无列表无命令）不落盘签名：否则会把真实的
        // lastPlaylistSig 覆盖成一个恒定空串，远端恢复同一份列表时签名恰好相同
        // → playlistChanged=false → 一次真实的队列变化被永久忽略。
        val emptySnapshot =
            snapshot.songIds.isEmpty() && snapshot.commandType == null && snapshot.targetSongId == null

        val localIds = (0 until activePlayer.mediaItemCount)
            .mapNotNull { activePlayer.getMediaItemAt(it).mediaId.toLongOrNull() }
        // 与 completeIds 比较用**完整 mediaId 字符串序列**（含非数字 id 的项），
        // 不用上面的 mapNotNull 过滤版：队列里混有本地文件/非 NCM 音源时，
        // 过滤版长度对不上会误判「一致/不一致」，进而错位复用或无谓全量重建。
        val localIdStrs = (0 until activePlayer.mediaItemCount)
            .map { activePlayer.getMediaItemAt(it).mediaId }
        val songIds = snapshot.songIds.ifEmpty { localIds }
        // ownEcho 时用本地当前那首作落点（remoteTarget 已被置空）。
        val targetId = remoteTarget
            ?: currentSongId()
            ?: songIds.firstOrNull()
            ?: error("房间无可同步的播放状态")
        Log.d(
            TAG,
            "applyRemote roomId=${room.id} initial=$initial playlistChanged=$playlistChanged " +
                "commandChanged=$commandChanged remoteTarget=${snapshot.targetSongId} ownEcho=$ownEcho " +
                "fallbackTarget=$targetId (fromRemote=${remoteTarget != null}) " +
                "localCur=${currentSongId()} localCount=${localIds.size} remoteCount=${songIds.size}"
        )
        val completeIds = if (targetId in songIds) songIds else songIds + targetId

        // 本地 id 序列与远端一致时直接复用现有 MediaItem，不重拉详情；
        // 不一致才按 100 条分片拉批量详情，缺详情的歌剔除（不再用占位 Song 兜底）。
        val sameAsLocal = completeIds.map { it.toString() } == localIdStrs
        val items = if (sameAsLocal) {
            (0 until activePlayer.mediaItemCount).map { activePlayer.getMediaItemAt(it) }
        } else {
            loadMediaItems(completeIds)
        }
        var targetIndex = items.indexOfFirst { it.mediaId == targetId.toString() }
        if (targetIndex < 0) {
            // 旧实现直接 check(...) 抛：远端列表里有一首拉不到详情（下架/VIP）就稳定命中，
            // 签名不落盘 → 下一 tick 重复失败 → reconnecting 常驻，整条列表同步永久卡死。
            // 降级：提示 + 落到列表首项，不中断同步循环。
            _state.value = _state.value.copy(
                notice = "房间列表中有歌曲无法加载（$targetId），已跳到列表开头"
            )
            Log.w(TAG, "target song unavailable id=$targetId loaded=${items.size}/${completeIds.size}")
            targetIndex = 0
        }

        applyingRemote = true
        suppressReportsUntilMs = SystemClock.elapsedRealtime() + 1000L
        try {
            if (!sameAsLocal) {
                // 远端缺 progress 时不要强制 0（旧实现 ?: 0L 会把正在播的歌打回曲头）；
                // 目标就是当前这首歌时沿用本地进度，否则从 0 起播。
                val startPos = snapshot.progressMs?.coerceAtLeast(0L)
                    ?: if (targetId == currentSongId()) activePlayer.currentPosition else 0L
                activePlayer.setMediaItems(items, targetIndex, startPos)
                activePlayer.prepare()
            } else if (initial || commandChanged) {
                activePlayer.seekTo(targetIndex, snapshot.progressMs?.coerceAtLeast(0L) ?: activePlayer.currentPosition)
            }
            val mode = snapshot.playMode?.uppercase()
            if (mode != null) {
                activePlayer.shuffleModeEnabled = "RANDOM" in mode || "SHUFFLE" in mode
            }
            snapshot.isPlaying?.let { playing ->
                if (playing) activePlayer.play() else activePlayer.pause()
            }
        } finally {
            applyingRemote = false
        }
        if (!emptySnapshot) {
            lastPlaylistSig = playlistSig
            lastCommandSig = commandSig
        }
    }

    /**
     * 上报当前歌心跳。返回的 timeSpan 不再驱动间隔，直接丢弃。
     *
     * isPlaying 用 **playWhenReady（播放意图）**而不是 player.isPlaying（有效输出）：
     * 缓冲/切歌瞬间 isPlaying=false，每 5s 的心跳会把房间状态写成 PAUSE，其他端跟着暂停。
     * 与 reportCommand 的捕获规则一致。
     */
    private suspend fun sendHeartbeat() {
        val room = _state.value.room ?: return
        val activePlayer = player ?: return
        val songId = currentSongId() ?: return
        ListenTogetherApi.heartbeat(
            roomId = room.id,
            songId = songId,
            isPlaying = activePlayer.playWhenReady,
            progressMs = activePlayer.currentPosition
        )
    }

    /** MeiloX loadMediaItems 原样：按 100 条分片拉批量歌曲详情（getOrThrow，任一分片失败整轮中止）；id→Song 保持序，缺详情的歌剔除。 */
    private suspend fun loadMediaItems(ids: List<Long>): List<MediaItem> {
        val byId = linkedMapOf<Long, Song>()
        ids.chunked(100).forEach { page ->
            ListenTogetherApi.songsByIds(page).getOrThrow()
                .forEach { byId[it.id] = it }
        }
        return ids.mapNotNull { id -> byId[id] }.toMediaItemList(sourceName = "一起听")
    }

    // ------------------------------------------------------------------
    // 本地播放事件 → 房间上报（Player.Listener）
    // ------------------------------------------------------------------

    private fun shouldReport() =
        _state.value.room != null &&
            !applyingRemote &&
            SystemClock.elapsedRealtime() >= suppressReportsUntilMs

    /**
     * 队列上报专用闸门：只判「在房里 + 不正在应用远端」，**不判抑制窗口**。
     * 切歌单必然同时触发 onMediaItemTransition（GOTO 把窗口推到 +1s）和
     * onTimelineChanged（REPLACE），若 REPLACE 也复用 [shouldReport]，350ms 防抖
     * 到期时仍在窗口内 → 队列上报被静默丢弃且不重试，房间列表停在旧值，而 GOTO
     * 已带着新列表的 targetSongId 发出 → 对端按旧列表对不上、回落到列表第一首。
     * REPLACE 是幂等全量写，被 applyRemote 回环触发也只是写回同一份 ids，
     * 代价远小于丢一次队列变更。
     */
    private fun canReportPlaylist() = _state.value.room != null && !applyingRemote

    private fun currentSongId(): Long? =
        player?.currentMediaItem?.mediaId?.toLongOrNull()?.takeIf { it > 0L }

    private suspend fun reportCommand(commandType: String, formerId: Long, targetId: Long, isPlaying: Boolean) {
        val room = _state.value.room ?: return
        val activePlayer = player ?: return
        val target = if (targetId > 0L) targetId else currentSongId() ?: return
        val seq = ++clientSeq
        val progress = activePlayer.currentPosition.coerceAtLeast(0L)
        // formerSongId 所有命令类型都发，缺失兜底 -1。
        val formerValue = formerId.takeIf { it > 0L } ?: -1L
        // isPlaying 由调用方在回调当下捕获（playWhenReady 表示播放意图）；
        // 协程不再延迟读 activePlayer.isPlaying（切歌 buffering 态会误判 PAUSE）。
        val playStatusValue = if (isPlaying) "\"PLAY\"" else "\"PAUSE\""
        val info = "{" +
            "\"commandType\":\"$commandType\"," +
            "\"progress\":$progress," +
            "\"playStatus\":$playStatusValue," +
            "\"formerSongId\":" + formerValue + "," +
            "\"targetSongId\":$target," +
            "\"clientSeq\":$seq}"
        // 失败真正抛出后 catch → reconnecting。
        // 注意：reportCommand 返回 Result（内部 requireOk/网络错都不抛），必须 getOrThrow 把失败
        // 转成异常，否则 runCatching catch 不到、不会置 reconnecting。
        runCatching { ListenTogetherApi.reportCommand(room.id, info).getOrThrow() }
            .onSuccess {
                // 记下「这条是我发的」，供 applyRemote 做回声识别（见 lastOwnCommandSeq）。
                lastOwnCommandSeq = seq
                lastOwnCommandTargetId = target
            }
            .onFailure { _state.value = _state.value.copy(reconnecting = true) }
    }

    private suspend fun reportPlaylist() {
        val room = _state.value.room ?: return
        val activePlayer = player ?: return
        val ids = (0 until activePlayer.mediaItemCount)
            .mapNotNull { activePlayer.getMediaItemAt(it).mediaId.toLongOrNull() }
        // 队列为空 / 账号未读到 → 抛（调用方 catch → reconnecting）。
        check(ids.isNotEmpty()) { "请先播放一首歌曲" }
        check(localUid > 0L) { "当前账号未读取，请重新登录" }
        // 队列 version 用**独立计数器**（playlistVersion），不复用 clientSeq：
        // clientSeq 是服务端播放命令的序号语义，与队列 version 交替递增会让服务端
        // 把队列判成旧值而拒 REPLACE（表现为 data.result=false）。
        // playlistVersion 不在 establish() 里归零：清会话（clearSession）才归零，
        // 同一房间重进（refresh → establish）时保持单调。
        val version = ++playlistVersion
        // anchorSongId/anchorPosition 真实上报当前播放位置，别再让服务端把
        // 房间锚点当成「清空 + 从头」（旧实现恒填 "" / -1，切歌单即抹掉锚点）。
        val anchorSongId = currentSongId() ?: 0L
        val anchorPosition = activePlayer.currentMediaItemIndex.takeIf { it >= 0 } ?: 0
        // API 调用直接抛（不在内部包 runCatching），由调用方处理：
        // onTimelineChanged → runCatching 置 reconnecting；create → try/catch 中止后续并 error。
        ListenTogetherApi.reportPlaylist(
            roomId = room.id,
            userId = localUid,
            version = version,
            songIds = ids,
            anchorSongId = anchorSongId,
            anchorPosition = anchorPosition
        ).getOrThrow()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val target = mediaItem?.mediaId?.toLongOrNull() ?: 0L
        val pwReady = player?.playWhenReady ?: true
        // reason == MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED 是 setMediaItems 换队列时
        // Media3 把 currentIndex 重置为 0 而触发的**合成切歌**，不是用户选歌：跟着它发 GOTO
        // 等于告诉房间「切到新歌单第一首」。用户随后点第 N 首，那条 GOTO 才是真实意图，
        // 但它落在下面 onMediaItemTransition 推满的 1s 抑制窗口里会被丢弃 → 服务端停留在
        // 合成的 #1 → 下一 tick 回灌 seekTo(#1) → 点第 5 首却自动播第 1 首。
        // 所以这里直接不报，位置由 onTimelineChanged 的 REPLACE(anchorSongId/anchorPosition) 表达，
        // 用户真正的选歌由它自己触发的 transition（reason=SEEK/AUTO）上报。
        val syntheticPlaylistSwitch = reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED
        if (!syntheticPlaylistSwitch && shouldReport() && target > 0L) {
            // 切歌时串行化：抑制窗口 1s 挡住并发的 PROGRESS 与 GOTO 竞争同一 seq。
            suppressReportsUntilMs = SystemClock.elapsedRealtime() + 1_000
            scope.launch { reportCommand("GOTO", formerSongId, target, pwReady) }
        }
        formerSongId = target
        updateInviteUrl()
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        val activePlayer = player ?: return
        if (!shouldReport()) return
        if (!playWhenReady && activePlayer.playbackState == Player.STATE_BUFFERING) return
        val target = currentSongId() ?: return
        scope.launch {
            reportCommand(if (playWhenReady) "PLAY" else "PAUSE", target, target, playWhenReady)
        }
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) {
        if (reason != Player.DISCONTINUITY_REASON_SEEK || !shouldReport()) return
        val target = currentSongId() ?: return
        val pwReady = player?.playWhenReady ?: true
        scope.launch { reportCommand("PROGRESS", target, target, pwReady) }
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        // 只对「队列真的换了」反应（TIMELINE_CHANGE_REASON_SOURCE_UPDATE 等不重排队列），
        // 起防抖 job 只会白白推高队列 version（与服务端命令 clientSeq 语义混淆）。
        if (reason != Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) return
        if (!canReportPlaylist()) return
        playlistReportJob?.cancel()
        // 注意：这里刻意不判 suppressReportsUntilMs（见 canReportPlaylist）。
        // 防抖 350ms 后复查一次；applyRemote 的 setMediaItems 触发的回环因
        // applyingRemote=true 在回调当下就被挡掉，不会把远端队列原样写回。
        playlistReportJob = scope.launch {
            delay(350)
            if (canReportPlaylist()) {
                runCatching { reportPlaylist() }
                    .onFailure { error ->
                        _state.value = _state.value.copy(reconnecting = true)
                        Log.w(TAG, "reportPlaylist failed: ${error.message}")
                    }
            }
        }
    }
}
