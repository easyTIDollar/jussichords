package com.jussicodes.music.ui.screen

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaMetadata
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.jussicodes.music.LocalPlayerController
import com.jussicodes.music.LocalPlayerState
import com.jussicodes.music.constants.MediaSessionConstants
import com.jussicodes.music.constants.MiniPlayerHeight
import com.jussicodes.music.R
import com.jussicodes.music.constants.ThumbnailCornerRadius
import com.jussicodes.music.constants.isLandscapePhone
import com.jussicodes.music.data.PinnedAlbumStore
import com.jussicodes.music.data.favoriteSongIdsDatastore
import com.jussicodes.music.extensions.playMediaAt
import com.jussicodes.music.extensions.playMediaAtId
import com.jussicodes.music.extensions.setPlaylist
import com.jussicodes.music.ui.components.JussiDropdownMenu
import com.jussicodes.music.ui.components.JussiMenuItem
import com.jussicodes.music.ui.components.PlayerComments
import com.jussicodes.music.ui.components.LargeImageDialog
import com.jussicodes.music.ui.components.SongListItem
import com.jussicodes.music.ui.components.SongMenuBottomSheet
import com.jussicodes.music.ui.components.SharePayload
import com.jussicodes.music.ui.components.ShareSheet
import com.jussicodes.music.ui.components.rememberFilteredSongs
import com.jussicodes.music.ui.icons.LibraryAdd
import com.jussicodes.music.ui.icons.LibraryAddCheck
import com.jussicodes.music.ui.icons.ModeComment
import com.jussicodes.music.ui.icons.PushPin
import com.jussicodes.music.ui.icons.PushPinFill
import com.jussicodes.music.ui.icons.Search
import com.jussicodes.music.ui.navigation.ArtistNav
import com.jussicodes.music.ui.navigation.UserNav
import com.jussicodes.music.utils.CoverImageSize
import com.jussicodes.music.utils.formatTimestamp
import com.jussicodes.music.utils.toCoverImageUrl
import com.jussicodes.music.viewModel.AlbumScreenViewModel
import com.rcmiku.ncmapi.model.Album
import com.rcmiku.ncmapi.model.Song
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun AlbumScreen(
    navController: NavHostController,
    albumScreenViewModel: AlbumScreenViewModel = hiltViewModel(),
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope
) {
    val albumDetailState by albumScreenViewModel.albumDetail.collectAsState()
    val listState = rememberLazyListState()
    val showAlbumTitle by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    var playlistTitle by remember { mutableStateOf("") }
    val mediaController = LocalPlayerController.current.controller
    val playerState = LocalPlayerState.current
    val isPlaying = playerState?.isPlaying == true
    val currentMediaId = playerState?.currentMediaItem?.mediaId?.toLongOrNull()
    // 小播放器是否显示（有歌在队列）：列表末尾避让空白块只在有歌时留
    val hasMiniPlayer = (playerState?.player?.mediaItemCount ?: 0) != 0
    val albumInfoState by albumScreenViewModel.albumInfo.collectAsState()
    var openBottomSheet by rememberSaveable { mutableStateOf(false) }
    var openAlbumComments by rememberSaveable { mutableStateOf(false) }
    var sharePayload by remember { mutableStateOf<SharePayload?>(null) }
    var selectSong by remember { mutableStateOf<Song?>(null) }
    var previewCoverUrl by remember { mutableStateOf<String?>(null) }
    var albumMenuExpanded by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchFocusRequester = remember { FocusRequester() }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val pinnedAlbums by PinnedAlbumStore.albums.collectAsState()
    val songIds by context.favoriteSongIdsDatastore.data.map { it.songIdsList }
        .collectAsState(emptyList())
    val currentAlbum = albumDetailState?.getOrNull()?.album
    val isLandscape = isLandscapePhone()
    val albumSongs = remember(albumDetailState?.getOrNull()?.songs) {
        albumDetailState?.getOrNull()?.songs.orEmpty()
    }
    val visibleSongs = rememberFilteredSongs(albumSongs, searchQuery)
    // 序号取全量列表里的位置：搜索过滤后不重新编号
    val albumIndexById = remember(albumSongs) { albumSongs.withIndex().associate { it.value.id to it.index + 1 } }
    val isPinned = pinnedAlbums.any { it.id == currentAlbum?.id }

    LaunchedEffect(searchActive) {
        if (searchActive) {
            // requestFocus 在 requester 尚未挂载到节点时会抛 IllegalStateException，用 runCatching 兜住。
            runCatching { searchFocusRequester.requestFocus() }
            keyboardController?.show()
        }
    }

    fun togglePinnedAlbum(album: Album) {
        coroutineScope.launch {
            PinnedAlbumStore.togglePin(album)
        }
    }

    Scaffold(
        topBar = {
            // 横屏（矮屏）走主从双栏，顶栏按钮移到左栏顶部，这里不显示顶栏。
            if (!isLandscape) {
                TopAppBar(
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
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                    unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant
                                )
                            )
                        } else {
                            Text(
                                if (showAlbumTitle) playlistTitle else stringResource(R.string.album),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (searchActive) {
                                searchActive = false
                                searchQuery = ""
                                keyboardController?.hide()
                            } else {
                                navController.navigateUp()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            searchActive = !searchActive
                            if (!searchActive) {
                                searchQuery = ""
                                keyboardController?.hide()
                            }
                        }) {
                            Icon(
                                imageVector = Search,
                                contentDescription = stringResource(R.string.search)
                            )
                        }
                        Box {
                            IconButton(onClick = { albumMenuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.more)
                                )
                            }
                            AlbumOverflowMenu(
                                expanded = albumMenuExpanded,
                                onDismiss = { albumMenuExpanded = false },
                                isPinned = isPinned,
                                onTogglePin = {
                                    albumMenuExpanded = false
                                    currentAlbum?.let { album ->
                                        togglePinnedAlbum(album)
                                        Toast.makeText(
                                            context,
                                            if (isPinned) "已取消置顶《${album.name}》" else "已固定到主页",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                onShare = {
                                    albumMenuExpanded = false
                                    currentAlbum?.let { album ->
                                        sharePayload = SharePayload.AlbumShare(album)
                                    }
                                }
                            )
                        }
                    }
                )
            }
        }
    ) { padding ->
        if (isLandscape) {
            // 不要在 onSuccess{}/let{} 这类内联 lambda 里调 @Composable 函数：
            // 编译器对内联 lambda 中的 composable 调用有额外限制。显式判空后直调。
            val landscapeDetail = albumDetailState?.getOrNull()
            if (landscapeDetail != null) {
                val detail = landscapeDetail
                val album = detail.album
                AlbumLandscapeBody(
                    albumName = album.name,
                    artistName = album.artist.name,
                    publishTimeText = formatTimestamp(album.publishTime),
                    cover = {
                        with(sharedTransitionScope) {
                            AsyncImage(
                                model = album.picUrl.toCoverImageUrl(CoverImageSize.DETAIL),
                                contentDescription = album.name,
                                contentScale = ContentScale.Crop,
                                filterQuality = FilterQuality.High,
                                modifier = Modifier
                                    .sharedElement(
                                        sharedTransitionScope.rememberSharedContentState(key = album.id),
                                        animatedVisibilityScope = animatedContentScope
                                    )
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(ThumbnailCornerRadius))
                                    .clickable {
                                        previewCoverUrl = album.picUrl.toCoverImageUrl(CoverImageSize.LARGE)
                                    }
                            )
                        }
                    },
                    isSubscribed = albumInfoState?.isSub == true,
                    isPinned = isPinned,
                    songs = visibleSongs,
                    isPlaying = isPlaying,
                    currentMediaId = currentMediaId,
                    likedSongIds = songIds,
                    hasMiniPlayer = hasMiniPlayer,
                    searchActive = searchActive,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    searchFocusRequester = searchFocusRequester,
                    onToggleSearch = {
                        searchActive = !searchActive
                        if (!searchActive) {
                            searchQuery = ""
                            keyboardController?.hide()
                        }
                    },
                    onBack = { navController.navigateUp() },
                    onArtistClick = { navController.navigate(ArtistNav(artistId = album.artist.id)) },
                    onToggleSubscribe = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        albumScreenViewModel.albumSub(isSub = albumInfoState?.isSub != true)
                    },
                    onTogglePin = {
                        togglePinnedAlbum(album)
                        Toast.makeText(
                            context,
                            if (isPinned) "已取消置顶《${album.name}》" else "已固定到主页",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onShare = { sharePayload = SharePayload.AlbumShare(album) },
                    onPlayAll = {
                        mediaController?.setPlaylist(
                            detail.songs,
                            sourceId = album.id,
                            sourceName = album.name,
                            sourceType = MediaSessionConstants.SOURCE_TYPE_ALBUM,
                            navId = album.id
                        )
                        mediaController?.playMediaAt()
                    },
                    onComments = { openAlbumComments = true },
                    onSongClick = { song ->
                        mediaController?.setPlaylist(
                            detail.songs,
                            sourceId = album.id,
                            sourceName = album.name,
                            sourceType = MediaSessionConstants.SOURCE_TYPE_ALBUM,
                            navId = album.id
                        )
                        mediaController?.playMediaAtId(song.id)
                    },
                    onSongMenu = { song ->
                        selectSong = song
                        openBottomSheet = true
                    },
                    modifier = Modifier.padding(padding)
                )
            }
            return@Scaffold
        }
        LazyColumn(contentPadding = padding, state = listState) {
            albumDetailState?.onSuccess { detail ->
                playlistTitle = detail.album.name
                if (!searchActive || searchQuery.isBlank()) {
                    item {
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            with(sharedTransitionScope) {
                                AsyncImage(
                                    model = detail.album.picUrl.toCoverImageUrl(CoverImageSize.DETAIL),
                                    contentDescription = detail.album.name,
                                    contentScale = ContentScale.Crop,
                                    filterQuality = FilterQuality.High,
                                    modifier = Modifier
                                        .sharedElement(
                                            sharedTransitionScope.rememberSharedContentState(key = detail.album.id),
                                            animatedVisibilityScope = animatedContentScope
                                        )
                                        .size(200.dp)
                                        .clip(RoundedCornerShape(ThumbnailCornerRadius))
                                        .clickable {
                                            previewCoverUrl = detail.album.picUrl.toCoverImageUrl(
                                                CoverImageSize.LARGE
                                            )
                                        }
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            // 专辑名与歌手要能一眼分开：专辑名大字号加粗，歌手小字号主色；
                            // 发布时间跟在歌手同一行，不再单起一行。
                            Text(
                                text = detail.album.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 2,
                                modifier = Modifier.padding(horizontal = 10.dp)
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(horizontal = 10.dp)
                            ) {
                                Text(
                                    text = detail.album.artist.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center,
                                    overflow = TextOverflow.Ellipsis,
                                    maxLines = 1,
                                    modifier = Modifier
                                        .clickable { navController.navigate(ArtistNav(artistId = detail.album.artist.id)) }
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "·",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = formatTimestamp(detail.album.publishTime),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    overflow = TextOverflow.Ellipsis,
                                    maxLines = 1
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        albumInfoState?.isSub?.let { isSub -> albumScreenViewModel.albumSub(isSub = !isSub) }
                                    },
                                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (albumInfoState?.isSub == true) LibraryAddCheck else LibraryAdd,
                                        contentDescription = null,
                                        modifier = Modifier.size(ButtonDefaults.IconSize)
                                    )
                                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                    Text(text = if (albumInfoState?.isSub == true) stringResource(R.string.library_add_check) else stringResource(R.string.library_add))
                                }
                                Button(
                                    onClick = {
                                        mediaController?.setPlaylist(
                                            detail.songs,
                                            sourceId = detail.album.id,
                                            sourceName = detail.album.name,
                                            sourceType = MediaSessionConstants.SOURCE_TYPE_ALBUM,
                                            navId = detail.album.id
                                        )
                                        mediaController?.playMediaAt()
                                    },
                                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                    Text(text = stringResource(R.string.play))
                                }
                                OutlinedButton(
                                    onClick = { openAlbumComments = true },
                                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = ModeComment,
                                        contentDescription = null,
                                        modifier = Modifier.size(ButtonDefaults.IconSize)
                                    )
                                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                    Text(text = "评论")
                                }
                            }
                        }
                    }
                }

                itemsIndexed(visibleSongs) { _, song ->
                    SongListItem(
                        isPlaying = isPlaying,
                        isActive = currentMediaId == song.id,
                        showLikedIcon = song.id in songIds,
                        song = song,
                        albumIndex = albumIndexById[song.id],
                        modifier = Modifier.clickable {
                            mediaController?.setPlaylist(
                                detail.songs,
                                sourceId = detail.album.id,
                                sourceName = detail.album.name,
                                sourceType = MediaSessionConstants.SOURCE_TYPE_ALBUM,
                                navId = detail.album.id
                            )
                            mediaController?.playMediaAtId(song.id)
                        },
                        trailingContent = {
                            IconButton(onClick = {
                                selectSong = song
                                openBottomSheet = true
                            }) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more))
                            }
                        }
                    )
                }

                item {
                    // 小播放器避让：有歌时末尾垫 64dp，最后一行不被悬浮播放器盖住
                    if (hasMiniPlayer) Spacer(modifier = Modifier.height(MiniPlayerHeight))
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

    sharePayload?.let { payload ->
        ShareSheet(
            payload = payload,
            openBottomSheet = true,
            onDismiss = { sharePayload = null }
        )
    }

    previewCoverUrl?.let { imageUrl ->
        LargeImageDialog(
            imageUrl = imageUrl,
            onDismiss = { previewCoverUrl = null },
            showSaveAction = true
        )
    }

    if (openAlbumComments) {
        currentAlbum?.let { album ->
            val albumMetadata = remember(album.id, album.picUrl) {
                MediaMetadata.Builder()
                    .setTitle(album.name)
                    .setArtist(album.artist.name)
                    .setArtworkUri(Uri.parse(album.picUrl.toCoverImageUrl(CoverImageSize.DETAIL)))
                    .build()
            }
            PlayerComments(
                mediaId = album.id,
                mediaMetadata = albumMetadata,
                commentType = 3,
                onBackPressed = { openAlbumComments = false },
                onUserClick = { userId ->
                    openAlbumComments = false
                    navController.navigate(UserNav(userId = userId))
                }
            )
        }
    }
}

/**
 * 专辑「更多」下拉菜单（固定专辑 / 分享）。
 *
 * 与歌单页的「更多」菜单一致：纯文字条目，不用图标。
 */
@Composable
fun AlbumOverflowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onShare: () -> Unit,
) {
    JussiDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        JussiMenuItem(
            text = if (isPinned) "取消固定" else "固定专辑",
            icon = if (isPinned) PushPinFill else PushPin,
            onClick = onTogglePin
        )
        JussiMenuItem(
            text = "分享",
            icon = Icons.Outlined.Share,
            onClick = onShare
        )
    }
}
