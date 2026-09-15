package com.jussicodes.music.ui.screen

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import com.jussicodes.music.BuildConfig
import com.jussicodes.music.LocalUiScaleController
import com.jussicodes.music.R
import com.jussicodes.music.constants.SettingItemCorner
import com.jussicodes.music.constants.SettingItemHeight
import com.jussicodes.music.constants.SettingItemSubCorner
import com.jussicodes.music.constants.apiBaseUrlKey
import com.jussicodes.music.constants.audioQualityKey
import com.jussicodes.music.constants.desktopLyricEnabledKey
import com.jussicodes.music.constants.ignoredUpdateVersionKey
import com.jussicodes.music.constants.githubDownloadSourceKey
import com.jussicodes.music.constants.ncmCookieKey
import com.jussicodes.music.constants.playerGestureTutorialVersionKey
import com.jussicodes.music.constants.themeColorSourceKey
import com.jussicodes.music.constants.unblockSourceKey
import com.jussicodes.music.lyric.DesktopLyricManager
import com.jussicodes.music.ui.components.Dialog
import com.jussicodes.music.ui.components.SongQualityDialog
import com.jussicodes.music.ui.components.ThemeColorSourceDialog
import com.jussicodes.music.ui.components.UnblockSourceDialog
import com.jussicodes.music.ui.components.UpdateDialog
import com.jussicodes.music.ui.icons.AudioLines
import com.jussicodes.music.ui.icons.DesktopLyrics
import com.jussicodes.music.ui.icons.Dns
import com.jussicodes.music.ui.icons.Github
import com.jussicodes.music.ui.icons.Login
import com.jussicodes.music.ui.icons.Logout
import com.jussicodes.music.ui.icons.PlayPause
import com.jussicodes.music.ui.icons.ModeComment
import com.jussicodes.music.ui.icons.Star
import com.jussicodes.music.ui.icons.UserRound
import com.jussicodes.music.ui.navigation.Screen
import com.jussicodes.music.ui.theme.ThemeColorSource
import com.jussicodes.music.utils.AppUpdateManager
import com.jussicodes.music.utils.ApiServerStatus
import com.jussicodes.music.utils.UpdateDownloadPhase
import com.jussicodes.music.utils.UpdateDownloadService
import com.jussicodes.music.utils.UpdateDownloadStateStore
import com.jussicodes.music.utils.GitHubDownloadSourceStatus
import com.jussicodes.music.utils.UpdateInfo
import com.jussicodes.music.utils.getItemShape
import com.jussicodes.music.utils.rememberEnumPreference
import com.jussicodes.music.utils.rememberPreference
import com.rcmiku.ncmapi.api.player.SongLevel
import com.rcmiku.ncmapi.utils.json
import com.rcmiku.ncmapi.utils.parseCookieString
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavHostController) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val dynamicColorAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    var desktopLyricEnabled by rememberPreference(desktopLyricEnabledKey, false)
    var audioQuality by rememberEnumPreference(audioQualityKey, defaultValue = SongLevel.STANDARD)
    var themeColorSource by rememberEnumPreference(
        themeColorSourceKey,
        defaultValue = ThemeColorSource.WALLPAPER,
    )
    var ncmCookie by rememberPreference(ncmCookieKey, "")
    var apiBaseUrl by rememberPreference(apiBaseUrlKey, "http://8.134.163.111:3000")
    var unblockSource by rememberPreference(unblockSourceKey, "pyncmd")
    var ignoredUpdateVersion by rememberPreference(ignoredUpdateVersionKey, "")
    var playerGestureTutorialVersion by rememberPreference(
        playerGestureTutorialVersionKey,
        0
    )
    val uiScaleController = LocalUiScaleController.current
    var githubDownloadSource by rememberPreference(
        githubDownloadSourceKey,
        AppUpdateManager.downloadSources.first().id
    )
    var showQualityDialog by remember { mutableStateOf(false) }
    var showThemeColorSourceDialog by remember { mutableStateOf(false) }
    var showUnblockSourceDialog by remember { mutableStateOf(false) }
    var showCookieDialog by remember { mutableStateOf(false) }
    var showGithubSourceDialog by remember { mutableStateOf(false) }
    var githubSourceStatuses by remember { mutableStateOf<List<GitHubDownloadSourceStatus>>(emptyList()) }
    var testingGithubSources by remember { mutableStateOf(false) }
    var showApiServerDialog by remember { mutableStateOf(false) }
    var apiServerStatuses by remember { mutableStateOf<List<ApiServerStatus>>(emptyList()) }
    var updating by rememberSaveable { mutableStateOf(false) }
    var pendingUpdateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var downloadingUpdate by remember { mutableStateOf(false) }
    var updateDownloadProgress by remember { mutableFloatStateOf(0f) }
    var updateDownloadText by remember { mutableStateOf<String?>(null) }
    var logout by rememberSaveable { mutableStateOf(false) }
    var overlayPermissionGranted by remember {
        mutableStateOf(DesktopLyricManager.canDrawOverlays(context))
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayPermissionGranted = DesktopLyricManager.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        UpdateDownloadStateStore.state.collectLatest { snapshot ->
            val pendingVersion = pendingUpdateInfo?.versionName ?: return@collectLatest
            val snapshotVersion = snapshot.updateInfo?.versionName
            if (snapshotVersion != null && pendingVersion != snapshotVersion) {
                return@collectLatest
            }
            when (snapshot.phase) {
                UpdateDownloadPhase.IDLE -> Unit
                UpdateDownloadPhase.DOWNLOADING -> {
                    downloadingUpdate = true
                    updateDownloadProgress =
                        if (snapshot.totalBytes > 0L) {
                            snapshot.downloadedBytes.toFloat() / snapshot.totalBytes
                        } else {
                            0f
                        }
                    updateDownloadText =
                        "${com.jussicodes.music.ui.components.formatFileSize(snapshot.downloadedBytes)} / ${com.jussicodes.music.ui.components.formatFileSize(snapshot.totalBytes)}"
                }
                UpdateDownloadPhase.COMPLETED -> {
                    downloadingUpdate = false
                    updateDownloadProgress = 1f
                    updateDownloadText = "下载完成，正在打开安装器"
                    snapshot.apkPath?.let { apkPath ->
                        AppUpdateManager.installApk(context, File(apkPath))
                            .onFailure { throwable ->
                                Toast.makeText(
                                    context,
                                    throwable.message ?: "无法打开安装器",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                    }
                    pendingUpdateInfo = null
                }
                UpdateDownloadPhase.FAILED -> {
                    downloadingUpdate = false
                    updateDownloadProgress = 0f
                    updateDownloadText = snapshot.message ?: "下载失败"
                    Toast.makeText(
                        context,
                        snapshot.message ?: "下载失败",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    val appearanceTitle = "外观"
    val appearanceSubtitle = when (themeColorSource) {
        ThemeColorSource.WALLPAPER -> if (dynamicColorAvailable) {
            "根据壁纸动态取色"
        } else {
            "壁纸动态取色（当前设备不支持）"
        }

        ThemeColorSource.ARTWORK -> "从当前播放音乐的封面取色"
    }

    val appearanceWithScale = "$appearanceSubtitle · 缩放 ${(uiScaleController.scale * 100).toInt()}%"
    val accountCookie = remember(ncmCookie) { ncmCookie.toCookieHeader() }
    val accountCookieMap = remember(accountCookie) { parseCookieString(accountCookie) }

    val baseSettingItems = listOf(
        SettingItemData(
            title = appearanceTitle,
            subtitle = appearanceWithScale,
            imageVector = Star,
            onClick = { showThemeColorSourceDialog = true }
        ),
        SettingItemData(
            title = stringResource(if (ncmCookie.isNotEmpty()) R.string.logout else R.string.login),
            imageVector = if (ncmCookie.isNotEmpty()) Logout else Login,
            onClick = {
                if (ncmCookie.isNotEmpty()) {
                    logout = true
                } else {
                    navController.navigate(Screen.Login.route)
                }
            }
        ),
        SettingItemData(
            title = "账号 Cookie",
            subtitle = if (accountCookie.isBlank()) {
                "未保存 Cookie"
            } else {
                "已保存 ${accountCookieMap.size} 项，点击查看或更改"
            },
            imageVector = UserRound,
            onClick = { showCookieDialog = true }
        ),
        SettingItemData(
            title = "桌面歌词",
            subtitle = when {
                desktopLyricEnabled && overlayPermissionGranted -> "已开启悬浮歌词"
                !overlayPermissionGranted -> "需要悬浮窗权限"
                else -> "已关闭"
            },
            imageVector = DesktopLyrics,
            trailingContent = {
                Switch(
                    checked = desktopLyricEnabled && overlayPermissionGranted,
                    onCheckedChange = {
                        coroutineScope.launch {
                            val result = DesktopLyricManager.setEnabled(
                                context = context,
                                enabled = it,
                                requestPermissionIfNeeded = it
                            )
                            overlayPermissionGranted = DesktopLyricManager.canDrawOverlays(context)
                            if (it && !result) {
                                Toast.makeText(
                                    context,
                                    "请先授予悬浮窗权限",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                )
                Spacer(Modifier.width(12.dp))
            },
            onClick = {
                coroutineScope.launch {
                    val target = !(desktopLyricEnabled && overlayPermissionGranted)
                    val result = DesktopLyricManager.setEnabled(
                        context = context,
                        enabled = target,
                        requestPermissionIfNeeded = target
                    )
                    overlayPermissionGranted = DesktopLyricManager.canDrawOverlays(context)
                    if (target && !result) {
                        Toast.makeText(
                            context,
                            "请先授予悬浮窗权限",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        ),
        SettingItemData(
            title = stringResource(R.string.audio_quality),
            subtitle = when (audioQuality) {
                SongLevel.STANDARD -> stringResource(R.string.standard)
                SongLevel.HIGHER -> stringResource(R.string.higer)
                SongLevel.EXHIGH -> stringResource(R.string.exhigh)
                SongLevel.LOSSLESS -> stringResource(R.string.lossless)
                SongLevel.HIRES -> stringResource(R.string.hi_res)
                SongLevel.JYEFFECT -> stringResource(R.string.jyeffect)
                SongLevel.SKY -> stringResource(R.string.sky)
                SongLevel.DOLBY -> stringResource(R.string.dolby)
                SongLevel.JYMASTER -> stringResource(R.string.jymaster)
            },
            imageVector = AudioLines,
            onClick = { showQualityDialog = true }
        ),
        SettingItemData(
            title = "重新学播放器手势",
            subtitle = "在播放页重新显示点击和上滑操作提示",
            imageVector = ModeComment,
            onClick = {
                playerGestureTutorialVersion = 0
                Toast.makeText(context, "播放器手势教程已重新开启", Toast.LENGTH_SHORT).show()
            }
        ),
        SettingItemData(
            title = stringResource(R.string.api_server),
            subtitle = apiBaseUrl,
            imageVector = Dns,
            onClick = { showApiServerDialog = true },
            onLongClick = { showUnblockSourceDialog = true }
        ),
        SettingItemData(
            title = if (updating) "正在检查" else "检查版本更新",
            subtitle = when {
                updating -> "正在检查 GitHub Release"
                else -> "当前版本：${BuildConfig.VERSION_NAME} · 点按检查更新 · 长按切换更新源"
            },
            imageVector = Github,
            onLongClick = {
                showGithubSourceDialog = true
                testingGithubSources = true
                coroutineScope.launch {
                    githubSourceStatuses = AppUpdateManager.measureDownloadSources(
                        "https://github.com/easyTIDollar/jussichords/releases/latest"
                    )
                    testingGithubSources = false
                }
            },
            onClick = {
                if (!updating) {
                    updating = true
                    coroutineScope.launch {
                        val updateResult = AppUpdateManager.checkUpdate()
                        val updateInfo = updateResult.getOrElse {
                            Toast.makeText(
                                context,
                                it.message ?: "更新失败",
                                Toast.LENGTH_LONG
                            ).show()
                            updating = false
                            return@launch
                        }

                        updating = false
                        if (updateInfo == null) {
                            Toast.makeText(context, "当前已是最新版本", Toast.LENGTH_SHORT).show()
                        } else {
                            pendingUpdateInfo = updateInfo
                        }
                    }
                }
            }
        )
    )

    val settingsItems = listOf(
        SettingItemData(
            title = "jussichords",
            subtitle = "简洁的第三方网易云音乐客户端",
            imageVector = PlayPause
        ),
        SettingItemData(
            title = "jussicodes",
            subtitle = "项目作者",
            imageVector = UserRound,
            onClick = { uriHandler.openUri("https://github.com/easyTIDollar") }
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = padding
        ) {
            item {
                Text(
                    stringResource(R.string.basic_settings),
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.titleSmall
                )
            }

            itemsIndexed(baseSettingItems) { index, item ->
                val shape = getItemShape(
                    prevItem = baseSettingItems.getOrNull(index - 1),
                    nextItem = baseSettingItems.getOrNull(index + 1),
                    corner = SettingItemCorner,
                    subCorner = SettingItemSubCorner,
                )

                SettingCard(
                    title = item.title,
                    description = item.subtitle,
                    shape = shape,
                    imageVector = item.imageVector,
                    onClick = item.onClick,
                    onLongClick = item.onLongClick,
                    trailingContent = item.trailingContent,
                    progress = item.progress,
                )
            }

            item {
                Text(
                    stringResource(R.string.about),
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.titleSmall
                )
            }

            itemsIndexed(settingsItems) { index, item ->
                val shape = getItemShape(
                    prevItem = settingsItems.getOrNull(index - 1),
                    nextItem = settingsItems.getOrNull(index + 1),
                    corner = SettingItemCorner,
                    subCorner = SettingItemSubCorner,
                )
                SettingCard(
                    title = item.title,
                    description = item.subtitle,
                    imageVector = item.imageVector,
                    shape = shape,
                    onClick = item.onClick,
                    onLongClick = item.onLongClick,
                    trailingContent = item.trailingContent,
                    progress = item.progress,
                )
            }
        }
    }

    if (showQualityDialog) {
        SongQualityDialog(
            currentLevel = audioQuality,
            onDismiss = { showQualityDialog = false },
            onQualitySelected = { audioQuality = it }
        )
    }

    if (showThemeColorSourceDialog) {
        ThemeColorSourceDialog(
            currentSource = themeColorSource,
            currentUiScale = uiScaleController.scale,
            wallpaperColorAvailable = dynamicColorAvailable,
            onDismiss = { showThemeColorSourceDialog = false },
            onSourceSelected = { themeColorSource = it },
            onUiScaleSelected = uiScaleController.setScale,
        )
    }

    if (logout) {
        Dialog(
            onConfirmation = {
                ncmCookie = ""
                logout = false
            },
            onDismissRequest = {
                logout = false
            },
            dialogTitle = stringResource(R.string.logout),
        )
    }

    if (showApiServerDialog) {
        ApiServerPingDialog(
            currentServer = apiBaseUrl,
            statuses = apiServerStatuses,
            onMeasured = { statuses, activateFastest ->
                apiServerStatuses = statuses
                if (activateFastest) {
                    val fastest = AppUpdateManager.pickFastestApiServer(statuses)
                    if (fastest != null && fastest != apiBaseUrl) {
                        apiBaseUrl = fastest
                    }
                }
            },
            onActivate = { server ->
                apiBaseUrl = server
                showApiServerDialog = false
            },
            onDismiss = { showApiServerDialog = false },
        )
    }

    if (showUnblockSourceDialog) {
        UnblockSourceDialog(
            currentSource = unblockSource,
            onDismiss = { showUnblockSourceDialog = false },
            onSourceSelected = { unblockSource = it }
        )
    }

    if (showCookieDialog) {
        CookieEditDialog(
            currentCookie = accountCookie,
            onDismiss = { showCookieDialog = false },
            onConfirm = { cookie ->
                ncmCookie = cookie.toStoredCookieJson()
            }
        )
    }

    if (showGithubSourceDialog) {
        GitHubDownloadSourceDialog(
            currentSourceId = githubDownloadSource,
            statuses = githubSourceStatuses,
            testing = testingGithubSources,
            onDismiss = { showGithubSourceDialog = false },
            onRefresh = {
                testingGithubSources = true
                coroutineScope.launch {
                    githubSourceStatuses = AppUpdateManager.measureDownloadSources(
                        "https://github.com/easyTIDollar/jussichords/releases/latest"
                    )
                    testingGithubSources = false
                }
            },
            onSourceSelected = { githubDownloadSource = it }
        )
    }

    pendingUpdateInfo?.let { updateInfo ->
        UpdateDialog(
            updateInfo = updateInfo,
            isDownloading = downloadingUpdate,
            downloadProgress = if (downloadingUpdate) updateDownloadProgress else null,
            progressText = updateDownloadText,
            selectedSourceId = githubDownloadSource,
            sourceStatuses = githubSourceStatuses,
            onSourceSelected = { githubDownloadSource = it },
            onDismiss = {
                pendingUpdateInfo = null
                downloadingUpdate = false
                updateDownloadProgress = 0f
                updateDownloadText = null
            },
            onIgnoreVersion = {
                ignoredUpdateVersion = updateInfo.versionName
                pendingUpdateInfo = null
                downloadingUpdate = false
                updateDownloadProgress = 0f
                updateDownloadText = null
            },
            onDownload = {
                if (downloadingUpdate) return@UpdateDialog
                downloadingUpdate = true
                updateDownloadProgress = 0f
                updateDownloadText = "准备开始下载更新"
                UpdateDownloadService.start(
                    context.applicationContext,
                    updateInfo,
                    githubDownloadSource
                )
            }
        )
    }

}

@Composable
private fun GitHubDownloadSourceDialog(
    currentSourceId: String,
    statuses: List<GitHubDownloadSourceStatus>,
    testing: Boolean,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onSourceSelected: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("GitHub 下载源") },
        text = {
            Column {
                if (testing) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                }
                AppUpdateManager.downloadSources.forEach { source ->
                    val status = statuses.firstOrNull { it.source.id == source.id }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = source.id == currentSourceId,
                            onClick = { onSourceSelected(source.id) }
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 8.dp)
                        ) {
                            Text(source.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = when {
                                    testing && status == null -> "测速中..."
                                    status == null -> when (source.id) {
                                        "direct" -> "不使用加速源"
                                        "gitee" -> "Gitee 国内加速"
                                        else -> source.name
                                    }
                                    status.available -> "可用 · ${status.latencyMs ?: 0} ms"
                                    else -> "不可用 · ${status.message}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onRefresh, enabled = !testing) {
                Text("重新测速")
            }
        }
    )
}

/**
 * 预设 ncmapi 后端的 Ping 对话框。
 *
 * 弹出时不再自动测速，改由用户点「重新测速」手动并行测一轮三个预设地址，
 * 把延迟最低且可用者设为活动（activate 回调交给父级写偏好）；也可手动点某一
 * 地址直接激活它。测速进度条由对话框自管，避免和父级状态打架。
 */
@Composable
private fun ApiServerPingDialog(
    currentServer: String,
    statuses: List<ApiServerStatus>,
    onMeasured: (List<ApiServerStatus>, activateFastest: Boolean) -> Unit,
    onActivate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var testing by remember { mutableStateOf(false) }
    // 自定义服务器输入框：默认回填当前非预设的活动地址，否则空。
    var customServer by remember {
        mutableStateOf(currentServer.takeIf { !AppUpdateManager.isPresetApiServer(it) } ?: "")
    }

    fun runPing(activateFastest: Boolean) {
        if (testing) return
        testing = true
        scope.launch {
            val result = AppUpdateManager.measureApiServers()
            testing = false
            onMeasured(result, activateFastest)
        }
    }

    // 弹出不再自动测速，交由用户点「重新测速」手动触发。

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("API 服务器") },
        text = {
            Column {
                if (testing) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                }
                AppUpdateManager.apiServers.forEach { server ->
                    val status = statuses.firstOrNull { it.server == server }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (!testing) onActivate(server)
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = server == currentServer,
                            onClick = { if (!testing) onActivate(server) }
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 8.dp)
                        ) {
                            Text(server, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = when {
                                    testing && status == null -> "测速中..."
                                    status == null -> if (server == currentServer) "当前" else "未测"
                                    status.available -> "可用 · ${status.latencyMs ?: 0} ms"
                                    else -> "不可用 · ${status.message}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (server == currentServer) {
                            Text(
                                text = "活动",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = customServer,
                    onValueChange = { customServer = it },
                    label = { Text("自定义服务器") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    onClick = {
                        val trimmed = customServer.trim()
                        if (trimmed.isNotEmpty()) {
                            onActivate(trimmed)
                        }
                    },
                    enabled = !testing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("保存为当前服务器")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { runPing(activateFastest = false) }, enabled = !testing) {
                Text("重新测速")
            }
        }
    )
}

@Composable
private fun CookieEditDialog(
    currentCookie: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var editedCookie by rememberSaveable(currentCookie) { mutableStateOf(currentCookie) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("账号 Cookie") },
        text = {
            OutlinedTextField(
                value = editedCookie,
                onValueChange = { editedCookie = it },
                label = { Text("完整 Cookie") },
                minLines = 5,
                maxLines = 10,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(editedCookie.trim())
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { editedCookie = "" }) {
                    Text("清空")
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}

private fun String.toCookieHeader(): String {
    if (isBlank()) return ""
    return runCatching {
        json.decodeFromString<Map<String, String>>(this)
            .entries
            .joinToString("; ") { (key, value) -> "$key=$value" }
    }.getOrElse { this }
}

private fun String.toStoredCookieJson(): String {
    if (isBlank()) return ""
    return json.encodeToString(parseCookieString(this))
}

@Composable
fun SettingCard(
    title: String,
    description: String? = null,
    imageVector: ImageVector,
    shape: Shape,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    progress: Float? = null,
) {
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        SettingItem(
            imageVector = imageVector,
            title = title,
            description = description,
            onClick = onClick,
            onLongClick = onLongClick,
            trailingContent = trailingContent,
            progress = progress,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingItem(
    imageVector: ImageVector,
    title: String,
    description: String? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    progress: Float? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SettingItemHeight)
            .combinedClickable(
                onClick = { onClick?.invoke() },
                onLongClick = { onLongClick?.invoke() }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            imageVector = imageVector,
            contentDescription = null,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.surfaceTint),
            modifier = Modifier.padding(start = 12.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
        }
        trailingContent?.invoke()
    }
}

data class SettingItemData(
    val title: String,
    val subtitle: String? = null,
    val imageVector: ImageVector,
    val onClick: (() -> Unit)? = null,
    val onLongClick: (() -> Unit)? = null,
    val trailingContent: @Composable (() -> Unit)? = null,
    val progress: Float? = null,
)
