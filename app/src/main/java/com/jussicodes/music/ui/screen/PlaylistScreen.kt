package com.jussicodes.music.ui.screen

import android.net.Uri
import android.icu.text.Transliterator
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaMetadata
import androidx.navigation.NavHostController
import com.jussicodes.music.LocalPlayerController
import com.jussicodes.music.LocalPlayerState
import com.jussicodes.music.R
import com.jussicodes.music.data.favoriteSongIdsDatastore
import com.jussicodes.music.extensions.playMediaAt
import com.jussicodes.music.extensions.playMediaAtId
import com.jussicodes.music.extensions.setPlaylist
import com.jussicodes.music.ui.components.PlayerComments
import com.jussicodes.music.ui.components.PlaylistThumbnailImage
import com.jussicodes.music.ui.components.SongListItem
import com.jussicodes.music.ui.components.SongMenuBottomSheet
import com.jussicodes.music.ui.icons.LibraryAdd
import com.jussicodes.music.ui.icons.LibraryAddCheck
import com.jussicodes.music.ui.icons.ModeComment
import com.jussicodes.music.ui.icons.Search
import com.jussicodes.music.utils.formatPlayCount
import com.jussicodes.music.utils.formatTimestamp
import com.jussicodes.music.viewModel.PlaylistScreenViewModel
import com.rcmiku.ncmapi.model.Song
import java.util.Locale
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun PlaylistScreen(
    navController: NavHostController,
    playlistScreenViewModel: PlaylistScreenViewModel = hiltViewModel(),
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope
) {
    val playlistDetailState by playlistScreenViewModel.playlistDetail.collectAsState()
    val listState = rememberLazyListState()
    val showPlaylistTitle by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    var playlistTitle by remember { mutableStateOf("") }
    val mediaController = LocalPlayerController.current.controller
    val playerState = LocalPlayerState.current
    val isPlaying = playerState?.isPlaying == true
    val currentMediaId = playerState?.currentMediaItem?.mediaId?.toLongOrNull()
    val playlistInfoState by playlistScreenViewModel.playlistInfo.collectAsState()
    var openBottomSheet by rememberSaveable { mutableStateOf(false) }
    var openPlaylistComments by rememberSaveable { mutableStateOf(false) }
    var selectSong by remember { mutableStateOf<Song?>(null) }
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchFocusRequester = remember { FocusRequester() }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val songIds by context.favoriteSongIdsDatastore.data.map { it.songIdsList }
        .collectAsState(emptyList())
    val subscribed = playlistInfoState?.subscribed ?: false

    LaunchedEffect(searchActive) {
        if (searchActive) {
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    with(sharedTransitionScope) {
        Scaffold(
            topBar = {
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
                                if (showPlaylistTitle) playlistTitle else stringResource(R.string.playlist),
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
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null
                            )
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
                    },
                )
            }
        ) { padding ->
            playlistDetailState?.let {
                val tracks = it.playlist.getAllTracks()
                val searchEntries = remember(tracks) {
                    tracks.map(::PlaylistSongSearchEntry)
                }
                val visibleTracks = remember(searchEntries, searchQuery) {
                    searchEntries.filterBy(searchQuery)
                }
                LazyColumn(
                    contentPadding = padding, state = listState,
                ) {
                    playlistTitle = it.playlist.name
                    if (!searchActive || searchQuery.isBlank()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                PlaylistThumbnailImage(
                                    url = it.playlist.coverImgUrl,
                                    modifier = Modifier.sharedElement(
                                        sharedTransitionScope.rememberSharedContentState(key = it.playlist.id),
                                        animatedVisibilityScope = animatedContentScope,
                                        placeHolderSize = { contentSize: IntSize, animatedSize: IntSize ->
                                            IntSize(contentSize.width, animatedSize.height)
                                        },
                                        boundsTransform = AlbumArtBoundsTransform,
                                    )
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = it.playlist.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center,
                                    overflow = TextOverflow.Ellipsis,
                                    maxLines = 2,
                                    modifier = Modifier.padding(horizontal = 10.dp)
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = stringResource(
                                        R.string.total_play_count,
                                        formatPlayCount(it.playlist.playCount)
                                    ) + " " + formatTimestamp(
                                        it.playlist.trackUpdateTime
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(6.dp))
                                it.playlist.description?.let { description ->
                                    Text(
                                        text = description,
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.labelMedium,
                                        overflow = TextOverflow.Ellipsis,
                                        maxLines = 4,
                                        modifier = Modifier.padding(horizontal = 20.dp)
                                    )
                                }

                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            playlistScreenViewModel.playlistSub(
                                                shouldSubscribe = !subscribed
                                            )
                                        },
                                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = if (subscribed) LibraryAddCheck else LibraryAdd,
                                            contentDescription = null,
                                            modifier = Modifier.size(ButtonDefaults.IconSize)
                                        )
                                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                        Text(
                                            text = if (subscribed) stringResource(
                                                R.string.library_add_check
                                            ) else stringResource(R.string.library_add)
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            mediaController?.setPlaylist(
                                                tracks,
                                                sourceId = it.playlist.id
                                            )
                                            mediaController?.playMediaAt()
                                        },
                                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            Icons.Outlined.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(ButtonDefaults.IconSize)
                                        )
                                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                        Text(
                                            text = stringResource(R.string.play)
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = { openPlaylistComments = true },
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

                    itemsIndexed(visibleTracks, key = { _, song -> song.id }) { index, song ->
                        SongListItem(
                            song = song,
                            isPlaying = isPlaying,
                            showLikedIcon = song.id in songIds,
                            isActive = currentMediaId == song.id,
                            songIndex = index + 1,
                            modifier = Modifier.clickable {
                                mediaController?.setPlaylist(
                                    tracks,
                                    sourceId = it.playlist.id
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
    }

    SongMenuBottomSheet(
        navController = navController,
        song = selectSong,
        onDismiss = { openBottomSheet = false },
        openBottomSheet = openBottomSheet
    )

    if (openPlaylistComments) {
        playlistDetailState?.playlist?.let { playlist ->
            val playlistMetadata = remember(playlist.id, playlist.coverImgUrl) {
                MediaMetadata.Builder()
                    .setTitle(playlist.name)
                    .setArtist(playlist.creator?.nickname.orEmpty())
                    .setArtworkUri(Uri.parse(playlist.coverImgUrl))
                    .build()
            }
            PlayerComments(
                mediaId = playlist.id,
                mediaMetadata = playlistMetadata,
                commentType = 2,
                onBackPressed = { openPlaylistComments = false }
            )
        }
    }
}

private class PlaylistSongSearchEntry(song: Song) {
    val song = song
    private val title = song.name.toSearchText()
    private val artists = song.ar.joinToString(" ") { it.name }.toSearchText()
    private val initials = buildString {
        append(song.name.toInitials())
        append(' ')
        song.ar.forEach { artist ->
            append(artist.name.toInitials())
            append(' ')
        }
    }

    fun matches(normalizedQuery: String): Boolean {
        if (normalizedQuery.isBlank()) return true
        return title.contains(normalizedQuery) ||
            artists.contains(normalizedQuery) ||
            initials.contains(normalizedQuery)
    }
}

private fun List<PlaylistSongSearchEntry>.filterBy(query: String): List<Song> {
    val normalizedQuery = query.toSearchText()
    if (normalizedQuery.isBlank()) return map { it.song }
    return asSequence()
        .filter { it.matches(normalizedQuery) }
        .map { it.song }
        .toList()
}

private fun String.toSearchText(): String =
    lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

private fun String.toInitials(): String =
    PinyinInitialTransliterator.transliterate(this)
        .split(' ', '-', '_', '/', '\\', '.', ',', '(', ')', '[', ']', '·')
        .mapNotNull { word -> word.firstOrNull { it.isLetterOrDigit() } }
        .joinToString("")
        .lowercase(Locale.ROOT)

private object PinyinInitialTransliterator {
    private val transliterator by lazy {
        Transliterator.getInstance("Han-Latin/Names; Latin-ASCII")
    }

    fun transliterate(value: String): String = transliterator.transliterate(value)
}
