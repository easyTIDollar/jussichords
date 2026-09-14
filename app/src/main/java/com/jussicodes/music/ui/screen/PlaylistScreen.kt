package com.jussicodes.music.ui.screen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.icu.text.Transliterator
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.ContentScale
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
import com.jussicodes.music.ui.components.LargeImageDialog
import com.jussicodes.music.ui.components.PlaylistThumbnailImage
import com.jussicodes.music.ui.components.SongListItem
import com.jussicodes.music.ui.components.SongMenuBottomSheet
import com.jussicodes.music.ui.icons.LibraryAdd
import com.jussicodes.music.ui.icons.LibraryAddCheck
import com.jussicodes.music.ui.icons.ModeComment
import com.jussicodes.music.ui.icons.Search
import com.jussicodes.music.constants.userIdKye
import com.jussicodes.music.utils.formatPlayCount
import com.jussicodes.music.utils.formatTimestamp
import com.jussicodes.music.viewModel.PlaylistScreenViewModel
import com.jussicodes.music.utils.PlaylistCoverSyncBus
import com.jussicodes.music.utils.withPlaylistCoverCacheBuster
import com.rcmiku.ncmapi.model.Song
import coil3.compose.AsyncImage
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import com.jussicodes.music.utils.rememberPreference
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private const val PLAYLIST_COVER_UPLOAD_SIZE = 300

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
    var playlistMenuExpanded by remember { mutableStateOf(false) }
    var showEditPlaylistDialog by remember { mutableStateOf(false) }
    var editPlaylistName by remember { mutableStateOf("") }
    var editPlaylistDescription by remember { mutableStateOf("") }
    var selectedCoverUri by remember { mutableStateOf<Uri?>(null) }
    var previewCoverUrl by remember { mutableStateOf<String?>(null) }
    var coverScale by remember { mutableStateOf(1f) }
    var coverOffset by remember { mutableStateOf(Offset.Zero) }
    var selectSong by remember { mutableStateOf<Song?>(null) }
    val context = LocalContext.current
    val view = LocalView.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchFocusRequester = remember { FocusRequester() }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val songIds by context.favoriteSongIdsDatastore.data.map { it.songIdsList }
        .collectAsState(emptyList())
    val playlistCoverVersions by PlaylistCoverSyncBus.versions.collectAsState()
    val userId by rememberPreference(userIdKye, 0)
    val subscribed = playlistInfoState?.subscribed ?: false
    var reorderedTracks by remember { mutableStateOf<List<Song>?>(null) }
    var songOrderChanged by remember { mutableStateOf(false) }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            coverScale = 1f
            coverOffset = Offset.Zero
            selectedCoverUri = it
        }
    }

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
                        Box {
                            IconButton(onClick = { playlistMenuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.more)
                                )
                            }
                            DropdownMenu(
                                expanded = playlistMenuExpanded,
                                onDismissRequest = { playlistMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("编辑信息") },
                                    onClick = {
                                        val playlist = playlistDetailState?.playlist
                                        playlistMenuExpanded = false
                                        if (playlist != null) {
                                            editPlaylistName = playlist.name
                                            editPlaylistDescription = playlist.description
                                            showEditPlaylistDialog = true
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("上传封面") },
                                    onClick = {
                                        playlistMenuExpanded = false
                                        coverPicker.launch("image/*")
                                    }
                                )
                            }
                        }
                    },
                )
            }
        ) { padding ->
            if (playlistDetailState == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            playlistDetailState?.let {
                val serverTracks = it.playlist.getAllTracks()
                LaunchedEffect(it.playlist.id, serverTracks.map { song -> song.id }) {
                    if (!songOrderChanged) {
                        reorderedTracks = serverTracks
                    }
                }
                val tracks = reorderedTracks ?: serverTracks
                val visibleTracks = rememberFilteredSongs(tracks, searchQuery)
                val canReorderSongs = !searchActive &&
                    searchQuery.isBlank() &&
                    it.playlist.creator?.userId == userId
                val currentPlaylistId = it.playlist.id
                val headerOffset = if (!searchActive || searchQuery.isBlank()) 1 else 0
                val reorderableLazyListState =
                    rememberReorderableLazyListState(listState) { from, to ->
                        val fromIndex = from.index - headerOffset
                        val toIndex = to.index - headerOffset
                        if (fromIndex in tracks.indices && toIndex in tracks.indices) {
                            reorderedTracks = tracks.toMutableList().apply {
                                add(toIndex, removeAt(fromIndex))
                            }
                            songOrderChanged = true
                            ViewCompat.performHapticFeedback(
                                view,
                                HapticFeedbackConstantsCompat.SEGMENT_FREQUENT_TICK
                            )
                        }
                    }

                LaunchedEffect(reorderableLazyListState.isAnyItemDragging) {
                    if (!reorderableLazyListState.isAnyItemDragging && songOrderChanged) {
                        reorderedTracks?.let { songs ->
                            playlistScreenViewModel.reorderSongs(songs) { success ->
                                if (!success) {
                                    Toast.makeText(context, "歌曲排序失败", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        songOrderChanged = false
                    }
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
                                    url = it.playlist.coverImgUrl.withPlaylistCoverCacheBuster(
                                        playlistCoverVersions[it.playlist.id] ?: 0L
                                    ),
                                    modifier = Modifier.sharedElement(
                                        sharedTransitionScope.rememberSharedContentState(key = it.playlist.id),
                                        animatedVisibilityScope = animatedContentScope,
                                        placeHolderSize = { contentSize: IntSize, animatedSize: IntSize ->
                                            IntSize(contentSize.width, animatedSize.height)
                                        },
                                        boundsTransform = AlbumArtBoundsTransform,
                                    ).clickable {
                                        previewCoverUrl = it.playlist.coverImgUrl.withPlaylistCoverCacheBuster(
                                            playlistCoverVersions[it.playlist.id] ?: 0L
                                        )
                                    }
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = it.playlist.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
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
                        ReorderableItem(
                            reorderableLazyListState,
                            key = song.id,
                            enabled = canReorderSongs
                        ) {
                            SongListItem(
                                song = song,
                                isPlaying = isPlaying,
                                showLikedIcon = song.id in songIds,
                                isActive = currentMediaId == song.id,
                                songIndex = index + 1,
                                modifier = Modifier
                                    .then(
                                        if (canReorderSongs) {
                                            Modifier.longPressDraggableHandle(
                                                onDragStarted = {
                                                    ViewCompat.performHapticFeedback(
                                                        view,
                                                        HapticFeedbackConstantsCompat.GESTURE_START
                                                    )
                                                },
                                                onDragStopped = {
                                                    ViewCompat.performHapticFeedback(
                                                        view,
                                                        HapticFeedbackConstantsCompat.GESTURE_END
                                                    )
                                                }
                                            )
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clickable {
                                        mediaController?.setPlaylist(
                                            tracks,
                                            sourceId = currentPlaylistId
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
    }

    SongMenuBottomSheet(
        navController = navController,
        song = selectSong,
        onDismiss = { openBottomSheet = false },
        openBottomSheet = openBottomSheet
    )

    previewCoverUrl?.let { imageUrl ->
        LargeImageDialog(
            imageUrl = imageUrl,
            onDismiss = { previewCoverUrl = null },
            showSaveAction = true
        )
    }

    if (showEditPlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showEditPlaylistDialog = false },
            title = { Text("编辑歌单") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editPlaylistName,
                        onValueChange = { editPlaylistName = it },
                        label = { Text("歌单名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editPlaylistDescription,
                        onValueChange = { editPlaylistDescription = it },
                        label = { Text("歌单描述") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        playlistScreenViewModel.updatePlaylistInfo(
                            name = editPlaylistName,
                            description = editPlaylistDescription
                        ) { success ->
                            Toast.makeText(
                                context,
                                if (success) "歌单已更新" else "更新歌单失败",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        showEditPlaylistDialog = false
                    },
                    enabled = editPlaylistName.isNotBlank()
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditPlaylistDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    selectedCoverUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { selectedCoverUri = null },
            title = { Text("编辑封面") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(260.dp)
                            .clip(MaterialTheme.shapes.large)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .pointerInput(uri) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    coverScale = (coverScale * zoom).coerceIn(1f, 6f)
                                    coverOffset += pan
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .size(260.dp)
                                .graphicsLayer {
                                    translationX = coverOffset.x
                                    translationY = coverOffset.y
                                    scaleX = coverScale
                                    scaleY = coverScale
                                }
                        )
                    }
                    Text(
                        "双指缩放，拖动调整区域 · ${PLAYLIST_COVER_UPLOAD_SIZE} x ${PLAYLIST_COVER_UPLOAD_SIZE}",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        uri.copyToPlaylistCoverCache(
                            context = context,
                            scale = coverScale,
                            offset = coverOffset
                        )?.let { file ->
                            selectedCoverUri = null
                            playlistScreenViewModel.uploadPlaylistCover(file) { success ->
                                Toast.makeText(
                                    context,
                                    if (success) "封面已更新" else "上传封面失败",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } ?: Toast.makeText(context, "封面处理失败", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("上传")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedCoverUri = null }) {
                    Text("取消")
                }
            }
        )
    }

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

private fun String.toSearchText(): String =
    lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

private fun String.toInitials(): String =
    PinyinInitialTransliterator.transliterate(this)
        .split(' ', '-', '_', '/', '\\', '.', ',', '(', ')', '[', ']', '·')
        .mapNotNull { word -> word.firstOrNull { it.isLetterOrDigit() } }
        .joinToString("")
        .lowercase(Locale.ROOT)

/**
 * Filters [tracks] for in-list search without blocking the UI thread.
 * The pinyin search index (ICU transliteration per song and artist) is built
 * on [Dispatchers.Default]. When no query is active the full list is returned
 * as-is, so merely opening a large playlist does no per-song work at all;
 * filtering only runs against the pre-computed index once a query is entered.
 */
@Composable
private fun rememberFilteredSongs(tracks: List<Song>, query: String): List<Song> {
    val normalizedQuery = remember(query) { query.toSearchText() }
    var index by remember(tracks) { mutableStateOf<PlaylistSearchIndex?>(null) }
    LaunchedEffect(tracks) {
        index = withContext(Dispatchers.Default) { tracks.toSearchIndex() }
    }
    return when {
        normalizedQuery.isBlank() -> tracks
        index == null -> tracks
        else -> index!!.filter(normalizedQuery)
    }
}

private fun List<Song>.toSearchIndex(): PlaylistSearchIndex =
    PlaylistSearchIndex(map(::PlaylistSongSearchEntry))

private class PlaylistSearchIndex(private val entries: List<PlaylistSongSearchEntry>) {
    fun filter(normalizedQuery: String): List<Song> =
        entries.asSequence()
            .filter { it.matches(normalizedQuery) }
            .map { it.song }
            .toList()
}

private fun Uri.copyToPlaylistCoverCache(context: Context, scale: Float, offset: Offset): File? {
    val outputSize = PLAYLIST_COVER_UPLOAD_SIZE
    val previewSize = 260f
    val file = File(context.cacheDir, "playlist_cover_${System.currentTimeMillis()}.jpg")
    return runCatching {
        context.contentResolver.openInputStream(this)?.use { input ->
            val bitmap = BitmapFactory.decodeStream(input) ?: return null
            val outputBitmap = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.RGB_565)
            val canvas = Canvas(outputBitmap)
            canvas.drawColor(Color.WHITE)
            val baseScale = maxOf(
                outputSize.toFloat() / bitmap.width.toFloat(),
                outputSize.toFloat() / bitmap.height.toFloat()
            )
            val finalScale = baseScale * scale
            val drawWidth = bitmap.width * finalScale
            val drawHeight = bitmap.height * finalScale
            val previewToOutputScale = outputSize / previewSize
            val left = (outputSize - drawWidth) / 2f + offset.x * previewToOutputScale
            val top = (outputSize - drawHeight) / 2f + offset.y * previewToOutputScale
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(
                bitmap,
                null,
                RectF(left, top, left + drawWidth, top + drawHeight),
                paint
            )
            file.outputStream().use { stream ->
                outputBitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
            }
            outputBitmap.recycle()
            bitmap.recycle()
        } ?: return null
        file
    }.getOrNull()
}

private object PinyinInitialTransliterator {
    private val transliterator by lazy {
        Transliterator.getInstance("Han-Latin/Names; Latin-ASCII")
    }

    fun transliterate(value: String): String = transliterator.transliterate(value)
}
