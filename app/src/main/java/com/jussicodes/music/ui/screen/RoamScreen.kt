package com.jussicodes.music.ui.screen
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import com.jussicodes.music.LocalPlayerController
import com.jussicodes.music.LocalPlayerState
import com.jussicodes.music.constants.DURATION_EXIT_SHORT
import com.jussicodes.music.constants.MediaSessionConstants
import com.jussicodes.music.data.favoriteSongIdsDatastore
import com.jussicodes.music.extensions.playMediaAt
import com.jussicodes.music.extensions.setPlaylist
import com.jussicodes.music.ui.components.ArtworkGlassBackdrop
import com.jussicodes.music.ui.components.ArtworkBackdropStyle
import com.jussicodes.music.ui.components.Lyric
import com.jussicodes.music.ui.icons.Favorite
import com.jussicodes.music.ui.icons.FavoriteFill
import com.jussicodes.music.ui.icons.PauseFill
import com.jussicodes.music.ui.icons.PersonalRadio
import com.jussicodes.music.ui.icons.SkipNextFill
import com.jussicodes.music.ui.icons.SkipPreviousFill
import com.jussicodes.music.utils.FavoriteSongAction
import com.jussicodes.music.utils.makeTimeString
import com.rcmiku.ncmapi.api.recommend.RecommendApi
import com.rcmiku.ncmapi.model.Song
import com.rcmiku.ncmapi.utils.json
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val ROAM_PLAYER = 0
private const val ROAM_LYRIC = 1
private const val PERSONAL_FM_SOURCE = "personal_fm"

private data class PersonalFmModeOption(
    val title: String,
    val mode: String,
    val submode: String? = null
)

private val personalFmModeOptions = listOf(
    PersonalFmModeOption("默认", "DEFAULT"),
    PersonalFmModeOption("AI DJ", "aidj"),
    PersonalFmModeOption("熟悉", "FAMILIAR"),
    PersonalFmModeOption("探索", "EXPLORE"),
    PersonalFmModeOption("运动场景", "SCENE_RCMD", "EXERCISE"),
    PersonalFmModeOption("专注场景", "SCENE_RCMD", "FOCUS"),
    PersonalFmModeOption("夜晚情绪", "SCENE_RCMD", "NIGHT_EMO")
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun RoamScreen(
    isActive: Boolean = true,
    onBackPressed: () -> Unit = {}
) {
    BackHandler(onBack = onBackPressed)

    val playerState = LocalPlayerState.current
    val mediaController = LocalPlayerController.current.controller
    val player = playerState?.player
    val currentSourceName = playerState?.currentMediaItem?.mediaMetadata?.extras
        ?.getString(MediaSessionConstants.EXTRA_SOURCE_NAME)
    val isPersonalFm = currentSourceName == PERSONAL_FM_SOURCE
    val metadata = playerState?.mediaMetadata.takeIf { isPersonalFm }
    val mediaId = playerState?.currentMediaItem?.mediaId.takeIf { isPersonalFm }
    val isPlaying = isPersonalFm && playerState?.isPlaying == true
    val screenHeight = LocalConfiguration.current.screenHeightDp
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val songIds by context.favoriteSongIdsDatastore.data.map { it.songIdsList }
        .collectAsState(emptyList())
    var position by rememberSaveable(playerState?.currentMediaItem?.mediaId) {
        mutableLongStateOf(player?.currentPosition ?: 0L)
    }
    var duration by rememberSaveable(playerState?.currentMediaItem?.mediaId) {
        mutableLongStateOf(player?.duration ?: 0L)
    }
    var sliderPosition by rememberSaveable {
        mutableStateOf<Long?>(null)
    }
    var isLoading by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var queuedFmSongs by remember { mutableStateOf(emptyList<Song>()) }
    var showLyric by rememberSaveable { mutableStateOf(false) }
    var selectedModeIndex by rememberSaveable { mutableStateOf(0) }
    val selectedMode = personalFmModeOptions[selectedModeIndex]

    fun playSong(song: Song) {
        player?.setPlaylist(
            songs = listOf(song),
            sourceName = PERSONAL_FM_SOURCE
        )
        player?.playMediaAt(0)
    }

    fun playPersonalFm(
        forceFetch: Boolean = false,
        modeOption: PersonalFmModeOption = selectedMode
    ) {
        if (isLoading) return
        if (!forceFetch && queuedFmSongs.isNotEmpty()) {
            playSong(queuedFmSongs.first())
            queuedFmSongs = queuedFmSongs.drop(1)
            return
        }
        scope.launch {
            isLoading = true
            errorMessage = null
            RecommendApi.personalFm(
                mode = modeOption.mode,
                submode = modeOption.submode
            )
                .onSuccess { response ->
                    val songs = response.data.map { it.toSong() }.filter { it.id != 0L }
                    if (songs.isNotEmpty()) {
                        playSong(songs.first())
                        queuedFmSongs = songs.drop(1)
                    } else {
                        errorMessage = "私人 FM 暂时没有返回歌曲"
                    }
                }
                .onFailure { throwable ->
                    errorMessage = throwable.message ?: "私人 FM 加载失败"
                }
            isLoading = false
        }
    }

    LaunchedEffect(player, isActive, isPersonalFm) {
        if (isActive && player != null && !isPersonalFm) {
            playPersonalFm()
        }
    }

    LaunchedEffect(playerState?.playbackState, playerState?.currentMediaItem?.mediaId, isPersonalFm) {
        if (isPersonalFm && playerState?.playbackState == Player.STATE_ENDED) {
            playPersonalFm()
        }
    }

    LaunchedEffect(player, isPlaying, playerState?.currentMediaItem?.mediaId, isPersonalFm) {
        position = if (isPersonalFm) player?.currentPosition ?: 0L else 0L
        duration = if (isPersonalFm) player?.duration ?: 0L else 0L
        while (isActive) {
            position = if (isPersonalFm) player?.currentPosition ?: 0L else 0L
            duration = if (isPersonalFm) player?.duration ?: 0L else 0L
            delay(if (isPlaying) 500 else 1000)
        }
    }

    SharedTransitionLayout(
        modifier = Modifier.fillMaxSize()
    ) {
        AnimatedContent(
            targetState = if (showLyric && metadata != null) ROAM_LYRIC else ROAM_PLAYER,
            transitionSpec = {
                fadeIn(tweenEnter(delayMillis = DURATION_EXIT_SHORT)) togetherWith
                    fadeOut(tweenExit(durationMillis = DURATION_EXIT_SHORT))
            }
        ) { target ->
            if (target == ROAM_LYRIC && metadata != null) {
                Lyric(
                    position = position,
                    mediaMetadata = metadata,
                    onBackPressed = { showLyric = false },
                    imageModifier = Modifier.sharedElement(
                        state = rememberSharedContentState(
                            key = "roamArtwork-${metadata.artworkUri ?: metadata.title}"
                        ),
                        animatedVisibilityScope = this@AnimatedContent,
                        placeHolderSize = SharedTransitionScope.PlaceHolderSize.animatedSize,
                        boundsTransform = AlbumArtBoundsTransform
                    )
                )
            } else {
                RoamPlayerContent(
                    metadata = metadata,
                    screenHeight = screenHeight,
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    position = position,
                    duration = duration,
                    sliderPosition = sliderPosition,
                    isPlaying = isPlaying,
                    onShowLyric = { showLyric = true },
                    onPlayPersonalFm = { playPersonalFm() },
                    onModeSelected = { index ->
                        selectedModeIndex = index
                        queuedFmSongs = emptyList()
                        playPersonalFm(
                            forceFetch = true,
                            modeOption = personalFmModeOptions[index]
                        )
                    },
                    onPlayPause = {
                        if (!isPersonalFm) {
                            playPersonalFm()
                        } else if (isPlaying) {
                            mediaController?.pause()
                        } else {
                            mediaController?.play()
                        }
                    },
                    onPrevious = {
                        if (isPersonalFm) {
                            mediaController?.seekToPrevious()
                        }
                    },
                    isFavorite = songIds.contains(mediaId?.toLongOrNull()),
                    onToggleLike = {
                        mediaId?.toLongOrNull()?.let { songId ->
                            scope.launch {
                                val song = playerState?.currentMediaItem?.mediaMetadata?.extras
                                    ?.getString("song")
                                    ?.let { runCatching { json.decodeFromString<Song>(it) }.getOrNull() }
                                FavoriteSongAction.toggle(
                                    context = context,
                                    songId = songId,
                                    likedSongIds = songIds,
                                    song = song
                                )
                            }
                        }
                    },
                    onSliderChange = { sliderPosition = it },
                    onSliderFinished = {
                        sliderPosition?.let {
                            if (isPersonalFm) {
                                mediaController?.seekTo(it)
                                position = it
                            }
                        }
                        sliderPosition = null
                    },
                    onBackPressed = onBackPressed,
                    artworkModifier = if (metadata?.artworkUri != null) {
                        Modifier.sharedElement(
                            state = rememberSharedContentState(
                                key = "roamArtwork-${metadata.artworkUri ?: metadata.title}"
                            ),
                            animatedVisibilityScope = this@AnimatedContent,
                            placeHolderSize = SharedTransitionScope.PlaceHolderSize.animatedSize,
                            boundsTransform = AlbumArtBoundsTransform
                        )
                    } else {
                        Modifier
                    },
                    selectedModeIndex = selectedModeIndex
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoamPlayerContent(
    metadata: androidx.media3.common.MediaMetadata?,
    screenHeight: Int,
    isLoading: Boolean,
    errorMessage: String?,
    position: Long,
    duration: Long,
    sliderPosition: Long?,
    isPlaying: Boolean,
    onShowLyric: () -> Unit,
    onPlayPersonalFm: () -> Unit,
    onModeSelected: (Int) -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    isFavorite: Boolean,
    onToggleLike: () -> Unit,
    onSliderChange: (Long?) -> Unit,
    onSliderFinished: () -> Unit,
    onBackPressed: () -> Unit,
    artworkModifier: Modifier,
    selectedModeIndex: Int
) {
    var modeMenuExpanded by rememberSaveable { mutableStateOf(false) }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var coverOffsetX by remember { mutableFloatStateOf(0f) }
    var coverOffsetY by remember { mutableFloatStateOf(0f) }
    var coverAnimationJob by remember { mutableStateOf<Job?>(null) }
    var coverDismissAnimationJob by remember { mutableStateOf<Job?>(null) }
    val swipeThresholdPx = with(density) { 72.dp.toPx() }
    val coverDragLimitPx = swipeThresholdPx * 1.6f
    val dismissThresholdPx = with(density) { 96.dp.toPx() }
    val coverDismissLimitPx = dismissThresholdPx * 1.1f
    val dragDirectionSlopPx = with(density) { 3.dp.toPx() }
    val coverDragProgress = maxOf(
        abs(coverOffsetX) / swipeThresholdPx,
        coverOffsetY / dismissThresholdPx
    ).coerceIn(0f, 1f)

    fun animateCoverOffsetTo(target: Float) {
        coverAnimationJob?.cancel()
        coverAnimationJob = scope.launch {
            animate(
                initialValue = coverOffsetX,
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = 0.85f,
                    stiffness = 450f
                )
            ) { value, _ ->
                coverOffsetX = value
            }
        }
    }

    fun animateCoverDismissOffsetTo(target: Float) {
        coverDismissAnimationJob?.cancel()
        coverDismissAnimationJob = scope.launch {
            animate(
                initialValue = coverOffsetY,
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = 0.85f,
                    stiffness = 480f
                )
            ) { value, _ ->
                coverOffsetY = value
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Transparent
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ArtworkGlassBackdrop(
                artwork = metadata?.artworkUri,
                style = ArtworkBackdropStyle.FullScreen
            )
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = coverOffsetY
                        val scale =
                            1f - (coverOffsetY / dismissThresholdPx).coerceIn(0f, 1f) * 0.02f
                        scaleX = scale
                        scaleY = scale
                    },
                verticalArrangement = if (screenHeight.dp < 700.dp) {
                    Arrangement.spacedBy(8.dp)
                } else {
                    Arrangement.spacedBy(24.dp)
                }
            ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    IconButton(onClick = { modeMenuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    DropdownMenu(
                        expanded = modeMenuExpanded,
                        onDismissRequest = { modeMenuExpanded = false }
                    ) {
                        personalFmModeOptions.forEachIndexed { index, option ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (index == selectedModeIndex) {
                                            "${option.title} · 当前"
                                        } else {
                                            option.title
                                        }
                                    )
                                },
                                onClick = {
                                    modeMenuExpanded = false
                                    onModeSelected(index)
                                }
                            )
                        }
                    }
                }
            }

            Surface(
                shape = MaterialTheme.shapes.small,
                tonalElevation = 2.dp,
                shadowElevation = 18.dp,
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .aspectRatio(1f)
                    .graphicsLayer {
                        translationX = coverOffsetX
                        rotationZ = (coverOffsetX / swipeThresholdPx).coerceIn(-1f, 1f) * 6f
                        val scale = 1f - coverDragProgress * 0.04f
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(MaterialTheme.shapes.small)
                    .pointerInput(metadata?.artworkUri, swipeThresholdPx) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                coverAnimationJob?.cancel()
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                coverOffsetX = (coverOffsetX + dragAmount).coerceIn(
                                    -coverDragLimitPx,
                                    coverDragLimitPx
                                )
                            },
                            onDragEnd = {
                                when {
                                    coverOffsetX <= -swipeThresholdPx -> onPlayPersonalFm()
                                    coverOffsetX >= swipeThresholdPx -> onPrevious()
                                }
                                animateCoverOffsetTo(0f)
                            },
                            onDragCancel = {
                                animateCoverOffsetTo(0f)
                            }
                        )
                    }
                    .pointerInput(dismissThresholdPx) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            coverDismissAnimationJob?.cancel()

                            var totalDragX = 0f
                            var totalDragY = 0f
                            var isVerticalDrag: Boolean? = null
                            var isDismissed = false

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break

                                val positionChange = change.positionChange()
                                totalDragX += positionChange.x
                                totalDragY += positionChange.y

                                if (isVerticalDrag == null &&
                                    (abs(totalDragX) > dragDirectionSlopPx || abs(totalDragY) > dragDirectionSlopPx)
                                ) {
                                    isVerticalDrag = abs(totalDragY) > abs(totalDragX)
                                }

                                if (isVerticalDrag == false) {
                                    break
                                }

                                if (isVerticalDrag == true && !modeMenuExpanded) {
                                    coverOffsetY = (coverOffsetY + positionChange.y).coerceIn(
                                        0f,
                                        coverDismissLimitPx
                                    )
                                    if (coverOffsetY > 0f) {
                                        change.consume()
                                    }
                                }
                            }

                            if (isVerticalDrag == true) {
                                if (coverOffsetY >= dismissThresholdPx) {
                                    coverOffsetY = 0f
                                    isDismissed = true
                                    onBackPressed()
                                } else {
                                    animateCoverDismissOffsetTo(0f)
                                }
                            }

                            if (!isDismissed && isVerticalDrag != true && coverOffsetY > 0f) {
                                animateCoverDismissOffsetTo(0f)
                            }
                        }
                    }
                    .pointerInput(metadata?.artworkUri) {
                        detectTapGestures {
                            if (metadata?.artworkUri != null) {
                                onShowLyric()
                            }
                        }
                    }
            ) {
                if (metadata?.artworkUri != null) {
                    AsyncImage(
                        model = metadata.artworkUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = artworkModifier.fillMaxSize()
                    )
                } else {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = PersonalRadio,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(88.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(6f)
                    .fillMaxWidth(),
                verticalArrangement = if (screenHeight.dp < 700.dp) {
                    Arrangement.spacedBy(0.dp)
                } else {
                    Arrangement.spacedBy(24.dp)
                }
            ) {
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = metadata?.title?.toString().orEmpty().ifBlank { "漫游" },
                                maxLines = 1,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.basicMarquee()
                            )
                            Text(
                                text = errorMessage
                                    ?: metadata?.artist?.toString().orEmpty().ifBlank { "私人 FM" },
                                maxLines = 1,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.basicMarquee()
                            )
                        }
                        FilledIconButton(
                            onClick = onToggleLike
                        ) {
                            Icon(
                                imageVector = if (isFavorite) FavoriteFill else Favorite,
                                contentDescription = null
                            )
                        }
                    }

                    if (screenHeight.dp > 700.dp) {
                        Spacer(Modifier.height(24.dp))
                    }

                    if (isLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(12.dp))
                    }

                    val resolvedDuration =
                        if (duration == C.TIME_UNSET || duration < 0L) 0L else duration
                    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    Slider(
                        value = (sliderPosition ?: position).coerceIn(0L, resolvedDuration).toFloat(),
                        valueRange = 0f..resolvedDuration.toFloat(),
                        onValueChange = { onSliderChange(it.toLong()) },
                        onValueChangeFinished = onSliderFinished,
                        track = { sliderState ->
                            SliderDefaults.Track(
                                sliderState = sliderState,
                                thumbTrackGapSize = 2.dp,
                                modifier = Modifier.height(4.dp)
                            )
                        },
                        thumb = {
                            SliderDefaults.Thumb(
                                interactionSource = interactionSource,
                                thumbSize = DpSize(4.dp, 20.dp)
                            )
                        }
                    )

                    Row {
                        Text(
                            text = makeTimeString(sliderPosition ?: position),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = makeTimeString(resolvedDuration),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (screenHeight.dp > 700.dp) {
                    Spacer(Modifier.height(24.dp))
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth()
                ) {
                    FilledTonalIconButton(
                        onClick = onPrevious,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = SkipPreviousFill,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    FilledTonalIconButton(
                        modifier = Modifier.size(72.dp),
                        onClick = onPlayPause
                    ) {
                        Icon(
                            imageVector = if (isPlaying) PauseFill else Icons.Filled.PlayArrow,
                            modifier = Modifier.size(48.dp),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    FilledTonalIconButton(
                        onClick = onPlayPersonalFm,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = SkipNextFill,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
}
