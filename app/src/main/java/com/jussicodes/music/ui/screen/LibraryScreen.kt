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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.jussicodes.music.R
import com.jussicodes.music.LocalPlayerState
import com.jussicodes.music.constants.MiniPlayerHeight
import com.jussicodes.music.data.HomeBgTextStore
import com.jussicodes.music.data.HomeTransparentCardStore
import com.jussicodes.music.data.HomeUserInfoStore
import com.jussicodes.music.data.NcmCookieStore
import com.jussicodes.music.data.PinnedAlbumStore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.jussicodes.music.ui.components.transparentCardColor
import com.jussicodes.music.ui.components.userCardGlassColor
import com.jussicodes.music.ui.components.JussiDropdownMenu
import com.jussicodes.music.ui.components.JussiMenuItem
import com.jussicodes.music.ui.components.LargeImageDialog
import com.jussicodes.music.ui.components.PinnedAlbumPickDialog
import com.jussicodes.music.ui.components.PinnedAlbumsCard
import com.jussicodes.music.ui.components.TopBar
import com.jussicodes.music.ui.components.resolveHomeBgTextColor
import com.jussicodes.music.ui.icons.Delete
import com.jussicodes.music.ui.icons.Favorite
import com.jussicodes.music.ui.icons.History
import com.jussicodes.music.ui.icons.Leaderboard
import com.jussicodes.music.ui.icons.Login
import com.jussicodes.music.ui.icons.Message
import com.jussicodes.music.ui.icons.MoreVert
import com.jussicodes.music.ui.icons.PersonalRadio
import com.jussicodes.music.ui.icons.Pencil
import com.jussicodes.music.ui.icons.PlaylistAdd
import com.jussicodes.music.ui.icons.VipFill
import com.jussicodes.music.ui.navigation.AlbumNav
import com.jussicodes.music.ui.navigation.PlaylistNav
import com.jussicodes.music.ui.navigation.RecordNav
import com.jussicodes.music.ui.navigation.Screen
import com.jussicodes.music.ui.navigation.UserFollowNav
import com.jussicodes.music.utils.CoverImageSize
import com.jussicodes.music.utils.AvatarUploadLimiter
import com.jussicodes.music.utils.PlaylistCoverSyncBus
import com.jussicodes.music.utils.toCoverImageUrl
import com.jussicodes.music.utils.withAvatarCacheBuster
import com.jussicodes.music.utils.withPlaylistCoverCacheBuster
import com.jussicodes.music.viewModel.LibraryScreenViewModel
import com.rcmiku.ncmapi.model.Album
import com.rcmiku.ncmapi.model.Playlist
import com.rcmiku.ncmapi.model.UserInfoBatch
import java.io.File
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import com.jussicodes.music.ui.components.ResponsiveAlertDialog

private const val LIKED_PLAYLIST_NAME_FRAGMENT = "\u559c\u6b22"
private const val AVATAR_UPLOAD_SIZE = 800
private const val PLAYLIST_COVER_UPLOAD_SIZE = 300

@Composable
fun LibraryScreen(
    navController: NavHostController,
    libraryScreenViewModel: LibraryScreenViewModel = hiltViewModel()
) {
    // cookie 读进程级 NcmCookieStore（Application watcher 从 DataStore 灌值）：
    // 本 Screen 每次"探索划回主页"都随路由切换整块重组，rememberNullablePreference
    // 是冷流 collectAsState(initial=null)，重组首帧 cookie=null 会闪一帧"点击登录"
    // 卡片 + LazyColumn 整块不渲染（用户报的"闪未登录提示框/信息卡片闪白"）。
    val ncmCookie by NcmCookieStore.cookie.collectAsState()
    // 小播放器是否显示（有歌在队列）：末尾避让空白块只在有歌时留。
    val hasMiniPlayer = (LocalPlayerState.current?.player?.mediaItemCount ?: 0) != 0
    val userInfoBatchState by libraryScreenViewModel.userInfo.collectAsState()
    // 信息卡内容走进程级 store（PinnedAlbumStore 同款）：VM 的 collectAsState 首帧可能出 null
    // （userInfo 门控整卡 / userDetail 无缓存恒 null）→ 切 tab 重组闪一下；进程级首帧即真值。
    val homeUserInfo = HomeUserInfoStore.userInfo.collectAsState().value
    val homeUserDetail = HomeUserInfoStore.userDetail.collectAsState().value
    val favoriteSongState by libraryScreenViewModel.favoriteSong.collectAsState()
    val userPlaylists by libraryScreenViewModel.userPlaylists.collectAsState()
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
    var showPinnedAlbumPickDialog by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var editNickname by remember { mutableStateOf("") }
    var editSignature by remember { mutableStateOf("") }
    var profileUpdateBusy by remember { mutableStateOf(false) }
    // 专辑墙关闭标志读进程级 PinnedAlbumStore.hiddenState（启动时 DataStore→单例回流）：
    // null = 冷启动首帧 DataStore 尚未出值，不渲染卡片（加号不会闪现）；
    // 切 tab 重组合时不丢——旧实现用 remember 存本地状态,切回主页会复位成
    // (false, settled=false) 先闪一帧再恢复,就是用户看到的"闪一下"。
    val pinnedAlbumsHidden by PinnedAlbumStore.hiddenState.collectAsState()

    LaunchedEffect(ncmCookie) {
        val cookie = ncmCookie ?: return@LaunchedEffect
        if (cookie.isNotEmpty()) {
            libraryScreenViewModel.fetchUserInfo(cookie = cookie)
            PinnedAlbumStore.sync()
        } else {
            libraryScreenViewModel.clear()
            PinnedAlbumStore.resetOnLogout()
        }
    }

    val userId = userInfoBatchState?.account?.profile?.userId ?: 0L
    val favoritePlaylistId = userPlaylists.firstOrNull { playlist ->
        playlist.creator?.userId == userId && (
            playlist.specialType == 5 ||
            playlist.name.contains(LIKED_PLAYLIST_NAME_FRAGMENT))
    }?.id
    val favoritePlaylist = userPlaylists.firstOrNull { it.id == favoritePlaylistId }
    val normalPlaylists = userPlaylists.filterNot { it.id == favoritePlaylistId }
    val collectedPlaylists = normalPlaylists.filter { it.subscribed || it.creator?.userId != userId }
    val createdPlaylists = normalPlaylists.filterNot { it in collectedPlaylists }

    // 主页文字色（设置页「外观」选择）：auto 档不覆盖（scheme 原样，逐像素一致、零重绘）；
    // 白/黑档用本地 ColorScheme 覆盖整页（卡片内 onSurface/onSurfaceVariant 读取处自动跟随）。
    val bgTextMode = HomeBgTextStore.state.collectAsState().value
    val bgTextColor = resolveHomeBgTextColor(bgTextMode, MaterialTheme.colorScheme)
    MaterialTheme(colorScheme = if (bgTextMode == "auto") {
        MaterialTheme.colorScheme
    } else {
        MaterialTheme.colorScheme.copy(
            onSurface = bgTextColor,
            onSurfaceVariant = bgTextColor.copy(alpha = 0.85f),
        )
    }) {
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = { TopBar(navController = navController, titleRes = R.string.mine, transparent = true, contentColor = bgTextColor) }
    ) { padding ->
        // 未登录（cookie 键不存在 / 空串）都显示「点击登录」卡片；
        // 原先键不存在（全新安装/从未登录过）走空 Box，登录提示整页看不见。
        if (ncmCookie == null || ncmCookie?.isEmpty() == true) {
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
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = transparentCardColor())
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
                        // 说明：点进去是内嵌 WebView 的网易云登录页，登录成功后自动抓取
                        // MUSIC_U 等 cookie 落盘（也可在登录页右上角手贴 cookie）。
                        Text(
                            text = "点击跳转webview登录获取cookie",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = 12.dp,
                    bottom = 20.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                homeUserInfo?.let {
                    item {
                        LibraryUserCard(
                            navController = navController,
                            userInfo = it,
                            avatarCacheVersion = avatarCacheVersion,
                            followsCount = homeUserDetail?.profile?.followsCount,
                            followedsCount = homeUserDetail?.profile?.followedsCount,
                            onRoamClick = { navController.navigate(Screen.Roam.route) },
                            onRecentPlayClick = { navController.navigate(Screen.RecentPlay.route) },
                            onMessagesClick = { navController.navigate(Screen.Messages.route) },
                            onRecordClick = { navController.navigate(RecordNav(uid = it.account.profile.userId)) },
                            onAvatarClick = {
                                libraryScreenViewModel.fetchUserInfo(cookie = ncmCookie, force = true)
                                showAvatarDialog = true
                            },
                            onEditProfileClick = {
                                editNickname = it.account.profile.nickname
                                editSignature = it.account.profile.signature
                                showEditProfileDialog = true
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
                                PlaylistMenuItem("编辑信息", icon = Pencil) { playlist ->
                                    editingPlaylist = playlist
                                    editingPlaylistName = playlist.name
                                    editingPlaylistDescription = playlist.description
                                },
                                PlaylistMenuItem("上传封面", icon = Icons.Outlined.AddPhotoAlternate) { playlist ->
                                    coverEditingPlaylist = playlist
                                    coverPicker.launch("image/*")
                                },
                                PlaylistMenuItem("删除歌单", icon = Delete, destructive = true) { playlist ->
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
                                PlaylistMenuItem("取消收藏", icon = Favorite) { playlist ->
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

                item {
                    if (pinnedAlbumsHidden == false) {
                        PinnedAlbumsCard(
                            onOpenAlbum = { album -> navController.navigate(AlbumNav(albumId = album.id)) },
                            onAddClick = { showPinnedAlbumPickDialog = true }
                        )
                    }
                }

                item {
                    // 列表末尾（专辑墙下方）空白：小播放器悬浮在底栏上方，滚到底最后一段
                    // 会被它盖住 64dp；有歌时末尾垫一块 MiniPlayerHeight 避让。
                    if (hasMiniPlayer) Spacer(modifier = Modifier.height(MiniPlayerHeight))
                    Spacer(modifier = Modifier.navigationBarsPadding())
                }
            }
        }
    }
    }

    editingPlaylist?.let { playlist ->
        ResponsiveAlertDialog(
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

    if (showEditProfileDialog) {
        ResponsiveAlertDialog(
            onDismissRequest = {
                if (!profileUpdateBusy) showEditProfileDialog = false
            },
            title = { Text("编辑个人信息") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editNickname,
                        onValueChange = { editNickname = it },
                        label = { Text("昵称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editSignature,
                        onValueChange = { editSignature = it },
                        label = { Text("签名") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val nick = editNickname
                        val sig = editSignature
                        profileUpdateBusy = true
                        libraryScreenViewModel.updateProfile(nick, sig) { success, msg ->
                            profileUpdateBusy = false
                            if (success) {
                                showEditProfileDialog = false
                                Toast.makeText(context, "资料已更新", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    msg?.let { "更新失败：$it" } ?: "更新资料失败",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    enabled = editNickname.isNotBlank() && !profileUpdateBusy
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showEditProfileDialog = false },
                    enabled = !profileUpdateBusy
                ) {
                    Text("取消")
                }
            }
        )
    }

    selectedCoverUri?.let { uri ->
        ResponsiveAlertDialog(
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
        ResponsiveAlertDialog(
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

    if (showPinnedAlbumPickDialog) {
        PinnedAlbumPickDialog(onDismiss = { showPinnedAlbumPickDialog = false })
    }

    selectedAvatarUri?.let { uri ->
        ResponsiveAlertDialog(
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
    followsCount: Int?,
    followedsCount: Int?,
    onRoamClick: () -> Unit,
    onRecentPlayClick: () -> Unit,
    onMessagesClick: () -> Unit,
    onRecordClick: () -> Unit,
    onAvatarClick: () -> Unit,
    onEditProfileClick: () -> Unit
) {
    val profile = userInfo.account.profile
    val secondaryText = profile.signature.takeIf { it.isNotBlank() }
    val haptics = LocalHapticFeedback.current
    val followsLabel = if (followsCount != null) "$followsCount 关注" else "关注"
    val followedsLabel = if (followedsCount != null) "$followedsCount 粉丝" else "粉丝"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        // 容器色恒为不透明 surfaceContainerLow：「主页透明卡片」开关不作用于信息卡
        // （见 userCardGlassColor 注释）。但开关开启时其他卡都透明成一片，信息卡
        // 作为唯一实底块需要一条细描边交代边界——走 Card 的 border 参数而不是在
        // 内容里垫Box：垫 Box 会占一行布局、把内容往下推，且描边画在卡片内部。
        border = if (HomeTransparentCardStore.state.collectAsState().value) {
            BorderStroke(
                width = 1.dp,
                color = androidx.compose.ui.graphics.Color.White.copy(
                    alpha = if (isSystemInDarkTheme()) 0.14f else 0.35f
                )
            )
        } else {
            null
        },
        colors = CardDefaults.cardColors(containerColor = userCardGlassColor())
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                        .clip(CircleShape)
                        .clickable(enabled = profile.avatarUrl.isNotBlank(), onClick = onAvatarClick),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    AsyncImage(
                        model = profile.avatarUrl.withAvatarCacheBuster(avatarCacheVersion),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.clip(CircleShape).size(76.dp)
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
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = profile.nickname,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(onClick = onEditProfileClick)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = followsLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable {
                                navController.navigate(
                                    UserFollowNav(
                                        userId = profile.userId,
                                        type = com.jussicodes.music.viewModel.UserFollowType.FOLLOWS.name,
                                        showArtistFollows = true
                                    )
                                )
                            }
                        )
                        Text(
                            text = followedsLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable {
                                navController.navigate(
                                    UserFollowNav(
                                        userId = profile.userId,
                                        type = com.jussicodes.music.viewModel.UserFollowType.FOLLOWEDS.name
                                    )
                                )
                            }
                        )
                    }
                    secondaryText?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onMessagesClick()
                        }
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Message,
                            contentDescription = stringResource(R.string.messages)
                        )
                    }
                    Text(text = "消息", style = MaterialTheme.typography.labelSmall)
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onRecordClick()
                        }
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Leaderboard,
                            contentDescription = stringResource(R.string.record)
                        )
                    }
                    Text(text = "排行", style = MaterialTheme.typography.labelSmall)
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onRecentPlayClick()
                        }
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = History,
                            contentDescription = stringResource(R.string.recent_play)
                        )
                    }
                    Text(text = "历史", style = MaterialTheme.typography.labelSmall)
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onRoamClick()
                        }
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = PersonalRadio,
                            contentDescription = stringResource(R.string.roam)
                        )
                    }
                    Text(text = "电台", style = MaterialTheme.typography.labelSmall)
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
private fun FavoritePlaylistCard(
    playlist: Playlist,
    coverVersion: Long,
    onCreatePlaylistClick: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = transparentCardColor())
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = transparentCardColor())
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
                    JussiDropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        menuItems.forEach { item ->
                            JussiMenuItem(
                                text = item.text,
                                icon = item.icon,
                                destructive = item.destructive,
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
    val icon: ImageVector? = null,
    val destructive: Boolean = false,
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

