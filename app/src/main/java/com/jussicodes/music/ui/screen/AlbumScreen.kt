package com.jussicodes.music.ui.screen

import android.net.Uri
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaMetadata
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import androidx.datastore.preferences.core.edit
import com.jussicodes.music.LocalPlayerController
import com.jussicodes.music.LocalPlayerState
import com.jussicodes.music.R
import com.jussicodes.music.constants.ThumbnailCornerRadius
import com.jussicodes.music.constants.pinnedAlbumIdsKey
import com.jussicodes.music.constants.pinnedAlbumsCacheKey
import com.jussicodes.music.data.favoriteSongIdsDatastore
import com.jussicodes.music.extensions.playMediaAt
import com.jussicodes.music.extensions.playMediaAtId
import com.jussicodes.music.extensions.setPlaylist
import com.jussicodes.music.ui.components.PlayerComments
import com.jussicodes.music.ui.components.SongListItem
import com.jussicodes.music.ui.components.SongMenuBottomSheet
import com.jussicodes.music.ui.icons.LibraryAdd
import com.jussicodes.music.ui.icons.LibraryAddCheck
import com.jussicodes.music.ui.icons.ModeComment
import com.jussicodes.music.ui.icons.PushPin
import com.jussicodes.music.ui.icons.PushPinFill
import com.jussicodes.music.ui.navigation.ArtistNav
import com.jussicodes.music.utils.CoverImageSize
import com.jussicodes.music.utils.dataStore
import com.jussicodes.music.utils.formatTimestamp
import com.jussicodes.music.utils.toCoverImageUrl
import com.jussicodes.music.viewModel.AlbumScreenViewModel
import com.rcmiku.ncmapi.model.Album
import com.rcmiku.ncmapi.model.Song
import com.rcmiku.ncmapi.utils.json
import kotlinx.serialization.encodeToString
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
    val albumInfoState by albumScreenViewModel.albumInfo.collectAsState()
    var openBottomSheet by rememberSaveable { mutableStateOf(false) }
    var openAlbumComments by rememberSaveable { mutableStateOf(false) }
    var selectSong by remember { mutableStateOf<Song?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val pinnedAlbumIdsText by context.dataStore.data.map { it[pinnedAlbumIdsKey].orEmpty() }
        .collectAsState(initial = "")
    val pinnedAlbumIds = remember(pinnedAlbumIdsText) {
        pinnedAlbumIdsText.split(",").mapNotNull { it.toLongOrNull() }
    }
    val songIds by context.favoriteSongIdsDatastore.data.map { it.songIdsList }
        .collectAsState(emptyList())
    val currentAlbum = albumDetailState?.getOrNull()?.album

    fun togglePinnedAlbum(album: Album) {
        coroutineScope.launch {
            context.dataStore.edit { prefs ->
                val currentIds = prefs[pinnedAlbumIdsKey]
                    .orEmpty()
                    .split(",")
                    .mapNotNull { it.toLongOrNull() }
                    .toMutableList()
                if (album.id in currentIds) {
                    currentIds.remove(album.id)
                } else {
                    currentIds.add(0, album.id)
                }
                val pinnedIds = currentIds.distinct()
                val currentAlbums = runCatching {
                    json.decodeFromString<List<Album>>(prefs[pinnedAlbumsCacheKey].orEmpty())
                }.getOrDefault(emptyList())
                val updatedAlbums = if (album.id in currentAlbums.map { it.id }) {
                    currentAlbums.filterNot { it.id == album.id }
                } else {
                    listOf(album) + currentAlbums
                }.filter { it.id in pinnedIds }
                prefs[pinnedAlbumIdsKey] = pinnedIds.joinToString(",")
                prefs[pinnedAlbumsCacheKey] = json.encodeToString(updatedAlbums)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (showAlbumTitle) playlistTitle else stringResource(R.string.album),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    currentAlbum?.let { album ->
                        val isPinned = album.id in pinnedAlbumIds
                        if (isPinned) {
                            FilledTonalIconButton(onClick = { togglePinnedAlbum(album) }) {
                                Icon(
                                    imageVector = PushPinFill,
                                    contentDescription = "取消固定",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            IconButton(onClick = { togglePinnedAlbum(album) }) {
                                Icon(
                                    imageVector = PushPin,
                                    contentDescription = "固定到主页"
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(contentPadding = padding, state = listState) {
            albumDetailState?.onSuccess { detail ->
                playlistTitle = detail.album.name
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
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = detail.album.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 2,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        )
                        Text(
                            text = detail.album.artist.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            textAlign = TextAlign.Center,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 2,
                            modifier = Modifier
                                .clickable { navController.navigate(ArtistNav(artistId = detail.album.artist.id)) }
                                .padding(horizontal = 10.dp)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = formatTimestamp(detail.album.publishTime),
                            style = MaterialTheme.typography.labelMedium,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = {
                                    albumInfoState?.isSub?.let { isSub -> albumScreenViewModel.albumSub(isSub = isSub) }
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
                                    mediaController?.setPlaylist(detail.songs, sourceId = detail.album.id, sourceName = "album")
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

                itemsIndexed(detail.songs) { index, song ->
                    SongListItem(
                        isPlaying = isPlaying,
                        isActive = currentMediaId == song.id,
                        showLikedIcon = song.id in songIds,
                        song = song,
                        albumIndex = index + 1,
                        modifier = Modifier.clickable {
                            mediaController?.setPlaylist(detail.songs, sourceId = detail.album.id, sourceName = "album")
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
            }
        }
    }

    SongMenuBottomSheet(
        navController = navController,
        song = selectSong,
        onDismiss = { openBottomSheet = false },
        openBottomSheet = openBottomSheet
    )

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
                onBackPressed = { openAlbumComments = false }
            )
        }
    }
}
