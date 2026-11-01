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
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
import com.jussicodes.music.ui.components.AudioQualityProbeDialog
import com.jussicodes.music.ui.components.ProbeDialogMode
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.material3.CircularProgressIndicator
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
import com.jussicodes.music.download.DailyDownloadQuotaManager
import com.jussicodes.music.extensions.playMediaAtId
import com.jussicodes.music.extensions.setPlaylist
import com.jussicodes.music.ui.components.DownloadQualityDialog
import com.jussicodes.music.ui.components.JussiDropdownMenu
import com.jussicodes.music.ui.components.JussiMenuItem
import com.jussicodes.music.utils.rememberPreference
import com.rcmiku.ncmapi.model.Artist
import com.rcmiku.ncmapi.model.Song
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.outlined.FolderOpen
import com.jussicodes.music.data.external.ExternalPlaylist
import com.jussicodes.music.data.external.ExternalPlaylistStore
import com.jussicodes.music.data.external.ExternalSong
import com.jussicodes.music.utils.makeTimeString
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
    var downloadQuality by rememberPreference(downloadQualityKey, "ask")
    var probeTask by remember { mutableStateOf<DownloadTask?>(null) }
    var probeDialogMode by remember { mutableStateOf(ProbeDialogMode.UPGRADE) }
    var showBenchmarkProbe by remember { mutableStateOf(false) }
    var customGroupsRaw by rememberPreference(downloadCustomGroupsKey, "")
    val customGroupNames: Set<String> = remember(customGroupsRaw) {
        customGroupsRaw.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
    }

    // 多选模式状态
    var isSelectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedSongIds by remember { mutableStateOf(setOf<Long>()) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    var showMoveToGroupDialog by remember { mutableStateOf(false) }
    var showBatchUpgradeDialog by remember { mutableStateOf(false) }

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
                                    showBatchUpgradeDialog = true
                                }
                            },
                            enabled = selectedSongIds.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.HighQuality,
                                contentDescription = "升级音质",
                                tint = if (selectedSongIds.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
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
                    },
                    onProbeQuality = { task ->
                        probeTask = task
                        probeDialogMode = ProbeDialogMode.UPGRADE
                    }
                )
                1 -> DownloadingListSection(
                    activeTasks = activeTasks,
                    isLandscape = isLandscape,
                    onPause = { DownloadManager.pause(context, it) },
                    onResume = { DownloadManager.resume(context, it) },
                    onCancel = { DownloadManager.cancel(context, it) },
                    onPauseAll = { DownloadManager.pauseAll(context) },
                    onResumeAll = { DownloadManager.resumeAll(context) },
                    onRetryWithProbe = { task ->
                        probeTask = task
                        probeDialogMode = ProbeDialogMode.RETRY_FAILED
                    }
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

    if (showBatchUpgradeDialog) {
        DownloadQualityDialog(
            currentKey = downloadQuality,
            title = "批量升级 ${selectedSongIds.size} 首歌曲音质",
            includeAskOption = false,
            onDismiss = { showBatchUpgradeDialog = false },
            onQualitySelected = { targetQuality ->
                showBatchUpgradeDialog = false
                val count = selectedSongIds.size
                selectedSongIds.forEach { songId ->
                    DownloadManager.redownload(
                        context = context,
                        songId = songId,
                        newQuality = targetQuality
                    )
                }
                Toast.makeText(
                    context,
                    "已将 $count 首歌曲加入重新下载队列 (${targetQuality})",
                    Toast.LENGTH_SHORT
                ).show()
                exitSelectionMode()
            }
        )
    }

    if (showSettingsSheet) {
        DownloadSettingsBottomSheet(
            isLandscape = isLandscape,
            onDismiss = { showSettingsSheet = false },
            onOpenBenchmarkProbe = {
                showSettingsSheet = false
                showBenchmarkProbe = true
            }
        )
    }

    if (showBenchmarkProbe) {
        AudioQualityProbeDialog(
            mode = ProbeDialogMode.BENCHMARK,
            songId = 2644774041L,
            title = "音源下载能力体检",
            subtitle = "基准曲目 · ID: 2644774041",
            durationMs = 0L,
            currentQualityKey = downloadQuality,
            onDismiss = { showBenchmarkProbe = false },
            onConfirm = { selectedLevel, _, _ ->
                downloadQuality = selectedLevel.value
                showBenchmarkProbe = false
                Toast.makeText(context, "已将默认下载音质设置为: ${selectedLevel.value}", Toast.LENGTH_SHORT).show()
            }
        )
    }

    probeTask?.let { task ->
        AudioQualityProbeDialog(
            mode = probeDialogMode,
            songId = task.songId,
            title = task.title,
            subtitle = task.artist,
            durationMs = task.durationMs,
            currentQualityKey = if (probeDialogMode == ProbeDialogMode.UPGRADE) {
                task.actualQualityText ?: task.quality
            } else {
                task.quality
            },
            onDismiss = { probeTask = null },
            onConfirm = { selectedLevel, preloadUrl, preloadSize ->
                val levelKey = selectedLevel.value
                if (probeDialogMode == ProbeDialogMode.UPGRADE) {
                    DownloadManager.redownload(
                        context = context,
                        songId = task.songId,
                        newQuality = levelKey,
                        preloadUrl = preloadUrl,
                        preloadSize = preloadSize
                    )
                    Toast.makeText(context, "已开始重新下载: ${task.title} ($levelKey)", Toast.LENGTH_SHORT).show()
                } else if (probeDialogMode == ProbeDialogMode.RETRY_FAILED) {
                    DownloadManager.retryWithQuality(
                        context = context,
                        songId = task.songId,
                        newQuality = levelKey,
                        preloadUrl = preloadUrl,
                        preloadSize = preloadSize
                    )
                    Toast.makeText(context, "已使用 $levelKey 重试: ${task.title}", Toast.LENGTH_SHORT).show()
                }
                probeTask = null
            }
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
    onDelete: (Long, Boolean) -> Unit,
    onProbeQuality: (DownloadTask) -> Unit = {}
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

    val externalPlaylists by ExternalPlaylistStore.playlists.collectAsState()
    var isImportingExternal by remember { mutableStateOf(false) }
    var groupFilterIndex by rememberSaveable { mutableIntStateOf(0) } // 0: 全部, 1: 专辑, 2: 分组, 3: 外部歌单
    var expandedExternalPlaylistIds by remember { mutableStateOf(setOf<String>()) }
    var renameTargetExternalPlaylist by remember { mutableStateOf<ExternalPlaylist?>(null) }
    var renameExternalPlaylistInput by remember { mutableStateOf("") }
    var deleteTargetExternalPlaylist by remember { mutableStateOf<ExternalPlaylist?>(null) }

    val externalFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            coroutineScope.launch {
                isImportingExternal = true
                try {
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            treeUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                    val doc = DocumentFile.fromTreeUri(context, treeUri)
                    val folderName = doc?.name?.ifBlank { "外部歌单" } ?: "外部歌单"
                    val pl = ExternalPlaylistStore.importFolder(context, treeUri, folderName)
                    if (pl.songs.isNotEmpty()) {
                        Toast.makeText(context, "成功导入「$folderName」，共识别到 ${pl.songs.size} 首歌曲", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "导入「$folderName」成功，但未识别到音频文件", Toast.LENGTH_LONG).show()
                    }
                    groupFilterIndex = 3
                    expandedExternalPlaylistIds = expandedExternalPlaylistIds + pl.id
                } catch (e: Exception) {
                    Toast.makeText(context, "导入失败：${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    isImportingExternal = false
                }
            }
        }
    }

    if (completedTasks.isEmpty() && externalPlaylists.isEmpty() && groupFilterIndex != 3) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                coroutineScope.launch {
                    isRefreshing = true
                    try {
                        val removed = DownloadStore.verifyAndSyncLocalFiles(context)
                        val (extAdded, extRemoved) = ExternalPlaylistStore.refreshAllPlaylists(context)
                        kotlinx.coroutines.delay(400)
                        val msg = when {
                            extAdded > 0 || extRemoved > 0 || removed > 0 -> {
                                val parts = mutableListOf<String>()
                                if (extAdded > 0) parts.add("外部新增 $extAdded 首")
                                if (extRemoved > 0) parts.add("外部清理 $extRemoved 首")
                                if (removed > 0) parts.add("清理已删下载 $removed 首")
                                "刷新完成：" + parts.joinToString("，")
                            }
                            else -> "刷新完成：本地文件状态全部正常"
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
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
                    Spacer(Modifier.height(16.dp))
                    FilledTonalButton(
                        onClick = {
                            groupFilterIndex = 3
                            externalFolderPicker.launch(null)
                        }
                    ) {
                        Icon(Icons.Outlined.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("导入外部歌单 (打造本地播放器)")
                    }
                }
            }
        }
        return
    }

    fun taskToSong(t: DownloadTask): Song {
        val cover = com.jussicodes.music.data.offline.OfflineMusicMetadataManager.getOfflineCover(
            context,
            t.songId,
            t.coverUrl
        )?.toString() ?: t.coverUrl
        return Song(
            id = t.songId,
            name = t.title,
            ar = listOf(Artist(id = t.artistId, name = t.artist)),
            al = com.rcmiku.ncmapi.model.SongAlbum(id = t.albumId, name = t.album, picUrl = cover),
            dt = t.durationMs
        )
    }

    val playAllAction: () -> Unit = {
        if (completedTasks.isNotEmpty()) {
            val songs = completedTasks.map { taskToSong(it) }
            mediaController?.setPlaylist(
                songs,
                sourceId = 20261L,
                sourceName = "已下载歌曲",
                sourceType = "download"
            )
            mediaController?.playMediaAtId(completedTasks.first().songId)
        }
    }

    val playExternalPlaylistAction: (ExternalPlaylist) -> Unit = { pl ->
        if (pl.songs.isNotEmpty()) {
            val songs = pl.songs.map { it.toSong(pl.name) }
            mediaController?.setPlaylist(
                songs = songs,
                sourceId = 20263L,
                sourceName = pl.name,
                sourceType = "external"
            )
            mediaController?.playMediaAtId(pl.songs.first().id)
        }
    }

    val playExternalSongAction: (ExternalPlaylist, ExternalSong) -> Unit = { pl, s ->
        val songs = pl.songs.map { it.toSong(pl.name) }
        mediaController?.setPlaylist(
            songs = songs,
            sourceId = 20263L,
            sourceName = pl.name,
            sourceType = "external"
        )
        mediaController?.playMediaAtId(s.id)
    }

    val refreshExternalPlaylistAction: (ExternalPlaylist) -> Unit = { pl ->
        coroutineScope.launch {
            Toast.makeText(context, "正在重新扫描「${pl.name}」...", Toast.LENGTH_SHORT).show()
            val (added, removed) = ExternalPlaylistStore.refreshPlaylist(context, pl.id)
            val msg = when {
                added > 0 && removed > 0 -> "刷新完成：新增 $added 首，移除 $removed 首"
                added > 0 -> "刷新完成：新增 $added 首歌曲"
                removed > 0 -> "刷新完成：已清理 $removed 首失效歌曲"
                else -> "刷新完成：本地文件无变动"
            }
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    val playGroupAction: (List<DownloadTask>, String) -> Unit = { list, groupTitle ->
        if (list.isNotEmpty()) {
            val songs = list.map { taskToSong(it) }
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
            val songs = completedTasks.map { taskToSong(it) }
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
                    val (extAdded, extRemoved) = ExternalPlaylistStore.refreshAllPlaylists(context)
                    kotlinx.coroutines.delay(400)
                    val msg = when {
                        extAdded > 0 || extRemoved > 0 || removed > 0 -> {
                            val parts = mutableListOf<String>()
                            if (extAdded > 0) parts.add("外部新增 $extAdded 首")
                            if (extRemoved > 0) parts.add("外部清理 $extRemoved 首")
                            if (removed > 0) parts.add("清理已删下载 $removed 首")
                            "刷新完成：" + parts.joinToString("，")
                        }
                        else -> "刷新完成：本地文件状态全部正常"
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
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
                                .padding(vertical = 4.dp)
                                .horizontalScroll(rememberScrollState()),
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
                            FilterChip(
                                selected = groupFilterIndex == 3,
                                onClick = { groupFilterIndex = 3 },
                                label = { Text("外部歌单 (${externalPlaylists.size})") }
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
                                onShareClick = { shareDownloadedFile(context, task) },
                                onProbeQualityClick = { onProbeQuality(task) }
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
                                onShareClick = { shareDownloadedFile(context, it) },
                                onProbeQualityClick = onProbeQuality
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

                        item(span = { GridItemSpan(2) }) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                                    .clickable { externalFolderPicker.launch(null) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CreateNewFolder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "导入外部歌单 (自动识别音频、歌词与封面)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.secondary,
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
                                    onProbeQualityClick = onProbeQuality,
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
                    3 -> {
                        item(span = { GridItemSpan(2) }) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                    .clickable { externalFolderPicker.launch(null) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CreateNewFolder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "导入外部文件夹歌单",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "自动识别音频、同名/内嵌歌词与封面，打造本地播放器",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isImportingExternal) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        if (externalPlaylists.isEmpty()) {
                            item(span = { GridItemSpan(2) }) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "暂无导入的外部歌单，点击上方导入手机中的任意音乐文件夹",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            items(externalPlaylists, key = { it.id }, span = { GridItemSpan(2) }) { pl ->
                                ExternalPlaylistCard(
                                    playlist = pl,
                                    isExpanded = pl.id in expandedExternalPlaylistIds,
                                    onToggleExpand = {
                                        expandedExternalPlaylistIds = if (pl.id in expandedExternalPlaylistIds) {
                                            expandedExternalPlaylistIds - pl.id
                                        } else {
                                            expandedExternalPlaylistIds + pl.id
                                        }
                                    },
                                    onPlayAll = { playExternalPlaylistAction(pl) },
                                    onRefresh = { refreshExternalPlaylistAction(pl) },
                                    onRename = {
                                        renameTargetExternalPlaylist = pl
                                        renameExternalPlaylistInput = pl.name
                                    },
                                    onDelete = {
                                        deleteTargetExternalPlaylist = pl
                                    },
                                    onSongClick = { song ->
                                        playExternalSongAction(pl, song)
                                    },
                                    onSongDelete = { song ->
                                        coroutineScope.launch {
                                            ExternalPlaylistStore.removeSong(context, pl.id, song.id)
                                            Toast.makeText(context, "已从歌单中移除「${song.title}」", Toast.LENGTH_SHORT).show()
                                        }
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
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .horizontalScroll(rememberScrollState()),
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
                            FilterChip(
                                selected = groupFilterIndex == 3,
                                onClick = { groupFilterIndex = 3 },
                                label = { Text("外部歌单 (${externalPlaylists.size})") }
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
                                onShareClick = { shareDownloadedFile(context, task) },
                                onProbeQualityClick = { onProbeQuality(task) }
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
                                onShareClick = { shareDownloadedFile(context, it) },
                                onProbeQualityClick = onProbeQuality
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

                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                                    .clickable { externalFolderPicker.launch(null) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CreateNewFolder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "导入外部歌单 (自动识别音频、歌词与封面)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.secondary,
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
                                    onProbeQualityClick = onProbeQuality,
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
                    3 -> {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                    .clickable { externalFolderPicker.launch(null) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CreateNewFolder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "导入外部文件夹歌单",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "自动识别音频、同名/内嵌歌词与封面，打造本地播放器",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isImportingExternal) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        if (externalPlaylists.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "暂无导入的外部歌单，点击上方导入手机中的任意音乐文件夹",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            items(externalPlaylists, key = { it.id }) { pl ->
                                ExternalPlaylistCard(
                                    playlist = pl,
                                    isExpanded = pl.id in expandedExternalPlaylistIds,
                                    onToggleExpand = {
                                        expandedExternalPlaylistIds = if (pl.id in expandedExternalPlaylistIds) {
                                            expandedExternalPlaylistIds - pl.id
                                        } else {
                                            expandedExternalPlaylistIds + pl.id
                                        }
                                    },
                                    onPlayAll = { playExternalPlaylistAction(pl) },
                                    onRefresh = { refreshExternalPlaylistAction(pl) },
                                    onRename = {
                                        renameTargetExternalPlaylist = pl
                                        renameExternalPlaylistInput = pl.name
                                    },
                                    onDelete = {
                                        deleteTargetExternalPlaylist = pl
                                    },
                                    onSongClick = { song ->
                                        playExternalSongAction(pl, song)
                                    },
                                    onSongDelete = { song ->
                                        coroutineScope.launch {
                                            ExternalPlaylistStore.removeSong(context, pl.id, song.id)
                                            Toast.makeText(context, "已从歌单中移除「${song.title}」", Toast.LENGTH_SHORT).show()
                                        }
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

    renameTargetExternalPlaylist?.let { targetPl ->
        AlertDialog(
            onDismissRequest = { renameTargetExternalPlaylist = null },
            title = { Text("重命名外部歌单") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "原文件夹：${targetPl.folderPathName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = renameExternalPlaylistInput,
                        onValueChange = { renameExternalPlaylistInput = it },
                        label = { Text("歌单名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newName = renameExternalPlaylistInput.trim()
                        if (newName.isNotBlank() && newName != targetPl.name) {
                            coroutineScope.launch {
                                ExternalPlaylistStore.renamePlaylist(context, targetPl.id, newName)
                                Toast.makeText(context, "歌单已重命名为「$newName」", Toast.LENGTH_SHORT).show()
                                renameTargetExternalPlaylist = null
                            }
                        }
                    },
                    enabled = renameExternalPlaylistInput.trim().isNotBlank() && renameExternalPlaylistInput.trim() != targetPl.name
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTargetExternalPlaylist = null }) {
                    Text("取消")
                }
            }
        )
    }

    deleteTargetExternalPlaylist?.let { targetPl ->
        AlertDialog(
            onDismissRequest = { deleteTargetExternalPlaylist = null },
            title = { Text("移除外部歌单「${targetPl.name}」") },
            text = { Text("仅从 App 外部歌单列表中解绑移除，不会删除手机中的音乐、歌词与封面文件。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            ExternalPlaylistStore.deletePlaylist(context, targetPl.id)
                            Toast.makeText(context, "歌单「${targetPl.name}」已移除", Toast.LENGTH_SHORT).show()
                            deleteTargetExternalPlaylist = null
                        }
                    }
                ) {
                    Text("移除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTargetExternalPlaylist = null }) {
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
    val context = LocalContext.current
    val quota by DailyDownloadQuotaManager.quotaState.collectAsState()

    LaunchedEffect(Unit) {
        DailyDownloadQuotaManager.checkAndRefresh(context)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // 每日限额说明与进度展示卡片
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "今日下载配额：${quota.used} / ${quota.limit} 首",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (quota.isExhausted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (quota.isExhausted) "今日已达上限" else "剩余 ${quota.remaining} 首",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (quota.isExhausted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(6.dp))

                LinearProgressIndicator(
                    progress = { quota.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (quota.isExhausted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "为了避免音源被滥用以及维护LX生态，强制限制每个用户每天只能下载60首歌（每天 0 点刷新额度）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 1.25.em
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
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
    onShareClick: () -> Unit,
    onProbeQualityClick: () -> Unit = {}
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
        val itemContext = LocalContext.current
        val offlineCover = remember(task.songId, task.coverUrl) {
            com.jussicodes.music.data.offline.OfflineMusicMetadataManager.getOfflineCover(
                itemContext,
                task.songId,
                task.coverUrl
            )
        }
        AsyncImage(
            model = offlineCover,
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
                    "jymaster" -> "母带"
                    "dolby" -> "杜比"
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
                        text = "音质体检 / 升级",
                        icon = Icons.Outlined.HighQuality,
                        onClick = {
                            menuExpanded = false
                            onProbeQualityClick()
                        }
                    )
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
    onResumeAll: () -> Unit,
    onRetryWithProbe: (DownloadTask) -> Unit = {}
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
                    onCancel = { onCancel(task.songId) },
                    onRetryWithProbe = { onRetryWithProbe(task) }
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
                    onCancel = { onCancel(task.songId) },
                    onRetryWithProbe = { onRetryWithProbe(task) }
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
    onCancel: () -> Unit,
    onRetryWithProbe: () -> Unit = {}
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
            if (task.status == DownloadStatus.FAILED) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "换档重试 ›",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onRetryWithProbe() }.padding(vertical = 2.dp)
                )
            }

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
            if (task.status == DownloadStatus.FAILED) {
                IconButton(onClick = onRetryWithProbe) {
                    Icon(
                        imageVector = Icons.Outlined.Tune,
                        contentDescription = "换档重试",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
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
    onDismiss: () -> Unit,
    onOpenBenchmarkProbe: () -> Unit = {}
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
                                                "hires" -> "Hi-Res (高解析)"
                                                "jymaster" -> "超清母带 (Master)"
                                                "dolby" -> "杜比全景声 (Dolby)"
                                                else -> "每次询问"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        TextButton(onClick = onOpenBenchmarkProbe) {
                                            Icon(
                                                imageVector = Icons.Outlined.Speed,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text("音源体检")
                                        }
                                        TextButton(onClick = { showQualityPicker = true }) {
                                            Text("更改")
                                        }
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
                                    "hires" -> "Hi-Res (高解析)"
                                    "jymaster" -> "超清母带 (Master)"
                                    "dolby" -> "杜比全景声 (Dolby)"
                                    else -> "每次下载时询问"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = onOpenBenchmarkProbe) {
                                Icon(
                                    imageVector = Icons.Outlined.Speed,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("音源体检")
                            }
                            TextButton(onClick = { showQualityPicker = true }) {
                                Text("更改")
                            }
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
    onDeleteGroup: (() -> Unit)? = null,
    onProbeQualityClick: (DownloadTask) -> Unit = {}
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
                        onShareClick = { onShareClick(songTask) },
                        onProbeQualityClick = { onProbeQualityClick(songTask) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ExternalPlaylistCard(
    playlist: ExternalPlaylist,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onPlayAll: () -> Unit,
    onRefresh: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onSongClick: (ExternalSong) -> Unit,
    onSongDelete: (ExternalSong) -> Unit
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
                if (playlist.coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = playlist.coverUrl,
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
                            imageVector = Icons.Outlined.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${playlist.songs.size} 首歌曲 · ${formatBytes(playlist.totalBytes)}",
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

                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "刷新扫描",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                var menuExpanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多操作")
                    }
                    JussiDropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        JussiMenuItem(
                            text = "刷新扫描文件夹",
                            icon = Icons.Default.Refresh,
                            onClick = {
                                menuExpanded = false
                                onRefresh()
                            }
                        )
                        JussiMenuItem(
                            text = "重命名歌单",
                            icon = Icons.Outlined.Edit,
                            onClick = {
                                menuExpanded = false
                                onRename()
                            }
                        )
                        JussiMenuItem(
                            text = "移除外部歌单",
                            icon = Icons.Outlined.Delete,
                            destructive = true,
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
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

                if (playlist.songs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "文件夹中未发现音频文件，点击右上角刷新重新扫描",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    playlist.songs.forEachIndexed { index, song ->
                        ExternalSongItem(
                            index = index + 1,
                            song = song,
                            onClick = { onSongClick(song) },
                            onDelete = { onSongDelete(song) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExternalSongItem(
    index: Int,
    song: ExternalSong,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!song.coverUri.isNullOrBlank()) {
            AsyncImage(
                model = song.coverUri,
                contentDescription = null,
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$index",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (!song.lyricText.isNullOrBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "词",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            val extTag = song.fileName.substringAfterLast('.', "").uppercase()
            Text(
                text = "${song.artist} · ${if (song.album.isNotBlank()) "${song.album} · " else ""}${if (extTag.isNotBlank()) "$extTag · " else ""}${makeTimeString(song.durationMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
                    text = "从歌单中移除",
                    icon = Icons.Outlined.Delete,
                    destructive = true,
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    }
                )
            }
        }
    }
}
