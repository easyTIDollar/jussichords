package com.jussicodes.music.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import com.jussicodes.music.LocalPlayerController
import com.jussicodes.music.LocalPlayerState
import com.jussicodes.music.R
import com.jussicodes.music.extensions.playMediaAtId
import com.jussicodes.music.extensions.setPlaylist
import com.jussicodes.music.ui.components.AlbumListItem
import com.jussicodes.music.ui.components.ArtistListItem
import com.jussicodes.music.ui.components.NavigationTitle
import com.jussicodes.music.ui.components.SongListItem
import com.jussicodes.music.ui.components.SongMenuBottomSheet
import com.jussicodes.music.ui.navigation.AlbumNav
import com.jussicodes.music.ui.navigation.ArtistNav
import com.jussicodes.music.utils.CoverImageSize
import com.jussicodes.music.utils.toCoverImageUrl
import com.jussicodes.music.viewModel.ArtistScreenViewModel
import com.rcmiku.ncmapi.model.Song

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistScreen(
    navController: NavHostController,
    artistScreenViewModel: ArtistScreenViewModel = hiltViewModel()
) {
    val artistHeadInfoState by artistScreenViewModel.artistHeadInfo.collectAsState()
    val artistTopSongState by artistScreenViewModel.artistTopSong.collectAsState()
    val artistAllSongs by artistScreenViewModel.artistAllSongs.collectAsState()
    val simiArtists by artistScreenViewModel.simiArtists.collectAsState()
    val artistAlbumList = artistScreenViewModel.artistAlbumList.collectAsLazyPagingItems()
    val listState = rememberLazyListState()
    val showTitle by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    var state by rememberSaveable { mutableIntStateOf(1) }
    val mediaController = LocalPlayerController.current.controller
    val playerState = LocalPlayerState.current
    val isPlaying = playerState?.isPlaying == true
    val currentMediaId = playerState?.currentMediaItem?.mediaId?.toLongOrNull()
    var openBottomSheet by rememberSaveable { mutableStateOf(false) }
    var selectSong by remember { mutableStateOf<Song?>(null) }
    var horizontalDragAmount = 0f

    val heroUrl = artistHeadInfoState?.data?.artist?.cover
        ?: artistTopSongState?.songs?.firstOrNull()?.al?.picUrl
        ?: artistAlbumList.itemSnapshotList.items.firstOrNull()?.picUrl
        ?: artistHeadInfoState?.data?.artist?.picUrl
    val avatarUrl = artistHeadInfoState?.data?.artist?.picUrl ?: heroUrl

    LazyColumn(
        state = listState,
        modifier = Modifier.pointerInput(state) {
            detectHorizontalDragGestures(
                onDragStart = { horizontalDragAmount = 0f },
                onHorizontalDrag = { _, dragAmount ->
                    horizontalDragAmount += dragAmount
                },
                onDragEnd = {
                    if (horizontalDragAmount < -80f && state < 2) {
                        state += 1
                    } else if (horizontalDragAmount > 80f && state > 0) {
                        state -= 1
                    }
                },
                onDragCancel = { horizontalDragAmount = 0f }
            )
        }
    ) {
        item {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.BottomStart
            ) {
                AsyncImage(
                    model = heroUrl.toCoverImageUrl(CoverImageSize.HERO),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.aspectRatio(4f / 3f)
                )
                AsyncImage(
                    model = avatarUrl.toCoverImageUrl(CoverImageSize.LIST),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .padding(12.dp)
                        .size(84.dp)
                        .clip(CircleShape)
                )
                artistHeadInfoState?.data?.artist?.name?.let {
                    Box(
                        Modifier
                            .padding(6.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(color = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(
                            text = it,
                            modifier = Modifier.padding(4.dp),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }

        item {
            Column {
                SecondaryTabRow(selectedTabIndex = state) {
                    listOf(
                        stringResource(R.string.explore),
                        stringResource(R.string.song),
                        stringResource(R.string.album)
                    ).forEachIndexed { index, title ->
                        Tab(
                            selected = state == index,
                            onClick = { state = index },
                            text = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                }
            }
        }

        when (state) {
            0 -> {
                item {
                    NavigationTitle(title = stringResource(R.string.artist_info))
                    artistHeadInfoState?.data?.artist?.briefDesc?.trimIndent()?.let {
                        Card(
                            modifier = Modifier
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
                    itemsIndexed(simiArtists) { _, artist ->
                        ArtistListItem(
                            artist = artist,
                            modifier = Modifier.clickable {
                                navController.navigate(ArtistNav(artistId = artist.id))
                            }
                        )
                    }
                }
            }

            1 -> {
                artistTopSongState?.songs?.let { songs ->
                    itemsIndexed(songs) { index, song ->
                        SongListItem(
                            song = song,
                            isPlaying = isPlaying,
                            isActive = currentMediaId == song.id,
                            songIndex = index + 1,
                            modifier = Modifier.clickable {
                                mediaController?.setPlaylist(songs)
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
                artistAllSongs?.songs?.let { songs ->
                    if (songs.isNotEmpty()) {
                        item {
                            NavigationTitle(title = "全部歌曲")
                        }
                        itemsIndexed(songs) { index, song ->
                            SongListItem(
                                song = song,
                                isPlaying = isPlaying,
                                isActive = currentMediaId == song.id,
                                songIndex = index + 1,
                                modifier = Modifier.clickable {
                                    mediaController?.setPlaylist(songs)
                                    mediaController?.playMediaAtId(song.id)
                                }
                            )
                            if (index != songs.lastIndex) {
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }

            2 -> {
                items(artistAlbumList.itemCount) { index ->
                    artistAlbumList[index]?.let {
                        AlbumListItem(
                            album = it,
                            modifier = Modifier.clickable {
                                navController.navigate(AlbumNav(albumId = it.id))
                            }
                        )
                    }
                }
            }
        }

        item {
            Spacer(Modifier.navigationBarsPadding())
        }
    }

    TopAppBar(
        title = {
            Text(
                text = if (showTitle) artistHeadInfoState?.data?.artist?.name ?: "" else "",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            IconButton(
                onClick = { navController.navigateUp() },
                colors = if (showTitle) IconButtonDefaults.iconButtonColors() else IconButtonDefaults.filledIconButtonColors()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null
                )
            }
        },
        modifier = Modifier.background(
            Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer, Color.Transparent))
        ),
        colors = if (showTitle) TopAppBarDefaults.topAppBarColors() else TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent
        )
    )

    SongMenuBottomSheet(
        navController = navController,
        song = selectSong,
        onDismiss = { openBottomSheet = false },
        openBottomSheet = openBottomSheet
    )
}
