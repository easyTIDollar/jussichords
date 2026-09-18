package com.jussicodes.music.ui.components
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import coil3.compose.AsyncImage
import com.jussicodes.music.LocalPlayerController
import com.jussicodes.music.LocalPlayerState
import com.jussicodes.music.data.favoriteSongIdsDatastore
import com.jussicodes.music.constants.playerGestureTutorialVersionKey
import com.jussicodes.music.constants.MediaSessionConstants
import com.jussicodes.music.ui.icons.Album
import com.jussicodes.music.ui.icons.Artist
import com.jussicodes.music.ui.icons.ChevronDown
import com.jussicodes.music.ui.icons.Favorite
import com.jussicodes.music.ui.icons.FavoriteFill
import com.jussicodes.music.ui.icons.PauseFill
import com.jussicodes.music.ui.icons.SkipNextFill
import com.jussicodes.music.ui.icons.SkipPreviousFill
import com.jussicodes.music.ui.navigation.AlbumNav
import com.jussicodes.music.ui.navigation.ArtistNav
import com.jussicodes.music.ui.navigation.CloudSongNav
import com.jussicodes.music.ui.navigation.PlaylistNav
import com.jussicodes.music.ui.navigation.RadioNav
import com.jussicodes.music.ui.navigation.RecordNav
import com.jussicodes.music.ui.navigation.Screen
import com.jussicodes.music.ui.screen.PERSONAL_FM_SOURCE
import com.jussicodes.music.ui.screen.PersonalFmQueueLoader
import com.jussicodes.music.ui.screen.personalFmModeOptions
import com.jussicodes.music.extensions.playMediaAt
import com.jussicodes.music.extensions.setPlaylist
import com.jussicodes.music.utils.CoverImageSize
import com.jussicodes.music.utils.FavoriteSongAction
import com.jussicodes.music.utils.getItemShape
import com.jussicodes.music.utils.makeTimeString
import com.jussicodes.music.utils.rememberPreference
import com.jussicodes.music.utils.toCoverImageUrl
import com.rcmiku.ncmapi.api.album.AlbumApi
import com.rcmiku.ncmapi.model.Artist
import com.rcmiku.ncmapi.model.Song
import com.rcmiku.ncmapi.model.SongAlbum
import com.rcmiku.ncmapi.utils.json
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Player(
    mediaMetadata: MediaMetadata,
    position: Long,
    duration: Long,
    modifier: Modifier = Modifier,
    imageModifier: Modifier = Modifier,
    onBackPressed: () -> Unit = {},
    onClick: () -> Unit = {},
    onContainerClick: () -> Unit = {},
    onPositionUpdate: (Long) -> Unit,
    navController: NavHostController,
    playerLabel: String? = null,
    playerLabelOptions: List<String> = emptyList(),
    selectedPlayerLabelOption: Int = 0,
    onPlayerLabelOptionSelected: (Int) -> Unit = {},
    metadataClickEnabled: Boolean = true,
) {

    BackHandler {
        onBackPressed()
    }

    var sliderPosition by rememberSaveable {
        mutableStateOf<Long?>(null)
    }

    val playerState = LocalPlayerState.current
    val mediaController = LocalPlayerController.current.controller
    val isPlaying = playerState?.isPlaying == true
    val context = LocalContext.current.applicationContext
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val mediaId = playerState?.currentMediaItem?.mediaId
    val songIds by context.favoriteSongIdsDatastore.data.map { it.songIdsList }
        .collectAsState(emptyList())
    var currentSong by remember { mutableStateOf<Song?>(null) }
    var openAlbumSheet by rememberSaveable { mutableStateOf(false) }
    var openArtistSheet by rememberSaveable { mutableStateOf(false) }
    var openPlayerBottomSheet by rememberSaveable { mutableStateOf(false) }
    var openComments by rememberSaveable { mutableStateOf(false) }
    var playerLabelMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var gestureTutorialVersion by rememberPreference(
        playerGestureTutorialVersionKey,
        0
    )
    val showGestureTutorial = gestureTutorialVersion < PLAYER_GESTURE_TUTORIAL_VERSION
    var gestureTutorialActionCompleted by rememberSaveable { mutableStateOf(false) }
    var coverPreviewUrl by remember { mutableStateOf<Any?>(null) }

    // 顶部来源标签：优先用调用方显式传入的 playerLabel（私人 FM 用它带模式切换），
    // 否则从当前 media item 的 sourceName 自动推导，做到「从哪里点的就显示什么来源」。
    val resolvedPlayerLabel = playerLabel
        ?: playerState?.currentMediaItem?.mediaMetadata?.extras
            ?.getString(MediaSessionConstants.EXTRA_SOURCE_NAME)
            ?.let(MediaSessionConstants::sourceLabel)
            ?.takeIf { it.isNotBlank() && it != "播放列表" }

    // 点击来源标签跳回来源页：按 sourceType + navId 路由。私人 FM（带模式菜单）
    // 与无任何来源标记的队列不跳。
    val sourceExtras = playerState?.currentMediaItem?.mediaMetadata?.extras
    val sourceType = sourceExtras?.getString(MediaSessionConstants.EXTRA_SOURCE_TYPE)
    val sourceNavId = sourceExtras?.getLong(MediaSessionConstants.EXTRA_NAV_ID) ?: 0L
    val canJumpToSource = sourceType != null &&
        sourceType != MediaSessionConstants.SOURCE_TYPE_ROAM

    // 调用方未传模式选项、且当前队列是私人 FM 时，自动挂上 FM 模式菜单
    // （覆盖「退出 FM 页后从小播放器进全屏」这条没传选项的路径）。
    val autoFmActive = playerLabelOptions.isEmpty() &&
        sourceType == MediaSessionConstants.SOURCE_TYPE_ROAM
    var autoFmMenuIndex by rememberSaveable {
        mutableIntStateOf(personalFmModeOptions.indexOf(PersonalFmQueueLoader.selectedMode).coerceAtLeast(0))
    }
    val fmLabelOptions = if (playerLabelOptions.isNotEmpty()) {
        playerLabelOptions
    } else if (autoFmActive) {
        personalFmModeOptions.map { it.title }
    } else {
        emptyList<String>()
    }
    val fmSelectedOption = if (playerLabelOptions.isNotEmpty()) selectedPlayerLabelOption else autoFmMenuIndex

    fun onFmOptionPicked(index: Int) {
        if (playerLabelOptions.isNotEmpty()) {
            onPlayerLabelOptionSelected(index)
        } else if (autoFmActive) {
            autoFmMenuIndex = index
            scope.launch {
                PersonalFmQueueLoader.load(personalFmModeOptions[index])
                    .onSuccess { songs ->
                        if (songs.isNotEmpty()) {
                            mediaController?.setPlaylist(
                                songs,
                                sourceName = PERSONAL_FM_SOURCE,
                                sourceType = MediaSessionConstants.SOURCE_TYPE_ROAM
                            )
                            mediaController?.playMediaAt(0)
                        }
                    }
            }
        }
    }
    fun navigateToSource() {
        when (sourceType) {
            MediaSessionConstants.SOURCE_TYPE_PLAYLIST ->
                if (sourceNavId != 0L) navController.navigate(PlaylistNav(playlistId = sourceNavId))
            MediaSessionConstants.SOURCE_TYPE_ALBUM ->
                if (sourceNavId != 0L) navController.navigate(AlbumNav(albumId = sourceNavId))
            MediaSessionConstants.SOURCE_TYPE_ARTIST ->
                if (sourceNavId != 0L) navController.navigate(ArtistNav(artistId = sourceNavId))
            MediaSessionConstants.SOURCE_TYPE_CLOUD ->
                if (sourceNavId != 0L) navController.navigate(CloudSongNav(uid = sourceNavId))
            MediaSessionConstants.SOURCE_TYPE_RADIO ->
                if (sourceNavId != 0L) navController.navigate(RadioNav(radioId = sourceNavId))
            MediaSessionConstants.SOURCE_TYPE_RECORD ->
                if (sourceNavId != 0L) navController.navigate(RecordNav(uid = sourceNavId))
            MediaSessionConstants.SOURCE_TYPE_RECENT ->
                if (navController.currentDestination?.route != Screen.RecentPlay.route) {
                    navController.navigate(Screen.RecentPlay.route)
                }
            MediaSessionConstants.SOURCE_TYPE_EXPLORE ->
                navController.navigate(Screen.Explore.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
        }
        // 收起全屏播放器，露出来源页 + 迷你播放器
        onBackPressed()
    }
    var coverOffsetX by remember { mutableFloatStateOf(0f) }
    var coverOffsetY by remember { mutableFloatStateOf(0f) }
    var coverAnimationJob by remember { mutableStateOf<Job?>(null) }
    var coverDismissAnimationJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(mediaId) {
        playerState?.currentMediaItem?.mediaMetadata?.extras?.getString("song")?.let {
            currentSong = json.decodeFromString<Song>(it)
        }
    }

    val screenHeight = LocalConfiguration.current.screenHeightDp
    val swipeThresholdPx = with(density) { 72.dp.toPx() }
    val coverDragLimitPx = swipeThresholdPx * 1.6f
    val dismissThresholdPx = with(density) { 96.dp.toPx() }
    val coverDismissLimitPx = dismissThresholdPx * 1.1f
    val dragDirectionSlopPx = with(density) { 3.dp.toPx() }
    val queueOpenThresholdPx = with(density) { 40.dp.toPx() }
    val tutorialContentColor = MaterialTheme.colorScheme.onSurface
    val tutorialAccentColor = MaterialTheme.colorScheme.primary
    val coverDragProgress = maxOf(
        abs(coverOffsetX) / swipeThresholdPx,
        coverOffsetY / dismissThresholdPx
    ).coerceIn(0f, 1f)

    LaunchedEffect(showGestureTutorial) {
        if (showGestureTutorial) {
            gestureTutorialActionCompleted = false
        }
    }

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
        modifier = modifier
            .fillMaxSize(),
        color = Color.Transparent,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .graphicsLayer {
                        translationY = coverOffsetY
                        val scale =
                            1f - (coverOffsetY / dismissThresholdPx).coerceIn(0f, 1f) * 0.02f
                        scaleX = scale
                        scaleY = scale
                    },
                verticalArrangement = if (screenHeight.dp < 700.dp) Arrangement.spacedBy(8.dp) else Arrangement.spacedBy(
                    24.dp
                )
            ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            imageVector = ChevronDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { openPlayerBottomSheet = true }) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                resolvedPlayerLabel?.let {
                    Box(modifier = Modifier.align(Alignment.Center)) {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable(
                                enabled = fmLabelOptions.isNotEmpty() || canJumpToSource
                            ) {
                                if (fmLabelOptions.isNotEmpty()) {
                                    playerLabelMenuExpanded = true
                                } else {
                                    navigateToSource()
                                }
                            }
                        )
                        DropdownMenu(
                            expanded = playerLabelMenuExpanded,
                            onDismissRequest = { playerLabelMenuExpanded = false }
                        ) {
                            fmLabelOptions.forEachIndexed { index, option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (index == fmSelectedOption) {
                                                "$option · 当前"
                                            } else {
                                                option
                                            }
                                        )
                                    },
                                    onClick = {
                                        playerLabelMenuExpanded = false
                                        onFmOptionPicked(index)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Box(
                contentAlignment = Alignment.Center,
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
                    .pointerInput(mediaId, swipeThresholdPx) {
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
                                    coverOffsetX <= -swipeThresholdPx -> mediaController?.seekToNextMediaItem()
                                    coverOffsetX >= swipeThresholdPx -> mediaController?.seekToPreviousMediaItem()
                                }
                                animateCoverOffsetTo(0f)
                            },
                            onDragCancel = {
                                animateCoverOffsetTo(0f)
                            }
                        )
                    }
                    .pointerInput(
                        mediaId,
                        openAlbumSheet,
                        openArtistSheet,
                        openPlayerBottomSheet,
                        dismissThresholdPx
                    ) {
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

                                if (isVerticalDrag == true &&
                                    !openAlbumSheet &&
                                    !openArtistSheet &&
                                    !openPlayerBottomSheet
                                ) {
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
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onClick() },
                            onLongPress = {
                                scope.launch {
                                    val albumCoverUrl = currentSong?.al
                                        ?.takeIf { it.id > 0 }
                                        ?.let { album ->
                                            AlbumApi.albumDetail(album.id)
                                                .getOrNull()
                                                ?.album
                                                ?.picUrl
                                        }
                                        ?.takeIf { it.isNotBlank() }
                                    coverPreviewUrl = (
                                        albumCoverUrl
                                            ?: currentSong?.al?.picUrl
                                            ?: mediaMetadata.artworkUri
                                    ).toCoverImageUrl(CoverImageSize.LARGE)
                                }
                            }
                        )
                    }
            ) {
                AsyncImage(
                    model = mediaMetadata.artworkUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = imageModifier
                        .clip(MaterialTheme.shapes.small)
                        .fillMaxSize()
                )
            }

            Column(
                modifier = Modifier
                    .weight(6f)
                    .fillMaxWidth(),
                verticalArrangement = if (screenHeight.dp < 700.dp) Arrangement.spacedBy(0.dp) else Arrangement.spacedBy(
                    24.dp
                )
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 24.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            mediaMetadata.title?.let {
                                Text(
                                    text = it.toString(),
                                    maxLines = 1,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .basicMarquee()
                                        .clickable(enabled = metadataClickEnabled) {
                                            openAlbumSheet = true
                                        }
                                )
                            }
                            mediaMetadata.artist?.let {
                                Text(
                                    text = it.toString(),
                                    maxLines = 1,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .basicMarquee()
                                        .clickable(enabled = metadataClickEnabled) {
                                            // 只有一个音乐人直接跳转，多个才弹二级选项
                                            val artists = currentSong?.ar.orEmpty()
                                            when {
                                                artists.size == 1 -> {
                                                    navController.navigate(ArtistNav(artistId = artists.first().id))
                                                    onBackPressed()
                                                }
                                                artists.size > 1 -> openArtistSheet = true
                                            }
                                        }
                                )
                            }
                        }
                        FilledIconButton(
                            onClick = {
                                mediaId?.toLongOrNull()?.let { songId ->
                                    scope.launch {
                                        FavoriteSongAction.toggle(
                                            context = context,
                                            songId = songId,
                                            likedSongIds = songIds,
                                            song = currentSong
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                if (songIds.contains(mediaId?.toLong())) FavoriteFill else Favorite,
                                contentDescription = null
                            )
                        }
                    }

                    if (screenHeight.dp > 700.dp)
                        Spacer(Modifier.height(24.dp))

                    val interactionSource = remember { MutableInteractionSource() }
                    Slider(
                        value = (sliderPosition ?: position).toFloat(),
                        valueRange = 0f..(if (duration == C.TIME_UNSET) 0f else duration.toFloat()),
                        onValueChange = { sliderPosition = it.toLong() },
                        onValueChangeFinished = {
                            sliderPosition?.let {
                                mediaController?.seekTo(it)
                                onPositionUpdate(it)
                            }
                            sliderPosition = null
                        },
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
                            text = makeTimeString(duration),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (screenHeight.dp > 700.dp)
                    Spacer(Modifier.height(24.dp))

                Box(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth()
                        .weight(1f)
                        .drawBehind {
                            if (showGestureTutorial) {
                                val top = 72.dp.toPx()
                                drawLine(
                                    color = tutorialAccentColor.copy(alpha = 0.8f),
                                    start = Offset(16.dp.toPx(), top),
                                    end = Offset(size.width - 16.dp.toPx(), top),
                                    strokeWidth = 2.dp.toPx()
                                )
                            }
                        }
                        .pointerInput(
                            openAlbumSheet,
                            openArtistSheet,
                            openPlayerBottomSheet,
                            queueOpenThresholdPx
                        ) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                if (down.position.y < 72.dp.toPx()) {
                                    return@awaitEachGesture
                                }
                                var totalDragX = 0f
                                var totalDragY = 0f
                                var isVerticalDrag: Boolean? = null
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

                                    if (isVerticalDrag == true && totalDragY < 0f) {
                                        change.consume()
                                    }

                                    if (isVerticalDrag == true &&
                                        totalDragY <= -queueOpenThresholdPx &&
                                        !openAlbumSheet &&
                                        !openArtistSheet &&
                                        !openPlayerBottomSheet
                                    ) {
                                        gestureTutorialActionCompleted = true
                                        openComments = true
                                        change.consume()
                                        break
                                    }
                                }
                            }
                        }
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilledTonalIconButton(
                                onClick = { mediaController?.seekToPrevious() },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(SkipPreviousFill, contentDescription = null)
                            }
                            FilledIconButton(
                                modifier = Modifier.size(72.dp),
                                onClick = {
                                    if (!isPlaying) mediaController?.play() else mediaController?.pause()
                                }
                            ) {
                                Icon(
                                    if (isPlaying) PauseFill else Icons.Filled.PlayArrow,
                                    modifier = Modifier.size(36.dp),
                                    contentDescription = null
                                )
                            }
                            FilledTonalIconButton(
                                onClick = { mediaController?.seekToNext() },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(SkipNextFill, contentDescription = null)
                            }
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    enabled = !openAlbumSheet && !openArtistSheet && !openPlayerBottomSheet
                                ) {
                                    gestureTutorialActionCompleted = true
                                    onContainerClick()
                                }
                        ) {
                            if (showGestureTutorial) {
                                if (gestureTutorialActionCompleted) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = "操作完成",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = tutorialContentColor
                                        )
                                        androidx.compose.material3.TextButton(
                                            onClick = {
                                                gestureTutorialVersion =
                                                    PLAYER_GESTURE_TUTORIAL_VERSION
                                            }
                                        ) {
                                            Text(text = "关闭教程", color = tutorialContentColor)
                                        }
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "点击 · 播放列表",
                                            maxLines = 1,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = tutorialContentColor
                                        )
                                        Text(
                                            text = "上滑 · 评论",
                                            maxLines = 1,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = tutorialContentColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }


        currentSong?.let {
            AlbumBottomSheet(
                currentSong = it,
                openBottomSheet = openAlbumSheet,
                onDismiss = { openAlbumSheet = false },
                onAlbumClick = { album ->
                    navController.navigate(AlbumNav(albumId = album.id))
                    onBackPressed()
                })
            ArtistBottomSheet(
                currentSong = it,
                openBottomSheet = openArtistSheet,
                onDismiss = { openArtistSheet = false },
                onClick = { artist ->
                    navController.navigate(ArtistNav(artistId = artist.id))
                    onBackPressed()
                })
        }

        PlayerMenuBottomSheet(
            currentSong = currentSong,
            onDismiss = { openPlayerBottomSheet = false },
            openBottomSheet = openPlayerBottomSheet
        )

        coverPreviewUrl?.let { url ->
            LargeImageDialog(
                imageUrl = url,
                onDismiss = { coverPreviewUrl = null },
                showSaveAction = true
            )
        }

        if (openComments) {
            PlayerComments(
                mediaId = mediaId?.toLongOrNull(),
                mediaMetadata = mediaMetadata,
                onBackPressed = { openComments = false }
            )
        }
        }
    }
}

private const val PLAYER_GESTURE_TUTORIAL_VERSION = 1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumBottomSheet(
    currentSong: Song,
    openBottomSheet: Boolean,
    onDismiss: () -> Unit,
    onAlbumClick: (SongAlbum) -> Unit,
) {
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(openBottomSheet) {
        if (openBottomSheet) {
            bottomSheetState.show()
        } else {
            bottomSheetState.hide()
        }
    }

    if (openBottomSheet) {
        ModalBottomSheet(
            modifier = Modifier.statusBarsPadding(),
            onDismissRequest = onDismiss,
            sheetState = bottomSheetState
        ) {
            LazyColumn(
                Modifier.padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .clickable {
                                    onDismiss()
                                    onAlbumClick(currentSong.al)
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Album,
                                contentDescription = null,
                                Modifier.padding(horizontal = 12.dp)
                            )
                            Text(
                                text = currentSong.al.name,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistBottomSheet(
    currentSong: Song,
    openBottomSheet: Boolean,
    onDismiss: () -> Unit,
    onClick: (Artist) -> Unit,
) {
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(openBottomSheet) {
        if (openBottomSheet) {
            bottomSheetState.show()
        } else {
            bottomSheetState.hide()
        }
    }

    if (openBottomSheet) {
        ModalBottomSheet(
            modifier = Modifier.statusBarsPadding(),
            onDismissRequest = onDismiss,
            sheetState = bottomSheetState
        ) {
            LazyColumn(
                Modifier.padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(currentSong.ar) { index, artist ->
                    val shape = getItemShape(
                        prevItem = currentSong.ar.getOrNull(index - 1),
                        nextItem = currentSong.ar.getOrNull(index + 1),
                        corner = 16.dp,
                        subCorner = 4.dp,
                    )
                    Card(
                        shape = shape,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .clickable {
                                    onDismiss()
                                    onClick(artist)
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Artist,
                                contentDescription = null,
                                Modifier.padding(horizontal = 12.dp)
                            )
                            Text(text = artist.name, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}
