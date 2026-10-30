package com.jussicodes.music.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.jussicodes.music.LocalPlayerController
import com.jussicodes.music.R
import com.jussicodes.music.constants.MiniPlayerHeight
import com.jussicodes.music.data.download.DownloadStatus
import com.jussicodes.music.data.download.DownloadStore
import com.jussicodes.music.data.download.DownloadTask
import com.jussicodes.music.download.DownloadManager
import com.jussicodes.music.extensions.playMediaAtId
import com.jussicodes.music.extensions.setPlaylist
import com.jussicodes.music.ui.components.JussiDropdownMenu
import com.jussicodes.music.ui.components.JussiMenuItem
import com.jussicodes.music.ui.components.TopBar
import com.rcmiku.ncmapi.model.Artist
import com.rcmiku.ncmapi.model.Song
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(
    navController: NavHostController
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tasks by DownloadStore.tasks.collectAsState()

    val completedTasks = remember(tasks) {
        tasks.filter { it.status == DownloadStatus.COMPLETED }
    }
    val activeTasks = remember(tasks) {
        tasks.filter { it.status != DownloadStatus.COMPLETED }
    }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopBar(
                title = { Text("下载管理") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = if (completedTasks.isNotEmpty()) "已下载 (${completedTasks.size})" else "已下载"
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = if (activeTasks.isNotEmpty()) "下载中 (${activeTasks.size})" else "下载中"
                        )
                    }
                )
            }

            if (selectedTab == 0) {
                DownloadedListSection(
                    completedTasks = completedTasks,
                    onDelete = { songId, deleteFile ->
                        DownloadManager.deleteDownloaded(context, songId, deleteFile)
                    }
                )
            } else {
                DownloadingListSection(
                    activeTasks = activeTasks,
                    onPause = { DownloadManager.pause(context, it) },
                    onResume = { DownloadManager.resume(context, it) },
                    onCancel = { DownloadManager.cancel(context, it) },
                    onPauseAll = { DownloadManager.pauseAll(context) },
                    onResumeAll = { DownloadManager.resumeAll(context) }
                )
            }
        }
    }
}

@Composable
private fun DownloadedListSection(
    completedTasks: List<DownloadTask>,
    onDelete: (Long, Boolean) -> Unit
) {
    val context = LocalContext.current
    val mediaController = LocalPlayerController.current.controller
    var pendingDeleteTask by remember { mutableStateOf<DownloadTask?>(null) }

    val totalBytes = remember(completedTasks) {
        completedTasks.sumOf { it.totalBytes }
    }

    if (completedTasks.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Outlined.CloudDownload,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "暂无已下载歌曲",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "在播放页或单曲菜单中点击下载，离线也能随心畅听",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            // 顶部统计与操作卡片
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "已下载 ${completedTasks.size} 首歌曲",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "占用空间: ${formatBytes(totalBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FilledTonalButton(
                    onClick = {
                        val songs = completedTasks.map { task ->
                            Song(
                                id = task.songId,
                                name = task.title,
                                ar = listOf(Artist(id = 0, name = task.artist)),
                                al = com.rcmiku.ncmapi.model.SongAlbum(id = 0, name = task.album, picUrl = task.coverUrl),
                                dt = task.durationMs
                            )
                        }
                        mediaController?.setPlaylist(
                            songs,
                            sourceId = 20261L,
                            sourceName = "已下载歌曲",
                            sourceType = "download"
                        )
                        mediaController?.playMediaAtId(completedTasks.first().songId)
                    }
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("全部播放")
                }
            }
        }

        items(completedTasks, key = { it.songId }) { task ->
            DownloadedSongItem(
                task = task,
                onClick = {
                    val songs = completedTasks.map { t ->
                        Song(
                            id = t.songId,
                            name = t.title,
                            ar = listOf(Artist(id = 0, name = t.artist)),
                            al = com.rcmiku.ncmapi.model.SongAlbum(id = 0, name = t.album, picUrl = t.coverUrl),
                            dt = t.durationMs
                        )
                    }
                    mediaController?.setPlaylist(
                        songs,
                        sourceId = 20261L,
                        sourceName = "已下载歌曲",
                        sourceType = "download"
                    )
                    mediaController?.playMediaAtId(task.songId)
                },
                onDeleteClick = { pendingDeleteTask = task },
                onShareClick = { shareDownloadedFile(context, task) }
            )
        }

        item {
            Spacer(Modifier.height(MiniPlayerHeight + 16.dp))
        }
    }

    pendingDeleteTask?.let { task ->
        AlertDialog(
            onDismissRequest = { pendingDeleteTask = null },
            title = { Text("删除已下载歌曲") },
            text = { Text("确定要删除「${task.title}」吗？文件将从本地存储彻底移除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(task.songId, true)
                        pendingDeleteTask = null
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteTask = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun DownloadedSongItem(
    task: DownloadTask,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onShareClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = task.coverUrl,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                QualityBadge(quality = task.quality)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "${task.artist} · ${formatBytes(task.totalBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "更多",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            JussiDropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                JussiMenuItem(
                    text = "分享文件",
                    icon = Icons.Outlined.Share,
                    onClick = {
                        menuExpanded = false
                        onShareClick()
                    }
                )
                JussiMenuItem(
                    text = "删除下载",
                    icon = Icons.Outlined.Delete,
                    danger = true,
                    onClick = {
                        menuExpanded = false
                        onDeleteClick()
                    }
                )
            }
        }
    }
}

@Composable
private fun DownloadingListSection(
    activeTasks: List<DownloadTask>,
    onPause: (Long) -> Unit,
    onResume: (Long) -> Unit,
    onCancel: (Long) -> Unit,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit
) {
    if (activeTasks.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "没有正在下载的任务",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "排队任务 (${activeTasks.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onPauseAll) {
                        Text("全部暂停")
                    }
                    FilledTonalButton(onClick = onResumeAll) {
                        Text("全部开始")
                    }
                }
            }
        }

        items(activeTasks, key = { it.songId }) { task ->
            DownloadingTaskItem(
                task = task,
                onPause = { onPause(task.songId) },
                onResume = { onResume(task.songId) },
                onCancel = { onCancel(task.songId) }
            )
        }

        item {
            Spacer(Modifier.height(MiniPlayerHeight + 16.dp))
        }
    }
}

@Composable
private fun DownloadingTaskItem(
    task: DownloadTask,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = task.coverUrl,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))

            val statusText = when (task.status) {
                DownloadStatus.PENDING -> "排队中..."
                DownloadStatus.RESOLVING -> "正在解析直链..."
                DownloadStatus.DOWNLOADING -> {
                    val percent = (task.progress * 100).toInt()
                    "$percent% · ${task.speedText}"
                }
                DownloadStatus.PAUSED -> "已暂停"
                DownloadStatus.FAILED -> "下载失败: ${task.errorMsg ?: "未知错误"}"
                DownloadStatus.COMPLETED -> "已完成"
            }

            Text(
                text = "${task.artist} · $statusText",
                style = MaterialTheme.typography.bodySmall,
                color = if (task.status == DownloadStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(6.dp))

            if (task.status == DownloadStatus.RESOLVING) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(
                    progress = { task.progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            when (task.status) {
                DownloadStatus.DOWNLOADING, DownloadStatus.RESOLVING, DownloadStatus.PENDING -> {
                    IconButton(onClick = onPause) {
                        Icon(imageVector = Icons.Default.Pause, contentDescription = "暂停")
                    }
                }
                DownloadStatus.PAUSED -> {
                    IconButton(onClick = onResume) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "继续")
                    }
                }
                DownloadStatus.FAILED -> {
                    IconButton(onClick = onResume) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "重试")
                    }
                }
                else -> Unit
            }

            IconButton(onClick = onCancel) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "取消")
            }
        }
    }
}

@Composable
private fun QualityBadge(quality: String) {
    val (label, bg) = when (quality) {
        "hires" -> "Hi-Res" to MaterialTheme.colorScheme.tertiaryContainer
        "lossless" -> "FLAC" to MaterialTheme.colorScheme.primaryContainer
        "exhigh" -> "320K" to MaterialTheme.colorScheme.secondaryContainer
        else -> "128K" to MaterialTheme.colorScheme.surfaceVariant
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024f * 1024f * 1024f))
        bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024f * 1024f))
        bytes >= 1024 -> String.format("%.0f KB", bytes / 1024f)
        else -> "$bytes B"
    }
}

private fun shareDownloadedFile(context: Context, task: DownloadTask) {
    runCatching {
        val uri: Uri = if (!task.filePath.isNullOrBlank()) {
            val file = File(task.filePath)
            if (file.exists()) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            } else null
        } else if (!task.fileUri.isNullOrBlank()) {
            Uri.parse(task.fileUri)
        } else null ?: run {
            Toast.makeText(context, "文件不存在", Toast.LENGTH_SHORT).show()
            return
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = task.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "分享歌曲文件"))
    }.onFailure {
        Toast.makeText(context, "无法分享该文件: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}
