package com.jussicodes.music.ui.screen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import androidx.compose.material3.OutlinedButton
import com.jussicodes.music.ui.components.LargeImageDialog
import com.jussicodes.music.ui.navigation.PlaylistNav
import com.jussicodes.music.ui.navigation.UserFollowNav
import com.jussicodes.music.utils.CoverImageSize
import com.jussicodes.music.utils.AvatarUploadLimiter
import com.jussicodes.music.utils.toCoverImageUrl
import com.jussicodes.music.utils.withAvatarCacheBuster
import com.jussicodes.music.viewModel.UserFollowType
import com.jussicodes.music.viewModel.UserScreenViewModel
import com.rcmiku.ncmapi.model.Playlist
import java.io.File

private const val LIKED_PLAYLIST_NAME_FRAGMENT = "\u559c\u6b22"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserScreen(
    navController: NavHostController,
    userScreenViewModel: UserScreenViewModel = hiltViewModel()
) {
    val userDetailState by userScreenViewModel.userDetail.collectAsState()
    val userPlaylists by userScreenViewModel.userPlaylists.collectAsState()
    val isFollowed by userScreenViewModel.isFollowed.collectAsState()
    val isFollowUpdating by userScreenViewModel.isFollowUpdating.collectAsState()
    val isSelf by userScreenViewModel.isSelf.collectAsState()
    val isAvatarUploading by userScreenViewModel.isAvatarUploading.collectAsState()
    val avatarCacheVersion by userScreenViewModel.avatarCacheVersion.collectAsState()
    val context = LocalContext.current
    val avatarUrl = userDetailState?.profile?.avatarUrl
    val profile = userDetailState?.profile
    val userId = profile?.userId ?: 0L
    val favoritePlaylistId = userPlaylists.firstOrNull { playlist ->
        playlist.specialType == 5 ||
            (playlist.creator?.userId == userId && playlist.name.contains(LIKED_PLAYLIST_NAME_FRAGMENT))
    }?.id
    val favoritePlaylist = userPlaylists.firstOrNull { it.id == favoritePlaylistId }
    val normalPlaylists = userPlaylists.filterNot { it.id == favoritePlaylistId }
    val collectedPlaylists = normalPlaylists.filter { it.subscribed || it.creator?.userId != userId }
    val createdPlaylists = normalPlaylists.filterNot { it in collectedPlaylists }
    var showAvatarDialog by remember { mutableStateOf(false) }
    var showAvatarEditDialog by remember { mutableStateOf(false) }
    var avatarWidth by remember { mutableStateOf("200") }
    var avatarHeight by remember { mutableStateOf("200") }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val width = avatarWidth.toIntOrNull()?.coerceIn(1, 2000) ?: 200
        val height = avatarHeight.toIntOrNull()?.coerceIn(1, 2000) ?: 200
        uri?.copyToAvatarCache(context, width, height)?.let { file ->
            userScreenViewModel.uploadAvatar(file) { success, message ->
                Toast.makeText(
                    context,
                    if (success) "头像上传成功" else "头像上传失败：${message.orEmpty()}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = profile?.nickname
                            ?.takeIf { it.isNotBlank() }
                            ?.let { "${it}的主页" }
                            ?: "用户主页",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                }
            )
        }
    ) { padding ->
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                UserProfileCard(
                    nickname = profile?.nickname.orEmpty(),
                    signature = profile?.signature?.takeIf { it.isNotBlank() } ?: "暂无签名",
                    avatarUrl = avatarUrl,
                    avatarCacheVersion = avatarCacheVersion,
                    isSelf = isSelf,
                    isFollowed = isFollowed,
                    isFollowUpdating = isFollowUpdating,
                    followsCount = profile?.followsCount ?: 0,
                    followedsCount = profile?.followedsCount ?: 0,
                    onAvatarClick = { showAvatarDialog = true },
                    onFollowClick = userScreenViewModel::toggleFollow,
                    onFollowsClick = {
                        navController.navigate(
                            UserFollowNav(
                                userId = profile?.userId ?: 0,
                                type = UserFollowType.FOLLOWS.name,
                                showArtistFollows = isSelf
                            )
                        )
                    },
                    onFollowedsClick = {
                        navController.navigate(
                            UserFollowNav(
                                userId = profile?.userId ?: 0,
                                type = UserFollowType.FOLLOWEDS.name
                            )
                        )
                    }
                )
            }

            favoritePlaylist?.let { playlist ->
                item {
                    UserPlaylistGroupCard(
                        title = "喜欢的音乐",
                        playlists = listOf(playlist),
                        onPlaylistClick = {
                            navController.navigate(
                                PlaylistNav(
                                    playlistId = playlist.id,
                                    noCache = isSelf
                                )
                            )
                        }
                    )
                }
            }

            if (createdPlaylists.isNotEmpty()) {
                item {
                    UserPlaylistGroupCard(
                        title = "创建的歌单",
                        playlists = createdPlaylists,
                        onPlaylistClick = { playlist ->
                            navController.navigate(
                                PlaylistNav(
                                    playlistId = playlist.id,
                                    limit = playlist.trackCount
                                )
                            )
                        }
                    )
                }
            }

            if (collectedPlaylists.isNotEmpty()) {
                item {
                    UserPlaylistGroupCard(
                        title = "收藏的歌单",
                        playlists = collectedPlaylists,
                        showDividers = false,
                        onPlaylistClick = { playlist ->
                            navController.navigate(
                                PlaylistNav(
                                    playlistId = playlist.id,
                                    limit = playlist.trackCount
                                )
                            )
                        }
                    )
                }
            }
        }
    }

    if (showAvatarDialog) {
        LargeImageDialog(
            imageUrl = avatarUrl.toCoverImageUrl(CoverImageSize.LARGE),
            onDismiss = { showAvatarDialog = false },
            showSaveAction = true
        ) {
            if (isSelf) {
                TextButton(
                    onClick = {
                        val remaining = AvatarUploadLimiter.remaining(context)
                        Toast.makeText(
                            context,
                            "头像每周最多上传 5 次，本周还可上传 $remaining 次",
                            Toast.LENGTH_LONG
                        ).show()
                        if (remaining > 0) showAvatarEditDialog = true
                    },
                    enabled = !isAvatarUploading
                ) {
                    Text(if (isAvatarUploading) "上传中" else "编辑")
                }
            }
        }
    }

    if (showAvatarEditDialog) {
        AlertDialog(
            onDismissRequest = { showAvatarEditDialog = false },
            title = { Text("调整头像尺寸") },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = avatarWidth,
                        onValueChange = { avatarWidth = it.filter(Char::isDigit).take(4) },
                        label = { Text("宽度") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = avatarHeight,
                        onValueChange = { avatarHeight = it.filter(Char::isDigit).take(4) },
                        label = { Text("高度") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAvatarEditDialog = false
                        avatarPicker.launch("image/*")
                    },
                    enabled = !isAvatarUploading
                ) {
                    Text("从相册选择")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAvatarEditDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun UserProfileCard(
    nickname: String,
    signature: String,
    avatarUrl: String?,
    avatarCacheVersion: Long,
    isSelf: Boolean,
    isFollowed: Boolean,
    isFollowUpdating: Boolean,
    followsCount: Int,
    followedsCount: Int,
    onAvatarClick: () -> Unit,
    onFollowClick: () -> Unit,
    onFollowsClick: () -> Unit,
    onFollowedsClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AsyncImage(
                model = avatarUrl.withAvatarCacheBuster(avatarCacheVersion),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .clickable(enabled = !avatarUrl.isNullOrBlank(), onClick = onAvatarClick)
            )
            Text(
                text = nickname,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = signature,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!isSelf) {
                OutlinedButton(
                    onClick = onFollowClick,
                    enabled = !isFollowUpdating
                ) {
                    Text(text = if (isFollowed) "取消关注" else "关注")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                    onClick = onFollowsClick
                ) {
                    Text(text = "关注：$followsCount")
                }
                TextButton(
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                    onClick = onFollowedsClick
                ) {
                    Text(text = "粉丝：$followedsCount")
                }
            }
        }
    }
}

@Composable
private fun UserPlaylistGroupCard(
    title: String,
    playlists: List<Playlist>,
    showDividers: Boolean = true,
    onPlaylistClick: (Playlist) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Column(modifier = Modifier.heightIn(max = (playlists.size * 66).dp)) {
                playlists.forEachIndexed { index, playlist ->
                    UserPlaylistRow(
                        playlist = playlist,
                        onClick = { onPlaylistClick(playlist) }
                    )
                    if (index != playlists.lastIndex) {
                        if (showDividers) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 3.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
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
private fun UserPlaylistRow(
    playlist: Playlist,
    onClick: () -> Unit
) {
    val creatorName = playlist.creator?.nickname.orEmpty()
    val subtitle = buildString {
        append("${playlist.trackCount} 首")
        if (creatorName.isNotEmpty()) {
            append(" by ")
            append(creatorName)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .padding(vertical = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = playlist.picUrl.toCoverImageUrl(CoverImageSize.LIST),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High,
                modifier = Modifier.clip(MaterialTheme.shapes.medium).size(52.dp)
            )
            Column(
                modifier = Modifier.padding(start = 10.dp).weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun Uri.copyToAvatarCache(context: Context, width: Int, height: Int): File? {
    val file = File(context.cacheDir, "avatar_${System.currentTimeMillis()}.jpg")
    return runCatching {
        context.contentResolver.openInputStream(this)?.use { input ->
            val bitmap = BitmapFactory.decodeStream(input) ?: return null
            val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
            file.outputStream().use { output ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 95, output)
            }
            if (scaled != bitmap) {
                scaled.recycle()
            }
            bitmap.recycle()
        } ?: return null
        file
    }.getOrNull()
}
