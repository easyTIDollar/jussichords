package com.jussicodes.music.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
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
import com.jussicodes.music.constants.desktopLyricEnabledKey
import com.jussicodes.music.constants.use40DpIconKey
import com.jussicodes.music.data.favoriteSongIdsDatastore
import com.jussicodes.music.extensions.updateMediaItemUri
import com.jussicodes.music.lyric.DesktopLyricManager
import com.jussicodes.music.playback.audio.AudioEffectMode
import com.jussicodes.music.playback.audio.PlaybackAudioProcessors
import com.jussicodes.music.utils.FavoriteSongAction
import com.jussicodes.music.utils.UserAgentUtil
import com.jussicodes.music.utils.dataStore
import com.jussicodes.music.utils.enumPreference
import com.jussicodes.music.utils.get
import com.jussicodes.music.utils.preference
import com.jussicodes.music.utils.toEnum
import com.rcmiku.ncmapi.api.API_BASE_URL
import com.rcmiku.ncmapi.api.account.AccountApi
import com.rcmiku.ncmapi.api.player.SongLevel
import com.rcmiku.ncmapi.model.Song
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
import kotlin.random.Random
import com.rcmiku.ncmapi.utils.CookieProvider
import com.rcmiku.ncmapi.utils.json

@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var favoriteSongIds: List<Long> by mutableStateOf(emptyList())
    private val use40DpIcon by preference(this, use40DpIconKey, false)
    private val desktopLyricEnabled by preference(this, desktopLyricEnabledKey, false)
    private val audioQuality by enumPreference(this, audioQualityKey, SongLevel.STANDARD)
    private var scrobbleJob: Job? = null
    private var scrobbleState: ScrobbleState? = null
    private var playSessionId: String? = null
    private val TAG_SCROBBLE = "Scrobble"
    // TEMP-DIAG: 临时诊断 toast，定位完成后可删
    private val mainHandler = Handler(Looper.getMainLooper())
    private var diagToastShown = false

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

    private val desktopLyricButtonOn: CommandButton
        get() = CommandButton.Builder(ICON_UNDEFINED)
            .setCustomIconResId(R.drawable.ic_lyrics_on)
            .setDisplayName("desktop_lyric_on")
            .setSessionCommand(MediaSessionConstants.CommandToggleDesktopLyric)
            .build()

    private val scope = CoroutineScope(Dispatchers.Main) + SupervisorJob()

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
                        "v2:${dataSpec.key ?: dataSpec.uri}#${audioQuality.value}"
                    }
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                val resolvingDataSourceFactory = ResolvingDataSource.Factory(
                    cacheDataSourceFactory
                ) { dataSpec ->
                    runBlocking {
                        val resolvedUri = updateMediaItemUri(
                            dataSpec.uri,
                            audioQuality
                        ) ?: throw PlaybackException(
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
            }.build()
        player.repeatMode = REPEAT_MODE_ALL
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            ).setCallback(MediaSessionCallback())
            .setCustomLayout(
                ImmutableList.of(
                    favoriteButton,
                    if (desktopLyricEnabled) desktopLyricButtonOn else desktopLyricButton
                )
            )
            .build()
        observeIconPreference()
        observeFavoriteSongIds()
        observeDesktopLyricPreference()
        observeAudioEffectMode()
        observeAudioEffectParams()
        observeScrobble(player)
        DesktopLyricManager.syncService(this)
    }

    override fun onDestroy() {
        scrobbleJob?.cancel()
        scope.cancel()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    @OptIn(UnstableApi::class)
    override fun onTaskRemoved(rootIntent: Intent?) {
        pauseAllPlayersAndStopSelf()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    fun updateCustomLayout() {
        mediaSession?.setCustomLayout(
            ImmutableList.of(
                if (favoriteSongIds.contains(mediaSession?.player?.currentMediaItem?.mediaId?.toLong())) favoriteButtonOn else favoriteButton,
                if (desktopLyricEnabled) desktopLyricButtonOn else desktopLyricButton
            )
        )
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

    /**
     * 每首歌的打卡状态。end-only 触发：播放期间用 [maxPositionMs] 记录最大位置（含 seek），
     * [reached] 标记是否达到时长阈值；真正提交发生在「离开这首歌」时（自然播完 STATE_ENDED
     * 或切到下一首 onMediaItemTransition）。[totalSeconds]/[sourceId]/[songName]/[songArtist]
     * 在离开前从当前 MediaItem 缓存，供 leave 时（原 item 已不在 currentMediaItem 上）使用。
     */
    private data class ScrobbleState(
        val mediaId: String,
        val mediaItemIndex: Int,
        var maxPositionMs: Long = 0,
        var reached: Boolean = false,
        var submitted: Boolean = false,
        var totalSeconds: Int? = null,
        var sourceId: Long? = null,
        var songName: String? = null,
        var songArtist: String? = null
    )

    private fun observeScrobble(player: Player) {
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // 离开上一首：先提交被打卡标记的那首（自然切歌 / seek / 手动切歌）
                if (mediaItem != null) submitScrobbleForState(scrobbleState)
                resetScrobble(player)
                if (player.isPlaying) startScrobbleTicker(player)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    startScrobbleTicker(player)
                    submitPlayState(player)
                } else {
                    stopScrobbleTicker()
                    submitPlayState(player)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    // 最后一首自然播完：提交本首
                    stopScrobbleTicker()
                    submitPlayState(player)
                    submitScrobbleForState(scrobbleState)
                }
            }
        })
    }

    private fun resetScrobble(player: Player) {
        val item = player.currentMediaItem
        scrobbleState = item?.let {
            ScrobbleState(
                mediaId = it.mediaId,
                mediaItemIndex = player.currentMediaItemIndex,
                totalSeconds = resolveTotalSeconds(player, it),
                sourceId = it.mediaMetadata.extras
                    ?.getLong(MediaSessionConstants.EXTRA_SOURCE_ID)?.takeIf { v -> v > 0 },
                songName = it.mediaMetadata.title?.toString(),
                songArtist = it.mediaMetadata.artist?.toString()
            )
        }
        startPlaySession(player)
        submitPlayState(player)
    }

    /** 切换曲目时开一个新的 12 位播放会话（不切换则沿用同一会话）。 */
    private fun startPlaySession(player: Player) {
        val mediaId = player.currentMediaItem?.mediaId
        if (mediaId == null) {
            playSessionId = null
            return
        }
        val alphabet = ('A'..'Z') + ('0'..'9')
        playSessionId = (1..12).map { alphabet[Random.nextInt(alphabet.size)] }.toString()
    }

    /** 上报播放状态到 /relay/play/state/submit。 */
    private fun submitPlayState(player: Player) {
        val mediaItem = player.currentMediaItem ?: return
        val songId = mediaItem.mediaId.toLongOrNull() ?: return
        val progressSeconds = (player.currentPosition / 1000L).toInt()
        val sessionId = playSessionId
        scope.launch(Dispatchers.IO) {
            runCatching {
                AccountApi.playStateSubmit(
                    songId = songId,
                    sessionId = sessionId,
                    progress = progressSeconds,
                    playMode = resolvePlayMode(player)
                ).getOrThrow()
            }.onFailure {
                // 网络/鉴权问题不影响播放；仅忽略
            }
        }
    }

    /** 映射到后端 playMode：单曲循环 / 列表循环 / 顺序（默认列表循环）。 */
    private fun resolvePlayMode(player: Player): String = when {
        player.repeatMode == Player.REPEAT_MODE_ONE -> "single_loop"
        else -> "list_loop"
    }

    private fun startScrobbleTicker(player: Player) {
        val mediaItem = player.currentMediaItem ?: return
        if (!CookieProvider.isLoggedIn()) {
            // TEMP-DIAG
            if (!diagToastShown) {
                diagToastShown = true
                mainHandler.post {
                    Toast.makeText(applicationContext, "打卡未进行：未登录网易云", Toast.LENGTH_LONG).show()
                }
            }
            return
        }
        if (mediaItem.mediaId.toLongOrNull() == null) return

        val currentState = scrobbleState
        if (currentState?.mediaId == mediaItem.mediaId &&
            currentState.mediaItemIndex == player.currentMediaItemIndex &&
            currentState.submitted
        ) {
            return
        }

        if (scrobbleJob?.isActive == true) return

        scrobbleJob = scope.launch {
            while (isActive) {
                delay(1000)
                tickScrobble(player)
            }
        }
    }

    private fun stopScrobbleTicker() {
        scrobbleJob?.cancel()
        scrobbleJob = null
    }

    private fun tickScrobble(player: Player) {
        if (!CookieProvider.isLoggedIn()) {
            Log.w(TAG_SCROBBLE, "skip: not logged in (no MUSIC_U in cookie) id=${player.currentMediaItem?.mediaId}")
            stopScrobbleTicker()
            return
        }

        val mediaItem = player.currentMediaItem ?: run {
            stopScrobbleTicker()
            return
        }
        if (mediaItem.mediaId.toLongOrNull() == null) {
            stopScrobbleTicker()
            return
        }
        val currentState = scrobbleState
            ?.takeIf {
                it.mediaId == mediaItem.mediaId &&
                    it.mediaItemIndex == player.currentMediaItemIndex
            }
            ?: run {
            // 正常路径下 resetScrobble 已建好 state；此处兜底重建并补缓存元数据
            val fresh = ScrobbleState(
                mediaId = mediaItem.mediaId,
                mediaItemIndex = player.currentMediaItemIndex,
                totalSeconds = resolveTotalSeconds(player, mediaItem),
                sourceId = mediaItem.mediaMetadata.extras
                    ?.getLong(MediaSessionConstants.EXTRA_SOURCE_ID)?.takeIf { v -> v > 0 },
                songName = mediaItem.mediaMetadata.title?.toString(),
                songArtist = mediaItem.mediaMetadata.artist?.toString()
            )
            scrobbleState = fresh
            fresh
            }

        if (currentState.submitted) {
            stopScrobbleTicker()
            return
        }

        // 记录最大位置（含向后 seek），达到时长阈值时置位（真正提交在「离开本曲」时）
        val positionMs = player.currentPosition
        if (positionMs > currentState.maxPositionMs) {
            currentState.maxPositionMs = positionMs
        }
        val reachedSeconds = currentState.maxPositionMs / 1000
        val thresholdSeconds = scrobbleThreshold(currentState.totalSeconds)
        if (reachedSeconds >= thresholdSeconds) {
            currentState.reached = true
            Log.d(TAG_SCROBBLE, "reached threshold id=${mediaItem.mediaId} reached=${reachedSeconds}s threshold=${thresholdSeconds}s total=${currentState.totalSeconds}")
        }
    }

    /**
     * 播放时长阈值（对齐 Vutron）：最早达标时间 = min(总时长/2, 30)。
     * 等价于 Vutron 的 `position >= duration/2 || position >= 30`：长歌 30 秒即算，
     * 短歌听到总时长一半即算。总时长未知时按 30 秒。
     */
    private fun scrobbleThreshold(totalSeconds: Int?): Int = totalSeconds?.let {
        minOf(maxOf(1, it / 2), 30)
    } ?: 30

    private fun resolveTotalSeconds(player: Player, mediaItem: MediaItem): Int? {
        val playerDuration = player.duration
            .takeIf { it != C.TIME_UNSET && it > 0 }
        val metadataDuration = mediaItem.mediaMetadata.extras
            ?.getLong(MediaSessionConstants.EXTRA_DURATION_MS)
            ?.takeIf { it > 0 }

        return (playerDuration ?: metadataDuration)
            ?.div(1000L)
            ?.toInt()
    }

    /**
     * 离开某首歌（切歌 / 自然播完）时，若该歌已达到时长阈值且未提交，则提交打卡。
     * time 上报实际听到的秒数（已封顶到总时长），并带上 total / name / artist（v1 上报所需）。
     */
    private fun submitScrobbleForState(state: ScrobbleState?) {
        if (state == null || !state.reached || state.submitted) return
        val songId = state.mediaId.toLongOrNull() ?: return
        if (!CookieProvider.isLoggedIn()) return
        state.submitted = true

        val playedSeconds = (state.maxPositionMs / 1000).toInt()
        val reportedSeconds = state.totalSeconds?.let { minOf(playedSeconds, it) } ?: playedSeconds
        val effectiveSourceId = state.sourceId ?: songId

        scope.launch(Dispatchers.IO) {
            Log.d(
                TAG_SCROBBLE,
                "submit scrobble/v1 id=$songId sourceid=$effectiveSourceId (fallback=${state.sourceId == null}) " +
                    "played=${reportedSeconds}s total=${state.totalSeconds} api=$API_BASE_URL"
            )
            AccountApi.scrobble(
                songId = songId,
                time = reportedSeconds,
                sourceId = state.sourceId,
                total = state.totalSeconds,
                name = state.songName,
                artist = state.songArtist
            ).onSuccess { resp ->
                // /scrobble/v1 透传 NCBL 上报结果，code!=200 表示未落库
                if (resp.code == 200) {
                    Log.d(TAG_SCROBBLE, "scrobble success id=$songId code=${resp.code} msg=${resp.msg ?: resp.message}")
                } else {
                    Log.e(TAG_SCROBBLE, "scrobble rejected id=$songId code=${resp.code} msg=${resp.msg ?: resp.message}")
                    mainHandler.post {
                        Toast.makeText(
                            applicationContext,
                            "打卡被服务端拒绝：" + (resp.msg ?: resp.message ?: "code=" + resp.code),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }.onFailure { err ->
                Log.e(TAG_SCROBBLE, "scrobble FAILED id=$songId api=$API_BASE_URL: ${err.message}", err)
                mainHandler.post {
                    Toast.makeText(
                        applicationContext,
                        "听歌打卡失败：" + err.message?.take(80),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
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
                .map { it[desktopLyricEnabledKey] ?: false }
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
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }
}
