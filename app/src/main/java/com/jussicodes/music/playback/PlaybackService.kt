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
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
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
                Player.STATE_IDLE -> playbackHistorySession
                    .finish(SystemClock.elapsedRealtime())
                    ?.let { reportDuration(it) }
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

    /** 解析播放地址（一次网络请求）的超时。 */
    private val resolveUrlTimeoutMs = 15_000L

    private val recoveryListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
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
        }
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
                if (player.currentMediaItem == null) return@collect
                // 重播同一 MediaItem：pause → seek 0 → prepare，走一遍新的 ResolvingDataSource
                player.pause()
                player.seekTo(0)
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
                val cacheDataSourceFactory = CacheDataSource.Factory()
                    .setCache(AudioCache.get(this@PlaybackService))
                    .setUpstreamDataSourceFactory(
                        DefaultHttpDataSource.Factory()
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
                    )
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
                                audioQuality
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
        // 弱网恢复：同曲退避重试 + 网络恢复后续播
        player.addListener(recoveryListener)
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
    }

    override fun onDestroy() {
        reloadJob?.cancel()
        recoveryJob?.cancel()
        unregisterNetworkCallback()
        scope.cancel()
        mediaSession?.run {
            player.removeListener(playbackHistoryListener)
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
                            "Please grant overlay permission",
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
}
