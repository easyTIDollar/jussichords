package com.jussicodes.music.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Process
import android.os.SystemClock
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.metadata.MetadataOutput
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.video.VideoRendererEventListener
import androidx.media3.session.CommandButton
import androidx.media3.session.CommandButton.ICON_UNDEFINED
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.datastore.preferences.core.edit
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.AsyncFunction
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.jussicodes.music.MainActivity
import com.jussicodes.music.R
import com.jussicodes.music.constants.MediaSessionConstants
import com.jussicodes.music.constants.audioEffectEightDEnabledKey
import com.jussicodes.music.constants.audioEffectIntensityKey
import com.jussicodes.music.constants.audioEffectModeKey
import com.jussicodes.music.constants.audioEffectReverbEnabledKey
import com.jussicodes.music.constants.audioEffectSpeedKey
import com.jussicodes.music.constants.audioQualityKey
import com.jussicodes.music.constants.autoSkipNextOnErrorKey
import com.jussicodes.music.constants.desktopLyricEnabledKey
import com.jussicodes.music.constants.desktopLyricLockedKey
import com.jussicodes.music.constants.use40DpIconKey
import com.jussicodes.music.data.favoriteSongIdsDatastore
import com.jussicodes.music.extensions.updateMediaItemUri
import com.jussicodes.music.lyric.DesktopLyricManager
import com.jussicodes.music.playback.audio.AudioEffectMode
import com.jussicodes.music.playback.audio.PlaybackAudioProcessors
import com.jussicodes.music.utils.AudioCache
import com.jussicodes.music.utils.FavoriteSongAction
import com.jussicodes.music.utils.UserAgentUtil
import com.jussicodes.music.utils.dataStore
import com.jussicodes.music.utils.enumPreference
import com.jussicodes.music.utils.get
import com.jussicodes.music.utils.preference
import com.jussicodes.music.utils.toEnum
import com.jussicodes.music.widget.WidgetHub
import com.jussicodes.music.widget.WidgetUpdater
import com.jussicodes.music.BuildConfig
import com.rcmiku.ncmapi.api.player.SongLevel
import com.rcmiku.ncmapi.model.Song
import com.rcmiku.ncmapi.ncbl.NcblDeviceInfo
import com.rcmiku.ncmapi.ncbl.NeteaseClientLogClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import com.rcmiku.ncmapi.utils.CookieProvider
import com.rcmiku.ncmapi.utils.json

@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var favoriteSongIds: List<Long> by mutableStateOf(emptyList())

    /**
     * 桌面小组件刷新：只在播放状态 / 曲目 / 元数据 / 队列变化时推一次快照，
     * 组件侧不做定时轮询。事件会成串到达，WidgetUpdater 内部合并。
     */
    private val widgetStateListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_MEDIA_METADATA_CHANGED,
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                    Player.EVENT_TIMELINE_CHANGED,
                    // seek / 重播 / 循环回到开头：只加这一条，组件进度才会跟着拖动的进度条走，
                    // 否则要等下一次状态变化才对齐（用户看到的就是「组件和 App 不同步」）。
                    Player.EVENT_POSITION_DISCONTINUITY,
                )
            ) {
                WidgetUpdater.refresh(applicationContext, player)
            }
        }
    }

    /** 播放队列落盘：进程被杀后媒体会话还能把上次的队列和进度恢复出来。 */
    private val queueStore by lazy { PlaybackQueueStore.get(this) }

    /**
     * 队列持久化监听：切歌 / 上下架（时间线变化）/ 播放暂停 / 就绪 时写盘。
     * 写盘本身是 apply() + 队列未变时跳过，不进播放热路径。
     */
    private val queuePersistListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            persistQueue()
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            persistQueue()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            persistQueue()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY ||
                playbackState == Player.STATE_PAUSED ||
                playbackState == Player.STATE_ENDED
            ) {
                persistQueue()
            }
        }
    }

    /** 把当前队列、下标、播放位置写盘。必须在播放器线程（主线程）调用。 */
    private fun persistQueue() {
        val player = mediaSession?.player ?: return
        runCatching {
            val items = ArrayList<MediaItem>(player.mediaItemCount)
            for (i in 0 until player.mediaItemCount) items.add(player.getMediaItemAt(i))
            queueStore.save(
                items = items,
                index = player.currentMediaItemIndex,
                positionMs = player.currentPosition,
                playWhenReady = player.playWhenReady,
            )
        }
    }

    private val use40DpIcon by preference(this, use40DpIconKey, false)
    private val desktopLyricEnabled by preference(this, desktopLyricEnabledKey, false)
    private val desktopLyricLocked by preference(this, desktopLyricLockedKey, false)
    private val audioQuality by enumPreference(this, audioQualityKey, SongLevel.STANDARD)

    private var playbackHistoryReporter: PlaybackHistoryReporter? = null
    private val playbackHistorySession = PlaybackHistorySession()

    /**
     * 播放历史监听器：纯内存计时 + 切歌/结束双通道上报。
     * 歌曲元数据（songId/来源/时长/曲名）在 begin() 时由 reporter 缓存，
     * 结束时原 item 已切走仍可用。
     */
    private val playbackHistoryListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // 离开旧曲：结算其会话。
            playbackHistorySession.onMediaItemTransition(
                mediaId = mediaItem?.mediaId,
                reason = reason,
                realtimeMs = SystemClock.elapsedRealtime(),
            )?.let { reportDuration(it) }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            onPlaybackHistoryStateChange()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_ENDED -> playbackHistorySession
                    .finish(SystemClock.elapsedRealtime(), "playend")
                    ?.let { reportDuration(it) }
                Player.STATE_IDLE ->
                    // 换源重放会主动 stop()（打回 IDLE）再 prepare，这一次 IDLE 不是「播完/中断」，
                    // 不结算也不上报，否则每首被换源的歌都会多出一条假的中断续播记录。
                    if (!retryingSource) {
                        playbackHistorySession
                            .finish(SystemClock.elapsedRealtime())
                            ?.let { reportDuration(it) }
                    }
            }
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED) ||
                events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) ||
                events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)
            ) {
                onPlaybackHistoryStateChange()
            }
        }
    }

    /** 播放状态/曲目变化：更新计时器，必要时上报「开始」。 */
    private fun onPlaybackHistoryStateChange() {
        val player = mediaSession?.player ?: return
        val mediaItem = player.currentMediaItem
        val update = playbackHistorySession.update(
            mediaId = mediaItem?.mediaId,
            isPlaying = player.isPlaying,
            wallClockMs = System.currentTimeMillis(),
            realtimeMs = SystemClock.elapsedRealtime(),
        )
        update.completed?.let { reportDuration(it) }
        if (update.startedAtMs != null && mediaItem != null) {
            startPlaybackHistory(mediaItem, update.startedAtMs)
        }
    }

    /** 上报一次会话开始（eapi startplay + NCBL _plv），元数据在此缓存进 reporter。 */
    private fun startPlaybackHistory(mediaItem: MediaItem, startedAtMs: Long) {
        val reporter = playbackHistoryReporter ?: return
        if (!CookieProvider.isLoggedIn()) return
        val source = mediaItem.playbackHistorySourceContext() ?: return
        val durationMs = mediaItem.mediaMetadata.extras
            ?.getLong(MediaSessionConstants.EXTRA_DURATION_MS, 0L)
            ?.coerceAtLeast(0L) ?: 0L
        reporter.begin(
            mediaId = mediaItem.mediaId,
            songId = source.songId,
            sourceId = source.sourceId,
            source = source.source,
            startedAtMs = startedAtMs,
            songName = mediaItem.mediaMetadata.title?.toString().orEmpty(),
            songArtist = mediaItem.mediaMetadata.artist?.toString().orEmpty(),
            songDurationMs = durationMs,
        )
    }

    /** 上报一次会话结束（eapi play + NCBL _pld），元数据用 reporter 内 begin() 时的缓存。 */
    private fun reportDuration(completed: PlaybackHistorySession.CompletedSession) {
        playbackHistoryReporter?.end(completed, endedAtMs = System.currentTimeMillis())
    }


    private val favoriteButton: CommandButton
        get() = CommandButton.Builder(ICON_UNDEFINED)
            .setCustomIconResId(if (use40DpIcon) R.drawable.ic_favorite_40_dp else R.drawable.ic_favorite)
            .setDisplayName("like")
            .setSessionCommand(MediaSessionConstants.CommandToggleLike)
            .build()

    private val favoriteButtonOn: CommandButton
        get() = CommandButton.Builder(ICON_UNDEFINED)
            .setCustomIconResId(if (use40DpIcon) R.drawable.ic_favorite_fill_40_dp else R.drawable.ic_favorite_fill)
            .setDisplayName("like_on")
            .setSessionCommand(MediaSessionConstants.CommandToggleLike)
            .build()

    private val shuffleButton: CommandButton
        get() = CommandButton.Builder(ICON_UNDEFINED)
            .setCustomIconResId(if (use40DpIcon) R.drawable.ic_shuffle_40_dp else R.drawable.ic_shuffle)
            .setDisplayName("shuffle")
            .setSessionCommand(MediaSessionConstants.CommandToggleShuffle)
            .build()

    private val shuffleButtonOn: CommandButton
        get() = CommandButton.Builder(ICON_UNDEFINED)
            .setCustomIconResId(if (use40DpIcon) R.drawable.ic_shuffle_on_40_dp else R.drawable.ic_shuffle_on)
            .setDisplayName("shuffle_on")
            .setSessionCommand(MediaSessionConstants.CommandToggleShuffle)
            .build()

    private val desktopLyricButton: CommandButton
        get() = CommandButton.Builder(ICON_UNDEFINED)
            .setCustomIconResId(R.drawable.ic_lyrics)
            .setDisplayName("desktop_lyric")
            .setSessionCommand(MediaSessionConstants.CommandToggleDesktopLyric)
            .build()

    /** 未锁定 → 显示「锁」图标，点一下锁定。 */
    private val desktopLyricLockButton: CommandButton
        get() = CommandButton.Builder(ICON_UNDEFINED)
            .setCustomIconResId(R.drawable.ic_lyric_lock)
            .setDisplayName("desktop_lyric_lock")
            .setSessionCommand(MediaSessionConstants.CommandToggleDesktopLyricLock)
            .build()

    /** 已锁定 → 显示「开锁」图标，点一下解锁。 */
    private val desktopLyricUnlockButton: CommandButton
        get() = CommandButton.Builder(ICON_UNDEFINED)
            .setCustomIconResId(R.drawable.ic_lyric_unlock)
            .setDisplayName("desktop_lyric_unlock")
            .setSessionCommand(MediaSessionConstants.CommandToggleDesktopLyricLock)
            .build()

    /**
     * 通知栏 / 系统媒体卡片的自定义按钮组合。
     *
     * 系统媒体卡片只放得下 5 个按钮（上一首 / 播放暂停 / 下一首 + 2 个自定义），
     * 所以这里固定只给 2 个自定义按钮：桌面歌词关着时是「开启歌词」，
     * 开着时这个位置让给「锁定 / 解锁」（开关本身在设置页里）。
     */
    private fun currentCustomLayout(): ImmutableList<CommandButton> {
        val liked = favoriteSongIds.contains(
            mediaSession?.player?.currentMediaItem?.mediaId?.toLong()
        )
        val builder = ImmutableList.builder<CommandButton>()
            .add(if (liked) favoriteButtonOn else favoriteButton)
        if (desktopLyricEnabled) {
            // 图标表示当前状态：已锁定显示「锁」，未锁定显示「开锁」，点一下切换。
            builder.add(if (desktopLyricLocked) desktopLyricLockButton else desktopLyricUnlockButton)
        } else {
            builder.add(desktopLyricButton)
        }
        return builder.build()
    }

    private val scope = CoroutineScope(Dispatchers.Main) + SupervisorJob()

    // ——————————————— 弱网恢复 ———————————————
    // 弱网/断流时 ExoPlayer 会进 STATE_IDLE 且不会自行恢复，这里做：
    // 同曲退避重试 → 仍失败则停在当前曲并提示 → 网络恢复（NetworkCallback）后续播。
    private var recoveryJob: kotlinx.coroutines.Job? = null
    private var networkCallback: android.net.ConnectivityManager.NetworkCallback? = null
    private var retryAttempt = 0
    private var errorMediaId: String? = null

    /** 重试耗尽后置位：等网络回来的 onAvailable 来续播。 */
    private var pausedByNetworkError = false

    /** 同曲重试的退避间隔；用完即停在当前曲提示用户。 */
    private val retryDelaysMs = listOf(1_000L, 3_000L, 8_000L)

    /** 重新 prepare 时回退一点，避免卡在同一个读位置反复失败。 */
    private val rewindOnRetryMs = 2_000L

    /** 解析播放地址（一次网络请求 + 可能的试听重解析）的超时。 */
    private val resolveUrlTimeoutMs = 20_000L

    private val recoveryListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            // 音源本身坏了（404 / HTML 页 / 不是音频）：同一条链重试没意义 → 换源。
            // 这条要先判，不能被下面的网络错误分支拦走。
            if (isBadSourceError(error)) {
                onBadSourceError()
                return
            }
            if (!isRetryableNetworkError(error)) return
            val player = mediaSession?.player ?: return
            val item = player.currentMediaItem ?: return
            // 换曲了 → 重试计数重来
            if (errorMediaId != item.mediaId) {
                errorMediaId = item.mediaId
                retryAttempt = 0
            }
            if (retryAttempt >= retryDelaysMs.size) {
                giveUpOnNetworkError()
                return
            }
            val delayMs = retryDelaysMs[retryAttempt]
            retryAttempt++
            val resumeAt = (player.currentPosition - rewindOnRetryMs).coerceAtLeast(0L)
            recoveryJob?.cancel()
            recoveryJob = scope.launch {
                delay(delayMs)
                val p = mediaSession?.player ?: return@launch
                // 期间用户切歌/手动操作了就别抢
                if (p.currentMediaItem?.mediaId != item.mediaId) return@launch
                p.seekTo(resumeAt)
                p.prepare()
                p.play()
            }
        }

        override fun onPlaybackStateChanged(@Player.State playbackState: Int) {
            // 真的开始缓冲/就绪了才算恢复成功
            if (playbackState == Player.STATE_READY) {
                pausedByNetworkError = false
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            retryAttempt = 0
            errorMediaId = null
            pausedByNetworkError = false
            recoveryJob?.cancel()
            // 时长校验状态跟着曲目走：换歌就清掉上一首的「已校验直链」和重试计数，
            // 已拉黑的坏链保留（坏源不会自愈，新一次播放直接跳过它）。
            verifiedUrl = null
            mediaItem?.mediaId?.takeIf { it.isNotBlank() }?.let(ResolveGuard::resetRetries)
        }
    }

    /**
     * 音源坏了（非网络原因）：拉黑这条直链，换下一个源重放；
     * 已经没有更好的源了，就按「自动跳过错误歌曲」设置处理，别再退避重试同一条链。
     */
    private fun onBadSourceError() {
        val player = mediaSession?.player ?: return
        val item = player.currentMediaItem ?: return
        val songId = item.mediaId.takeIf { it.isNotBlank() } ?: return
        val observation = ResolveGuard.observe(songId) ?: run {
            // 没有解析记录（本地下载音频文件或外部导入文件）：
            val idLong = songId.substringBefore("?").toLongOrNull()
            val isLocal = idLong != null && (
                com.jussicodes.music.data.download.DownloadStore.isSongDownloaded(idLong) ||
                com.jussicodes.music.data.external.ExternalPlaylistStore.findSongById(idLong) != null
            )
            if (isLocal) {
                Toast.makeText(this, "本地音频文件无法读取或已损坏", Toast.LENGTH_SHORT).show()
                if (dataStore.get(autoSkipNextOnErrorKey, false) && player.hasNextMediaItem()) {
                    player.seekToNext()
                    player.prepare()
                    player.play()
                }
            } else {
                giveUpOnNetworkError()
            }
            return
        }
        verifiedUrl = null
        if (observation.trial || observation.alreadyRejected ||
            !ResolveGuard.rejectAndShouldRetry(observation)
        ) {
            android.util.Log.w(
                TAG,
                "bad source with no more candidates songId=$songId retry=${observation.retries}"
            )
            if (ResolveGuard.shouldWarn(observation)) {
                Toast.makeText(this, "这首暂无可用的完整音源", Toast.LENGTH_LONG).show()
            }
            if (dataStore.get(autoSkipNextOnErrorKey, false) && player.hasNextMediaItem()) {
                player.stop()
                player.seekToNext()
                player.prepare()
                player.play()
            }
            return
        }
        android.util.Log.w(TAG, "bad source, switching source songId=$songId url=${observation.url.take(100)}")
        retryWithNextSource(songId)
    }

    /** 重试耗尽：停在当前曲提示；若用户开了「自动跳过错误歌曲」才跳下一首。 */
    private fun giveUpOnNetworkError() {
        val player = mediaSession?.player ?: return
        pausedByNetworkError = true
        if (dataStore.get(autoSkipNextOnErrorKey, false) && player.hasNextMediaItem()) {
            player.seekToNext()
            player.prepare()
            player.play()
            return
        }
        Toast.makeText(
            this,
            "网络异常，播放已暂停，点播放键可重试",
            Toast.LENGTH_LONG
        ).show()
    }

    /** 当前曲目已经校验过时长的直链，避免同一根链反复触发校验。 */
    private var verifiedUrl: String? = null

    /**
     * 正在「拉黑坏源并重放」的过程中。
     *
     * 重放必须走 stop()（见 [retryWithNextSource]），而 stop() 会让播放状态回到 IDLE，
     * 播放历史上报会把这次打断当成一次「播完/中断」结算并上报。这个标志让上报层跳过它。
     */
    @Volatile
    private var retryingSource = false

    /** 时长校验允许的误差：至少 5s，长歌按 2% 放宽。 */
    private fun durationToleranceMs(expectedMs: Long): Long =
        maxOf(5_000L, expectedMs / 50)

    /**
     * 时长校验：解析出来的直链未必是这首歌的完整音频。
     *
     * 典型坏源：别的歌、被截断/拼接的文件、时长被裁过的转码。它们都能「解析成功」并正常开始播放，
     * 用户看到的现象是「放一半没声 / 放到下一首之前就断了」，只能手动切歌再切回来。
     * 所以这里等真实音频就绪后核对 ExoPlayer 读到的时长与歌曲元数据时长，超出容差就把这条链
     * 拉黑（[ResolveGuard]）并换源重放——重试会跳过坏链、升级到下一个脚本/直连源。
     *
     * 试听片段（30s 占位）不在这里重试：能走到「拿到试听」说明所有脚本/直连源都已试过，
     * 再解析一遍结果只会一样，反而把每首无源歌的加载时间翻几倍。
     */
    private val durationVerifyListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState != Player.STATE_READY) return
            val player = mediaSession?.player ?: return
            val item = player.currentMediaItem ?: return
            val songId = item.mediaId.takeIf { it.isNotBlank() } ?: return
            val expectedMs = item.mediaMetadata.extras
                ?.getLong(MediaSessionConstants.EXTRA_DURATION_MS, 0L) ?: 0L
            if (expectedMs <= 0) return
            // 只有「由我们解析出来的」曲目才有记录；本地文件 / 直接给的 http 地址没有，直接跳过。
            val observation = ResolveGuard.observe(songId) ?: return
            if (verifiedUrl == observation.url) return
            verifiedUrl = observation.url

            scope.launch {
                val p = mediaSession?.player ?: return@launch
                // 时长要读「稳定后」的值：边下边播的 MP3（VBR / 无 Xing 头）一开始给的是按码率估算的
                // 近似值，播了几秒才收敛；拿 STATE_READY 那一瞬间的值去比会误判成坏源。
                delay(300)
                if (p.currentMediaItem?.mediaId != songId) return@launch
                val early = p.duration
                // 等到「播了几秒」或「时长有值」，上限 9s；两个条件都要计入上限，
                // 否则 duration 一直为 0 时会退化成死循环（主线程上就是 ANR）。
                var waited = 0L
                while (waited < MEDIA_STATE_SETTLE_MS) {
                    if (p.currentMediaItem?.mediaId != songId) return@launch
                    if (p.duration > 0 && p.currentPosition >= DURATION_SAMPLE_AFTER_MS) break
                    delay(500)
                    waited += 500
                }
                if (early > 0 && p.duration > 0 && kotlin.math.abs(p.duration - early) > 500) {
                    // 估算还在收敛：再给 2.5s
                    repeat(5) { delay(500) }
                }
                val actualMs = p.duration
                if (actualMs == androidx.media3.common.C.TIME_UNSET || actualMs <= 0) return@launch
                if (p.currentMediaItem?.mediaId != songId) return@launch
                val diff = kotlin.math.abs(actualMs - expectedMs)
                if (diff <= durationToleranceMs(expectedMs)) return@launch

                android.util.Log.w(
                    TAG,
                    "duration mismatch songId=$songId expected=${expectedMs}ms actual=${actualMs}ms " +
                        "diff=${diff}ms retry=${observation.retries} trial=${observation.trial} " +
                        "url=${observation.url.take(100)}"
                )
                // 试听片段：所有源都试过了，重试没有意义，保持「能听 30 秒」的现状。
                if (observation.trial) return@launch
                // 这次交出去的直链本身就在拉黑集合里（候选已试穿）→ 别再重放，直接接受并提示一次。
                if (observation.alreadyRejected || !ResolveGuard.rejectAndShouldRetry(observation)) {
                    if (ResolveGuard.shouldWarn(observation)) {
                        Toast.makeText(
                            this@PlaybackService,
                            "这首音源与歌曲时长不符，已尝试其它音源仍失败",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@launch
                }
                retryWithNextSource(songId)
            }
        }
    }

    /**
     * 换源重放当前曲目。
     *
     * **必须 stop() 而不是 prepare()**：ExoPlayerImpl.prepare() 在非 IDLE 状态下直接 return
     * （1.4.1 源码：`if (playbackInfo.playbackState != Player.STATE_IDLE) return;`），
     * 所以 READY 状态下 pause → seekTo(0) → prepare() 什么都不会发生，ResolvingDataSource
     * 不会重新解析 —— 那样「换源重试」只是把同一根坏链再放一遍。
     * stop() 会把播放器打回 IDLE 并释放 MediaSourceList，随后的 prepare() 才会重新创建
     * MediaSource、重新走一遍 uri 解析（带上拉黑集合）。
     */
    private fun retryWithNextSource(songId: String) {
        val player = mediaSession?.player ?: return
        if (player.currentMediaItem?.mediaId != songId) return
        retryingSource = true
        verifiedUrl = null
        scope.launch {
            try {
                player.stop()
                player.seekTo(0)
                player.prepare()
                player.play()
            } finally {
                // prepare() 是异步的；给内部播发线程一点时间再过掉「正在重放」标志，
                // 让 stop() 引起的 IDLE 事件不会结算播放历史上报。
                delay(1_500)
                retryingSource = false
            }
        }
    }

    /** 监听网络恢复：之前因弱网停住的，走到这续播。 */
    private fun observeNetworkRecovery() {
        val cm = getSystemService(android.net.ConnectivityManager::class.java) ?: return
        val callback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                if (!pausedByNetworkError) return
                recoveryJob?.cancel()
                retryAttempt = 0
                recoveryJob = scope.launch {
                    val p = mediaSession?.player ?: return@launch
                    if (p.playerError == null || p.currentMediaItem == null) return@launch
                    p.prepare()
                    p.play()
                }
            }
        }
        runCatching { cm.registerDefaultNetworkCallback(callback) }
            .onFailure { return }
        networkCallback = callback
    }

    private fun unregisterNetworkCallback() {
        val callback = networkCallback ?: return
        networkCallback = null
        val cm = getSystemService(android.net.ConnectivityManager::class.java) ?: return
        runCatching { cm.unregisterNetworkCallback(callback) }
    }

    // 换源信号 → 重新解析并播放当前这首歌（音源代号已由调用方写进 cacheKey）
    private var reloadJob: kotlinx.coroutines.Job? = null

    fun observeSourceReloads() {
        reloadJob = scope.launch {
            PlaybackReloadBus.requests.collect {
                val player = mediaSession?.player ?: return@collect
                val item = player.currentMediaItem ?: return@collect
                // 用户主动换了音源：清掉这首歌上一轮（旧音源）的拉黑集合与重试计数，
                // 否则新音源会被旧源的坏链记录连坐跳过。
                item.mediaId.takeIf { it.isNotBlank() }?.let(ResolveGuard::forgetSong)
                verifiedUrl = null
                // 必须 stop() → prepare()：prepare() 在非 IDLE 状态直接 return，
                // pause + seekTo(0) + prepare() 不会重新解析，等于没换源。
                val pos = player.currentPosition
                player.stop()
                if (pos > 0) {
                    player.seekTo(pos)
                }
                player.prepare()
                player.play()
            }
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider(
                this,
                { 2000 },
                DefaultMediaNotificationProvider.DEFAULT_CHANNEL_ID,
                DefaultMediaNotificationProvider.DEFAULT_CHANNEL_NAME_RESOURCE_ID
            ).apply {
                setSmallIcon(R.drawable.ic_music_note)
            }
        )
        val audioOnlyRenderersFactory =
            RenderersFactory {
                    handler: Handler,
                    _: VideoRendererEventListener,
                    audioListener: AudioRendererEventListener,
                    _: TextOutput,
                    _: MetadataOutput,
                ->
                arrayOf<Renderer>(
                    MediaCodecAudioRenderer(
                        this,
                        MediaCodecSelector.DEFAULT,
                        handler,
                        audioListener,
                        DefaultAudioSink.Builder(this)
                            .setAudioProcessors(PlaybackAudioProcessors.asArray())
                            .build()
                    )
                )
            }

        val player = ExoPlayer.Builder(this, audioOnlyRenderersFactory)
            .apply {
                val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                    .setUserAgent(UserAgentUtil.DEFAULT_USER_AGENT)
                    // 弱网下别 8s 就判死，给足恢复窗口
                    .setConnectTimeoutMs(15_000)
                    .setReadTimeoutMs(30_000)
                    .setDefaultRequestProperties(
                        buildMap {
                            put("Referer", "https://music.163.com/")
                            put("Origin", "https://music.163.com")
                            val cookie = CookieProvider.cookie
                            if (cookie.isNotBlank()) {
                                put("Cookie", cookie)
                            }
                        }
                    )
                val upstreamFactory = androidx.media3.datasource.DefaultDataSource.Factory(
                    this@PlaybackService,
                    httpDataSourceFactory
                )
                val cacheDataSourceFactory = CacheDataSource.Factory()
                    .setCache(AudioCache.get(this@PlaybackService))
                    .setUpstreamDataSourceFactory(upstreamFactory)
                    .setCacheKeyFactory { dataSpec ->
                        // 音源代号进 cacheKey：换源后同一首歌的直链缓存必须失效，
                        // 否则 ResolvingDataSource 会直接复用旧音源解析出的 url。
                        "v3:${dataSpec.key ?: dataSpec.uri}#${audioQuality.value}#${PlaybackReloadBus.sourceTag}"
                    }
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                val resolvingDataSourceFactory = ResolvingDataSource.Factory(
                    cacheDataSourceFactory
                ) { dataSpec ->
                    runBlocking {
                        // 解析播放地址本身也是一次网络请求，弱网下不能无限期卡住加载线程
                        val resolvedUri = withTimeout(resolveUrlTimeoutMs) {
                            updateMediaItemUri(
                                dataSpec.uri,
                                audioQuality,
                                this@PlaybackService
                            )
                        } ?: throw PlaybackException(
                            null,
                            null,
                            PlaybackException.ERROR_CODE_REMOTE_ERROR
                        )

                        dataSpec.buildUpon()
                            .setUri(resolvedUri)
                            .setKey(resolvedUri.toString())
                            .build()
                    }
                }
                setMediaSourceFactory(DefaultMediaSourceFactory(resolvingDataSourceFactory))
                // 媒体播放身份：USAGE_MEDIA 让系统把它当正经媒体会话；
                // WAKE_MODE_NETWORK 播放期间持 wakelock/wifilock，
                // 锁屏/后台走网络流媒体不被 CPU/无线电休眠打断。
                setAudioAttributes(
                    androidx.media3.common.AudioAttributes.Builder()
                        .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                        .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    /* handleAudioBecomingNoisy = */ true
                )
                setWakeMode(androidx.media3.common.C.WAKE_MODE_NETWORK)
                // 弱网缓冲余量：最小缓冲 15s，最大 50s
                setLoadControl(
                    DefaultLoadControl.Builder()
                        .setBufferDurationsMs(
                            15_000,
                            50_000,
                            2_500,
                            5_000
                        )
                        .build()
                )
            }.build()
        player.repeatMode = REPEAT_MODE_ALL
        // 播放历史上报（eapi weblog + NCBL 双通道）：MeiloX 式纯内存计时器 + 串行 IO 上报。
        playbackHistoryReporter = PlaybackHistoryReporter(
            ncblClient = NeteaseClientLogClient(),
            ncblDevice = NcblDeviceInfo(
                deviceId = CookieProvider.getCookieMap()["deviceId"].orEmpty(),
                osVersion = Build.VERSION.RELEASE.orEmpty(),
                model = Build.MODEL.orEmpty(),
                brand = Build.BRAND.orEmpty(),
                processName = applicationInfo.processName ?: packageName,
                buildType = BuildConfig.BUILD_TYPE,
                pid = Process.myPid(),
                buildId = Build.ID.orEmpty(),
            ),
            ncblDeviceId = { CookieProvider.getCookieMap()["deviceId"].orEmpty() },
        )
        player.addListener(playbackHistoryListener)
        // 队列持久化：杀进程后组件/蓝牙/系统媒体面板点播放还能续播
        player.addListener(queuePersistListener)
        // 播放中每 15s 记一次位置：进程被强杀时最多丢 15s 进度
        scope.launch {
            while (isActive) {
                delay(15_000)
                if (mediaSession?.player?.isPlaying == true) persistQueue()
            }
        }
        // 桌面小组件：播放/暂停、切歌、缓冲变化主动刷新
        player.addListener(widgetStateListener)
        // 弱网恢复：同曲退避重试 + 网络恢复后续播
        player.addListener(recoveryListener)
        // 音源时长校验：解析出的直链与歌曲元数据时长不符 → 拉黑该链并换源重试
        player.addListener(durationVerifyListener)
        observeNetworkRecovery()
        observeSourceReloads()
        // 一起听会话：挂到全局播放器上，冷启动恢复/创建房间时接管同步。
        ListenTogetherSession.attach(player, this)
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            ).setCallback(MediaSessionCallback())
            .setCustomLayout(currentCustomLayout())
            .build()
        observeIconPreference()
        observeFavoriteSongIds()
        observeDesktopLyricPreference()
        observeAudioEffectMode()
        observeAudioEffectParams()
        DesktopLyricManager.syncService(this)
        // 服务刚起来：播放器刚 build 完、队列还没恢复时 currentMediaItem=null。
        // 空快照即时提交会把磁盘上“上次播放的歌”覆盖掉——“关掉 App 后组件
        // 变“暂无播放歌曲”、播放控件全没了，主源就是这里。这里只做重绘对齐：
        // 从磁盘读回已有快照刷新一帧显示，不通过 capture 提交空帧。
    }

    override fun onDestroy() {
        reloadJob?.cancel()
        recoveryJob?.cancel()
        unregisterNetworkCallback()
        // 服务没了就不会再有播放：组件不能继续停在「正在播放」。
        // 先同步清播放态（保留歌曲信息），再在尾部补一次同步重绘；协程里的
        // 落盘/重绘在 scope.cancel() 后不保证跑完。
        WidgetUpdater.markStopped(applicationContext)
        // 服务销毁前把队列落盘：下次冷启动/杀进程后组件点播放才能接着放。
        persistQueue()
        mediaSession?.run {
            player.removeListener(playbackHistoryListener)
            player.removeListener(queuePersistListener)
            player.removeListener(widgetStateListener)
            player.removeListener(recoveryListener)
            // 服务销毁前结算并排空未上报的播放历史事件。
            playbackHistoryReporter?.let { reporter ->
                playbackHistorySession.finish(SystemClock.elapsedRealtime())?.let { completed ->
                    reporter.end(completed, System.currentTimeMillis())
                }
                reporter.close()
            }
            playbackHistoryReporter = null
            // 一起听会话先于播放器释放解除监听（MeiloX 原样：只摘监听 + 停监控）。
            ListenTogetherSession.detach(player)
            player.release()
            release()
            mediaSession = null
        }
        // 同步补一帧重绘的工作交给 WidgetUpdater 的对象级 scope：
        // 这里以前是 runBlocking{ WidgetHub.updateAll(...) }，onDestroy 在主线程序，
        // 拉 launcher 的 Glance 合成会把服务销毁卡住几百毫秒甚至更久（启动下一首时更明显）。
        scope.cancel()
        super.onDestroy()
    }

    @OptIn(UnstableApi::class)
    override fun onTaskRemoved(rootIntent: Intent?) {
        // 只在「没有任何前台播放」时才停服务；正在放歌时划掉任务页不该连播一起断。
        if (!isPlaybackOngoing) {
            pauseAllPlayersAndStopSelf()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    fun updateCustomLayout() {
        mediaSession?.setCustomLayout(currentCustomLayout())
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        updateCustomLayout()
        super.onUpdateNotification(session, startInForegroundRequired)
    }

    private fun toggleLike(like: Boolean, songId: Long) {
        scope.launch {
            val song = mediaSession?.player?.currentMediaItem?.mediaMetadata?.extras
                ?.getString("song")
                ?.let { runCatching { json.decodeFromString<Song>(it) }.getOrNull() }
            FavoriteSongAction.setLiked(
                context = applicationContext,
                songId = songId,
                like = like,
                song = song
            )
        }
    }

    @kotlin.OptIn(FlowPreview::class)
    private fun observeIconPreference() {
        scope.launch {
            applicationContext.dataStore.data.debounce(1000)
                .map { it[use40DpIconKey] ?: false }.distinctUntilChanged().collect {
                    updateCustomLayout()
                }
        }
    }

    @kotlin.OptIn(FlowPreview::class)
    private fun observeFavoriteSongIds() {
        scope.launch {
            applicationContext.favoriteSongIdsDatastore.data.debounce(1000).distinctUntilChanged()
                .collect { favoriteSongs ->
                    favoriteSongIds = favoriteSongs.songIdsList
                    updateCustomLayout()
                    WidgetUpdater.refresh(applicationContext, mediaSession?.player)
                }
        }
    }

    @kotlin.OptIn(FlowPreview::class)
    private fun observeDesktopLyricPreference() {
        scope.launch {
            applicationContext.dataStore.data.debounce(300)
                .map {
                    (it[desktopLyricEnabledKey] ?: false) to (it[desktopLyricLockedKey] ?: false)
                }
                .distinctUntilChanged()
                .collect {
                    updateCustomLayout()
                    DesktopLyricManager.syncService(applicationContext)
                }
        }
    }

    @kotlin.OptIn(FlowPreview::class)
    private fun observeAudioEffectMode() {
        scope.launch {
            applicationContext.dataStore.data.debounce(300)
                .map { preferences ->
                    val legacyMode = preferences[audioEffectModeKey].toEnum(AudioEffectMode.OFF)
                    val eightDEnabled = preferences[audioEffectEightDEnabledKey]
                        ?: (legacyMode == AudioEffectMode.EIGHT_D)
                    val reverbEnabled = preferences[audioEffectReverbEnabledKey]
                        ?: (legacyMode == AudioEffectMode.REVERB)
                    eightDEnabled to reverbEnabled
                }
                .distinctUntilChanged()
                .collect { (eightDEnabled, reverbEnabled) ->
                    PlaybackAudioProcessors.setEnabled(eightDEnabled, reverbEnabled)
                }
        }
    }

    @kotlin.OptIn(FlowPreview::class)
    private fun observeAudioEffectParams() {
        scope.launch {
            applicationContext.dataStore.data.debounce(100)
                .map { preferences ->
                    (preferences[audioEffectIntensityKey] ?: 0.5f) to
                        (preferences[audioEffectSpeedKey] ?: 0.5f)
                }
                .distinctUntilChanged()
                .collect { (intensity, speed) ->
                    PlaybackAudioProcessors.setIntensity(intensity)
                    PlaybackAudioProcessors.setSpeed(speed)
                }
        }
    }

    private inner class MediaSessionCallback : MediaSession.Callback {

        /**
         * 播放队列恢复：控制器在队列为空时按下播放（桌面组件 / 蓝牙 / 系统媒体面板 /
         * MediaButtonReceiver），media3 会先问这里要「上次播到哪」。
         * 读不到就返回失败 future，media3 会退化成普通的 play()，不会崩。
         */
        @OptIn(UnstableApi::class)
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            return Futures.transformAsync(
                queueStore.readAsync(),
                object : AsyncFunction<PlaybackQueueStore.SavedQueue?, MediaSession.MediaItemsWithStartPosition> {
                    override fun apply(
                        input: PlaybackQueueStore.SavedQueue?
                    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                        if (input == null) {
                            return Futures.immediateFailedFuture(
                                IllegalStateException("没有可恢复的播放队列")
                            )
                        }
                        return Futures.immediateFuture(
                            MediaSession.MediaItemsWithStartPosition(
                                input.mediaItems,
                                input.index,
                                input.positionMs,
                            )
                        )
                    }
                },
                MoreExecutors.directExecutor(),
            )
        }

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(MediaSessionConstants.CommandToggleLike)
                        .add(MediaSessionConstants.CommandToggleShuffle)
                        .add(MediaSessionConstants.CommandToggleDesktopLyric)
                        .add(MediaSessionConstants.CommandToggleDesktopLyricLock)
                        .build()
                )
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == MediaSessionConstants.ACTION_TOGGLE_SHUFFLE) {
                session.player.shuffleModeEnabled = !session.player.shuffleModeEnabled
                updateCustomLayout()
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            if (customCommand.customAction == MediaSessionConstants.ACTION_TOGGLE_LIKE) {
                session.player.currentMediaItem?.mediaId?.toLongOrNull()?.let {
                    toggleLike(it !in favoriteSongIds, it)
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            if (customCommand.customAction == MediaSessionConstants.ACTION_TOGGLE_DESKTOP_LYRIC) {
                scope.launch {
                    val enabled = applicationContext.dataStore.get(desktopLyricEnabledKey, false)
                    val result = DesktopLyricManager.setEnabled(
                        context = applicationContext,
                        enabled = !enabled,
                        requestPermissionIfNeeded = !enabled
                    )
                    if (!result && !enabled) {
                        Toast.makeText(
                            applicationContext,
                            "请授予悬浮窗权限以显示桌面歌词",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            if (customCommand.customAction == MediaSessionConstants.ACTION_TOGGLE_DESKTOP_LYRIC_LOCK) {
                scope.launch {
                    val locked = applicationContext.dataStore.get(desktopLyricLockedKey, false)
                    applicationContext.dataStore.edit { it[desktopLyricLockedKey] = !locked }
                    updateCustomLayout()
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    companion object {
        private const val TAG = "PlaybackService"

        /** 时长校验：等播放器时长读数稳定的上限（毫秒）。 */
        private const val MEDIA_STATE_SETTLE_MS = 9_000L

        /** 时长校验：至少播到这么久再采样，避开 VBR 粗估。 */
        private const val DURATION_SAMPLE_AFTER_MS = 4_000L
    }
}
