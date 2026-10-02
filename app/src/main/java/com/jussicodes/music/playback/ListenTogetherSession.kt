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
    private var localUid: Long = 0L
    private var formerSongId: Long = 0L
    @Volatile
    private var applyingRemote = false
    @Volatile
    private var suppressReportsUntilMs = 0L
    private var lastPlaylistSig: String? = null
    private var lastCommandSig: String? = null
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
                Log.d(TAG, "create ok roomId=${room.id} isHost=${_state.value.isHost} localUid=$localUid")
                reportPlaylist()
                reportCommand("GOTO", songId, songId, player?.playWhenReady ?: true)
                sendHeartbeat()
                startMonitoring()
                _state.value = _state.value.copy(isLoading = false)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _state.value = _state.value.copy(isLoading = false, error = error.message)
            }
        }
    }

    /** 从官方分享链接文本进房。 */
    fun join(invitationText: String) {
        if (actionJob?.isActive == true) return
        actionJob = scope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, notice = null)
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
                Log.d(TAG, "join ok roomId=${room.id} inviterId=$inviterId isHost=${_state.value.isHost} localUid=$localUid")
                // MeiloX 原样：初始同步失败直接抛 → 下方 catch 置 error（不进监测，不展开播放页）。
                applyRemote(ListenTogetherApi.snapshot(room.id).getOrThrow(), initial = true)
                sendHeartbeat()
                startMonitoring()
                // 接收端进房成功：自动展开全屏播放界面，落到 host 当前那首歌。
                // host（自己重进自己的房间）不展开，保持现状。
                if (!_state.value.isHost) PlayerExpandBus.expand()
                _state.value = _state.value.copy(isLoading = false)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _state.value = _state.value.copy(isLoading = false, error = error.message)
            }
        }
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
        clientSeq = 0L
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
        localUid = 0L
        applyingRemote = false
        lastPlaylistSig = null
        lastCommandSig = null
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

        val localIds = (0 until activePlayer.mediaItemCount)
            .mapNotNull { activePlayer.getMediaItemAt(it).mediaId.toLongOrNull() }
        val songIds = snapshot.songIds.ifEmpty { localIds }
        // MeiloX 原样：三处 fallback 全空 → 房间无有效播放状态，抛（监控循环 catch 计数）。
        val targetId = snapshot.targetSongId
            ?: currentSongId()
            ?: songIds.firstOrNull()
            ?: error("房间无可同步的播放状态")
        Log.d(
            TAG,
            "applyRemote roomId=${room.id} initial=$initial playlistChanged=$playlistChanged " +
                "commandChanged=$commandChanged remoteTarget=${snapshot.targetSongId} " +
                "fallbackTarget=$targetId (fromRemote=${snapshot.targetSongId != null}) " +
                "localCur=${currentSongId()} localCount=${localIds.size} remoteCount=${songIds.size}"
        )
        val completeIds = if (targetId in songIds) songIds else songIds + targetId

        // MeiloX 原样：本地 id 序列与远端一致时直接复用现有 MediaItem，不重拉详情；
        // 不一致才按 100 条分片拉批量详情，缺详情的歌剔除（不再用占位 Song 兜底）。
        val items = if (completeIds == localIds) {
            (0 until activePlayer.mediaItemCount).map { activePlayer.getMediaItemAt(it) }
        } else {
            loadMediaItems(completeIds)
        }
        val targetIndex = items.indexOfFirst { it.mediaId == targetId.toString() }
        check(targetIndex >= 0) { "目标歌曲未在播放列表中: $targetId" }

        applyingRemote = true
        suppressReportsUntilMs = SystemClock.elapsedRealtime() + 1000L
        try {
            if (completeIds != localIds) {
                activePlayer.setMediaItems(items, targetIndex, snapshot.progressMs?.coerceAtLeast(0L) ?: 0L)
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
        lastPlaylistSig = playlistSig
        lastCommandSig = commandSig
    }

    /** MeiloX sendHeartbeat 原样：上报当前歌心跳（Models heartbeat 内部已 getOrThrow+requireOk，失败抛给调用方 catch 计数）；返回的 timeSpan 不再驱动间隔，直接丢弃。 */
    private suspend fun sendHeartbeat() {
        val room = _state.value.room ?: return
        val activePlayer = player ?: return
        val songId = currentSongId() ?: return
        ListenTogetherApi.heartbeat(
            roomId = room.id,
            songId = songId,
            isPlaying = activePlayer.isPlaying,
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
        // MeiloX 原样：失败真正抛出后 catch → markReconnecting。
        // 注意：reportCommand 返回 Result（内部 requireOk/网络错都不抛），必须 getOrThrow 把失败
        // 转成异常，否则 runCatching catch 不到、不会置 reconnecting。
        runCatching { ListenTogetherApi.reportCommand(room.id, info).getOrThrow() }
            .onFailure { _state.value = _state.value.copy(reconnecting = true) }
    }

    private suspend fun reportPlaylist() {
        val room = _state.value.room ?: return
        val activePlayer = player ?: return
        val ids = (0 until activePlayer.mediaItemCount)
            .mapNotNull { activePlayer.getMediaItemAt(it).mediaId.toLongOrNull() }
        // MeiloX 原样：队列空 / 账号未读到 → 抛（调用方 catch → markReconnecting）。
        check(ids.isNotEmpty()) { "请先播放一首歌曲" }
        check(localUid > 0L) { "当前账号未读取，请重新登录" }
        val version = ++clientSeq
        // MeiloX 原样：API 调用直接抛（不在内部包 runCatching），由调用方处理：
        // onTimelineChanged → runCatching 置 reconnecting；create → try/catch 中止后续并 error。
        ListenTogetherApi.reportPlaylist(room.id, localUid, version, ids).getOrThrow()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val target = mediaItem?.mediaId?.toLongOrNull() ?: 0L
        val pwReady = player?.playWhenReady ?: true
        if (shouldReport() && target > 0L) {
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
        if (!shouldReport()) return
        playlistReportJob?.cancel()
        // MeiloX 原样：reportPlaylist 的 check()/API 失败不 crash，runCatching 置 reconnecting。
        playlistReportJob = scope.launch {
            delay(350)
            if (shouldReport()) {
                runCatching { reportPlaylist() }
                    .onFailure { _state.value = _state.value.copy(reconnecting = true) }
            }
        }
    }
}
