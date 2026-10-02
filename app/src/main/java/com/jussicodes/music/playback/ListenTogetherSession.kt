package com.jussicodes.music.playback

import android.content.Context
import android.os.SystemClock
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
 * - 应用远端状态时置 [applyingRemote] + 1.5s 抑制窗口，防止「应用别人切歌 →
 *   触发本地回调 → 又报回去」的回环；
 * - 心跳（/heartbeat）按 NCM 返回的 timeSpan 间隔发送，防止单人群 NCM 自动回收房间。
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
    @Volatile
    private var lastHeartbeatAt = 0L
    @Volatile
    private var heartbeatSpanSec = 30
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
        if (CookieProvider.isLoggedIn()) refresh()
    }

    fun detach() {
        player?.removeListener(this)
        player = null
        appContext = null
        monitorJob?.cancel()
        monitorJob = null
        playlistReportJob?.cancel()
        playlistReportJob = null
        clientSeq = 0L
        localUid = 0L
        _state.value = _state.value.copy(room = null, reconnecting = false)
    }

    // ------------------------------------------------------------------
    // 用户动作
    // ------------------------------------------------------------------

    fun refresh() {
        if (actionJob?.isActive == true) return
        actionJob = scope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val status = ListenTogetherApi.status().getOrNull()
                val room = status?.room
                if (status?.inRoom != true || room == null) {
                    clearSession()
                } else {
                    loadLocalUid()
                    establish(room)
                    val snapshot = ListenTogetherApi.snapshot(room.id).getOrNull()
                    if (snapshot != null) applyRemote(snapshot, initial = true)
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
                reportPlaylist()
                reportCommand("GOTO", songId, songId)
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
                val snapshot = ListenTogetherApi.snapshot(room.id).getOrNull()
                if (snapshot != null) applyRemote(snapshot, initial = true)
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
        val snapshot = ListenTogetherApi.snapshot(room.id).getOrNull()
        if (snapshot != null) applyRemote(snapshot, initial = true)
        sendHeartbeat()
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
            inviteUrl = buildListenTogetherInviteUrl(
                roomId = room.id,
                inviterId = localUid.takeIf { it > 0 } ?: room.creatorId,
                songId = currentSongId() ?: 0L
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
                    val snapshot = ListenTogetherApi.snapshot(room.id).getOrNull()
                    if (snapshot != null) applyRemote(snapshot, initial = tick == 1)

                    if (SystemClock.elapsedRealtime() - lastHeartbeatAt >= heartbeatSpanSec * 1000L) {
                        sendHeartbeat()
                    }
                    if (tick % 10 == 0) refreshRoomStatus()

                    consecutiveFailures = 0
                    if (_state.value.reconnecting) {
                        _state.value = _state.value.copy(reconnecting = false)
                    }
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

    private suspend fun refreshRoomStatus() {
        val expected = _state.value.room ?: return
        val status = ListenTogetherApi.status().getOrNull() ?: return
        if (status.inRoom != true || status.room == null) {
            clearSession("房间已结束")
            return
        }
        if (status.room.id != expected.id) {
            clearSession("你已在另一个一起听房间")
            return
        }
        _state.value = _state.value.copy(room = status.room)
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
        val targetId = snapshot.targetSongId
            ?: currentSongId()
            ?: songIds.firstOrNull()
            ?: return
        val completeIds = if (targetId in songIds) songIds else songIds + targetId
        if (completeIds.isEmpty()) return

        val remoteSongs = runCatching {
            ListenTogetherApi.songsByIds(completeIds).getOrNull()
        }.getOrNull().orEmpty()
        val items = completeIds.map { id ->
            remoteSongs.firstOrNull { it.id == id } ?: Song(id = id, name = "歌曲")
        }.toMediaItemList(sourceName = "一起听")
        val targetIndex = items.indexOfFirst { it.mediaId == targetId.toString() }.coerceAtLeast(0)

        applyingRemote = true
        suppressReportsUntilMs = SystemClock.elapsedRealtime() + 1500L
        try {
            if (completeIds != localIds) {
                activePlayer.setMediaItems(items, targetIndex, snapshot.progressMs.coerceAtLeast(0L))
                activePlayer.prepare()
            } else if (initial || commandChanged) {
                activePlayer.seekTo(targetIndex, snapshot.progressMs.coerceAtLeast(0L))
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
        _state.value = _state.value.copy(
            lastSyncMs = System.currentTimeMillis(),
            inviteUrl = _state.value.inviteUrl
                ?: buildListenTogetherInviteUrl(room.id, localUid, targetId)
        )
    }

    /** 按 NCM timeSpan 间隔发心跳，保活房间（单人房 NCM 会回收无心跳房间）。 */
    private fun sendHeartbeat() {
        val room = _state.value.room ?: return
        val activePlayer = player ?: return
        val songId = currentSongId() ?: return
        scope.launch {
            val span = runCatching {
                ListenTogetherApi.heartbeat(
                    roomId = room.id,
                    songId = songId,
                    isPlaying = activePlayer.isPlaying,
                    progressMs = activePlayer.currentPosition
                )
            }.getOrNull()
            if (span != null) heartbeatSpanSec = span
            lastHeartbeatAt = SystemClock.elapsedRealtime()
        }
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

    private suspend fun reportCommand(commandType: String, formerId: Long, targetId: Long) {
        val room = _state.value.room ?: return
        val activePlayer = player ?: return
        val target = if (targetId > 0L) targetId else currentSongId() ?: return
        val seq = ++clientSeq
        val progress = activePlayer.currentPosition.coerceAtLeast(0L)
        val formerValue = if (commandType == "GOTO") formerId.takeIf { it > 0L } ?: -1L else -1L
        val playStatusValue = if (activePlayer.isPlaying) "\"PLAY\"" else "\"PAUSE\""
        val info = "{" +
            "\"commandType\":\"$commandType\"," +
            "\"progress\":$progress," +
            "\"playStatus\":$playStatusValue," +
            "\"formerSongId\":" + formerValue + "," +
            "\"targetSongId\":$target," +
            "\"clientSeq\":$seq}"
        runCatching { ListenTogetherApi.reportCommand(room.id, info) }
            .onFailure { _state.value = _state.value.copy(reconnecting = true) }
    }

    private suspend fun reportPlaylist() {
        val room = _state.value.room ?: return
        val activePlayer = player ?: return
        val ids = (0 until activePlayer.mediaItemCount)
            .mapNotNull { activePlayer.getMediaItemAt(it).mediaId.toLongOrNull() }
        if (ids.isEmpty() || localUid <= 0L) return
        val version = ++clientSeq
        runCatching { ListenTogetherApi.reportPlaylist(room.id, localUid, version, ids) }
            .onFailure { _state.value = _state.value.copy(reconnecting = true) }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val target = mediaItem?.mediaId?.toLongOrNull() ?: 0L
        if (shouldReport() && target > 0L) {
            scope.launch { reportCommand("GOTO", formerSongId, target) }
        }
        formerSongId = target
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        val activePlayer = player ?: return
        if (!shouldReport()) return
        if (!playWhenReady && activePlayer.playbackState == Player.STATE_BUFFERING) return
        val target = currentSongId() ?: return
        scope.launch {
            reportCommand(if (playWhenReady) "PLAY" else "PAUSE", target, target)
        }
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) {
        if (reason != Player.DISCONTINUITY_REASON_SEEK || !shouldReport()) return
        val target = currentSongId() ?: return
        scope.launch { reportCommand("PROGRESS", target, target) }
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        if (!shouldReport()) return
        playlistReportJob?.cancel()
        playlistReportJob = scope.launch {
            delay(350)
            if (shouldReport()) reportPlaylist()
        }
    }
}
