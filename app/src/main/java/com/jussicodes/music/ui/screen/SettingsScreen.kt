package com.jussicodes.music.ui.screen

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clip
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
import com.jussicodes.music.constants.MiniPlayerHeight
import com.jussicodes.music.constants.apiBaseUrlKey
import com.jussicodes.music.constants.apiServerListKey
import com.jussicodes.music.constants.audioQualityKey
import com.jussicodes.music.constants.desktopLyricEnabledKey
import com.jussicodes.music.constants.ignoredUpdateVersionKey
import com.jussicodes.music.constants.githubDownloadSourceKey
import com.jussicodes.music.constants.ncmCookieKey
import com.jussicodes.music.constants.playerGestureTutorialVersionKey
import com.jussicodes.music.constants.pinnedAlbumsHiddenKey
import com.jussicodes.music.constants.themeColorSourceKey
import com.jussicodes.music.constants.unblockSourceKey
import com.jussicodes.music.lyric.DesktopLyricManager
import com.jussicodes.music.ui.components.Dialog
import com.jussicodes.music.ui.components.SongQualityDialog
import com.jussicodes.music.ui.components.ThemeColorSourceDialog
import com.jussicodes.music.ui.components.UnblockSourceDialog
import com.jussicodes.music.ui.components.UpdateDialog
import com.jussicodes.music.ui.components.unblockSourceOptions
import com.jussicodes.music.ui.icons.Album
import com.jussicodes.music.ui.icons.AudioLines
import com.jussicodes.music.ui.icons.DesktopLyrics
import com.jussicodes.music.ui.icons.Dns
import com.jussicodes.music.ui.icons.Gesture
import com.jussicodes.music.ui.icons.Github
import com.jussicodes.music.ui.icons.Login
import com.jussicodes.music.ui.icons.Logout
import com.jussicodes.music.ui.icons.MusicNote
import com.jussicodes.music.ui.icons.Palette
import com.jussicodes.music.ui.icons.Remove
import com.jussicodes.music.ui.icons.UserRound
import com.jussicodes.music.ui.navigation.Screen
import com.jussicodes.music.ui.theme.ThemeColorSource
import com.jussicodes.music.utils.AppUpdateManager
import com.jussicodes.music.utils.NcmCookieNormalizer
import com.jussicodes.music.utils.ApiServerStatus
import com.jussicodes.music.utils.UpdateDownloadPhase
import com.jussicodes.music.utils.UpdateDownloadService
import com.jussicodes.music.utils.UpdateDownloadStateStore
import com.jussicodes.music.utils.UpdateInfo
import com.jussicodes.music.utils.UpdateSourceStatus
import com.jussicodes.music.utils.apiServers
import com.jussicodes.music.utils.downloadSources
import com.jussicodes.music.utils.rememberEnumPreference
import com.jussicodes.music.utils.rememberPreference
import com.jussicodes.music.utils.sourceById
import com.rcmiku.ncmapi.api.account.AccountApi
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
    var pinnedAlbumsHidden by rememberPreference(pinnedAlbumsHiddenKey, false)
    var apiBaseUrl by rememberPreference(apiBaseUrlKey, "http://8.134.163.111:3000")
    var unblockSource by rememberPreference(unblockSourceKey, "AUTO")
    var ignoredUpdateVersion by rememberPreference(ignoredUpdateVersionKey, "")
    var playerGestureTutorialVersion by rememberPreference(
        playerGestureTutorialVersionKey,
        0
    )
    val uiScaleController = LocalUiScaleController.current
    var githubDownloadSource by rememberPreference(
        githubDownloadSourceKey,
        downloadSources.first().id
    )
    var showQualityDialog by remember { mutableStateOf(false) }
    var showThemeColorSourceDialog by remember { mutableStateOf(false) }
    var showUnblockSourceDialog by remember { mutableStateOf(false) }
    var showCookieDialog by remember { mutableStateOf(false) }
    var showGithubSourceDialog by remember { mutableStateOf(false) }
    var githubSourceStatuses by remember { mutableStateOf<List<UpdateSourceStatus>>(emptyList()) }
    var isMeasuringUpdateSources by remember { mutableStateOf(false) }
    var testingGithubSources by remember { mutableStateOf(false) }
    var showApiServerDialog by remember { mutableStateOf(false) }
    var apiServerStatuses by remember { mutableStateOf<List<ApiServerStatus>>(emptyList()) }
    var apiServerListJson by rememberPreference(
        apiServerListKey,
        json.encodeToString(apiServers.toList())
    )
    val apiServerList = remember(apiServerListJson) {
        runCatching { json.decodeFromString<List<String>>(apiServerListJson) }
            .getOrElse { apiServers.toList() }
    }
    var updating by rememberSaveable { mutableStateOf(false) }
    var pendingUpdateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var downloadingUpdate by remember { mutableStateOf(false) }
    var updateDownloadProgress by remember { mutableFloatStateOf(0f) }
    var updateDownloadText by remember { mutableStateOf<String?>(null) }
    var logout by rememberSaveable { mutableStateOf(false) }
    var overlayPermissionGranted by remember {
        mutableStateOf(DesktopLyricManager.canDrawOverlays(context))
    }
    var pendingOverlayGrant by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var accountNickname by rememberSaveable { mutableStateOf("") }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayPermissionGranted = DesktopLyricManager.canDrawOverlays(context)
                // 从系统悬浮窗授权页返回且已授权 → 自动开启桌面歌词
                if (pendingOverlayGrant && overlayPermissionGranted) {
                    pendingOverlayGrant = false
                    coroutineScope.launch {
                        DesktopLyricManager.setEnabled(
                            context = context,
                            enabled = true,
                            requestPermissionIfNeeded = false,
                        )
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 退出登录副标题显示当前账号昵称
    LaunchedEffect(ncmCookie) {
        if (ncmCookie.isEmpty()) {
            accountNickname = ""
            return@LaunchedEffect
        }
        accountNickname = AccountApi.account().getOrNull()
            ?.account?.profile?.nickname.orEmpty()
    }

    LaunchedEffect(Unit) {
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

    // 桌面歌词有效开关 = 存储开启 && 悬浮窗权限已授予
    val lyricOn = desktopLyricEnabled && overlayPermissionGranted
    fun setLyricTarget(on: Boolean) {
        if (on && !overlayPermissionGranted) {
            // 未授权：记录意图并直接跳系统授权页，返回后自动开启
            pendingOverlayGrant = true
            DesktopLyricManager.requestOverlayPermission(context)
        } else {
            coroutineScope.launch {
                DesktopLyricManager.setEnabled(
                    context = context,
                    enabled = on,
                    requestPermissionIfNeeded = false,
                )
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
    val loggedOut = ncmCookie.isEmpty()
    val httpWarning = if (apiBaseUrl.startsWith("http://")) {
        "未加密连接，建议使用 HTTPS"
    } else {
        null
    }

    val accountGroup = SettingGroupData(
        name = "账号",
        items = listOf(
            SettingItemData(
                title = "账号 Cookie",
                subtitle = if (accountCookie.isBlank()) {
                    "未保存 Cookie"
                } else {
                    "已保存 ${accountCookieMap.size} 项 · 请勿泄露给他人"
                },
                imageVector = UserRound,
                onClick = { showCookieDialog = true }
            ),
            SettingItemData(
                title = stringResource(if (loggedOut) R.string.login else R.string.logout),
                imageVector = if (loggedOut) Login else Logout,
                danger = !loggedOut,
                subtitle = if (loggedOut) null else "当前账号：${accountNickname.ifEmpty { "点击退出" }}",
                onClick = {
                    if (loggedOut) {
                        navController.navigate(Screen.Login.route)
                    } else {
                        logout = true
                    }
                }
            )
        )
    )

    val uiGroup = SettingGroupData(
        name = "界面",
        items = listOf(
            SettingItemData(
                title = appearanceTitle,
                subtitle = appearanceWithScale,
                imageVector = Palette,
                onClick = { showThemeColorSourceDialog = true }
            ),
            SettingItemData(
                title = "桌面歌词",
                subtitle = if (lyricOn) "已开启" else "需要悬浮窗权限",
                imageVector = DesktopLyrics,
                trailingContent = {
                    Switch(
                        checked = lyricOn,
                        onCheckedChange = { setLyricTarget(it) }
                    )
                    Spacer(Modifier.width(12.dp))
                },
                onClick = { setLyricTarget(!lyricOn) }
            ),
            SettingItemData(
                title = "主页专辑墙",
                subtitle = if (pinnedAlbumsHidden) {
                    "已关闭，打开开关恢复"
                } else {
                    "显示在主页的置顶专辑"
                },
                imageVector = Album,
                trailingContent = {
                    Switch(
                        checked = !pinnedAlbumsHidden,
                        onCheckedChange = { pinnedAlbumsHidden = !it }
                    )
                    Spacer(Modifier.width(12.dp))
                }
            )
        )
    )

    val playbackGroup = SettingGroupData(
        name = "播放",
        items = listOf(
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
                title = "播放器手势教程",
                subtitle = "重新显示点按和上滑提示",
                imageVector = Gesture,
                onClick = {
                    playerGestureTutorialVersion = 0
                    Toast.makeText(context, "播放器手势教程已重新开启", Toast.LENGTH_SHORT).show()
                }
            )
        )
    )

    val advancedGroup = SettingGroupData(
        name = "高级",
        items = listOf(
            SettingItemData(
                title = stringResource(R.string.api_server),
                subtitle = apiBaseUrl,
                warning = httpWarning,
                imageVector = Dns,
                onClick = { showApiServerDialog = true }
            ),
            SettingItemData(
                title = "音乐源",
                subtitle = unblockSourceOptions.firstOrNull { it.value == unblockSource }?.label,
                imageVector = MusicNote,
                onClick = { showUnblockSourceDialog = true }
            ),
            SettingItemData(
                title = if (updating) "正在检查" else "检查更新",
                subtitle = if (updating) "正在检查更新" else BuildConfig.VERSION_NAME,
                imageVector = Github,
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
            ),
            SettingItemData(
                title = "更新源",
                subtitle = sourceById(githubDownloadSource)?.name,
                imageVector = Github,
                onClick = {
                    showGithubSourceDialog = true
                    testingGithubSources = true
                    coroutineScope.launch {
                        githubSourceStatuses = AppUpdateManager.measureDownloadSources()
                        testingGithubSources = false
                    }
                }
            )
        )
    )

    val aboutGroup = SettingGroupData(
        name = "关于",
        items = listOf(
            SettingItemData(
                title = "jussichords",
                subtitle = "简洁的第三方网易云音乐客户端",
                imageVector = MusicNote
            ),
            SettingItemData(
                title = "jussicodes",
                subtitle = "项目作者",
                imageVector = UserRound,
                onClick = { uriHandler.openUri("https://github.com/easyTIDollar") }
            )
        )
    )

    val groups = listOf(accountGroup, uiGroup, playbackGroup, advancedGroup, aboutGroup)

    // 搜索：按标题 / 副标题 / 分组名过滤，无结果的分组整组隐藏
    val query = searchQuery.trim().lowercase()
    val visibleGroups = groups
        .map { group ->
            group.copy(
                items = group.items.filter { item ->
                    query.isEmpty() ||
                        item.title.lowercase().contains(query) ||
                        (item.subtitle?.lowercase()?.contains(query) == true) ||
                        group.name.lowercase().contains(query)
                }
            )
        }
        .filter { it.items.isNotEmpty() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
            )
        }
    ) { padding ->
        val bottomInset = MiniPlayerHeight +
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        LazyColumn(
            contentPadding = PaddingValues(
                start = 12.dp,
                top = padding.calculateTopPadding(),
                end = 12.dp,
                bottom = bottomInset
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("搜索设置") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            items(visibleGroups) { group ->
                SettingGroupCard(group)
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
            dialogText = "退出后将清除本地保存的登录信息",
        )
    }

    if (showApiServerDialog) {
        ApiServerPingDialog(
            currentServer = apiBaseUrl,
            servers = apiServerList,
            statuses = apiServerStatuses,
            onServersChanged = { list ->
                apiServerListJson = json.encodeToString(list)
            },
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
                val normalized = NcmCookieNormalizer.normalizeForStorage(cookie)
                if (normalized == null) {
                    Toast.makeText(
                        context,
                        "Cookie 缺少 MUSIC_U，无法保存登录态",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    ncmCookie = json.encodeToString(normalized)
                }
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
                    githubSourceStatuses = AppUpdateManager.measureDownloadSources()
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
            isMeasuringSources = isMeasuringUpdateSources,
            onMeasureSources = {
                if (!isMeasuringUpdateSources) {
                    isMeasuringUpdateSources = true
                    coroutineScope.launch {
                        githubSourceStatuses = AppUpdateManager.measureDownloadSources()
                        isMeasuringUpdateSources = false
                    }
                }
            },
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

/** 一个分组：主色小标题 + 同组所有项放一张圆角卡片，行间细分割线。 */
@Composable
private fun SettingGroupCard(group: SettingGroupData) {
    val colors = MaterialTheme.colorScheme
    Column {
        Text(
            text = group.name,
            style = MaterialTheme.typography.titleSmall,
            color = colors.primary,
            modifier = Modifier.padding(start = 6.dp, bottom = 6.dp)
        )
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer)
        ) {
            Column {
                group.items.forEachIndexed { index, item ->
                    SettingRow(item)
                    if (index < group.items.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 62.dp, end = 14.dp),
                            thickness = 1.dp,
                            color = colors.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

/** 统一行高设置行：36dp 圆角底色图标块 + 标题/副标题(/弱提示) + 可选 Switch/箭头。 */
@Composable
private fun SettingRow(item: SettingItemData) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(
                if (item.onClick != null) {
                    Modifier.clickable { item.onClick?.invoke() }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (item.danger) colors.errorContainer else colors.surfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = item.imageVector,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (item.danger) colors.error else colors.primary
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (item.danger) colors.error else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            item.subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            item.warning?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.error,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        item.trailingContent?.invoke()
        if (item.onClick != null && item.trailingContent == null) {
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = colors.onSurfaceVariant
            )
        }
    }
}

data class SettingItemData(
    val title: String,
    val subtitle: String? = null,
    val imageVector: ImageVector,
    val onClick: (() -> Unit)? = null,
    val trailingContent: @Composable (() -> Unit)? = null,
    val danger: Boolean = false,
    val warning: String? = null,
)

data class SettingGroupData(
    val name: String,
    val items: List<SettingItemData>,
)

@Composable
private fun GitHubDownloadSourceDialog(
    currentSourceId: String,
    statuses: List<UpdateSourceStatus>,
    testing: Boolean,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onSourceSelected: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("更新源") },
        text = {
            Column {
                if (testing) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                }
                downloadSources.forEach { source ->
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
                                    status == null -> source.detail
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
 * 可增删的 ncmapi 后端列表对话框。
 *
 * 列表持久化在 DataStore（apiServerListKey）：可添加/删除自定义地址。点行或单选
 * 即激活该服务器；「重新测速」并行 Ping 列表全部地址。当前活动服务器若不在列表
 * 中，会置顶显示为系统行（不可删除）。
 */
@Composable
private fun ApiServerPingDialog(
    currentServer: String,
    servers: List<String>,
    statuses: List<ApiServerStatus>,
    onServersChanged: (List<String>) -> Unit,
    onMeasured: (List<ApiServerStatus>, activateFastest: Boolean) -> Unit,
    onActivate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var testing by remember { mutableStateOf(false) }
    var newServer by remember { mutableStateOf("") }
    val currentNotListed = currentServer !in servers

    fun runPing(activateFastest: Boolean) {
        if (testing) return
        testing = true
        scope.launch {
            val result = AppUpdateManager.measureApiServers(servers)
            testing = false
            onMeasured(result, activateFastest)
        }
    }

    fun addServer() {
        val trimmed = newServer.trim()
        if (trimmed.isEmpty()) return
        if (trimmed in servers || trimmed == currentServer) {
            Toast.makeText(context, "该地址已在列表中", Toast.LENGTH_SHORT).show()
            return
        }
        onServersChanged(servers + trimmed)
        newServer = ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("API 服务器") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (testing) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                }
                if (currentNotListed) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (!testing) onActivate(currentServer)
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = true,
                            onClick = {
                                if (!testing) onActivate(currentServer)
                            }
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                currentServer,
                                style = MaterialTheme.typography.titleMedium,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1
                            )
                            Text(
                                text = "当前 · 未在列表中",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                servers.forEach { server ->
                    val status = statuses.firstOrNull { it.server == server }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    if (!testing) onActivate(server)
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = server == currentServer,
                                onClick = {
                                    if (!testing) onActivate(server)
                                }
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(
                                    server,
                                    style = MaterialTheme.typography.titleMedium,
                                    overflow = TextOverflow.Ellipsis,
                                    maxLines = 1
                                )
                                Text(
                                    text = when {
                                        testing && status == null -> "测速中..."
                                        status == null ->
                                            if (server == currentServer) "当前" else "未测"
                                        status.available -> "可用 · ${status.latencyMs ?: 0} ms"
                                        else -> "不可用 · ${status.message}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(
                            onClick = { onServersChanged(servers - server) },
                            enabled = !testing,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Remove,
                                contentDescription = "删除",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = newServer,
                    onValueChange = { newServer = it },
                    label = { Text("添加服务器") },
                    placeholder = { Text("http://host:3000") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    onClick = { addServer() },
                    enabled = !testing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("添加到列表")
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
                label = { Text("MUSIC_U 或完整 Cookie") },
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
