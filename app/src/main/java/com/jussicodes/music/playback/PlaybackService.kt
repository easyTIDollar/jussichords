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
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.analytics.AnalyticsListener
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
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
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
import com.jussicodes.music.extensions.toMediaItemList
import com.jussicodes.music.extensions.toResolveKey
import com.jussicodes.music.extensions.updateMediaItemUri
import com.jussicodes.music.lyric.DesktopLyricManager
import com.jussicodes.music.ui.screen.PersonalFmQueueLoader
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
import com.jussicodes.music.widget.WidgetUpdater
import com.jussicodes.music.BuildConfig
import com.rcmiku.ncmapi.api.player.PlayUrlSkip
import com.rcmiku.ncmapi.api.player.SongLevel
import com.rcmiku.ncmapi.model.Song
import com.rcmiku.ncmapi.ncbl.NcblDeviceInfo
import com.rcmiku.ncmapi.ncbl.NeteaseClientLogClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
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
import kotlinx.coroutines.withContext
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
            // 冷启动起播计时：出了声 / 到达 READY 各记一次（相对组件点击时刻）。
            if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) &&
                player.playbackState == Player.STATE_READY
            ) {
                PlaybackTrace.markOnce(applicationContext, "ready", "state READY")
            }
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED) && player.isPlaying) {
                PlaybackTrace.markOnce(applicationContext, "playing", "isPlaying true")
            }
            if (events.containsAny(
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_MEDIA_METADATA_CHANGED,
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                    Player.EVENT_TIMELINE_CHANGED,
                    // seek / 重播 / 循环回到开头：只加这一条，组件进度才会跟着拖动的进度条走。
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
     * 解析链路面板的播放器侧打点：解析层只写到「拿到直链」，真正「多久出声」在播放器这里。
     * 只补两类事件 —— 首块可播（READY）与加载失败，其余步骤都由解析层负责。
     */
    private var pathReadySong: String? = null

    private val pathListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            pathReadySong = null
            PlaybackPath.focus(mediaItem?.mediaId)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState != Player.STATE_READY) return
            val player = mediaSession?.player ?: return
            val mediaId = player.currentMediaItem?.mediaId ?: return
            if (pathReadySong == mediaId) return
            val snapshot = PlaybackPath.snapshot()
            if (snapshot.steps.isEmpty()) {
                // 解析层一次都没被调用就 READY 了：整首在分片缓存里、或本地文件直达。
                // 补一条真实事实，面板才不会空着让人以为「卡在这 0s」。
                pathReadySong = mediaId
                PlaybackPath.stepFor(
                    mediaId,
                    "缓存直达",
                    "整首已在分片缓存或本地文件，播放器直接读取（0 次解析）",
                    listOf("缓存"),
                    PlaybackPath.Status.OK,
                )
                PlaybackPath.resolvedFor(mediaId, "缓存直达 · 未解析")
                return
            }
            pathReadySong = mediaId
            PlaybackPath.step(
                "音频数据已就绪",
                "首块音频可播 · 已交给解码器",
                listOf("播放器"),
                PlaybackPath.Status.OK,
            )
        }

        override fun onPlayerError(error: PlaybackException) {
            PlaybackPath.step(
                "播放加载失败",
                "${error.errorCodeName} · ${error.message.orEmpty().take(80)}",
                listOf("播放器"),
                PlaybackPath.Status.FAIL,
            )
        }
    }

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
            // 暂停由 onIsPlayingChanged 覆盖；media3 没有 STATE_PAUSED。
            if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) {
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

    // ——————————————— 私人 FM 续队列 ———————————————
    // 私人 FM 是无限流，但接口一次只返回几首（常见 3 首）。以前「快播完就补下一批」写在
    // MainScreen 的 LaunchedEffect 里 —— 从桌面小组件直接起播时 App 没有 Compose 树，
    // 没人补队列，播完首批就停，用户看到的就是「组件开 FM 只放三首」。
    // 挪到服务侧：任何入口（组件 / 蓝牙 / 通知栏 / App）起播都能续。
    private var fmRefillJob: kotlinx.coroutines.Job? = null

    private val personalFmRefillListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            maybeRefillPersonalFm()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) maybeRefillPersonalFm()
        }
    }

    private fun maybeRefillPersonalFm() {
        val player = mediaSession?.player ?: return
        val sourceName = player.currentMediaItem?.mediaMetadata?.extras
            ?.getString(MediaSessionConstants.EXTRA_SOURCE_NAME)
        if (sourceName != MediaSessionConstants.SOURCE_PERSONAL_FM) return
        val count = player.mediaItemCount
        if (count <= 0) return
        // 剩两首时开始补（和原来 UI 侧的触发条件一致），播到 ENDED 也补一次兜底。
        val ended = player.playbackState == Player.STATE_ENDED
        if (!ended && player.currentMediaItemIndex < count - 2) return
        if (fmRefillJob?.isActive == true) return
        fmRefillJob = scope.launch {
            val songs = runCatching { PersonalFmQueueLoader.load().getOrThrow() }.getOrNull()
                ?: return@launch
            val p = mediaSession?.player ?: return@launch
            val existingIds = (0 until p.mediaItemCount)
                .mapTo(mutableSetOf()) { p.getMediaItemAt(it).mediaId }
            val fresh = songs.filter { it.id.toString() !in existingIds }
            if (fresh.isEmpty()) return@launch
            val wasEnded = p.playbackState == Player.STATE_ENDED
            val startIndex = p.mediaItemCount
            p.addMediaItems(
                fresh.toMediaItemList(
                    sourceName = MediaSessionConstants.SOURCE_PERSONAL_FM,
                    sourceType = MediaSessionConstants.SOURCE_TYPE_ROAM,
                )
            )
            if (wasEnded) {
                p.seekToDefaultPosition(startIndex)
                p.prepare()
                p.play()
            }
        }
    }

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

    /** 距结尾多久开始预取下一首的直链。 */
    private val prefetchBeforeEndMs = 10_000L

    /** 预取本身也走网络，弱网下不能把它变成新的卡点。 */
    private val prefetchTimeoutMs = 12_000L

    /**
     * 缓冲卡死判定：进入 BUFFERING 这么久还没有任何进展，就在面板上留一行；
     * 再多等一会儿仍然卡着，就自动强制重解析一次（每条曲目最多一次）。
     *
     * 为什么需要：「解析成功、进度条一直转」这类现场没有异常，onPlayerError 不会触发，
     * 用户只能切歌才行，事后又拿不到 logcat。先让它自己救一次，救不了也留下证据。
     */
    private val bufferStallWarnMs = 8_000L
    private val bufferStallHealMs = 12_000L

    private var prefetchJob: kotlinx.coroutines.Job? = null

    /**
     * 下一首预取。
     *
     * 起播的等待几乎全在「解析直链」这一步（LX 脚本约 100ms，官方 eapi 几百 ms），音频字节
     * 本来就是边下边播。所以距结尾 [prefetchBeforeEndMs] 时先把下一首的直链解析好塞进
     * [PlayUrlCache]，真正切歌时 `ResolvingDataSource` 直接命中缓存 —— 解析这一步归零。
     *
     * 只解析、不下载：不占磁盘音频缓存，也不吃音频流量；代价是提前打一次解析请求，
     * 预取完就被跳过的那首等于白解析一次。
     */
    private val prefetchListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            schedulePrefetch()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) schedulePrefetch()
        }
    }

    private fun schedulePrefetch() {
        prefetchJob?.cancel()
        val current = mediaSession?.player?.currentMediaItem?.mediaId ?: return
        prefetchJob = scope.launch {
            while (isActive) {
                val p = mediaSession?.player ?: return@launch
                // 换歌了：让 onMediaItemTransition 重启这一轮
                if (p.currentMediaItem?.mediaId != current) return@launch
                val duration = p.duration
                if (duration <= 0 || duration == androidx.media3.common.C.TIME_UNSET) {
                    delay(1_000)
                    continue
                }
                val remain = duration - p.currentPosition
                if (remain <= prefetchBeforeEndMs) {
                    prefetchNext(p)
                    return@launch
                }
                delay((remain - prefetchBeforeEndMs).coerceIn(1_000L, 5_000L))
            }
        }
    }

    private fun prefetchNext(player: Player) {
        val count = player.mediaItemCount
        if (count <= 1) return
        if (player.repeatMode == Player.REPEAT_MODE_ONE) return
        var next = player.currentMediaItemIndex + 1
        if (next >= count) {
            if (player.repeatMode != Player.REPEAT_MODE_ALL) return
            next = 0
        }
        val uri = player.getMediaItemAt(next).localConfiguration?.uri ?: return
        val qualityTag = audioQuality.value
        val sourceTag = PlaybackReloadBus.sourceTag
        // 已经在缓存里（本轮播过/刚预取过）就别再打一次解析请求
        if (PlayUrlCache.get(uri.toString(), qualityTag, sourceTag) != null) return
        // 预取是「当前这轮播放」的后台动作，挂在当前曲目链上，切歌时用户能看到
        // 下一首为什么起播快。mediaId 必须在主线程取。
        val focusId = player.currentMediaItem?.mediaId
        val nextId = uri.toResolveKey()
        scope.launch(Dispatchers.IO) {
            val resolved = runCatching {
                withTimeout(prefetchTimeoutMs) {
                    updateMediaItemUri(uri, audioQuality, this@PlaybackService)
                }
            }.getOrNull() ?: return@launch
            // 预取只拿到 30 秒试听链时绝不能写直链缓存：正式解析路径有这层过滤，预取漏了的话，
            // 切过去会直接「直链缓存命中」播 30 秒，连 updateMediaItemUri 里的试听短重试都不会走。
            // 用户看到的就是「切下一首只播一半 / 放到一半就跳」。
            val trial = isTrialClipUri(resolved) || ResolveGuard.observe(nextId)?.trial == true
            if (trial) {
                android.util.Log.w(TAG, "prefetch got trial clip, not cached songId=$nextId")
                if (focusId != null) {
                    PlaybackPath.stepFor(
                        focusId,
                        "预取下一首",
                        "$nextId 只拿到试听链 · 不写直链缓存，切歌时重新解析",
                        listOf("预取"),
                        PlaybackPath.Status.FAIL,
                    )
                }
                return@launch
            }
            PlayUrlCache.put(uri.toString(), qualityTag, sourceTag, resolved.toString())
            if (focusId != null) {
                PlaybackPath.stepFor(
                    focusId,
                    "预取下一首",
                    "$nextId 直链已就绪（只解析不下载）",
                    listOf("预取"),
                    PlaybackPath.Status.OK,
                )
            }
            android.util.Log.d(TAG, "prefetch url ready songId=$nextId")
        }
    }

    /**
     * 播放器侧的读盘打点：解析完成到「首块数据可播放」之间是一段没有任何埋点的黑箱
     * （DNS/TLS、首字节、写缓存分片、解码器初始化），线上出现「地址有了但一直不响」时
     * 只能靠这一行定位是 CDN 慢、还是我们自己的缓存/解码慢。
     */
    @OptIn(UnstableApi::class)
    private val loadTraceListener = object : AnalyticsListener {
        private var firstBytesSong = ""
        private var loadErrorSong = ""

        override fun onLoadCompleted(
            eventTime: AnalyticsListener.EventTime,
            loadEventInfo: LoadEventInfo,
            mediaLoadData: MediaLoadData,
        ) {
            if (mediaLoadData.dataType != C.DATA_TYPE_MEDIA) return
            if (loadEventInfo.bytesLoaded <= 0) return
            val songId = mediaSession?.player?.currentMediaItem?.mediaId ?: return
            if (firstBytesSong == songId) return
            firstBytesSong = songId
            val cacheHit = runCatching { loadEventInfo.uri.scheme == "file" }.getOrDefault(false)
            PlaybackPath.stepFor(
                songId,
                "首块数据到达",
                "${loadEventInfo.bytesLoaded / 1024}KB" + if (cacheHit) " · 本地缓存" else "",
                listOf("播放器"),
                PlaybackPath.Status.OK,
            )
        }

        override fun onLoadError(
            eventTime: AnalyticsListener.EventTime,
            loadEventInfo: LoadEventInfo,
            mediaLoadData: MediaLoadData,
            error: java.io.IOException,
            wasCanceled: Boolean,
        ) {
            if (mediaLoadData.dataType != C.DATA_TYPE_MEDIA) return
            val songId = mediaSession?.player?.currentMediaItem?.mediaId ?: return
            if (loadErrorSong == songId) return
            loadErrorSong = songId
            PlaybackPath.stepFor(
                songId,
                "读取中断",
                "${error.javaClass.simpleName} · ${error.message.orEmpty().take(60)}",
                listOf("播放器"),
                PlaybackPath.Status.FAIL,
            )
        }
    }

    /** 见 [bufferStallWarnMs]：卡缓冲看门狗。 */
    private val bufferWatchdog = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState != Player.STATE_BUFFERING) {
                bufferWatchJob?.cancel()
                return
            }
            val player = mediaSession?.player ?: return
            val item = player.currentMediaItem ?: return
            if (!player.playWhenReady) return
            startBufferWatch(item.mediaId)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            autoHealCount = 0
            bufferWatchJob?.cancel()
        }
    }

    private var bufferWatchJob: Job? = null
    private var autoHealCount = 0

    private fun startBufferWatch(songId: String) {
        bufferWatchJob?.cancel()
        bufferWatchJob = scope.launch {
            delay(bufferStallWarnMs)
            val player = mediaSession?.player ?: return@launch
            if (player.playbackState != Player.STATE_BUFFERING) return@launch
            if (player.currentMediaItem?.mediaId != songId) return@launch
            if (!player.playWhenReady) return@launch

            // 解析还没结束时播放器本来就停在 BUFFERING（地址都还没拿到），
            // 那不是卡死，是解析慢——重新计时，让「缓冲卡住」只报告真的卡在读数据上。
            val chain = PlaybackPath.snapshot()
            if (chain.outcome.isBlank() && chain.steps.isNotEmpty()) {
                startBufferWatch(songId)
                return@launch
            }

            val observation = ResolveGuard.observe(songId)
            val detail = buildString {
                append("已 ${bufferStallWarnMs / 1000}s 无进展")
                append(" · 位置 ${formatClock(player.currentPosition)}/${formatClock(player.duration)}")
                append(" · 已缓冲 ${formatClock(player.bufferedPosition)}")
                observation?.let {
                    append(" · ${com.rcmiku.ncmapi.api.PlayUrlTrace.hostOf(it.url)}")
                    if (it.retries > 0) append(" · 已换源 ${it.retries} 次")
                }
            }
            PlaybackPath.stepFor(
                songId,
                "缓冲卡住",
                detail,
                listOf("播放器"),
                PlaybackPath.Status.FAIL,
            )
            android.util.Log.w(TAG, "buffer stall songId=$songId detail=$detail")

            delay(bufferStallHealMs)
            val p = mediaSession?.player ?: return@launch
            if (p.playbackState != Player.STATE_BUFFERING) return@launch
            if (p.currentMediaItem?.mediaId != songId) return@launch
            if (!p.playWhenReady) return@launch
            if (autoHealCount >= 1) {
                PlaybackPath.stepFor(
                    songId,
                    "仍无进展",
                    "自动重连已用过一次，点「重新解析当前音乐」可手动换源",
                    listOf("播放器"),
                    PlaybackPath.Status.FAIL,
                )
                return@launch
            }
            autoHealCount++
            PlaybackPath.stepFor(
                songId,
                "自动重连",
                "丢掉直链缓存与分片缓存，重新解析一次",
                listOf("播放器"),
                PlaybackPath.Status.RUNNING,
            )
            reloadCurrent(force = true)
        }
    }

    private fun formatClock(ms: Long): String {
        if (ms <= 0L) return "0:00"
        val total = ms / 1000
        return "%d:%02d".format(total / 60, total % 60)
    }

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
                // prepare() 在非 IDLE 状态直接 return；重试必须先 stop() 打回 IDLE，
                // 否则「退避重试」只是空转一轮，用户看到的就是一直转圈。
                p.stop()
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
        // 坏源响应（HTML 页 / 404 页 / 被截断的流）可能已经被 CacheDataSource 写进分片缓存。
        // 现在缓存层在解析层外面、命中就完全不解析，坏字节会比以前更「粘」——
        // 换源前先把这首歌的缓存条目删掉，否则重试读到的还是同一段坏数据。
        dropAudioCache(item.localConfiguration?.uri?.toString())
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

    /** 每首歌每进程只自动救一次试听片段：真无源的歌再救也不会变成完整源。 */
    private val trialRescuedSongs: MutableSet<String> =
        java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** 无解析记录（缓存直达 / 本地文件）时每首歌只核对一次时长。 */
    private val durationCheckedSongs: MutableSet<String> =
        java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** NCM 试听占位的时长区间（实际 30040ms，留点余量）。 */
    private val TRIAL_DURATION_RANGE = 25_000L..35_000L

    /** 元数据比实际长出这么多才算「被截成试听」，避免短歌被误判。 */
    private val TRIAL_MIN_GAP_MS = 60_000L

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

    /** 本地下载 / 外部导入的音频：时长校验与试听自愈都不适用于它们。 */
    private fun isLocalSong(songId: String): Boolean {
        val idLong = songId.substringBefore("?").toLongOrNull() ?: return false
        return com.jussicodes.music.data.download.DownloadStore.isSongDownloaded(idLong) ||
            com.jussicodes.music.data.external.ExternalPlaylistStore.findSongById(idLong) != null
    }

    /**
     * 时长校验：解析出来的直链未必是这首歌的完整音频。
     *
     * 典型坏源：别的歌、被截断/拼接的文件、时长被裁过的转码。它们都能「解析成功」并正常开始播放，
     * 用户看到的现象是「放一半没声 / 放到下一首之前就断了」，只能手动切歌再切回来。
     * 所以这里等真实音频就绪后核对 ExoPlayer 读到的时长与歌曲元数据时长，超出容差就把这条链
     * 拉黑（[ResolveGuard]）并换源重放——重试会跳过坏链、升级到下一个脚本/直连源。
     *
     * 试听片段（30s 占位）：以前直接放过（「再解析也一样」），结果 30 秒分片被当成整首歌写进
     * 缓存，从此每次播都是「缓存直达」，再没有自愈机会。现在改为每首歌每进程自动救一次：
     * 清掉分片/直链缓存后重新解析（等价于用户手动点「重新解析」）；仍失败就接受，并在这首歌
     * 离开播放器后清掉那段分片，避免它长期冒充「整首已缓存」。
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
            // 有解析记录就按记录核对；没有记录（本地文件、或进程重启后缓存直达）也继续往下走：
            // 31 秒试听分片会以「整首已缓存」的身份存在，光靠内存记录永远认不出来。
            val observation = ResolveGuard.observe(songId)
            if (observation != null) {
                if (verifiedUrl == observation.url) return
                verifiedUrl = observation.url
            } else if (isLocalSong(songId) || !durationCheckedSongs.add(songId)) {
                // 本地下载/外部导入文件没有解析记录；它们的坏文件由 onPlayerError 处理，
                // 这里不碰缓存，也不做「试听重解析」。
                return
            }
            val rawUri = item.localConfiguration?.uri?.toString()

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

                // 试听片段：除了 ResolveGuard 的标记，还认「时长 ≈ 30s 且元数据远长于它」。
                // 后者是缓存直达 / 进程重启后唯一的证据。
                val looksTrial = actualMs in TRIAL_DURATION_RANGE &&
                    expectedMs - actualMs > TRIAL_MIN_GAP_MS
                val trial = observation?.trial == true || looksTrial

                // 面板上这一行是「解析成功但放不对」的唯一证据：链是好的、歌不对。
                PlaybackPath.stepFor(
                    songId,
                    if (trial) "试听片段" else "时长校验不符",
                    "元数据 ${expectedMs / 1000}s / 实际 ${actualMs / 1000}s，" +
                        (if (trial) "不是完整音频" else "拉黑这条链"),
                    listOf("校验"),
                    PlaybackPath.Status.FAIL,
                )
                android.util.Log.w(
                    TAG,
                    "duration mismatch songId=$songId expected=${expectedMs}ms actual=${actualMs}ms " +
                        "diff=${diff}ms trial=$trial retry=${observation?.retries ?: 0} " +
                        "url=${observation?.url?.take(100) ?: "-"}"
                )
                if (trial) {
                    // 试听不能当完整音频留在缓存里（否则以后每次播都是「缓存直达」，永远没有
                    // 自愈机会）。每首歌每进程自动救一次：清两层缓存后重新解析——这正是
                    // 用户手动点「重新解析」做的事。再失败就接受现状，不反复重放。
                    if (trialRescuedSongs.add(songId)) {
                        PlaybackPath.stepFor(
                            songId,
                            "试听自动重解析",
                            "丢掉分片缓存与直链缓存后重新解析一次",
                            listOf("校验", "重试"),
                            PlaybackPath.Status.RUNNING,
                        )
                        retryWithNextSource(
                            songId,
                            detail = "试听片段：清掉分片与直链缓存后重新解析",
                            dropUrlCache = true,
                        )
                    } else {
                        // 已经救过还是试听 → 真无源。分片留着可以听 30 秒，但等这首歌
                        // 离开播放器后必须清掉，避免它冒充「整首已缓存」。
                        scheduleTrialCachePurge(songId, rawUri)
                    }
                    return@launch
                }
                // 没有解析记录（缓存直达）：知道它不对，但没有可拉黑的链，只清缓存下次重解析。
                if (observation == null) {
                    scheduleTrialCachePurge(songId, rawUri)
                    return@launch
                }
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
    private fun retryWithNextSource(
        songId: String,
        detail: String = "清掉这首歌的分片缓存，重新解析直链",
        dropUrlCache: Boolean = false,
    ) {
        val player = mediaSession?.player ?: return
        if (player.currentMediaItem?.mediaId != songId) return
        retryingSource = true
        verifiedUrl = null
        val rawUri = player.currentMediaItem?.localConfiguration?.uri?.toString()
        PlaybackPath.stepFor(
            songId,
            "换源重放",
            detail,
            listOf("校验"),
            PlaybackPath.Status.RUNNING,
        )
        scope.launch {
            // 必须先删分片缓存再 stop → prepare：顺序反过来，prepare 会立刻命中同一段
            // 坏分片（31 秒试听 / 截断流），换源就是原地打转——以前这里只有一行文案，
            // 真正的 removeResource 从来没被调用过。
            if (rawUri != null) {
                dropAudioCacheNow(rawUri)
                if (dropUrlCache) {
                    PlayUrlCache.invalidate(
                        rawUri,
                        audioQuality.value,
                        PlaybackReloadBus.sourceTag,
                    )
                }
            }
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

    /**
     * 音频分片缓存 key：与 CacheDataSource.Factory 的 cacheKeyFactory 同一口径。
     * 用 MediaItem 的原始 uri（歌曲级、跨进程稳定），解析出来的直链不参与缓存身份。
     */
    private fun audioCacheKey(rawUri: String): String =
        "v5:$rawUri#${audioQuality.value}#${PlaybackReloadBus.sourceTag}"

    /** 从音频分片缓存里删掉某首歌（当前音质/音源）的条目，坏源换源时用。 */
    private fun dropAudioCache(rawUri: String?) {
        val uri = rawUri ?: return
        scope.launch(Dispatchers.IO) {
            runCatching {
                AudioCache.get(this@PlaybackService).removeResource(audioCacheKey(uri))
                android.util.Log.w(TAG, "audio cache dropped for bad source: $uri")
            }
        }
    }

    /**
     * 同步版：调用方必须先等删除完成再 prepare，否则 prepare 会立刻命中同一段坏分片。
     * 换源 / 试听重解析走这条。
     */
    private suspend fun dropAudioCacheNow(rawUri: String?) {
        val uri = rawUri ?: return
        withContext(Dispatchers.IO) {
            runCatching {
                AudioCache.get(this@PlaybackService).removeResource(audioCacheKey(uri))
                android.util.Log.w(TAG, "audio cache dropped for bad source: $uri")
            }
        }
    }

    /** NCM 试听片段（30 秒占位）的 CDN 标识。 */
    private fun isTrialClipUri(uri: android.net.Uri): Boolean =
        uri.toString().contains("musicrep-ts", ignoreCase = true)

    /**
     * 等这首歌离开播放器之后再把试听分片删掉。
     *
     * 播放中直接 removeResource 会把正在读的 span 抽走，可能触发一次读取异常；
     * 切歌（或 90s 超时）后再删，下次播放就会重新解析而不是「缓存直达 30 秒」。
     */
    private fun scheduleTrialCachePurge(songId: String, rawUri: String?) {
        if (rawUri == null) return
        if (trialPurgeJobs.putIfAbsent(songId, true) != null) return
        scope.launch {
            try {
                val deadline = SystemClock.elapsedRealtime() + 90_000
                while (SystemClock.elapsedRealtime() < deadline) {
                    if (mediaSession?.player?.currentMediaItem?.mediaId != songId) break
                    delay(1_000)
                }
                dropAudioCacheNow(rawUri)
            } finally {
                trialPurgeJobs.remove(songId)
            }
        }
    }

    /** 同一首歌的延后清理任务只挂一个。 */
    private val trialPurgeJobs = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

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
    private var reloadForceJob: kotlinx.coroutines.Job? = null

    fun observeSourceReloads() {
        reloadJob = scope.launch {
            PlaybackReloadBus.requests.collect { reloadCurrent(force = false) }
        }
        reloadForceJob = scope.launch {
            PlaybackReloadBus.forceRequests.collect { reloadCurrent(force = true) }
        }
    }

    /**
     * 重新 prepare 当前歌曲。
     *
     * @param force 「重新解析当前音乐」：音源/音质都没变，直链缓存与音频分片缓存必须一起丢掉，
     *   否则 CacheDataSource 直接命中旧分片，ResolvingDataSource 根本不会被调用，
     *   面板上也就看不到新的解析链路。
     */
    private suspend fun reloadCurrent(force: Boolean) {
        val player = mediaSession?.player ?: return
        val item = player.currentMediaItem ?: return
        // 用户主动换了音源/要求重解析：清掉这首歌上一轮（旧源）的拉黑集合与重试次数，
        // 否则新音源会被旧源的坏链记录连坐跳过。
        item.mediaId.takeIf { it.isNotBlank() }?.let(ResolveGuard::forgetSong)
        verifiedUrl = null
        val rawUri = item.localConfiguration?.uri?.toString()
        if (force && rawUri != null) {
            val qualityTag = audioQuality.value
            val sourceTag = PlaybackReloadBus.sourceTag
            PlaybackPath.reset(item.mediaId)
            pathReadySong = null
            PlayUrlCache.invalidate(rawUri, qualityTag, sourceTag)
            withContext(Dispatchers.IO) {
                runCatching { AudioCache.get(this@PlaybackService).removeResource(audioCacheKey(rawUri)) }
            }
            PlaybackPath.stepFor(
                item.mediaId,
                "重新解析当前音乐",
                "已丢弃直链缓存与分片缓存，重新走一遍解析",
                listOf("手动"),
                PlaybackPath.Status.RUNNING,
            )
        }
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

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        // 组件看「播放服务在不在」来决定这帧要不要显示播放态，越早知道越好。
        WidgetUpdater.markServiceRunning()
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
                val httpDataSourceFactory = CookieAwareHttpDataSourceFactory { CookieProvider.cookie }
                val upstreamFactory = androidx.media3.datasource.DefaultDataSource.Factory(
                    this@PlaybackService,
                    httpDataSourceFactory
                )
                // 解析层放在缓存层「里面」：CacheDataSource 先用歌曲级 key 查本地分片，
                // 命中就直接读盘，完全不进 resolver。冷启动续播 / 拖进度条时音频分片
                // 基本都在 512MB LRU 里，于是「起播前必等一次 /song/url 往返」这一步整段消失。
                // 旧顺序（Resolving 在外、Cache 在内）每次 open 都要先联网解析一次，
                // 缓存命中与否都得等，这是「冷启动点了要等一截才出声」的主因。
                val resolvingDataSourceFactory = ResolvingDataSource.Factory(
                    upstreamFactory
                ) { dataSpec ->
                    runBlocking {
                        val rawUri = dataSpec.uri
                        val resolveKey = rawUri.toResolveKey()
                        val qualityTag = audioQuality.value
                        val sourceTag = PlaybackReloadBus.sourceTag
                        // seek 会重新 open 数据流，这里先查内存/磁盘里的解析结果：同一首歌在
                        // 同一音质/音源下不必反复走网络解析（VIP 歌还可能要等 LX 沙盒）。
                        // 命中前必须过滤已拉黑的坏链，否则换源重放会又把同一条链放一遍。
                        val skipUrls = ResolveGuard.skipUrls(resolveKey)
                        val cachedUrl = PlayUrlCache.get(rawUri.toString(), qualityTag, sourceTag)
                            ?.takeUnless { PlayUrlSkip.blocks(skipUrls, it) }

                        val resolvedUri = if (cachedUrl != null) {
                            PlaybackTrace.markOnce(this@PlaybackService, "hit", "resolve hit")
                            // 预取下一首也走这里：begin 返回 false 时说明焦点不在它身上，
                            // 后续打点必须一起跳过，否则会把当前歌的时间线搅乱。
                            if (PlaybackPath.begin(resolveKey, qualityTag)) {
                                // 能走到 ResolvingDataSource 说明分片缓存没兜住：先记这一层，
                                // 面板上的「第一行」才对得上真实链路（缓存 → 直链 → 音源）。
                                PlaybackPath.step(
                                    "音频分片缓存",
                                    "本曲无缓存",
                                    listOf("缓存"),
                                    PlaybackPath.Status.INFO,
                                )
                                PlaybackPath.step(
                                    "直链缓存 命中",
                                    com.rcmiku.ncmapi.api.PlayUrlTrace.hostOf(cachedUrl),
                                    listOf("缓存"),
                                    PlaybackPath.Status.OK,
                                )
                                PlaybackPath.resolved(
                                    "直链缓存 · " + com.rcmiku.ncmapi.api.PlayUrlTrace.hostOf(cachedUrl)
                                )
                            }
                            android.util.Log.d(TAG, "play url cache hit songId=$resolveKey")
                            cachedUrl.toUri()
                        } else {
                            PlaybackTrace.markOnce(this@PlaybackService, "miss", "resolve miss")
                            if (PlaybackPath.begin(resolveKey, qualityTag)) {
                                PlaybackPath.step(
                                    "音频分片缓存",
                                    "本曲无缓存",
                                    listOf("缓存"),
                                    PlaybackPath.Status.INFO,
                                )
                                PlaybackPath.step(
                                    "直链缓存 未命中",
                                    "有效期 30 分钟（磁盘 15 分钟），本次重新解析",
                                    listOf("缓存"),
                                    PlaybackPath.Status.INFO,
                                )
                            }
                            // 解析播放地址本身也是一次网络请求，弱网下不能无限期卡住加载线程
                            withTimeout(resolveUrlTimeoutMs) {
                                updateMediaItemUri(
                                    rawUri,
                                    audioQuality,
                                    this@PlaybackService
                                )
                            } ?: throw PlaybackException(
                                null,
                                null,
                                PlaybackException.ERROR_CODE_REMOTE_ERROR
                            )
                        }
                        if (cachedUrl == null) {
                            // 试听片段（musicrep-ts）不进直链缓存：一旦存下来，之后 30 分钟内
                            // 每次播这首歌都会直接拿这条 30 秒链，连沙盒都不走，永远回不到完整源。
                            if (isTrialClipUri(resolvedUri)) {
                                PlaybackPath.stepFor(
                                    resolveKey,
                                    "试听片段不入直链缓存",
                                    "下次播放会重新解析，避免 30 秒片段被当成整首",
                                    listOf("缓存"),
                                    PlaybackPath.Status.INFO,
                                )
                            } else {
                                PlayUrlCache.put(
                                    rawUri.toString(),
                                    qualityTag,
                                    sourceTag,
                                    resolvedUri.toString()
                                )
                                // 链路闭合：这条直链进了缓存，30 分钟内再播这首歌就不会再解析。
                                PlaybackPath.stepFor(
                                    resolveKey,
                                    "直链已缓存",
                                    "30 分钟内重播不再解析",
                                    listOf("缓存"),
                                    PlaybackPath.Status.OK,
                                )
                            }
                        }

                        dataSpec.buildUpon()
                            .setUri(resolvedUri)
                            // cacheKey 用歌曲级标识，不能拿直链当 key：直链普遍带一次性
                            // token，同一首歌每次解析都不同，会让缓存永远命中不了 ——
                            // seek 回到已播放位置也要整段重新下载。
                            .setKey("song:$resolveKey")
                            .build()
                    }
                }
                val cacheDataSourceFactory = CacheDataSource.Factory()
                    .setCache(AudioCache.get(this@PlaybackService))
                    .setUpstreamDataSourceFactory(resolvingDataSourceFactory)
                    .setCacheKeyFactory { dataSpec ->
                        // 外层 dataSpec 的 uri 就是 MediaItem 的原始 uri（歌曲级、跨进程稳定），
                        // 用它当 key，解析出来的直链不参与缓存身份。
                        // 版本前缀在 audioCacheKey 里是 v5：key 语义变了（v4 及更早的条目不会再被读到）。
                        this@PlaybackService.audioCacheKey(
                            dataSpec.key ?: dataSpec.uri.toString()
                        )
                    }
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
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
                // 缓冲策略：最小缓冲 15s / 最大 50s；起播只要 1.0s，seek 或断流后
                // 续播只要 1.1s（原来是 1.5s / 5s，后两项直接决定「点完多久出声」）。
                // 1.5s 的起播门槛在网络一般时也要白攒 1.5 秒音频才肯开口，弱网下更明显。
                setLoadControl(
                    DefaultLoadControl.Builder()
                        .setBufferDurationsMs(
                            15_000,
                            50_000,
                            1_000,
                            1_100
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
        // 弱网恢复：同曲退避重试 + 网络恢复后续播
        player.addListener(recoveryListener)
        // 桌面小组件：播放/暂停、切歌、缓冲变化主动刷新
        player.addListener(widgetStateListener)
        player.addListener(pathListener)
        // 卡缓冲看门狗：异常路径看不到的「一直转圈」在这里留证据并自救
        player.addListener(bufferWatchdog)
        player.addAnalyticsListener(loadTraceListener)
        player.addListener(personalFmRefillListener)
        // 音源时长校验：解析出的直链与歌曲元数据时长不符 → 拉黑该链并换源重试
        player.addListener(durationVerifyListener)
        // 下一首直链预取：距结尾 10s 先解析好，切歌时零解析
        player.addListener(prefetchListener)
        observeNetworkRecovery()
        observeSourceReloads()
        // 一起听会话：挂到全局播放器上，冷启动恢复/创建房间时接管同步。
        ListenTogetherSession.attach(player, this)
        // 会话播放器包一层：暂停时「下一首」自动开播（见 AutoPlayNextPlayer 注释）。
        // 只包装交给 MediaSession 的那一份，ListenTogetherSession 仍挂原始 ExoPlayer，
        // 远端同步的 seek 不会被本地的自动播放规则掺进来。
        mediaSession = MediaSession.Builder(this, AutoPlayNextPlayer(player))
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
        // 桌面歌词同步要同步读一次 DataStore（首次读要开文件），冷启动时压在 onCreate
        // 主线程上会直接推后「服务可连接 + 播放器可播」的时间点。挪到后台错峰执行。
        scope.launch {
            delay(300)
            runCatching { DesktopLyricManager.syncService(this@PlaybackService) }
        }
        // 服务刚起来：播放器刚 build 完、队列还没恢复时 currentMediaItem=null。
        // 这里做一次初始对齐：空帧会命中 commit 的空帧防护——磁盘上有歌就保留歌曲信息、
        // 只把 isPlaying/isLoading 清零（进程被杀后组件停在「播放中」，服务重启后先对齐到真实状态）。
        // 队列恢复 / 播放事件到达后由 widgetStateListener 推真实帧。
        scope.launch { WidgetUpdater.refresh(applicationContext, player) }
        PlaybackTrace.mark(this, "svc ready")
    }

    override fun onDestroy() {
        reloadJob?.cancel()
        recoveryJob?.cancel()
        unregisterNetworkCallback()
        // 服务没了就不会再有播放：组件不能继续停在「正在播放」。
        // 先同步清播放态（保留歌曲信息），重绘交给 WidgetUpdater 的对象级 scope——
        // 这里以前是 runBlocking{updateAll}，onDestroy 在主线程，会把服务销毁卡住几百毫秒。
        WidgetUpdater.markStopped(applicationContext)
        // 服务销毁前把队列落盘：下次冷启动/杀进程后还能接着放。
        persistQueue()
        mediaSession?.run {
            player.removeListener(playbackHistoryListener)
            player.removeListener(queuePersistListener)
            player.removeListener(widgetStateListener)
            player.removeListener(recoveryListener)
            player.removeListener(durationVerifyListener)
            player.removeListener(prefetchListener)
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
        scope.cancel()
        super.onDestroy()
    }

    @OptIn(UnstableApi::class)
    override fun onTaskRemoved(rootIntent: Intent?) {
        // 用户从最近任务划掉 App 视为「关闭应用」：无条件暂停并停服务。
        // 以前「正在放歌就不停服务」会让服务继续前台播放，桌面组件停在「播放中」，
        // 和用户对「关掉 App」的预期不一致（产品口径：划掉即停，不再后台续播）。
        // 先同步把「已暂停」推给桌面再停服务：stopSelf 之后进程随时可能被回收，
        // 异步重绘来不及跑，组件就会一直停在最后一帧。
        WidgetUpdater.markStoppedNow(applicationContext)
        pauseAllPlayersAndStopSelf()
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
                    // 红心在 App 内变化时同步到组件（内部比对，未变化不重绘）。
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
            PlaybackTrace.mark(this@PlaybackService, "resume start")
            return Futures.transformAsync(
                queueStore.readAsync(),
                object : AsyncFunction<PlaybackQueueStore.SavedQueue?, MediaSession.MediaItemsWithStartPosition> {
                    override fun apply(
                        input: PlaybackQueueStore.SavedQueue?
                    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                        if (input == null) {
                            PlaybackTrace.mark(this@PlaybackService, "resume fail: no queue")
                            return Futures.immediateFailedFuture(
                                IllegalStateException("没有可恢复的播放队列")
                            )
                        }
                        PlaybackTrace.mark(
                            this@PlaybackService,
                            "resume items=${input.mediaItems.size} pos=${input.positionMs}"
                        )
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

/**
 * 会话播放器包装：暂停状态下「下一首」自动开播。
 *
 * media3 1.4.1 的 MediaSession.Callback 里没有 onSeekToNext 这类钩子（1.5 才有），
 * 控制器发来的 COMMAND_SEEK_TO_NEXT 被直接转发给 player。默认播放器只挪
 * currentIndex、不动 playWhenReady，于是「暂停时点下一首」会停在新曲的 0 秒，
 * 用户得再点一次播放才出声。包一层 ForwardingPlayer 是 1.4.1 里唯一能补上这层
 * 意图的位置，并且播放页按钮 / MiniPlayer 滑动 / 通知栏 / 桌面歌词 / 桌面组件
 * 都经 MediaSession，一处生效全覆盖。
 *
 * 先 play() 再 seek：一起听的 onMediaItemTransition 是同步回调，上报 GOTO 时读
 * playWhenReady；反序会上报 PAUSE —— 对端跟着暂停而本端在放，两端就不同步了。
 * 队列末尾（未开循环）没有可切项，保持原状，避免把「没切动」变成「重播当前曲」。
 */
@OptIn(UnstableApi::class)
private class AutoPlayNextPlayer(player: Player) : ForwardingPlayer(player) {

    override fun seekToNext() {
        if (!playWhenReady && hasNextMediaItem()) play()
        super.seekToNext()
    }

    override fun seekToNextMediaItem() {
        if (!playWhenReady && hasNextMediaItem()) play()
        super.seekToNextMediaItem()
    }
}

/**
 * 每次创建 DataSource 时都重新读一次登录 cookie。
 *
 * 旧写法在 service onCreate 里把 `CookieProvider.cookie` 抓成固定请求头：冷启动
 * （桌面组件点播放）时 Application 的 DataStore 协程可能还没把 cookie 灌进
 * CookieProvider，于是整段播放请求都不带 MUSIC_U —— 解析出的地址会降级成试听 /
 * 直接失败再换源重试，观感就是「点了要等很久才开始出声」。
 * 改成惰性工厂后，每次 open 都拿当时最新的 cookie，冷启动也不再错过登录态。
 */
@UnstableApi
private class CookieAwareHttpDataSourceFactory(
    private val cookieProvider: () -> String,
) : HttpDataSource.Factory {

    private var extraProperties: Map<String, String> = emptyMap()

    override fun createDataSource(): HttpDataSource {
        val cookie = runCatching { cookieProvider() }.getOrDefault("")
        val props = buildMap {
            putAll(extraProperties)
            put("Referer", "https://music.163.com/")
            put("Origin", "https://music.163.com")
            if (cookie.isNotBlank()) put("Cookie", cookie)
        }
        return DefaultHttpDataSource.Factory()
            .setUserAgent(UserAgentUtil.DEFAULT_USER_AGENT)
            // 弱网下别 8s 就判死，给足恢复窗口
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
            .setDefaultRequestProperties(props)
            .createDataSource()
    }

    override fun setDefaultRequestProperties(
        defaultRequestProperties: Map<String, String>
    ): HttpDataSource.Factory {
        extraProperties = defaultRequestProperties
        return this
    }
}
