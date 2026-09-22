package com.jussicodes.music.ui.screen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.jussicodes.music.R
import com.jussicodes.music.constants.ncmCookieKey
import com.jussicodes.music.constants.pinnedAlbumIdsKey
import com.jussicodes.music.ui.components.LargeImageDialog
import com.jussicodes.music.ui.components.TopBar
import com.jussicodes.music.ui.icons.Favorite
import com.jussicodes.music.ui.icons.History
import com.jussicodes.music.ui.icons.Login
import com.jussicodes.music.ui.icons.Message
import com.jussicodes.music.ui.icons.MoreVert
import com.jussicodes.music.ui.icons.PersonalRadio
import com.jussicodes.music.ui.icons.PlaylistAdd
import com.jussicodes.music.ui.icons.VipFill
import com.jussicodes.music.ui.navigation.AlbumNav
import com.jussicodes.music.ui.navigation.PlaylistNav
import com.jussicodes.music.ui.navigation.Screen
import com.jussicodes.music.ui.navigation.UserFollowNav
import com.jussicodes.music.utils.CoverImageSize
import com.jussicodes.music.utils.AvatarUploadLimiter
import com.jussicodes.music.utils.PlaylistCoverSyncBus
import com.jussicodes.music.utils.dataStore
import com.jussicodes.music.utils.rememberNullablePreference
import com.jussicodes.music.utils.toCoverImageUrl
import com.jussicodes.music.utils.withAvatarCacheBuster
import com.jussicodes.music.utils.withPlaylistCoverCacheBuster
import com.jussicodes.music.viewModel.LibraryScreenViewModel
import com.rcmiku.ncmapi.model.Album
import com.rcmiku.ncmapi.model.Playlist
import com.rcmiku.ncmapi.model.UserInfoBatch
import java.io.File
import kotlinx.coroutines.flow.map
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private const val LIKED_PLAYLIST_NAME_FRAGMENT = "\u559c\u6b22"
private const val AVATAR_UPLOAD_SIZE = 800
private const val PLAYLIST_COVER_UPLOAD_SIZE = 300

@Composable
fun LibraryScreen(
    navController: NavHostController,
    libraryScreenViewModel: LibraryScreenViewModel = hiltViewModel()
) {
    val ncmCookie by rememberNullablePreference(ncmCookieKey)
    val userInfoBatchState by libraryScreenViewModel.userInfo.collectAsState()
    val favoriteSongState by libraryScreenViewModel.favoriteSong.collectAsState()
    val userPlaylists by libraryScreenViewModel.userPlaylists.collectAsState()
    val pinnedAlbums by libraryScreenViewModel.pinnedAlbums.collectAsState()
    val pinnedAlbumsCacheLoaded by libraryScreenViewModel.pinnedAlbumsCacheLoaded.collectAsState()
    val isAvatarUploading by libraryScreenViewModel.isAvatarUploading.collectAsState()
    val avatarCacheVersion by libraryScreenViewModel.avatarCacheVersion.collectAsState()
    val playlistCoverVersions by PlaylistCoverSyncBus.versions.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showAvatarDialog by remember { mutableStateOf(false) }
    var selectedAvatarUri by remember { mutableStateOf<Uri?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var editingPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var editingPlaylistName by remember { mutableStateOf("") }
    var editingPlaylistDescription by remember { mutableStateOf("") }
    var coverEditingPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var selectedCoverUri by remember { mutableStateOf<Uri?>(null) }
    var avatarScale by remember { mutableStateOf(1f) }
    var avatarOffset by remember { mutableStateOf(Offset.Zero) }
    var coverScale by remember { mutableStateOf(1f) }
    var coverOffset by remember { mutableStateOf(Offset.Zero) }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            avatarScale = 1f
            avatarOffset = Offset.Zero
            selectedAvatarUri = it
        }
    }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            coverScale = 1f
            coverOffset = Offset.Zero
            selectedCoverUri = it
        }
    }
    val pinnedAlbumIdsText by remember {
        context.dataStore.data.map { it[pinnedAlbumIdsKey] }
    }.collectAsState(initial = null)
    val pinnedAlbumIds = remember(pinnedAlbumIdsText) {
        pinnedAlbumIdsText?.split(",")?.mapNotNull { it.toLongOrNull() }
    }

    LaunchedEffect(ncmCookie) {
        if (ncmCookie == null) {
            return@LaunchedEffect
        }
        if (ncmCookie?.isNotEmpty() == true) {
            libraryScreenViewModel.fetchUserInfo(cookie = ncmCookie)
        } else {
            libraryScreenViewModel.clear()
        }
    }

    LaunchedEffect(pinnedAlbumIds, pinnedAlbumsCacheLoaded) {
        if (pinnedAlbumsCacheLoaded && pinnedAlbumIds != null) {
            libraryScreenViewModel.fetchPinnedAlbums(pinnedAlbumIds)
        }
    }

    val userId = userInfoBatchState?.account?.profile?.userId ?: 0L
    val favoritePlaylistId = userPlaylists.firstOrNull { playlist ->
        playlist.specialType == 5 ||
            (playlist.creator?.userId == userId && playlist.name.contains(LIKED_PLAYLIST_NAME_FRAGMENT))
    }?.id
    val favoritePlaylist = userPlaylists.firstOrNull { it.id == favoritePlaylistId }
    val normalPlaylists = userPlaylists.filterNot { it.id == favoritePlaylistId }
    val collectedPlaylists = normalPlaylists.filter { it.subscribed || it.creator?.userId != userId }
    val createdPlaylists = normalPlaylists.filterNot { it in collectedPlaylists }

    Scaffold(
        topBar = { TopBar(navController = navController, titleRes = R.string.mine) }
    ) { padding ->
        if (ncmCookie == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(padding)
            )
        } else if (ncmCookie?.isEmpty() == true) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .padding(24.dp)
                        .clickable { navController.navigate(Screen.Login.route) },
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Image(
                            imageVector = Login,
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = stringResource(R.string.click_to_login),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = 12.dp,
                    bottom = 20.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                userInfoBatchState?.let {
                    item {
                        LibraryUserCard(
                            navController = navController,
                            userInfo = it,
                            avatarCacheVersion = avatarCacheVersion,
                            onRoamClick = { navController.navigate(Screen.Roam.route) },
                            onRecentPlayClick = { navController.navigate(Screen.RecentPlay.route) },
                            onMessagesClick = { navController.navigate(Screen.Messages.route) },
                            onAvatarClick = {
                                libraryScreenViewModel.fetchUserInfo(cookie = ncmCookie, force = true)
                                showAvatarDialog = true
                            }
                        )
                    }
                }

                favoritePlaylist?.let { playlist ->
                    item {
                        FavoritePlaylistCard(
                            playlist = playlist,
                            coverVersion = playlistCoverVersions[playlist.id] ?: 0L,
                            onCreatePlaylistClick = { showCreatePlaylistDialog = true },
                            onClick = {
                                navController.navigate(
                                    PlaylistNav(
                                        playlistId = playlist.id,
                                        noCache = true
                                    )
                                )
                            }
                        )
                    }
                }

                if (createdPlaylists.isNotEmpty()) {
                    item {
                        PlaylistGroupCard(
                            title = stringResource(R.string.create_playlist),
                            playlists = createdPlaylists,
                            coverVersions = playlistCoverVersions,
                            showDividers = false,
                            onPlaylistClick = { playlist ->
                                navController.navigate(
                                    PlaylistNav(
                                        playlistId = playlist.id,
                                        limit = playlist.trackCount
                                    )
                                )
                            },
                            menuItems = listOf(
                                PlaylistMenuItem("编辑信息") { playlist ->
                                    editingPlaylist = playlist
                                    editingPlaylistName = playlist.name
                                    editingPlaylistDescription = playlist.description
                                },
                                PlaylistMenuItem("上传封面") { playlist ->
                                    coverEditingPlaylist = playlist
                                    coverPicker.launch("image/*")
                                },
                                PlaylistMenuItem("删除歌单") { playlist ->
                                    libraryScreenViewModel.deletePlaylist(playlist) { success ->
                                        Toast.makeText(
                                            context,
                                            if (success) "歌单已删除" else "删除歌单失败",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            ),
                            reorderEnabled = true,
                            onPlaylistOrderChanged = { playlists ->
                                libraryScreenViewModel.reorderCreatedPlaylists(playlists) { success ->
                                    if (!success) {
                                        Toast.makeText(context, "歌单排序失败", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    }
                }

                if (collectedPlaylists.isNotEmpty()) {
                    item {
                        PlaylistGroupCard(
                            title = stringResource(R.string.collect_playlist),
                            playlists = collectedPlaylists,
                            coverVersions = playlistCoverVersions,
                            showDividers = false,
                            reorderEnabled = true,
                            onPlaylistOrderChanged = { playlists ->
                                libraryScreenViewModel.reorderCollectedPlaylists(playlists)
                            },
                            onPlaylistClick = { playlist ->
                                navController.navigate(
                                    PlaylistNav(
                                        playlistId = playlist.id,
                                        limit = playlist.trackCount
                                    )
                                )
                            },
                            menuItems = listOf(
                                PlaylistMenuItem("取消收藏") { playlist ->
                                    libraryScreenViewModel.uncollectPlaylist(playlist) { success ->
                                        Toast.makeText(
                                            context,
                                            if (success) "已取消收藏" else "取消收藏失败",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )
                        )
                    }
                }

                if (pinnedAlbums.isNotEmpty()) {
                    item {
                        AlbumShowcaseCard(
                            albums = pinnedAlbums,
                            onAlbumClick = { album -> navController.navigate(AlbumNav(albumId = album.id)) }
                        )
                    }
                }

                item { Spacer(modifier = Modifier.navigationBarsPadding()) }
            }
        }
    }

    editingPlaylist?.let { playlist ->
        AlertDialog(
            onDismissRequest = { editingPlaylist = null },
            title = { Text("编辑歌单") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editingPlaylistName,
                        onValueChange = { editingPlaylistName = it },
                        label = { Text("歌单名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editingPlaylistDescription,
                        onValueChange = { editingPlaylistDescription = it },
                        label = { Text("歌单描述") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        libraryScreenViewModel.updatePlaylistInfo(
                            playlist = playlist,
                            name = editingPlaylistName,
                            description = editingPlaylistDescription
                        ) { success ->
                            Toast.makeText(
                                context,
                                if (success) "歌单已更新" else "更新歌单失败",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        editingPlaylist = null
                    },
                    enabled = editingPlaylistName.isNotBlank()
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingPlaylist = null }) {
                    Text("取消")
                }
            }
        )
    }

    selectedCoverUri?.let { uri ->
        AlertDialog(
            onDismissRequest = {
                selectedCoverUri = null
                coverEditingPlaylist = null
            },
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
                                .fillMaxSize()
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
                        val playlist = coverEditingPlaylist
                        val file = uri.copyToSquareImageCache(
                            context = context,
                            outputSize = PLAYLIST_COVER_UPLOAD_SIZE,
                            scale = coverScale,
                            offset = coverOffset,
                            prefix = "playlist_cover"
                        )
                        if (playlist != null && file != null) {
                            selectedCoverUri = null
                            coverEditingPlaylist = null
                            libraryScreenViewModel.uploadPlaylistCover(playlist, file) { success ->
                                Toast.makeText(
                                    context,
                                    if (success) "封面已更新" else "上传封面失败",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } else {
                            Toast.makeText(context, "封面处理失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("上传")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    selectedCoverUri = null
                    coverEditingPlaylist = null
                }) {
                    Text("取消")
                }
            }
        )
    }

    val avatarUrl = userInfoBatchState?.account?.profile?.avatarUrl
    if (showAvatarDialog && !avatarUrl.isNullOrBlank()) {
        LargeImageDialog(
            imageUrl = avatarUrl.toCoverImageUrl(CoverImageSize.LARGE),
            onDismiss = { showAvatarDialog = false },
            showSaveAction = true
        ) {
            TextButton(
                onClick = {
                    val remaining = AvatarUploadLimiter.remaining(context)
                    Toast.makeText(
                        context,
                        "头像每周最多上传 5 次，本周还可上传 $remaining 次",
                        Toast.LENGTH_LONG
                    ).show()
                    if (remaining > 0) avatarPicker.launch("image/*")
                },
                enabled = !isAvatarUploading
            ) {
                Text(if (isAvatarUploading) "上传中" else "编辑")
            }
        }
    }

    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("新建歌单") },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("歌单名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val playlistName = newPlaylistName
                        libraryScreenViewModel.createPlaylist(playlistName) { success ->
                            Toast.makeText(
                                context,
                                if (success) "歌单已创建" else "新建歌单失败",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        newPlaylistName = ""
                        showCreatePlaylistDialog = false
                    },
                    enabled = newPlaylistName.isNotBlank()
                ) {
                    Text("创建")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    selectedAvatarUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { selectedAvatarUri = null },
            title = { Text("编辑头像") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(260.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .pointerInput(uri) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    avatarScale = (avatarScale * zoom).coerceIn(1f, 6f)
                                    avatarOffset += pan
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    translationX = avatarOffset.x
                                    translationY = avatarOffset.y
                                    scaleX = avatarScale
                                    scaleY = avatarScale
                                }
                        )
                    }
                    Text(
                        "双指缩放，拖动调整区域 · ${AVATAR_UPLOAD_SIZE} x ${AVATAR_UPLOAD_SIZE}",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        uri.copyToSquareImageCache(
                            context = context,
                            outputSize = AVATAR_UPLOAD_SIZE,
                            scale = avatarScale,
                            offset = avatarOffset,
                            prefix = "avatar"
                        )?.let { file ->
                            selectedAvatarUri = null
                            libraryScreenViewModel.uploadAvatar(file) { success, message ->
                                Toast.makeText(
                                    context,
                                    if (success) "头像上传成功" else "头像上传失败：${message.orEmpty()}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        } ?: Toast.makeText(context, "头像处理失败", Toast.LENGTH_SHORT).show()
                    },
                    enabled = !isAvatarUploading
                ) {
                    Text("上传")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedAvatarUri = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun LibraryUserCard(
    navController: NavHostController,
    userInfo: UserInfoBatch,
    avatarCacheVersion: Long,
    onRoamClick: () -> Unit,
    onRecentPlayClick: () -> Unit,
    onMessagesClick: () -> Unit,
    onAvatarClick: () -> Unit
) {
    val profile = userInfo.account.profile
    val secondaryText = profile.signature.takeIf { it.isNotBlank() }
    val unread by com.jussicodes.music.data.MsgSessionCache.unread.collectAsState()

    Box(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 38.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.align(Alignment.CenterStart).padding(start = 14.dp)) {
                    FilledTonalIconButton(
                        onClick = onMessagesClick
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Message,
                            contentDescription = stringResource(R.string.messages)
                        )
                    }
                    // 全局未读红点（/msg/private 顶层 newMsgCount，来自 MsgSessionCache）
                    if (unread > 0) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error)
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.material3.Text(
                                text = if (unread > 99) "99+" else unread.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onError,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalIconButton(onClick = onRecentPlayClick) {
                        androidx.compose.material3.Icon(
                            imageVector = History,
                            contentDescription = stringResource(R.string.recent_play)
                        )
                    }
                    FilledTonalIconButton(onClick = onRoamClick) {
                        androidx.compose.material3.Icon(
                            imageVector = PersonalRadio,
                            contentDescription = stringResource(R.string.roam)
                        )
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp, bottom = 16.dp, start = 16.dp, end = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = profile.nickname,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextButton(
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            onClick = {
                                navController.navigate(
                                    UserFollowNav(
                                        userId = profile.userId,
                                        type = com.jussicodes.music.viewModel.UserFollowType.FOLLOWS.name,
                                        showArtistFollows = true
                                    )
                                )
                            },
                        ) { Text(text = "关注") }
                        TextButton(
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            onClick = {
                                navController.navigate(
                                    UserFollowNav(
                                        userId = profile.userId,
                                        type = com.jussicodes.music.viewModel.UserFollowType.FOLLOWEDS.name
                                    )
                                )
                            },
                        ) { Text(text = "粉丝") }
                    }
                    secondaryText?.let {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .clickable(enabled = profile.avatarUrl.isNotBlank(), onClick = onAvatarClick),
            contentAlignment = Alignment.BottomEnd
        ) {
            AsyncImage(
                model = profile.avatarUrl.withAvatarCacheBuster(avatarCacheVersion),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.clip(CircleShape).border(3.dp, MaterialTheme.colorScheme.surface, CircleShape).size(76.dp)
            )
            if (profile.vipType != 0) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                        .padding(6.dp)
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = VipFill,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

private fun Uri.copyToSquareImageCache(
    context: Context,
    outputSize: Int,
    scale: Float,
    offset: Offset,
    prefix: String
): File? {
    val previewSize = 260f
    val file = File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}.jpg")
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

@Composable
private fun AlbumShowcaseCard(
    albums: List<Album>,
    onAlbumClick: (Album) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            albums.chunked(3).forEach { rowAlbums ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    rowAlbums.forEach { album ->
                        AsyncImage(
                            model = album.picUrl.toCoverImageUrl(CoverImageSize.DETAIL),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            filterQuality = FilterQuality.High,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .aspectRatio(1f)
                                .clickable { onAlbumClick(album) }
                        )
                    }
                    repeat(3 - rowAlbums.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoritePlaylistCard(
    playlist: Playlist,
    coverVersion: Long,
    onCreatePlaylistClick: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Box(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(14.dp)) {
            RowCardContent(
                title = playlist.name,
                subtitle = stringResource(R.string.track_count, playlist.trackCount),
                coverUrl = playlist.picUrl,
                coverVersion = coverVersion,
                trailingContent = {
                    IconButton(onClick = onCreatePlaylistClick) {
                        androidx.compose.material3.Icon(
                            imageVector = PlaylistAdd,
                            contentDescription = "新建歌单"
                        )
                    }
                }
            )
            Box(
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 38.dp, top = 38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)).padding(5.dp)
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
private fun PlaylistGroupCard(
    title: String,
    playlists: List<Playlist>,
    coverVersions: Map<Long, Long>,
    showDividers: Boolean = true,
    onPlaylistClick: (Playlist) -> Unit,
    menuItems: List<PlaylistMenuItem>,
    reorderEnabled: Boolean = false,
    onPlaylistOrderChanged: (List<Playlist>) -> Unit = {}
) {
    val view = LocalView.current
    val listState = rememberLazyListState()
    var orderedPlaylists by remember(playlists) { mutableStateOf(playlists) }
    var dragChanged by remember { mutableStateOf(false) }
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        orderedPlaylists = orderedPlaylists.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        dragChanged = true
        ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.SEGMENT_FREQUENT_TICK)
    }

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging && dragChanged) {
            onPlaylistOrderChanged(orderedPlaylists)
            dragChanged = false
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
            LazyColumn(
                state = listState,
                userScrollEnabled = false,
                modifier = Modifier.heightIn(max = (orderedPlaylists.size * 66).dp)
            ) {
                itemsIndexed(orderedPlaylists, key = { _, playlist -> playlist.id }) { index, playlist ->
                    ReorderableItem(reorderableState, key = playlist.id, enabled = reorderEnabled) {
                        LibraryPlaylistRow(
                            playlist = playlist,
                            coverVersion = coverVersions[playlist.id] ?: 0L,
                            menuItems = menuItems,
                            reorderEnabled = reorderEnabled,
                            dragModifier = Modifier.longPressDraggableHandle(
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
                            ),
                            onClick = { onPlaylistClick(playlist) }
                        )
                    }
                    if (index != orderedPlaylists.lastIndex) {
                        if (showDividers) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 3.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        } else {
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryPlaylistRow(
    playlist: Playlist,
    coverVersion: Long,
    menuItems: List<PlaylistMenuItem>,
    reorderEnabled: Boolean,
    dragModifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val creatorName = playlist.creator?.nickname.orEmpty()
    val subtitle = buildString {
        append(stringResource(R.string.track_count, playlist.trackCount))
        if (creatorName.isNotEmpty()) {
            append(" by ")
            append(creatorName)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .then(if (reorderEnabled) dragModifier else Modifier)
            .clickable(onClick = onClick)
            .padding(vertical = 1.dp)
    ) {
        RowCardContent(
            title = playlist.name,
            subtitle = subtitle,
            coverUrl = playlist.picUrl,
            coverVersion = coverVersion,
            trailingContent = {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        androidx.compose.material3.Icon(
                            imageVector = MoreVert,
                            contentDescription = stringResource(R.string.more)
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        menuItems.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item.text) },
                                onClick = {
                                    menuExpanded = false
                                    item.onClick(playlist)
                                }
                            )
                        }
                    }
                }
            }
        )
    }
}

private data class PlaylistMenuItem(
    val text: String,
    val onClick: (Playlist) -> Unit
)

@Composable
private fun RowCardContent(
    title: String,
    subtitle: String,
    coverUrl: String,
    coverVersion: Long = 0L,
    trailingContent: (@Composable () -> Unit)? = null
) {
    androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = coverUrl.toCoverImageUrl(CoverImageSize.LIST)
                .withPlaylistCoverCacheBuster(coverVersion),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.clip(MaterialTheme.shapes.medium).size(52.dp)
        )
        Column(modifier = Modifier.padding(start = 10.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailingContent?.invoke()
    }
}

