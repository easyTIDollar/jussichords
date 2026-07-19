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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import androidx.compose.material3.OutlinedButton
import com.jussicodes.music.ui.components.LargeImageDialog
import com.jussicodes.music.ui.components.PlaylistListItem
import com.jussicodes.music.ui.navigation.PlaylistNav
import com.jussicodes.music.ui.navigation.UserFollowNav
import com.jussicodes.music.utils.CoverImageSize
import com.jussicodes.music.utils.toCoverImageUrl
import com.jussicodes.music.utils.withAvatarCacheBuster
import com.jussicodes.music.viewModel.UserFollowType
import com.jussicodes.music.viewModel.UserScreenViewModel
import java.io.File

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
    var showAvatarDialog by remember { mutableStateOf(false) }
    var showAvatarEditDialog by remember { mutableStateOf(false) }
    var avatarWidth by remember { mutableStateOf("200") }
    var avatarHeight by remember { mutableStateOf("200") }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val width = avatarWidth.toIntOrNull()?.coerceIn(1, 2000) ?: 200
        val height = avatarHeight.toIntOrNull()?.coerceIn(1, 2000) ?: 200
        uri?.copyToAvatarCache(context, width, height)?.let { file ->
            userScreenViewModel.uploadAvatar(file) { success ->
                Toast.makeText(
                    context,
                    if (success) "头像上传成功" else "头像上传失败",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("用户主页") }) }) { padding ->
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AsyncImage(
                        model = avatarUrl.withAvatarCacheBuster(avatarCacheVersion),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(84.dp)
                            .clip(CircleShape)
                            .clickable(enabled = isSelf && !avatarUrl.isNullOrBlank()) {
                                showAvatarDialog = true
                            }
                    )
                    Text(text = userDetailState?.profile?.nickname.orEmpty())
                    Text(text = userDetailState?.profile?.signature?.takeIf { it.isNotBlank() } ?: "暂无签名")
                    if (!isSelf) {
                        OutlinedButton(
                            onClick = userScreenViewModel::toggleFollow,
                            enabled = !isFollowUpdating && userDetailState != null
                        ) {
                            Text(text = if (isFollowed) "取消关注" else "关注")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "关注：${userDetailState?.profile?.followsCount ?: 0}",
                            modifier = Modifier.clickable {
                                navController.navigate(
                                    UserFollowNav(
                                        userId = userDetailState?.profile?.userId ?: 0,
                                        type = UserFollowType.FOLLOWS.name
                                    )
                                )
                            }
                        )
                        Text(
                            text = "粉丝：${userDetailState?.profile?.followedsCount ?: 0}",
                            modifier = Modifier.clickable {
                                navController.navigate(
                                    UserFollowNav(
                                        userId = userDetailState?.profile?.userId ?: 0,
                                        type = UserFollowType.FOLLOWEDS.name
                                    )
                                )
                            }
                        )
                    }
                }
            }

            items(userPlaylists.size) { index ->
                val playlist = userPlaylists[index]
                PlaylistListItem(
                    playlist = playlist,
                    modifier = Modifier.clickable {
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

    if (showAvatarDialog) {
        LargeImageDialog(
            imageUrl = avatarUrl.toCoverImageUrl(CoverImageSize.LARGE),
            onDismiss = { showAvatarDialog = false }
        ) {
            TextButton(
                onClick = { showAvatarEditDialog = true },
                enabled = !isAvatarUploading
            ) {
                Text(if (isAvatarUploading) "上传中" else "编辑")
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
