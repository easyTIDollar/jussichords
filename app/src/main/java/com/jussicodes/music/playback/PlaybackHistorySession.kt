package com.jussicodes.music.playback

import androidx.media3.common.Player
import com.rcmiku.ncmapi.model.CompletedPlaybackSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 播放历史计时器（移植自 MeiloX PlaybackHistorySession）。
 *
 * 跟踪一个逻辑媒资项的真实播放时长：
 * - 只在 isPlaying 时累计 elapsedRealtime 区间（暂停/切歌不计入）；
 * - startlogtime 用墙钟（System.currentTimeMillis）首次进入播放的时刻；
 * - 切歌/seek/repeat 结束旧会话并开新会话；playend/ui/interrupt 区分结束原因。
 */
internal class PlaybackHistorySession {

    /** 会话完成时的计时快照（不含歌曲元数据，元数据由调用方在离开前从 MediaItem 缓存）。 */
    data class CompletedSession(
        val mediaId: String,
        val playedDurationMs: Long,
        val startedAtMs: Long,
        val endReason: String
    )

    data class SessionUpdate(
        val startedAtMs: Long? = null,
        val completed: CompletedSession? = null
    )

    private var currentMediaId: String? = null
    private var startedAtMs: Long? = null
    private var playedDurationMs = 0L
    private var playingSinceRealtimeMs: Long? = null

    /**
     * 媒资项切换（onMediaItemTransition）：AUTO/REPEAT 计为自然播完（playend），
     * SEEK 计为用户切歌（ui），其余（PLAYLIST_CHANGED 等）计为 interrupt。
     * 返回被结束的旧会话（若有）。
     */
    fun onMediaItemTransition(
        mediaId: String?,
        reason: Int,
        realtimeMs: Long
    ): CompletedSession? {
        val startsNewSession = mediaId != currentMediaId || reason in NEW_SESSION_TRANSITION_REASONS
        if (!startsNewSession) {
            currentMediaId = mediaId
            return null
        }
        val endReason = when (reason) {
            Player.MEDIA_ITEM_TRANSITION_REASON_AUTO,
            Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> "playend"
            Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> "ui"
            else -> "interrupt"
        }
        val completed = finish(realtimeMs, endReason)
        currentMediaId = mediaId
        return completed
    }

    /**
     * 播放状态/位置变化时调用。首次进入播放记录墙钟开始时间；
     * mediaId 变化时先结算旧会话。返回会话更新（新开始时间 / 已结束会话）。
     */
    fun update(
        mediaId: String?,
        isPlaying: Boolean,
        wallClockMs: Long,
        realtimeMs: Long
    ): SessionUpdate {
        val completed = if (mediaId != currentMediaId) {
            finish(realtimeMs).also { currentMediaId = mediaId }
        } else {
            null
        }

        if (!isPlaying || mediaId == null) {
            pauseTimer(realtimeMs)
            return SessionUpdate(completed = completed)
        }

        val started = if (this.startedAtMs == null) {
            this.startedAtMs = wallClockMs
            wallClockMs
        } else {
            null
        }
        if (playingSinceRealtimeMs == null) {
            playingSinceRealtimeMs = realtimeMs
        }
        return SessionUpdate(startedAtMs = started, completed = completed)
    }

    /** 强制结算当前会话（服务销毁等场景），返回已完成记录。 */
    fun finish(
        realtimeMs: Long,
        endReason: String = "interrupt"
    ): CompletedSession? {
        pauseTimer(realtimeMs)
        val mediaId = currentMediaId
        val startedAt = startedAtMs
        val completed = if (startedAt != null && mediaId != null) {
            CompletedSession(mediaId, playedDurationMs, startedAt, endReason)
        } else {
            null
        }
        startedAtMs = null
        playedDurationMs = 0L
        playingSinceRealtimeMs = null
        return completed
    }

    private fun pauseTimer(realtimeMs: Long) {
        val playingSince = playingSinceRealtimeMs ?: return
        playedDurationMs += (realtimeMs - playingSince).coerceAtLeast(0L)
        playingSinceRealtimeMs = null
    }

    companion object {
        private val NEW_SESSION_TRANSITION_REASONS = setOf(
            Player.MEDIA_ITEM_TRANSITION_REASON_AUTO,
            Player.MEDIA_ITEM_TRANSITION_REASON_SEEK,
            Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT,
        )

        /**
         * 把计时快照 + 媒体元数据（离开前缓存）装配成 ncmapi 的 [CompletedPlaybackSession]。
         */
        fun toCompleted(
            completed: CompletedSession,
            songId: Long,
            sourceId: Long,
            source: String,
            endedAtMs: Long,
            songName: String = "",
            songArtist: String = "",
            songDurationMs: Long = 0L
        ): CompletedPlaybackSession =
            CompletedPlaybackSession(
                mediaId = completed.mediaId,
                songId = songId,
                sourceId = sourceId,
                source = source,
                startedAtMs = completed.startedAtMs,
                playedDurationMs = completed.playedDurationMs,
                endedAtMs = endedAtMs,
                endReason = completed.endReason,
                songName = songName,
                songArtist = songArtist,
                songDurationMs = songDurationMs,
            )
    }
}

/**
 * 持久化类协程（对齐 MeiloX launchPlaybackHistoryPersistence）：
 * 一旦进入 IO 段即挂到 NonCancellable，父作用域被取消也不会中断正在上报的写操作。
 */
internal fun CoroutineScope.launchPlaybackHistoryPersistence(
    block: suspend () -> Unit
): Job = launch(start = CoroutineStart.UNDISPATCHED) {
    withContext(Dispatchers.IO + NonCancellable) {
        block()
    }
}
