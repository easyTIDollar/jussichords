package com.jussicodes.music.playback

import android.util.Log
import com.jussicodes.music.playback.PlaybackHistorySession.CompletedSession
import com.rcmiku.ncmapi.api.account.AccountApi
import com.rcmiku.ncmapi.model.CompletedPlaybackSession
import com.rcmiku.ncmapi.ncbl.NcblCredentials
import com.rcmiku.ncmapi.ncbl.NcblClientProfile
import com.rcmiku.ncmapi.ncbl.NcblDeviceInfo
import com.rcmiku.ncmapi.ncbl.NcblSessionContext
import com.rcmiku.ncmapi.ncbl.NeteaseClientLogClient
import com.rcmiku.ncmapi.ncbl.buildNcblSession
import com.rcmiku.ncmapi.utils.CookieProvider
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * 播放历史上报器（移植自 MeiloX PlaybackHistoryReporter，适配 jussichords 双通道）：
 *
 * - eapi feedback/weblog（osx 桌面 profile）：begin 时发 startplay，会话结束时发
 *   play（实际收听秒 + end 原因），两条日志成对落 NCM 播放历史。
 * - NCBL clientlogsf：_plv（start）+ _pld（end），第二条写通道。
 *
 * 两条通道都在独立 IO 协程的串行队列里提交，绝不阻塞播放；失败只打日志。
 * 歌曲元数据（name/artist/duration/source）在「离开该曲前」由调用方缓存进
 * [ActivePlayback]，结束时（原 item 已不在 currentMediaItem 上）仍可用。
 */
internal class PlaybackHistoryReporter(
    private val ncblClient: NeteaseClientLogClient,
    private val ncblDevice: NcblDeviceInfo,
    private val ncblDeviceId: () -> String
) {
    private class ActivePlayback(
        val mediaId: String,
        val songId: Long,
        val sourceId: Long,
        val source: String,
        val startedAtMs: Long,
        val songName: String,
        val songArtist: String,
        val songDurationMs: Long
    ) {
        /** NCBL 会话在 start 上报完成后挂到这里；duration 上报读（enqueue 链保证顺序）。 */
        var ncblSession: NcblSessionContext? = null
    }

    private val lock = Any()
    private val reporterJob = SupervisorJob()
    private val reporterScope = CoroutineScope(reporterJob + Dispatchers.IO)
    private var active: ActivePlayback? = null
    private var submissionJob: Job? = null
    private var closed = false

    /**
     * 歌曲开始播放（update() 首次报告墙钟开始时刻）。sourceId/source/元数据由调用方在切歌前
     * 从 MediaItem extras 解析后传入。
     */
    fun begin(
        mediaId: String,
        songId: Long,
        sourceId: Long,
        source: String,
        startedAtMs: Long,
        songName: String,
        songArtist: String,
        songDurationMs: Long
    ) {
        if (songId <= 0L) return
        val playback = ActivePlayback(
            mediaId = mediaId,
            songId = songId,
            sourceId = sourceId,
            source = source,
            startedAtMs = startedAtMs,
            songName = songName,
            songArtist = songArtist,
            songDurationMs = songDurationMs,
        )
        synchronized(lock) {
            if (closed || active?.mediaId == mediaId) return
            active = playback
            enqueue("start") {
                coroutineScope {
                    launch { submitWeblogStart(playback) }
                    launch { submitNcblStart(playback) }
                }
            }
        }
    }

    /** 会话结束（切歌 / 自然播完 / 服务销毁）：结算计时并双通道上报。 */
    fun end(
        completed: CompletedSession,
        endedAtMs: Long
    ) {
        val playback = synchronized(lock) {
            val p = active
            if (closed || p?.mediaId != completed.mediaId) return
            active = null
            p
        }
        val timeSeconds = completed.playedDurationMs.coerceAtLeast(0L) / 1_000L
        val durationSession = CompletedPlaybackSession(
            mediaId = completed.mediaId,
            songId = playback.songId,
            sourceId = playback.sourceId,
            source = playback.source,
            startedAtMs = completed.startedAtMs,
            playedDurationMs = completed.playedDurationMs,
            endedAtMs = endedAtMs,
            endReason = completed.endReason,
            songName = playback.songName,
            songArtist = playback.songArtist,
            songDurationMs = playback.songDurationMs,
        )
        enqueue("duration") {
            coroutineScope {
                launch { submitWeblogDuration(durationSession, timeSeconds) }
                // 这里再读 ncblSession：start 批次先 join（enqueue 链），此时已写入。
                launch { submitNcblDuration(playback, durationSession, endedAtMs) }
            }
        }
    }

    /** 切到新歌（旧会话已结束）：清理未结算的旧会话缓存。 */
    fun onMediaTransition() {
        synchronized(lock) {
            active = null
        }
    }

    /** 排空 pending 上报并拒绝后续事件（服务销毁）。 */
    fun close() {
        val pending = synchronized(lock) {
            if (closed) return
            closed = true
            submissionJob
        }
        if (pending == null) {
            reporterJob.cancel()
            return
        }
        reporterScope.launch {
            pending.join()
            reporterJob.cancel()
        }
    }

    private fun enqueue(operation: String, block: suspend () -> Unit) {
        synchronized(lock) {
            val previous = submissionJob
            submissionJob = reporterScope.launch {
                previous?.join()
                try {
                    block()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.w(TAG, "playback history submit failed op=$operation: $error")
                }
            }
        }
    }

    // ---- eapi weblog（主通道）----

    private suspend fun submitWeblogStart(playback: ActivePlayback) {
        AccountApi.recordPlaybackStart(
            songId = playback.songId,
            sourceId = playback.sourceId,
            source = playback.source,
            startedAtMs = playback.startedAtMs,
        )
            .onSuccess { resp -> logWeblog("startplay", resp.code, resp.message ?: resp.msg) }
            .onFailure { Log.w(TAG, "weblog startplay failed: ${it.message}") }
    }

    private suspend fun submitWeblogDuration(
        session: CompletedPlaybackSession,
        timeSeconds: Long
    ) {
        AccountApi.recordPlaybackDuration(session)
            .onSuccess { resp -> logWeblog("play(time=$timeSeconds)", resp.code, resp.message ?: resp.msg) }
            .onFailure { Log.w(TAG, "weblog play failed: ${it.message}") }
    }

    private fun logWeblog(action: String, code: Int?, message: String?) {
        if (code in 200..299) {
            Log.i(TAG, "weblog $action accepted code=$code")
        } else {
            Log.w(TAG, "weblog $action rejected code=$code msg=$message")
        }
    }

    // ---- NCBL clientlogsf（第二通道）----

    private suspend fun submitNcblStart(playback: ActivePlayback) {
        val musicU = CookieProvider.getCookieMap()["MUSIC_U"].orEmpty()
        if (musicU.isBlank() || playback.sourceId <= 0L) return
        val session = buildNcblSession(
            session = CompletedPlaybackSession(
                mediaId = playback.mediaId,
                songId = playback.songId,
                sourceId = playback.sourceId,
                source = playback.source,
                startedAtMs = playback.startedAtMs,
                playedDurationMs = 0L,
                endedAtMs = 0L,
                endReason = "",
                songName = playback.songName,
                songArtist = playback.songArtist,
                songDurationMs = playback.songDurationMs,
            ),
            credentials = NcblCredentials(musicU = musicU, deviceId = ncblDeviceId()),
            device = ncblDevice,
            profile = NcblClientProfile.Android,
            sessionId = UUID.randomUUID().toString(),
        )
        val accepted = ncblClient.beginSession(session) ?: return
        playback.ncblSession = accepted
        val result = ncblClient.submitStart(accepted, eventTimeMs = playback.startedAtMs)
        if (result.fileAccepted) Log.i(TAG, "NCBL _plv accepted") else Log.w(TAG, "NCBL _plv not accepted")
    }

    private suspend fun submitNcblDuration(
        playback: ActivePlayback,
        duration: CompletedPlaybackSession,
        endedAtMs: Long
    ) {
        val session = playback.ncblSession ?: return
        val result = ncblClient.submitEnd(
            session = session,
            playedDurationMs = duration.playedDurationMs,
            eventTimeMs = endedAtMs,
            endReason = duration.endReason,
        )
        if (result.fileAccepted) Log.i(TAG, "NCBL _pld accepted") else Log.w(TAG, "NCBL _pld not accepted")
    }

    companion object {
        private const val TAG = "PlaybackHistory"
    }
}
