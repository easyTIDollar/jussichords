package com.jussicodes.music.ui.screen

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.jussicodes.music.LocalPlayerController
import com.jussicodes.music.LocalPlayerState
import com.jussicodes.music.R
import com.jussicodes.music.constants.AlbumThumbnailSize
import com.jussicodes.music.constants.DURATION_EXIT_SHORT
import com.jussicodes.music.constants.ListItemHeight
import com.jussicodes.music.data.favoriteSongIdsDatastore
import com.jussicodes.music.extensions.playMediaAtId
import com.jussicodes.music.extensions.setPlaylist
import com.jussicodes.music.ui.components.GridThumbnailImage
import com.jussicodes.music.ui.components.NavigationTitle
import com.jussicodes.music.ui.components.PlaylistGridItem
import com.jussicodes.music.ui.components.SongListItem
import com.jussicodes.music.ui.components.SongMenuBottomSheet
import com.jussicodes.music.ui.components.TopBar
import com.jussicodes.music.ui.navigation.PlaylistNav
import com.jussicodes.music.viewModel.ExploreScreenViewModel
import com.rcmiku.ncmapi.model.Song
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    navController: NavHostController,
    exploreScreenViewModel: ExploreScreenViewModel = hiltViewModel(),
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope
) {
    val recommendSongsState by exploreScreenViewModel.recommendSongs.collectAsState()
    val recommendPlaylistState by exploreScreenViewModel.recommendPlaylist.collectAsState()
    val gridState = rememberLazyGridState()
    val playlistRowState = rememberLazyListState()
    val mediaController = LocalPlayerController.current.controller
    val playerState = LocalPlayerState.current
    val isPlaying = playerState?.isPlaying == true
    val currentMediaId = playerState?.currentMediaItem?.mediaId?.toLongOrNull()
    var openBottomSheet by rememberSaveable { mutableStateOf(false) }
    var selectSong by remember { mutableStateOf<Song?>(null) }
    val context = LocalContext.current
    val state = rememberPullToRefreshState()
    var isRefreshing by remember { mutableStateOf(false) }
    val songIds by context.favoriteSongIdsDatastore.data.map { it.songIdsList }
        .collectAsState(emptyList())
    val coroutineScope = rememberCoroutineScope()
    val viewConfiguration = LocalViewConfiguration.current
    var horizontalDragDirection by remember { mutableStateOf(0) }
    val yieldToHomePager = horizontalDragDirection > 0

    val onRefresh: () -> Unit = {
        isRefreshing = true
        coroutineScope.launch {
            exploreScreenViewModel.refresh()
            delay(1000)
            isRefreshing = false
        }
    }

    with(sharedTransitionScope) {

        Scaffold(
            topBar = {
                TopBar(navController = navController, titleRes = R.string.explore)
            },
        ) { padding ->
            PullToRefreshBox(
                modifier = Modifier
                    .padding(top = padding.calculateTopPadding())
                    .fillMaxSize()
                    .pointerInput(viewConfiguration.touchSlop) {
                        awaitEachGesture {
                            horizontalDragDirection = 0
                            val down = awaitFirstDown(
                                requireUnconsumed = false,
                                pass = PointerEventPass.Initial
                            )
                            var totalX = 0f
                            var totalY = 0f

                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: break
                                val delta = change.positionChange()
                                totalX += delta.x
                                totalY += delta.y

                                if (
                                    horizontalDragDirection == 0 &&
                                    abs(totalX) > viewConfiguration.touchSlop &&
                                    abs(totalX) > abs(totalY)
                                ) {
                                    horizontalDragDirection = if (totalX > 0f) 1 else -1
                                }
                            } while (change.pressed)

                            horizontalDragDirection = 0
                        }
                    },
                state = state,
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                indicator = {
                    Indicator(
                        modifier = Modifier.align(Alignment.TopCenter),
                        isRefreshing = isRefreshing,
                        state = state
                    )
                }
            ) {
                LazyColumn {
                    item {
                        recommendSongsState?.onSuccess {
                            NavigationTitle(
                                title = stringResource(R.string.recommend_songs),
                                modifier = Modifier.animateItem()
                            )
                            LazyHorizontalGrid(
                                rows = GridCells.Fixed(4),
                                state = gridState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(ListItemHeight * 4)
                                    .animateItem(),
                                flingBehavior = rememberSnapFlingBehavior(
                                    gridState,
                                    snapPosition = SnapPosition.Start
                                ),
                                userScrollEnabled = !(yieldToHomePager && !gridState.canScrollBackward)
                            ) {
                                itemsIndexed(it.data.dailySongs) { _, song ->
                                    SongListItem(
                                        isPlaying = isPlaying,
                                        isActive = currentMediaId == song.id,
                                        showLikedIcon = song.id in songIds,
                                        song = song,
                                        modifier = Modifier
                                            .clip(MaterialTheme.shapes.small)
                                            .width(340.dp)
                                            .clickable {
                                                mediaController?.setPlaylist(it.data.dailySongs)
                                                mediaController?.playMediaAtId(song.id)
                                            },
                                        trailingContent = {
                                            IconButton(onClick = {
                                                selectSong = song
                                                openBottomSheet = true
                                            }) {
                                                Icon(
                                                    imageVector = Icons.Default.MoreVert,
                                                    contentDescription = stringResource(R.string.more)
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        recommendPlaylistState?.onSuccess {
                            NavigationTitle(
                                title = stringResource(R.string.recommend_playlist),
                                modifier = Modifier.animateItem()
                            )
                            LazyRow(
                                state = playlistRowState,
                                userScrollEnabled = !(yieldToHomePager && !playlistRowState.canScrollBackward)
                            ) {
                                items(it.result) { playlist ->

                                    PlaylistGridItem(
                                        playlist = playlist,
                                        modifier = Modifier
                                            .sharedBounds(
                                                sharedContentState = rememberSharedContentState(
                                                    key = playlist.name + playlist.id
                                                ),
                                                animatedVisibilityScope = animatedContentScope,
                                                placeHolderSize = SharedTransitionScope.PlaceHolderSize.animatedSize,
                                                boundsTransform = AlbumArtBoundsTransform,
                                                enter = fadeIn(
                                                    tweenEnter(delayMillis = DURATION_EXIT_SHORT)
                                                ),
                                                exit = fadeOut(
                                                    tweenExit(durationMillis = DURATION_EXIT_SHORT)
                                                )
                                            )
                                            .clip(MaterialTheme.shapes.small)
                                            .width(AlbumThumbnailSize)
                                            .clickable(
                                                onClick = {
                                                    navController.navigate(
                                                        PlaylistNav(
                                                            playlistId = playlist.id,
                                                            limit = playlist.trackCount
                                                        )
                                                    )
                                                }
                                            ),
                                        thumbnailContent = {
                                            GridThumbnailImage(
                                                url = playlist.picUrl,
                                                modifier = Modifier
                                                    .sharedElement(
                                                        sharedTransitionScope.rememberSharedContentState(
                                                            key = playlist.id
                                                        ),
                                                        animatedVisibilityScope = animatedContentScope
                                                    )
                                            )
                                        }
                                    )
                                }

                            }
                        }
                    }
                }
            }
        }
    }

    SongMenuBottomSheet(
        navController = navController,
        song = selectSong,
        onDismiss = { openBottomSheet = false },
        openBottomSheet = openBottomSheet
    )
}
