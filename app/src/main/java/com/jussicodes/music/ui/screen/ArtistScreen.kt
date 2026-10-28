package com.jussicodes.music.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import com.jussicodes.music.LocalPlayerController
import com.jussicodes.music.LocalPlayerState
import com.jussicodes.music.constants.MediaSessionConstants
import com.jussicodes.music.constants.MiniPlayerHeight
import com.jussicodes.music.constants.isLandscapePhone
import com.jussicodes.music.R
import com.jussicodes.music.extensions.playMediaAtId
import com.jussicodes.music.extensions.setPlaylist
import com.jussicodes.music.ui.components.AlbumListItem
import com.jussicodes.music.ui.components.ArtistListItem
import com.jussicodes.music.ui.components.LargeImageDialog
import com.jussicodes.music.ui.components.JussiDropdownMenu
import com.jussicodes.music.ui.components.JussiMenuItem
import com.jussicodes.music.ui.components.NavigationTitle
import com.jussicodes.music.ui.components.SongListItem
import com.jussicodes.music.ui.components.SongMenuBottomSheet
import com.jussicodes.music.ui.components.SwipableTabPager
import com.jussicodes.music.ui.components.rememberFilteredSongs
import com.jussicodes.music.ui.navigation.AlbumNav
import com.jussicodes.music.ui.navigation.ArtistNav
import com.jussicodes.music.ui.icons.ChevronDown
import com.jussicodes.music.ui.icons.Search
import com.jussicodes.music.utils.CoverImageSize
import com.jussicodes.music.utils.formatPlayCount
import com.jussicodes.music.utils.toCoverImageUrl
import com.jussicodes.music.viewModel.ArtistScreenViewModel
import com.rcmiku.ncmapi.model.Song
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistScreen(
    navController: NavHostController,
    artistScreenViewModel: ArtistScreenViewModel = hiltViewModel()
) {
    val artistHeadInfoState by artistScreenViewModel.artistHeadInfo.collectAsState()
    val artistTopSongState by artistScreenViewModel.artistTopSong.collectAsState()
    val songMode by artistScreenViewModel.songMode.collectAsState()
    val sortOrder by artistScreenViewModel.sortOrder.collectAsState()
    val allSongsForQueue by artistScreenViewModel.allSongsForQueue.collectAsState()
    val artistAllSongs = artistScreenViewModel.artistAllSongs.collectAsLazyPagingItems()
    val simiArtists by artistScreenViewModel.simiArtists.collectAsState()
    val isArtistSubscribed by artistScreenViewModel.isArtistSubscribed.collectAsState()
    val isArtistSubUpdating by artistScreenViewModel.isArtistSubUpdating.collectAsState()
    val likedSongIds by artistScreenViewModel.likedSongIds.collectAsState()
    val artistAlbumList = artistScreenViewModel.artistAlbumList.collectAsLazyPagingItems()
    val artistFollowStats by artistScreenViewModel.artistFollowStats.collectAsState()
    var openBottomSheet by rememberSaveable { mutableStateOf(false) }
    var selectSong by remember { mutableStateOf<Song?>(null) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var previewArtistImageUrl by remember { mutableStateOf<String?>(null) }
    val mediaController = LocalPlayerController.current.controller
    val playerState = LocalPlayerState.current
    val haptics = LocalHapticFeedback.current
    val isPlaying = playerState?.isPlaying == true
    val currentMediaId = playerState?.currentMediaItem?.mediaId?.toLongOrNull()
    // 小播放器是否显示（有歌在队列）：列表末尾避让空白块只在有歌时留
    val hasMiniPlayer = (playerState?.player?.mediaItemCount ?: 0) != 0

    // 三个 tab（探索/歌曲/专辑）改成左右滑动：HorizontalPager + 跟手指示器 + 相邻页左右预览，
    // 与“最近播放”页同一套 SwipableTabPager。头部（封面 + 信息行）支持向上嵌套滚动折叠吸顶。
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val candidateSongs = allSongsForQueue ?: artistTopSongState?.songs.orEmpty()
    val filteredSongs = rememberFilteredSongs(candidateSongs, searchQuery)

    var headerHeightPx by remember { mutableFloatStateOf(0f) }
    var topBarHeightPx by remember { mutableFloatStateOf(0f) }
    var headerOffsetPx by remember { mutableFloatStateOf(0f) }
    val maxCollapsePx = (headerHeightPx - topBarHeightPx).coerceAtLeast(0f)

    val nestedScrollConnection = remember(maxCollapsePx, searchActive) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (searchActive) return Offset.Zero
                val delta = available.y
                return if (delta < 0f && headerOffsetPx > -maxCollapsePx) {
                    val newOffset = (headerOffsetPx + delta).coerceIn(-maxCollapsePx, 0f)
                    val consumed = newOffset - headerOffsetPx
                    headerOffsetPx = newOffset
                    Offset(0f, consumed)
                } else {
                    Offset.Zero
                }
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (searchActive) return Offset.Zero
                val delta = available.y
                return if (delta > 0f && headerOffsetPx < 0f) {
                    val newOffset = (headerOffsetPx + delta).coerceIn(-maxCollapsePx, 0f)
                    val consumed = newOffset - headerOffsetPx
                    headerOffsetPx = newOffset
                    Offset(0f, consumed)
                } else {
                    Offset.Zero
                }
            }
        }
    }

    LaunchedEffect(searchActive, maxCollapsePx) {
        if (searchActive) {
            headerOffsetPx = -maxCollapsePx
            try {
                searchFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    BackHandler(enabled = searchActive) {
        searchActive = false
        searchQuery = ""
        keyboardController?.hide()
    }

    val heroUrl = artistHeadInfoState?.data?.artist?.cover
        ?: artistTopSongState?.songs?.firstOrNull()?.al?.picUrl
        ?: artistAlbumList.itemSnapshotList.items.firstOrNull()?.picUrl
        ?: artistHeadInfoState?.data?.artist?.picUrl
    val avatarUrl = artistHeadInfoState?.data?.artist?.picUrl ?: heroUrl

    // 横屏（矮屏）走左右分栏：左栏沉浸区 + 右栏内容浏览区。
    // 竖屏保持「顶部横幅 + 三 tab」的单列堆叠，两套布局互不影响。
    if (isLandscapePhone()) {
        ArtistLandscapeBody(
            artistName = artistHeadInfoState?.data?.artist?.name.orEmpty(),
            briefDesc = artistHeadInfoState?.data?.artist?.briefDesc,
            fansText = artistFollowStats?.let { "粉丝 ${formatPlayCount(it.fansCnt.toDouble())}" },
            followText = if (isArtistSubscribed) {
                artistFollowStats?.followDay?.takeIf { it.isNotBlank() }
            } else {
                null
            },
            isSubscribed = isArtistSubscribed,
            subscribeEnabled = !isArtistSubUpdating && artistHeadInfoState != null,
            onToggleSubscribe = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                artistScreenViewModel.toggleArtistSub()
            },
            heroUrl = heroUrl,
            posterUrl = heroUrl,
            onPosterClick = { previewArtistImageUrl = it },
            onBack = { navController.navigateUp() },
            onPlayAll = {
                val queue = allSongsForQueue
                    ?: artistTopSongState?.songs
                    ?: artistAllSongs.itemSnapshotList.items
                if (queue.isNotEmpty()) {
                    mediaController?.setPlaylist(
                        queue,
                        sourceName = artistHeadInfoState?.data?.artist?.name ?: "歌手",
                        sourceType = MediaSessionConstants.SOURCE_TYPE_ARTIST,
                        navId = artistHeadInfoState?.data?.artist?.id ?: 0L
                    )
                    mediaController?.playMediaAtId(queue.first().id)
                }
            },
            songMode = songMode,
            onSongModeChange = { artistScreenViewModel.setSongMode(it) },
            sortOrder = sortOrder,
            onSortOrderChange = { artistScreenViewModel.setSortOrder(it) },
            topSongs = artistTopSongState?.songs.orEmpty(),
            allSongs = artistAllSongs,
            albums = artistAlbumList,
            simiArtists = simiArtists,
            likedSongIds = likedSongIds,
            isPlaying = isPlaying,
            currentMediaId = currentMediaId,
            hasMiniPlayer = hasMiniPlayer,
            onSongClick = { song ->
                // 「全部歌曲」模式下队列用全量列表；热门模式用 Top 50
                val queue = if (songMode == "all") {
                    allSongsForQueue ?: artistTopSongState?.songs.orEmpty()
                } else {
                    artistTopSongState?.songs.orEmpty()
                }
                mediaController?.setPlaylist(
                    queue,
                    sourceName = artistHeadInfoState?.data?.artist?.name ?: "歌手",
                    sourceType = MediaSessionConstants.SOURCE_TYPE_ARTIST,
                    navId = artistHeadInfoState?.data?.artist?.id ?: 0L
                )
                mediaController?.playMediaAtId(song.id)
            },
            onSongMenu = { song ->
                selectSong = song
                openBottomSheet = true
            },
            onAlbumClick = { navController.navigate(AlbumNav(albumId = it.id)) },
            onSimilarArtistClick = {
                navController.navigate(ArtistNav(artistId = it.id))
            }
        )
    } else {
        // 竖屏方案 A：Header 固定原始尺寸随手势平滑向上移出（绝不变形），Tab 栏平滑上移吸顶在顶栏下方，列表底边始终贴紧屏幕底部
        val density = LocalDensity.current
        val backgroundColor = MaterialTheme.colorScheme.background

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .clipToBounds()
                .nestedScroll(nestedScrollConnection)
        ) {
            val screenHeight = maxHeight
            val pagerHeight = if (topBarHeightPx > 0f) {
                (screenHeight - with(density) { topBarHeightPx.toDp() }).coerceAtLeast(0.dp)
            } else {
                screenHeight
            }

            // 1. 头部展示区：Header 完整原始尺寸，绝对不压缩变形，随手势平滑向上移出
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, headerOffsetPx.roundToInt()) }
                    .onSizeChanged {
                        if (headerHeightPx == 0f && it.height > 0) {
                            headerHeightPx = it.height.toFloat()
                        }
                    }
                    .draggable(
                        orientation = Orientation.Vertical,
                        enabled = !searchActive,
                        state = rememberDraggableState { delta ->
                            val newOffset = (headerOffsetPx + delta).coerceIn(-maxCollapsePx, 0f)
                            headerOffsetPx = newOffset
                        }
                    )
            ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        AsyncImage(
                            model = heroUrl.toCoverImageUrl(CoverImageSize.HERO),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .aspectRatio(4f / 3f)
                                .clickable(enabled = heroUrl != null) {
                                    previewArtistImageUrl = heroUrl
                                }
                        )
                        AsyncImage(
                            model = avatarUrl.toCoverImageUrl(CoverImageSize.LIST),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .padding(12.dp)
                                .size(84.dp)
                                .clip(CircleShape)
                                .clickable(enabled = avatarUrl != null) {
                                    previewArtistImageUrl = avatarUrl
                                }
                        )
                    }
                    // 信息行：歌手名 / 粉丝数 / 收藏
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = artistHeadInfoState?.data?.artist?.name.orEmpty(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val stats = artistFollowStats
                            if (stats != null) {
                                val parts = mutableListOf("粉丝 ${formatPlayCount(stats.fansCnt.toDouble())}")
                                if (isArtistSubscribed && stats.followDay.isNotBlank()) {
                                    parts.add(stats.followDay)
                                }
                                Text(
                                    text = parts.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                artistScreenViewModel.toggleArtistSub()
                            },
                            enabled = !isArtistSubUpdating && artistHeadInfoState != null,
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text(text = if (isArtistSubscribed) "取消收藏" else "收藏")
                        }
                    }
                }

            // 2. 列表区：SwipableTabPager（固定高度 pagerHeight，随手势平滑上移吸顶在顶栏下方）
            SwipableTabPager(
                titles = listOf(
                    stringResource(R.string.explore),
                    stringResource(R.string.song),
                    stringResource(R.string.album)
                ),
                pagerState = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(pagerHeight)
                    .offset {
                        val currentY = if (headerHeightPx > 0f && topBarHeightPx > 0f) {
                            (headerHeightPx + headerOffsetPx).coerceAtLeast(topBarHeightPx)
                        } else {
                            0f
                        }
                        IntOffset(0, currentY.roundToInt())
                    }
            ) { page ->
                    when (page) {
                        0 -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                            item(key = "explore-info") {
                                NavigationTitle(
                                    title = stringResource(R.string.artist_info),
                                    modifier = Modifier.animateItem(placementSpec = null)
                                )
                                artistHeadInfoState?.data?.artist?.briefDesc?.trimIndent()?.let {
                                    Card(
                                        modifier = Modifier
                                            .animateItem(placementSpec = null)
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp)
                                            .padding(bottom = 12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                                        )
                                    ) {
                                        Text(
                                            text = if (it.isNotBlank()) it else stringResource(R.string.no_brief),
                                            modifier = Modifier.padding(12.dp),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                            if (simiArtists.isNotEmpty()) {
                                item {
                                    NavigationTitle(title = "相似歌手")
                                }
                                itemsIndexed(
                                    items = simiArtists,
                                    key = { _, artist -> "explore-artist-${artist.id}" }
                                ) { _, artist ->
                                    ArtistListItem(
                                        artist = artist,
                                        modifier = Modifier
                                            .animateItem(placementSpec = null)
                                            .clickable {
                                                navController.navigate(ArtistNav(artistId = artist.id))
                                            }
                                    )
                                }
                            }

                            item {
                                // 小播放器避让：有歌时末尾垫 64dp，最后一行不被悬浮播放器盖住
                                if (hasMiniPlayer) Spacer(modifier = Modifier.height(MiniPlayerHeight))
                            }
                        }

                        1 -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                            if (searchActive && searchQuery.isNotBlank()) {
                                if (filteredSongs.isEmpty()) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(32.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "未找到相关歌曲",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                } else {
                                    itemsIndexed(
                                        items = filteredSongs,
                                        key = { _, song -> "search-song-${song.id}" }
                                    ) { index, song ->
                                        SongListItem(
                                            song = song,
                                            isPlaying = isPlaying,
                                            showLikedIcon = song.id in likedSongIds,
                                            isActive = currentMediaId == song.id,
                                            songIndex = index + 1,
                                            modifier = Modifier
                                                .animateItem(placementSpec = null)
                                                .clickable {
                                                    mediaController?.setPlaylist(
                                                        filteredSongs,
                                                        sourceName = artistHeadInfoState?.data?.artist?.name ?: "歌手",
                                                        sourceType = MediaSessionConstants.SOURCE_TYPE_ARTIST,
                                                        navId = artistHeadInfoState?.data?.artist?.id ?: 0L
                                                    )
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
                            } else {
                                item(key = "song-toolbar") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp)
                                            .padding(top = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        FilterChip(
                                            selected = songMode == "top",
                                            onClick = { artistScreenViewModel.setSongMode("top") },
                                            label = { Text(stringResource(R.string.artist_song_top50)) }
                                        )
                                        FilterChip(
                                            selected = songMode == "all",
                                            onClick = { artistScreenViewModel.setSongMode("all") },
                                            label = { Text(stringResource(R.string.artist_song_all)) }
                                        )
                                        Spacer(Modifier.weight(1f))
                                        Box {
                                            FilterChip(
                                                selected = sortMenuExpanded,
                                                enabled = songMode == "all",
                                                onClick = { sortMenuExpanded = true },
                                                label = { Text(stringResource(R.string.artist_sort)) },
                                                trailingIcon = {
                                                    Icon(
                                                        imageVector = ChevronDown,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            )
                                            JussiDropdownMenu(
                                                expanded = sortMenuExpanded,
                                                onDismissRequest = { sortMenuExpanded = false }
                                            ) {
                                                listOf(
                                                    "hot" to stringResource(R.string.artist_sort_hot),
                                                    "time" to stringResource(R.string.artist_sort_time)
                                                ).forEach { (order, label) ->
                                                    val checked = sortOrder == order
                                                    JussiMenuItem(
                                                        text = label,
                                                        icon = if (order == "hot") {
                                                            Icons.Outlined.LocalFireDepartment
                                                        } else {
                                                            Icons.Outlined.Schedule
                                                        },
                                                        selected = checked,
                                                        trailing = {
                                                            Checkbox(checked = checked, onCheckedChange = null)
                                                        },
                                                        onClick = {
                                                            artistScreenViewModel.setSortOrder(order)
                                                            sortMenuExpanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                if (songMode == "top") {
                                    artistTopSongState?.songs?.let { songs ->
                                        itemsIndexed(
                                            items = songs,
                                            key = { _, song -> "top-song-${song.id}" }
                                        ) { index, song ->
                                            SongListItem(
                                                song = song,
                                                isPlaying = isPlaying,
                                                showLikedIcon = song.id in likedSongIds,
                                                isActive = currentMediaId == song.id,
                                                songIndex = index + 1,
                                                modifier = Modifier
                                                    .animateItem(placementSpec = null)
                                                    .clickable {
                                                        mediaController?.setPlaylist(
                                                            songs,
                                                            sourceName = artistHeadInfoState?.data?.artist?.name ?: "歌手",
                                                            sourceType = MediaSessionConstants.SOURCE_TYPE_ARTIST,
                                                            navId = artistHeadInfoState?.data?.artist?.id ?: 0L
                                                        )
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
                                } else {
                                    items(
                                        count = artistAllSongs.itemCount,
                                        key = { index -> "all-song-${artistAllSongs.peek(index)?.id ?: index}" }
                                    ) { index ->
                                        artistAllSongs[index]?.let { song ->
                                            val queueSongs = allSongsForQueue
                                                ?: artistAllSongs.itemSnapshotList.items
                                            SongListItem(
                                                song = song,
                                                isPlaying = isPlaying,
                                                showLikedIcon = song.id in likedSongIds,
                                                isActive = currentMediaId == song.id,
                                                songIndex = index + 1,
                                                modifier = Modifier
                                                    .animateItem(placementSpec = null)
                                                    .clickable {
                                                        mediaController?.setPlaylist(
                                                            queueSongs,
                                                            sourceName = artistHeadInfoState?.data?.artist?.name ?: "歌手",
                                                            sourceType = MediaSessionConstants.SOURCE_TYPE_ARTIST,
                                                            navId = artistHeadInfoState?.data?.artist?.id ?: 0L
                                                        )
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
                                // 小播放器避让：有歌时末尾垫 64dp，最后一行不被悬浮播放器盖住
                                if (hasMiniPlayer) Spacer(modifier = Modifier.height(MiniPlayerHeight))
                            }
                        }

                        else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(
                                count = artistAlbumList.itemCount,
                                key = { index -> "album-${artistAlbumList.peek(index)?.id ?: index}" }
                            ) { index ->
                                artistAlbumList[index]?.let {
                                    AlbumListItem(
                                        album = it,
                                        modifier = Modifier
                                            .animateItem(placementSpec = null)
                                            .clickable {
                                                navController.navigate(AlbumNav(albumId = it.id))
                                            }
                                    )
                                }
                            }

                            item {
                                // 小播放器避让：有歌时末尾垫 64dp，最后一行不被悬浮播放器盖住
                                if (hasMiniPlayer) Spacer(modifier = Modifier.height(MiniPlayerHeight))
                            }
                        }
                    }
                }

            // 3. 悬浮吸顶顶栏：TopAppBar（固定在最顶层，吸顶时歌手名在返回按钮旁平滑淡入，搜索按钮无圆底）
            TopAppBar(
                modifier = Modifier
                    .onSizeChanged {
                        if (topBarHeightPx == 0f && it.height > 0) {
                            topBarHeightPx = it.height.toFloat()
                        }
                    }
                    .drawBehind {
                        val maxCollapse = maxCollapsePx
                        val fraction = if (maxCollapse > 0f) {
                            (-headerOffsetPx / maxCollapse).coerceIn(0f, 1f)
                        } else 0f
                        drawRect(
                            color = if (searchActive) backgroundColor else backgroundColor.copy(alpha = fraction)
                        )
                    },
                title = {
                    if (searchActive) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(searchFocusRequester),
                            placeholder = { Text(stringResource(R.string.search)) },
                            singleLine = true,
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "清空"
                                        )
                                    }
                                }
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                    } else {
                        // 滚动到 Tab 吸顶时，歌手名在返回按钮旁平滑淡入
                        Text(
                            text = artistHeadInfoState?.data?.artist?.name.orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.graphicsLayer {
                                val fraction = if (maxCollapsePx > 0f) {
                                    (-headerOffsetPx / maxCollapsePx).coerceIn(0f, 1f)
                                } else 0f
                                alpha = ((fraction - 0.75f) / 0.25f).coerceIn(0f, 1f)
                            }
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (searchActive) {
                                searchActive = false
                                searchQuery = ""
                                keyboardController?.hide()
                            } else {
                                navController.navigateUp()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                actions = {
                    if (!searchActive) {
                        IconButton(
                            onClick = {
                                searchActive = true
                                artistScreenViewModel.loadAllSongsIfNeeded()
                                scope.launch {
                                    pagerState.animateScrollToPage(1)
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Search,
                                contentDescription = stringResource(R.string.search)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    }

    SongMenuBottomSheet(
        navController = navController,
        song = selectSong,
        onDismiss = { openBottomSheet = false },
        openBottomSheet = openBottomSheet
    )
    previewArtistImageUrl?.let { imageUrl ->
        LargeImageDialog(
            imageUrl = imageUrl.toCoverImageUrl(CoverImageSize.LARGE),
            onDismiss = { previewArtistImageUrl = null },
            showSaveAction = true
        )
    }
}