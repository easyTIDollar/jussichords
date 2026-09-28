package com.jussicodes.music.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.Handler
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
import com.rcmiku.ncmapi.api.player.SongLevel
import com.rcmiku.ncmapi.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import com.rcmiku.ncmapi.utils.CookieProvider
import com.rcmiku.ncmapi.utils.json

@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var favoriteSongIds: List<Long> by mutableStateOf(emptyList())
    private val use40DpIcon by preference(this, use40DpIconKey, false)
    private val desktopLyricEnabled by preference(this, desktopLyricEnabledKey, false)
    private val audioQuality by enumPreference(this, audioQualityKey, SongLevel.STANDARD)


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
        DesktopLyricManager.syncService(this)
    }

    override fun onDestroy() {
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
