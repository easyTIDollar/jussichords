package com.jussicodes.music.ui.screen

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import com.jussicodes.music.ui.components.SwipableTabPager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.jussicodes.music.LocalPlayerController
import com.jussicodes.music.constants.MiniPlayerHeight
import com.jussicodes.music.constants.downloadCoverKey
import com.jussicodes.music.constants.downloadCustomDirNameKey
import com.jussicodes.music.constants.downloadCustomDirUriKey
import com.jussicodes.music.constants.downloadCustomGroupsKey
import com.jussicodes.music.constants.downloadLrcKey
import com.jussicodes.music.constants.downloadTlyricKey
import com.jussicodes.music.constants.downloadQualityKey
import com.jussicodes.music.constants.downloadWifiOnlyKey
import com.jussicodes.music.data.download.DownloadStatus
import com.jussicodes.music.data.download.DownloadStore
import com.jussicodes.music.data.download.DownloadTask
import com.jussicodes.music.download.DownloadManager
import com.jussicodes.music.extensions.playMediaAtId
import com.jussicodes.music.extensions.setPlaylist
import com.jussicodes.music.ui.components.DownloadQualityDialog
import com.jussicodes.music.ui.components.JussiDropdownMenu
import com.jussicodes.music.ui.components.JussiMenuItem
import com.jussicodes.music.utils.rememberPreference
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
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val tasks by DownloadStore.tasks.collectAsState()

    val completedTasks = remember(tasks) {
        tasks.filter { it.status == DownloadStatus.COMPLETED }
    }
    val activeTasks = remember(tasks) {
        tasks.filter { it.status != DownloadStatus.COMPLETED }
    }

    var showSettingsSheet by rememberSaveable { mutableStateOf(false) }
    var customGroupsRaw by rememberPreference(downloadCustomGroupsKey, "")
    val customGroupNames: Set<String> = remember(customGroupsRaw) {
        customGroupsRaw.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
    }

    // 多选模式状态
    var isSelectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedSongIds by remember { mutableStateOf(setOf<Long>()) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    var showMoveToGroupDialog by remember { mutableStateOf(false) }

    // 当切换 tab 或退出多选模式
    val exitSelectionMode = {
        isSelectionMode = false
        selectedSongIds = emptySet()
    }

    // 当处于多选模式时，拦截系统返回键/手势返回，仅退出多选而非返回上一页
    BackHandler(enabled = isSelectionMode) {
        exitSelectionMode()
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                // 多选模式顶部栏
                TopAppBar(
                    title = { Text("已选择 ${selectedSongIds.size} 首") },
                    navigationIcon = {
                        IconButton(onClick = exitSelectionMode) {
                            Icon(Icons.Default.Close, contentDescription = "取消选择")
                        }
                    },
                    actions = {
                        val allSelected = completedTasks.isNotEmpty() && selectedSongIds.size == completedTasks.size
                        TextButton(onClick = {
                            selectedSongIds = if (allSelected) {
                                emptySet()
                            } else {
                                completedTasks.map { it.songId }.toSet()
                            }
                        }) {
                            Text(if (allSelected) "取消全选" else "全选")
                        }
                        IconButton(
                            onClick = {
                                if (selectedSongIds.isNotEmpty()) {
                                    showMoveToGroupDialog = true
                                }
                            },
                            enabled = selectedSongIds.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CreateNewFolder,
                                contentDescription = "加入分组"
                            )
                        }
                        IconButton(
                            onClick = {
                                if (selectedSongIds.isNotEmpty()) {
                                    showBatchDeleteConfirm = true
                                }
                            },
                            enabled = selectedSongIds.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "删除选中项",
                                tint = if (selectedSongIds.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            } else {
                // 常规顶部栏
                TopAppBar(
                    title = { Text("下载管理") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { DownloadManager.openDownloadFolder(context) }) {
                            Icon(
                                imageVector = Icons.Outlined.Folder,
                                contentDescription = "打开系统文件夹"
                            )
                        }
                        IconButton(onClick = { showSettingsSheet = true }) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = "下载偏好设置"
                            )
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        val tabTitles = listOf(
            if (completedTasks.isNotEmpty()) "已下载 (${completedTasks.size})" else "已下载",
            if (activeTasks.isNotEmpty()) "下载中 (${activeTasks.size})" else "下载中"
        )
        val pagerState = rememberPagerState(initialPage = 0, pageCount = { tabTitles.size })

        LaunchedEffect(pagerState.currentPage) {
            if (pagerState.currentPage != 0) {
                exitSelectionMode()
            }
        }

        SwipableTabPager(
            titles = tabTitles,
            pagerState = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) { page ->
            when (page) {
                0 -> DownloadedListSection(
                    completedTasks = completedTasks,
                    isLandscape = isLandscape,
                    isSelectionMode = isSelectionMode,
                    selectedSongIds = selectedSongIds,
                    onToggleSelect = { songId ->
                        selectedSongIds = if (songId in selectedSongIds) {
                            val next = selectedSongIds - songId
                            if (next.isEmpty()) isSelectionMode = false
                            next
                        } else {
                            selectedSongIds + songId
                        }
                    },
                    onEnterSelection = { songId ->
                        isSelectionMode = true
                        selectedSongIds = setOf(songId)
                    },
                    onDelete = { songId, deleteFile ->
                        DownloadManager.deleteDownloaded(context, songId, deleteFile)
                    }
                )
                1 -> DownloadingListSection(
                    activeTasks = activeTasks,
                    isLandscape = isLandscape,
                    onPause = { DownloadManager.pause(context, it) },
                    onResume = { DownloadManager.resume(context, it) },
                    onCancel = { DownloadManager.cancel(context, it) },
                    onPauseAll = { DownloadManager.pauseAll(context) },
                    onResumeAll = { DownloadManager.resumeAll(context) }
                )
            }
        }
    }

    if (showBatchDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = { Text("批量删除已下载歌曲") },
            text = { Text("确定要删除选中的 ${selectedSongIds.size} 首歌曲吗？对应音频文件将从本地存储彻底移除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        DownloadManager.deleteDownloadedBatch(context, selectedSongIds, deleteFiles = true)
                        showBatchDeleteConfirm = false
                        exitSelectionMode()
                    }
                ) {
                    Text("删除 (${selectedSongIds.size})", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showMoveToGroupDialog) {
        var newGroupNameInput by remember { mutableStateOf("") }
        var isCreatingNew by remember { mutableStateOf(false) }

        val existingGroups: List<String> = remember(customGroupNames, completedTasks) {
            val fromTasks = completedTasks.mapNotNull { it.customGroup?.takeIf { g -> g.isNotBlank() } }
            (customGroupNames + fromTasks).toSet().toList().sorted()
        }

        AlertDialog(
            onDismissRequest = { showMoveToGroupDialog = false },
            title = { Text("移至分组 (${selectedSongIds.size} 首)") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "将选中的歌曲移入指定分组，对应音频文件将同步移动至对应文件夹。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))

                    if (isCreatingNew) {
                        OutlinedTextField(
                            value = newGroupNameInput,
                            onValueChange = { newGroupNameInput = it },
                            label = { Text("新分组名称") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            isCreatingNew = true
                                        }
                                        .padding(vertical = 10.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Outlined.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Text("+ 新建分组", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            }

                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
                                            scope.launch {
                                                DownloadManager.moveSongsToGroup(context, selectedSongIds, null)
                                                Toast.makeText(context, "已移出分组至根目录", Toast.LENGTH_SHORT).show()
                                                showMoveToGroupDialog = false
                                                exitSelectionMode()
                                            }
                                        }
                                        .padding(vertical = 10.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Outlined.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(8.dp))
                                    Text("根目录 (无分组)", style = MaterialTheme.typography.bodyMedium)
                                }
                            }

                            items(existingGroups, key = { it }) { groupName ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
                                            scope.launch {
                                                DownloadManager.moveSongsToGroup(context, selectedSongIds, groupName)
                                                Toast.makeText(context, "已移至分组「$groupName」", Toast.LENGTH_SHORT).show()
                                                showMoveToGroupDialog = false
                                                exitSelectionMode()
                                            }
                                        }
                                        .padding(vertical = 10.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Outlined.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Text(groupName, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (isCreatingNew) {
                    TextButton(
                        onClick = {
                            val name = newGroupNameInput.trim()
                            if (name.isNotBlank()) {
                                customGroupsRaw = (customGroupNames + name).joinToString(",")
                                val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
                                scope.launch {
                                    DownloadManager.moveSongsToGroup(context, selectedSongIds, name)
                                    Toast.makeText(context, "已创建分组「$name」并移入歌曲", Toast.LENGTH_SHORT).show()
                                    showMoveToGroupDialog = false
                                    exitSelectionMode()
                                }
                            }
                        },
                        enabled = newGroupNameInput.trim().isNotBlank()
                    ) {
                        Text("创建并移入")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (isCreatingNew) {
                            isCreatingNew = false
                        } else {
                            showMoveToGroupDialog = false
                        }
                    }
                ) {
                    Text(if (isCreatingNew) "返回列表" else "取消")
                }
            }
        )
    }

    if (showSettingsSheet) {
        DownloadSettingsBottomSheet(
            isLandscape = isLandscape,
            onDismiss = { showSettingsSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadedListSection(
    completedTasks: List<DownloadTask>,
    isLandscape: Boolean,
    isSelectionMode: Boolean,
    selectedSongIds: Set<Long>,
    onToggleSelect: (Long) -> Unit,
    onEnterSelection: (Long) -> Unit,
    onDelete: (Long, Boolean) -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val mediaController = LocalPlayerController.current.controller
    var pendingDeleteTask by remember { mutableStateOf<DownloadTask?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()

    val totalBytes = remember(completedTasks) {
        completedTasks.sumOf { it.totalBytes }
    }

    if (completedTasks.isEmpty()) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                coroutineScope.launch {
                    isRefreshing = true
                    try {
                        val removed = DownloadStore.verifyAndSyncLocalFiles(context)
                        if (removed > 0) {
                            Toast.makeText(context, "已清理 $removed 首失效歌曲", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "本地文件校验完毕", Toast.LENGTH_SHORT).show()
                        }
                    } finally {
                        isRefreshing = false
                    }
                }
            },
            state = pullToRefreshState,
            indicator = {
                PullToRefreshDefaults.Indicator(
                    modifier = Modifier.align(Alignment.TopCenter),
                    isRefreshing = isRefreshing,
                    state = pullToRefreshState
                )
            },
            modifier = Modifier.fillMaxSize()
        ) {
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
                        text = "下滑可校验本地文件，在专辑或播放页中点击下载",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
        return
    }

    val playAllAction: () -> Unit = {
        val songs = completedTasks.map { task ->
            Song(
                id = task.songId,
                name = task.title,
                ar = listOf(Artist(id = task.artistId, name = task.artist)),
                al = com.rcmiku.ncmapi.model.SongAlbum(id = task.albumId, name = task.album, picUrl = task.coverUrl),
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

    val playGroupAction: (List<DownloadTask>, String) -> Unit = { list, groupTitle ->
        if (list.isNotEmpty()) {
            val songs = list.map { task ->
                Song(
                    id = task.songId,
                    name = task.title,
                    ar = listOf(Artist(id = task.artistId, name = task.artist)),
                    al = com.rcmiku.ncmapi.model.SongAlbum(id = task.albumId, name = task.album, picUrl = task.coverUrl),
                    dt = task.durationMs
                )
            }
            mediaController?.setPlaylist(
                songs,
                sourceId = 20261L,
                sourceName = groupTitle,
                sourceType = "download"
            )
            mediaController?.playMediaAtId(list.first().songId)
        }
    }

    val onItemClick: (DownloadTask) -> Unit = { task ->
        if (isSelectionMode) {
            onToggleSelect(task.songId)
        } else {
            val songs = completedTasks.map { t ->
                Song(
                    id = t.songId,
                    name = t.title,
                    ar = listOf(Artist(id = t.artistId, name = t.artist)),
                    al = com.rcmiku.ncmapi.model.SongAlbum(id = t.albumId, name = t.album, picUrl = t.coverUrl),
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
        }
    }

    // 分组模型与计算：只有在专辑页下载整张专辑的歌曲（isAlbumFolder == true）才进入专辑分组！
    val albumGroups = remember(completedTasks) {
        completedTasks.filter { it.isAlbumFolder }.groupBy { task ->
            task.groupFolder?.takeIf { it.isNotBlank() } ?: task.album.ifBlank { "未知专辑" }
        }.map { (name, songs) ->
            val first = songs.first()
            val groupBytes = songs.sumOf { it.totalBytes }
            val artistStr = first.artist
            val subtitle = buildString {
                if (artistStr.isNotBlank()) append("$artistStr · ")
                append("${songs.size} 首歌曲")
                if (groupBytes > 0) append(" · ${formatBytes(groupBytes)}")
            }
            DownloadGroupItem(
                key = name,
                title = first.album.ifBlank { name },
                subtitle = subtitle,
                coverUrl = songs.firstOrNull { it.coverUrl.isNotBlank() }?.coverUrl.orEmpty(),
                tasks = songs
            )
        }.sortedByDescending { it.tasks.size }
    }

    var customGroupsRaw by rememberPreference(downloadCustomGroupsKey, "")
    val customGroupNames: Set<String> = remember(customGroupsRaw) {
        customGroupsRaw.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
    }
    val allCustomGroupNames = remember(customGroupNames, completedTasks) {
        val fromTasks = completedTasks.mapNotNull { it.customGroup?.takeIf { g -> g.isNotBlank() } }.toSet()
        (customGroupNames + fromTasks).toList().sorted()
    }
    val customGroups = remember(allCustomGroupNames, completedTasks) {
        allCustomGroupNames.map { name ->
            val songs = completedTasks.filter { it.customGroup == name }
            val groupBytes = songs.sumOf { it.totalBytes }
            val subtitle = buildString {
                append("${songs.size} 首歌曲")
                if (groupBytes > 0) append(" · ${formatBytes(groupBytes)}")
            }
            DownloadGroupItem(
                key = name,
                title = name,
                subtitle = subtitle,
                coverUrl = songs.firstOrNull { it.coverUrl.isNotBlank() }?.coverUrl.orEmpty(),
                tasks = songs
            )
        }
    }

    var groupFilterIndex by rememberSaveable { mutableIntStateOf(0) } // 0: 全部, 1: 专辑, 2: 分组
    var expandedGroupKeys by remember { mutableStateOf(setOf<String>()) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var renameTargetGroup by remember { mutableStateOf<String?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var deleteTargetGroup by remember { mutableStateOf<String?>(null) }
    var newGroupNameInput by remember { mutableStateOf("") }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            coroutineScope.launch {
                isRefreshing = true
                try {
                    val removed = DownloadStore.verifyAndSyncLocalFiles(context)
                    if (removed > 0) {
                        Toast.makeText(context, "已清理 $removed 首失效歌曲", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "已校验，本地文件全部正常", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    isRefreshing = false
                }
            }
        },
        state = pullToRefreshState,
        indicator = {
            PullToRefreshDefaults.Indicator(
                modifier = Modifier.align(Alignment.TopCenter),
                isRefreshing = isRefreshing,
                state = pullToRefreshState
            )
        },
        modifier = Modifier.fillMaxSize()
    ) {
        if (isLandscape) {
            // 横屏双列网格布局
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
            ) {
                item(span = { GridItemSpan(2) }) {
                    Column {
                        DownloadedHeaderCard(
                            taskCount = completedTasks.size,
                            totalBytes = totalBytes,
                            onPlayAll = playAllAction
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = groupFilterIndex == 0,
                                onClick = { groupFilterIndex = 0 },
                                label = { Text("全部 (${completedTasks.size})") }
                            )
                            FilterChip(
                                selected = groupFilterIndex == 1,
                                onClick = { groupFilterIndex = 1 },
                                label = { Text("专辑 (${albumGroups.size})") }
                            )
                            FilterChip(
                                selected = groupFilterIndex == 2,
                                onClick = { groupFilterIndex = 2 },
                                label = { Text("分组 (${customGroups.size})") }
                            )
                        }
                    }
                }

                when (groupFilterIndex) {
                    0 -> {
                        items(completedTasks, key = { it.songId }) { task ->
                            DownloadedSongItem(
                                task = task,
                                isSelectionMode = isSelectionMode,
                                isSelected = task.songId in selectedSongIds,
                                onClick = { onItemClick(task) },
                                onLongClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onEnterSelection(task.songId)
                                },
                                onDeleteClick = { pendingDeleteTask = task },
                                onShareClick = { shareDownloadedFile(context, task) }
                            )
                        }
                    }
                    1 -> {
                        items(albumGroups, key = { it.key }, span = { GridItemSpan(2) }) { group ->
                            DownloadedGroupCard(
                                group = group,
                                isExpanded = group.key in expandedGroupKeys,
                                onToggleExpand = {
                                    expandedGroupKeys = if (group.key in expandedGroupKeys) {
                                        expandedGroupKeys - group.key
                                    } else {
                                        expandedGroupKeys + group.key
                                    }
                                },
                                onPlayAll = { playGroupAction(group.tasks, group.title) },
                                isSelectionMode = isSelectionMode,
                                selectedSongIds = selectedSongIds,
                                onItemClick = onItemClick,
                                onLongClick = { songId ->
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onEnterSelection(songId)
                                },
                                onDeleteClick = { pendingDeleteTask = it },
                                onShareClick = { shareDownloadedFile(context, it) }
                            )
                        }
                    }
                    2 -> {
                        item(span = { GridItemSpan(2) }) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                    .clickable { showCreateGroupDialog = true }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "新建分组 (同步本地文件夹)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        if (customGroups.isEmpty()) {
                            item(span = { GridItemSpan(2) }) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "暂无自定义分组，可点击上方新建分组，或多选歌曲移入分组",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            items(customGroups, key = { it.key }, span = { GridItemSpan(2) }) { group ->
                                DownloadedGroupCard(
                                    group = group,
                                    isExpanded = group.key in expandedGroupKeys,
                                    onToggleExpand = {
                                        expandedGroupKeys = if (group.key in expandedGroupKeys) {
                                            expandedGroupKeys - group.key
                                        } else {
                                            expandedGroupKeys + group.key
                                        }
                                    },
                                    onPlayAll = { playGroupAction(group.tasks, group.title) },
                                    isSelectionMode = isSelectionMode,
                                    selectedSongIds = selectedSongIds,
                                    onItemClick = onItemClick,
                                    onLongClick = { songId ->
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onEnterSelection(songId)
                                    },
                                    onDeleteClick = { pendingDeleteTask = it },
                                    onShareClick = { shareDownloadedFile(context, it) },
                                    onRenameGroup = {
                                        renameTargetGroup = group.title
                                        renameInput = group.title
                                    },
                                    onDeleteGroup = {
                                        deleteTargetGroup = group.title
                                    }
                                )
                            }
                        }
                    }
                }

                item(span = { GridItemSpan(2) }) {
                    Spacer(Modifier.height(MiniPlayerHeight + 16.dp))
                }
            }
        } else {
            // 竖屏单列流式布局
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    Column {
                        DownloadedHeaderCard(
                            taskCount = completedTasks.size,
                            totalBytes = totalBytes,
                            onPlayAll = playAllAction
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = groupFilterIndex == 0,
                                onClick = { groupFilterIndex = 0 },
                                label = { Text("全部 (${completedTasks.size})") }
                            )
                            FilterChip(
                                selected = groupFilterIndex == 1,
                                onClick = { groupFilterIndex = 1 },
                                label = { Text("专辑 (${albumGroups.size})") }
                            )
                            FilterChip(
                                selected = groupFilterIndex == 2,
                                onClick = { groupFilterIndex = 2 },
                                label = { Text("分组 (${customGroups.size})") }
                            )
                        }
                    }
                }

                when (groupFilterIndex) {
                    0 -> {
                        items(completedTasks, key = { it.songId }) { task ->
                            DownloadedSongItem(
                                task = task,
                                isSelectionMode = isSelectionMode,
                                isSelected = task.songId in selectedSongIds,
                                onClick = { onItemClick(task) },
                                onLongClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onEnterSelection(task.songId)
                                },
                                onDeleteClick = { pendingDeleteTask = task },
                                onShareClick = { shareDownloadedFile(context, task) }
                            )
                        }
                    }
                    1 -> {
                        items(albumGroups, key = { it.key }) { group ->
                            DownloadedGroupCard(
                                group = group,
                                isExpanded = group.key in expandedGroupKeys,
                                onToggleExpand = {
                                    expandedGroupKeys = if (group.key in expandedGroupKeys) {
                                        expandedGroupKeys - group.key
                                    } else {
                                        expandedGroupKeys + group.key
                                    }
                                },
                                onPlayAll = { playGroupAction(group.tasks, group.title) },
                                isSelectionMode = isSelectionMode,
                                selectedSongIds = selectedSongIds,
                                onItemClick = onItemClick,
                                onLongClick = { songId ->
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onEnterSelection(songId)
                                },
                                onDeleteClick = { pendingDeleteTask = it },
                                onShareClick = { shareDownloadedFile(context, it) }
                            )
                        }
                    }
                    2 -> {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                    .clickable { showCreateGroupDialog = true }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "新建分组 (同步本地文件夹)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        if (customGroups.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "暂无自定义分组，可点击上方新建分组，或多选歌曲移入分组",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            items(customGroups, key = { it.key }) { group ->
                                DownloadedGroupCard(
                                    group = group,
                                    isExpanded = group.key in expandedGroupKeys,
                                    onToggleExpand = {
                                        expandedGroupKeys = if (group.key in expandedGroupKeys) {
                                            expandedGroupKeys - group.key
                                        } else {
                                            expandedGroupKeys + group.key
                                        }
                                    },
                                    onPlayAll = { playGroupAction(group.tasks, group.title) },
                                    isSelectionMode = isSelectionMode,
                                    selectedSongIds = selectedSongIds,
                                    onItemClick = onItemClick,
                                    onLongClick = { songId ->
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onEnterSelection(songId)
                                    },
                                    onDeleteClick = { pendingDeleteTask = it },
                                    onShareClick = { shareDownloadedFile(context, it) },
                                    onRenameGroup = {
                                        renameTargetGroup = group.title
                                        renameInput = group.title
                                    },
                                    onDeleteGroup = {
                                        deleteTargetGroup = group.title
                                    }
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(MiniPlayerHeight + 16.dp))
                }
            }
        }
    }

    pendingDeleteTask?.let { task ->
        AlertDialog(
            onDismissRequest = { pendingDeleteTask = null },
            title = { Text("删除已下载歌曲") },
            text = { Text("确定要删除「${task.title}」吗？音频文件将从本地存储彻底移除。") },
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

    if (showCreateGroupDialog) {
        AlertDialog(
            onDismissRequest = { showCreateGroupDialog = false },
            title = { Text("新建分组") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "分组将自动同步在本地下载目录创建对应文件夹。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newGroupNameInput,
                        onValueChange = { newGroupNameInput = it },
                        label = { Text("分组名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = newGroupNameInput.trim()
                        if (name.isNotBlank()) {
                            customGroupsRaw = (customGroupNames + name).joinToString(",")
                            coroutineScope.launch {
                                DownloadManager.moveSongsToGroup(context, emptySet(), name)
                                Toast.makeText(context, "分组「$name」创建成功", Toast.LENGTH_SHORT).show()
                                showCreateGroupDialog = false
                                newGroupNameInput = ""
                            }
                        }
                    },
                    enabled = newGroupNameInput.trim().isNotBlank()
                ) {
                    Text("创建")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateGroupDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    renameTargetGroup?.let { oldName ->
        AlertDialog(
            onDismissRequest = { renameTargetGroup = null },
            title = { Text("重命名分组") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "将同步重命名本地文件夹及该分组下的所有歌曲。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = renameInput,
                        onValueChange = { renameInput = it },
                        label = { Text("新分组名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newName = renameInput.trim()
                        if (newName.isNotBlank() && newName != oldName) {
                            customGroupsRaw = (customGroupNames - oldName + newName).joinToString(",")
                            coroutineScope.launch {
                                DownloadManager.renameCustomGroup(context, oldName, newName)
                                Toast.makeText(context, "分组已重命名为「$newName」", Toast.LENGTH_SHORT).show()
                                renameTargetGroup = null
                            }
                        }
                    },
                    enabled = renameInput.trim().isNotBlank() && renameInput.trim() != oldName
                ) {
                    Text("重命名")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTargetGroup = null }) {
                    Text("取消")
                }
            }
        )
    }

    deleteTargetGroup?.let { groupName ->
        AlertDialog(
            onDismissRequest = { deleteTargetGroup = null },
            title = { Text("解散分组「$groupName」") },
            text = { Text("解散后，该分组下的所有歌曲将移回根目录，本地对应空文件夹将被清理。不会删除歌曲文件。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        customGroupsRaw = (customGroupNames - groupName).joinToString(",")
                        coroutineScope.launch {
                            DownloadManager.deleteCustomGroup(context, groupName)
                            Toast.makeText(context, "分组「$groupName」已解散", Toast.LENGTH_SHORT).show()
                            deleteTargetGroup = null
                        }
                    }
                ) {
                    Text("解散", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTargetGroup = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun DownloadedHeaderCard(
    taskCount: Int,
    totalBytes: Long,
    onPlayAll: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "已下载 $taskCount 首歌曲",
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
            onClick = onPlayAll,
            modifier = Modifier.height(38.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text("全部播放")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DownloadedSongItem(
    task: DownloadTask,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onShareClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
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
                val badgeText = task.actualQualityText ?: when (task.quality) {
                    "hires" -> "Hi-Res"
                    "lossless" -> "FLAC"
                    "exhigh" -> "320K"
                    else -> "128K"
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
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

        if (isSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = null,
                modifier = Modifier.padding(start = 8.dp)
            )
        } else {
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
                        text = "分享音频文件",
                        icon = Icons.Outlined.Share,
                        onClick = {
                            menuExpanded = false
                            onShareClick()
                        }
                    )
                    JussiMenuItem(
                        text = "删除已下载",
                        icon = Icons.Outlined.Delete,
                        destructive = true,
                        onClick = {
                            menuExpanded = false
                            onDeleteClick()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadingListSection(
    activeTasks: List<DownloadTask>,
    isLandscape: Boolean,
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

    if (isLandscape) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
        ) {
            item(span = { GridItemSpan(2) }) {
                DownloadingHeaderRow(activeTasks.size, onPauseAll, onResumeAll)
            }

            items(activeTasks, key = { it.songId }) { task ->
                DownloadingSongItem(
                    task = task,
                    onPause = { onPause(task.songId) },
                    onResume = { onResume(task.songId) },
                    onCancel = { onCancel(task.songId) }
                )
            }

            item(span = { GridItemSpan(2) }) {
                Spacer(Modifier.height(MiniPlayerHeight + 16.dp))
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                DownloadingHeaderRow(activeTasks.size, onPauseAll, onResumeAll)
            }

            items(activeTasks, key = { it.songId }) { task ->
                DownloadingSongItem(
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
}

@Composable
private fun DownloadingHeaderRow(
    count: Int,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "下载队列 ($count)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Row {
            OutlinedButton(onClick = onPauseAll) {
                Text("全部暂停")
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = onResumeAll) {
                Text("全部开始")
            }
        }
    }
}

@Composable
private fun DownloadingSongItem(
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
            val statusDesc = when (task.status) {
                DownloadStatus.RESOLVING -> "正在解析直链…"
                DownloadStatus.DOWNLOADING -> {
                    val downloadedStr = formatBytes(task.downloadedBytes)
                    val totalStr = if (task.totalBytes > 0) formatBytes(task.totalBytes) else "未知"
                    val speed = if (task.speedText.isNotBlank()) " · ${task.speedText}" else ""
                    "$downloadedStr / $totalStr$speed"
                }
                DownloadStatus.PAUSED -> "已暂停"
                DownloadStatus.FAILED -> task.errorMsg ?: "下载失败"
                else -> "等待中…"
            }
            Text(
                text = statusDesc,
                style = MaterialTheme.typography.bodySmall,
                color = if (task.status == DownloadStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(4.dp))
            if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.PAUSED) {
                LinearProgressIndicator(
                    progress = { task.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                )
            } else if (task.status == DownloadStatus.RESOLVING || task.status == DownloadStatus.PENDING) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            when (task.status) {
                DownloadStatus.DOWNLOADING, DownloadStatus.RESOLVING -> {
                    IconButton(onClick = onPause) {
                        Icon(Icons.Outlined.Pause, contentDescription = "暂停")
                    }
                }
                DownloadStatus.PAUSED, DownloadStatus.FAILED -> {
                    IconButton(onClick = onResume) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = "继续")
                    }
                }
                else -> Unit
            }
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = "取消任务")
            }
        }
    }
}

/**
 * 下载偏好设置：横屏下弹 Dialog 双栏展示；竖屏下使用 ModalBottomSheet。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadSettingsBottomSheet(
    isLandscape: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    var downloadQuality by rememberPreference(downloadQualityKey, "ask")
    var downloadWifiOnly by rememberPreference(downloadWifiOnlyKey, false)
    var downloadLrc by rememberPreference(downloadLrcKey, true)
    var downloadTlyric by rememberPreference(downloadTlyricKey, true)
    var downloadCover by rememberPreference(downloadCoverKey, true)
    var downloadCustomDirUri by rememberPreference(downloadCustomDirUriKey, "")
    var downloadCustomDirName by rememberPreference(downloadCustomDirNameKey, "")

    var showQualityPicker by remember { mutableStateOf(false) }

    val dirPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, flags)
            }
            val docFile = DocumentFile.fromTreeUri(context, uri)
            val name = docFile?.name ?: uri.lastPathSegment ?: "自定义目录"
            downloadCustomDirUri = uri.toString()
            downloadCustomDirName = name
            Toast.makeText(context, "存储目录已更改为: $name", Toast.LENGTH_SHORT).show()
        }
    }

    if (isLandscape) {
        // 横屏弹窗：双栏并排布局，高度自适应滚动
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 700.dp)
                    .fillMaxWidth(0.85f)
                    .padding(vertical = 16.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "下载偏好设置",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = onDismiss) {
                            Text("完成")
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 左栏：音质与目录
                        Column(modifier = Modifier.weight(1f)) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth().clickable { showQualityPicker = true }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("默认音质", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = when (downloadQuality) {
                                                "standard" -> "标准 (128 kbps)"
                                                "exhigh" -> "极高 (320 kbps)"
                                                "lossless" -> "无损 (FLAC)"
                                                "hires" -> "Hi-Res (母带)"
                                                else -> "每次询问"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    TextButton(onClick = { showQualityPicker = true }) {
                                        Text("更改")
                                    }
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                                    Text("存储目录", style = MaterialTheme.typography.titleSmall)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = if (downloadCustomDirName.isNotBlank()) downloadCustomDirName else "Music/Jussichords/歌手 - 专辑/",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Row {
                                        FilledTonalButton(onClick = { dirPickerLauncher.launch(null) }) {
                                            Text("更改")
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        OutlinedButton(onClick = { DownloadManager.openDownloadFolder(context) }) {
                                            Text("查看")
                                        }
                                    }
                                }
                            }
                        }

                        // 右栏：三个开关
                        Column(modifier = Modifier.weight(1f)) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("仅在 Wi-Fi 下载", style = MaterialTheme.typography.bodyMedium)
                                        Switch(checked = downloadWifiOnly, onCheckedChange = { downloadWifiOnly = it })
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("内嵌歌词", style = MaterialTheme.typography.bodyMedium)
                                        Switch(checked = downloadLrc, onCheckedChange = { downloadLrc = it })
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("下载歌词翻译", style = MaterialTheme.typography.bodyMedium)
                                        Switch(
                                            checked = downloadTlyric,
                                            onCheckedChange = { downloadTlyric = it },
                                            enabled = downloadLrc
                                        )
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("内嵌高清封面", style = MaterialTheme.typography.bodyMedium)
                                        Switch(checked = downloadCover, onCheckedChange = { downloadCover = it })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        // 竖屏：ModalBottomSheet
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "下载偏好设置",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = onDismiss) {
                        Text("完成")
                    }
                }

                Spacer(Modifier.height(16.dp))

                // 1. 默认音质
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().clickable { showQualityPicker = true }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("默认下载音质", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = when (downloadQuality) {
                                    "standard" -> "标准 (128 kbps)"
                                    "exhigh" -> "极高 (320 kbps)"
                                    "lossless" -> "无损 (FLAC)"
                                    "hires" -> "Hi-Res (母带)"
                                    else -> "每次下载时询问"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        TextButton(onClick = { showQualityPicker = true }) {
                            Text("更改")
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // 2. 存储目录与快速跳转
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("存储目录", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (downloadCustomDirName.isNotBlank()) downloadCustomDirName else "系统默认 (Music/Jussichords/歌手 - 专辑/)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Row {
                            FilledTonalButton(onClick = { dirPickerLauncher.launch(null) }) {
                                Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("更改目录")
                            }
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(onClick = { DownloadManager.openDownloadFolder(context) }) {
                                Text("打开目录")
                            }
                            if (downloadCustomDirUri.isNotBlank()) {
                                Spacer(Modifier.width(8.dp))
                                TextButton(onClick = {
                                    downloadCustomDirUri = ""
                                    downloadCustomDirName = ""
                                    Toast.makeText(context, "已恢复为默认存储目录", Toast.LENGTH_SHORT).show()
                                }) {
                                    Text("恢复默认")
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // 3. 网络与内嵌标签开关
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("仅在 Wi-Fi 下下载", style = MaterialTheme.typography.bodyLarge)
                                Text("移动网络时不自动启动后台下载任务", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = downloadWifiOnly, onCheckedChange = { downloadWifiOnly = it })
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("内嵌歌词到音频文件", style = MaterialTheme.typography.bodyLarge)
                                Text("写入 ID3v2 USLT / Vorbis 歌词标签，离线即时滚动", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = downloadLrc, onCheckedChange = { downloadLrc = it })
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("下载歌词翻译", style = MaterialTheme.typography.bodyLarge)
                                Text("若歌曲含有译文，合并写入双语歌词标签", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = downloadTlyric,
                                onCheckedChange = { downloadTlyric = it },
                                enabled = downloadLrc
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("内嵌高清封面到音频文件", style = MaterialTheme.typography.bodyLarge)
                                Text("写入 APIC / PICTURE 封面，车载与本地播放器完美显示", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = downloadCover, onCheckedChange = { downloadCover = it })
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showQualityPicker) {
        DownloadQualityDialog(
            currentKey = downloadQuality,
            includeAskOption = true,
            onDismiss = { showQualityPicker = false },
            onQualitySelected = {
                downloadQuality = it
                showQualityPicker = false
            }
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
        val uri: Uri? = if (!task.filePath.isNullOrBlank()) {
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
        } else {
            null
        }

        if (uri == null) {
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

data class DownloadGroupItem(
    val key: String,
    val title: String,
    val subtitle: String,
    val coverUrl: String,
    val tasks: List<DownloadTask>
)

@Composable
private fun DownloadedGroupCard(
    group: DownloadGroupItem,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onPlayAll: () -> Unit,
    isSelectionMode: Boolean,
    selectedSongIds: Set<Long>,
    onItemClick: (DownloadTask) -> Unit,
    onLongClick: (Long) -> Unit,
    onDeleteClick: (DownloadTask) -> Unit,
    onShareClick: (DownloadTask) -> Unit,
    onRenameGroup: (() -> Unit)? = null,
    onDeleteGroup: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (group.coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = group.coverUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.LibraryMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = group.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onPlayAll) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "播放全组",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                if (onRenameGroup != null || onDeleteGroup != null) {
                    var groupMenuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { groupMenuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "分组操作")
                        }
                        JussiDropdownMenu(
                            expanded = groupMenuExpanded,
                            onDismissRequest = { groupMenuExpanded = false }
                        ) {
                            if (onRenameGroup != null) {
                                JussiMenuItem(
                                    text = "重命名分组",
                                    icon = Icons.Outlined.Edit,
                                    onClick = {
                                        groupMenuExpanded = false
                                        onRenameGroup()
                                    }
                                )
                            }
                            if (onDeleteGroup != null) {
                                JussiMenuItem(
                                    text = "删除/解散分组",
                                    icon = Icons.Outlined.Delete,
                                    destructive = true,
                                    onClick = {
                                        groupMenuExpanded = false
                                        onDeleteGroup()
                                    }
                                )
                            }
                        }
                    }
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "折叠" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isExpanded) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
                group.tasks.forEach { songTask ->
                    DownloadedSongItem(
                        task = songTask,
                        isSelectionMode = isSelectionMode,
                        isSelected = songTask.songId in selectedSongIds,
                        onClick = { onItemClick(songTask) },
                        onLongClick = { onLongClick(songTask.songId) },
                        onDeleteClick = { onDeleteClick(songTask) },
                        onShareClick = { onShareClick(songTask) }
                    )
                }
            }
        }
    }
}
