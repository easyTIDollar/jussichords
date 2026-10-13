package com.jussicodes.music.ui.screen

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.jussicodes.music.utils.CoverImageSize
import com.jussicodes.music.utils.toCoverImageUrl
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import com.jussicodes.music.constants.audioSourceKey
import com.jussicodes.music.constants.directSourceKey
import com.jussicodes.music.constants.githubDownloadSourceKey
import com.jussicodes.music.constants.ncmCookieKey
import com.jussicodes.music.constants.playerGestureTutorialVersionKey
import com.jussicodes.music.constants.pinnedAlbumsHiddenKey
import com.jussicodes.music.constants.homeBgBlurKey
import com.jussicodes.music.constants.homeBgImageKey
import com.jussicodes.music.constants.homeBgOffsetXKey
import com.jussicodes.music.constants.homeBgOffsetYKey
import com.jussicodes.music.constants.homeBgOpacityKey
import com.jussicodes.music.constants.homeBgScaleKey
import com.jussicodes.music.constants.themeColorSourceKey
import com.jussicodes.music.constants.unblockSourceKey
import com.jussicodes.music.lyric.DesktopLyricManager
import com.jussicodes.music.ui.components.ScaledDialogContent
import com.jussicodes.music.ui.components.Dialog
import com.jussicodes.music.ui.components.SongQualityDialog
import com.jussicodes.music.ui.components.ThemeColorSourceDialog
import com.jussicodes.music.ui.components.HomeBackgroundConfig
import com.jussicodes.music.ui.components.UnblockSourceDialog
import com.jussicodes.music.ui.components.UpdateDialog
import com.jussicodes.music.ui.components.unblockSourceOptions
import com.jussicodes.music.ui.icons.Album
import com.jussicodes.music.ui.icons.AudioLines
import com.jussicodes.music.ui.icons.DesktopLyrics
import androidx.compose.material.icons.outlined.Dns
import com.jussicodes.music.ui.icons.Gesture
import com.jussicodes.music.ui.icons.Github
import com.jussicodes.music.ui.icons.Login
import com.jussicodes.music.ui.icons.Logout
import com.jussicodes.music.ui.icons.MusicNote
import com.jussicodes.music.ui.icons.Palette
import com.jussicodes.music.ui.icons.Remove
import com.jussicodes.music.ui.icons.Script
import com.jussicodes.music.ui.icons.SettingsGear
import com.jussicodes.music.ui.icons.UserRound
import com.jussicodes.music.ui.navigation.Screen
import com.jussicodes.music.ui.theme.ThemeColorSource
import com.jussicodes.music.utils.AppUpdateManager
import com.jussicodes.music.utils.SettingsAppearanceBus
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
import com.rcmiku.ncmapi.api.AudioSources
import com.rcmiku.ncmapi.api.LocalSources
import com.rcmiku.ncmapi.api.player.PlayerApi
import com.jussicodes.music.lx.AudioSourceSwitcher
import com.jussicodes.music.ui.components.AudioSourceSelector
import com.jussicodes.music.ui.components.LocalSourceDialog
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
    var apiBaseUrl by rememberPreference(apiBaseUrlKey, "")
    var unblockSource by rememberPreference(unblockSourceKey, "AUTO")
    // 音源三选一（LX / 内置直连 / 代理）。旧选择由 JetMeloApp 里的一次性迁移清掉，
    // 这里不做重置——否则每次进设置页都会把用户的选择打回 LX。
    var audioSource by rememberPreference(audioSourceKey, AudioSources.DEFAULT)
    // 「直连音源」档位下的六源单选（音源设置里的子设置）。
    var directSource by rememberPreference(directSourceKey, LocalSources.AUTO)
    LaunchedEffect(directSource) {
        PlayerApi.DirectSourceSettings.SOURCE = directSource
    }
    // 三行子设置各自报「当前正在使用谁」，按 audioSource 档位走：
    // LX 档位说脚本名，直连/代理档位说「当前正在使用 LX源」（或直连源名/服务器）。
    val lxPlugins by com.jussicodes.music.lx.LxPluginManager.plugins.collectAsState(initial = emptyList())
    val enabledLxScripts = lxPlugins.filter { it.isEnabled }
    // 当前真正负责解析的脚本名：LX 档位取列表首个启用项（PlayerApi 也是按顺序轮询）
    val activeLxScriptName = enabledLxScripts.firstOrNull()?.name
    val inUseLabel = when (audioSource) {
        AudioSources.LX -> "LX源"
        AudioSources.DIRECT -> LocalSources.fromKey(directSource)?.label ?: "直连源"
        else -> apiBaseUrl.ifBlank { "API代理服务器" }
    }
    val lxScriptSummary = when {
        lxPlugins.isEmpty() -> "未导入脚本"
        audioSource == AudioSources.LX -> if (activeLxScriptName != null) {
            "当前正在使用${activeLxScriptName}脚本 · ${enabledLxScripts.size}/${lxPlugins.size} 个脚本已启用"
        } else {
            "${lxPlugins.size} 个脚本（全部停用）"
        }
        enabledLxScripts.isNotEmpty() -> "当前正在使用 ${activeLxScriptName ?: "LX源"}"
        else -> "当前正在使用 $inUseLabel"
    }
    val directSourceSummary = if (audioSource == AudioSources.DIRECT) {
        LocalSources.fromKey(directSource)?.label ?: directSource
    } else {
        "当前正在使用 $inUseLabel"
    }
    val apiServerSummary = if (audioSource == AudioSources.PROXY) {
        apiBaseUrl.ifBlank { "未设置" }
    } else {
        "当前正在使用 $inUseLabel"
    }
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
    // 主页背景（"我的"/"探索"页）：本地图片路径 + 缩放/透明度/模糊度/平移。
    var homeBgPath by rememberPreference(homeBgImageKey, "")
    var homeBgScale by rememberPreference(homeBgScaleKey, 1f)
    var homeBgOpacity by rememberPreference(homeBgOpacityKey, 1f)
    var homeBgBlur by rememberPreference(homeBgBlurKey, 0f)
    val homeTransparentCard = com.jussicodes.music.data.HomeTransparentCardStore.state.collectAsState().value
    // 主页文字颜色（设置页「外观」选择）：进程级 store，auto/white/black。
    val homeBgTextColor = com.jussicodes.music.data.HomeBgTextStore.state.collectAsState().value
    var homeBgOffsetX by rememberPreference(homeBgOffsetXKey, 0f)
    var homeBgOffsetY by rememberPreference(homeBgOffsetYKey, 0f)
    val homeBg = remember(
        homeBgPath, homeBgScale, homeBgOpacity, homeBgBlur, homeBgOffsetX, homeBgOffsetY
    ) {
        HomeBackgroundConfig(
            path = homeBgPath,
            scale = homeBgScale,
            opacity = homeBgOpacity,
            blur = homeBgBlur,
            offsetX = homeBgOffsetX,
            offsetY = homeBgOffsetY,
        )
    }
    // 选完图不留在设置页：建 draft（保留现有参数、换上图）→ 关闭弹窗 → 跳回主页进入编辑模式。
    var showThemeColorSourceDialog by remember { mutableStateOf(false) }
    // 主页背景编辑"放弃"后回来：自动打开「外观」弹窗（一次性消费，手动进设置页不会再弹）
    LaunchedEffect(Unit) {
        SettingsAppearanceBus.requested.collectLatest { open ->
            if (open) {
                showThemeColorSourceDialog = true
                SettingsAppearanceBus.consume()
            }
        }
    }
    fun startHomeBgEdit(config: HomeBackgroundConfig, ownsFile: Boolean) {
        com.jussicodes.music.data.HomeBgEditor.begin(config, ownsFile)
        showThemeColorSourceDialog = false
        // 立即返回主页进编辑模式：把设置页（含外观弹窗）整体弹出栈，
        // 调参全部在主页编辑面板完成，保存后按返回也不会再冒出旧设置页。
        navController.navigate(Screen.Library.route) {
            popUpTo(Screen.Settings.route) { inclusive = true }
            launchSingleTop = true
        }
    }
    // 选图失败原因（完整可读，走弹窗）。声明须先于 homeBgPicker：
    // launcher 回调（lambda）不能前向引用后声明的本地 remember 变量。
    var bgSelectFailureReason by remember { mutableStateOf<String?>(null) }
    val homeBgPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val result = com.jussicodes.music.data.HomeBgStore.select(context, uri)
                when (result) {
                    is com.jussicodes.music.data.HomeBgStore.HomeBgSelectResult.Success -> {
                        // 成功：立即进主页编辑模式实时预览（startHomeBgEdit 内部 begin draft + 跳 Library）。
                        startHomeBgEdit(
                            HomeBackgroundConfig(
                                path = result.path,
                                scale = homeBgScale,
                                opacity = homeBgOpacity,
                                blur = homeBgBlur,
                                offsetX = homeBgOffsetX,
                                offsetY = homeBgOffsetY,
                            ),
                            ownsFile = true
                        )
                    }
                    is com.jussicodes.music.data.HomeBgStore.HomeBgSelectResult.Failure -> {
                        // 完整原因（含 URI/异常 message）走可滚动弹窗，避免 toast 截断读不全。
                        bgSelectFailureReason = result.reason
                    }
                }
            }
        }
    }
    fun removeHomeBg() {
        coroutineScope.launch {
            // 隐藏bug修复：编辑进行中（选了新图还没保存）也能从底栏回到设置页点「移除」，
            // 旧逻辑 clear() 会把编辑草稿引用的临时文件一并删掉，之后 commit 会把
            // 一个已不存在的路径写进 DataStore → 背景静默变空。先丢弃草稿再清理。
            com.jussicodes.music.data.HomeBgEditor.cancel(context)
            com.jussicodes.music.data.HomeBgStore.clear(context)
            homeBgPath = ""
            homeBgScale = 1f
            homeBgOpacity = 1f
            homeBgBlur = 0f
            homeBgOffsetX = 0f
            homeBgOffsetY = 0f
        }
    }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showUnblockSourceDialog by remember { mutableStateOf(false) }
    var showDirectSourceDialog by remember { mutableStateOf(false) }



    // 切换音源后重解析当前播放的歌：清掉 ResolvingDataSource 的解析缓存（cacheKey
    // 只含音质，不含音源），重新 prepare 当前 MediaItem 让它走一遍新的解析链。
    fun reloadCurrentSong() {
        coroutineScope.launch {
            // rememberPreference 是异步写盘，延迟一下让 DataStore 先落定，
            // 否则 JetMeloApp 的 collect 可能用旧值把 ncmapi 全局变量覆盖回去。
            kotlinx.coroutines.delay(150)
            AudioSourceSwitcher.apply(audioSource, directSource)
        }
    }
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
    var accountAvatar by rememberSaveable { mutableStateOf("") }
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

    // 退出登录行：副标题=昵称，图标=当前账号头像
    LaunchedEffect(ncmCookie) {
        if (ncmCookie.isEmpty()) {
            accountNickname = ""
            accountAvatar = ""
            return@LaunchedEffect
        }
        val profile = AccountApi.account().getOrNull()?.account?.profile
        accountNickname = profile?.nickname.orEmpty()
        accountAvatar = profile?.avatarUrl.orEmpty()
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

        ThemeColorSource.HOME_BG -> if (homeBgPath.isNotBlank()) {
            "从主页背景图取色"
        } else {
            "主页背景图取色（未设置图片）"
        }
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
                // 已登录时用头像当图标（36dp 圆形），未登录/取不到头像则退回 Logout 图标
                iconContent = if (!loggedOut && accountAvatar.isNotBlank()) {
                    {
                        AsyncImage(
                            model = accountAvatar.toCoverImageUrl(CoverImageSize.LIST),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                        )
                    }
                } else null,
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
            )
        )
    )

    val advancedGroup = SettingGroupData(
        name = "音源设置",
        note = "三种音源只生效一个；切换后立即用新音源重新解析当前播放的歌",
        header = {
            AudioSourceSelector(
                currentSource = audioSource,
                onSourceSelected = { selected ->
                    if (selected != audioSource) {
                        audioSource = selected
                        reloadCurrentSong()
                    }
                }
            )
        },
        items = listOf(
            SettingItemData(
                title = "LX 脚本管理",
                subtitle = lxScriptSummary,
                imageVector = Script,
                onClick = { navController.navigate(Screen.LxPlugins.route) }
            ),
            SettingItemData(
                title = "直连源",
                subtitle = directSourceSummary,
                imageVector = MusicNote,
                onClick = { showDirectSourceDialog = true }
            ),
            SettingItemData(
                title = stringResource(R.string.api_server),
                subtitle = apiServerSummary,
                warning = httpWarning,
                imageVector = Icons.Outlined.Dns,
                onClick = { showApiServerDialog = true },
                trailingContent = {
                    GearButton("更换解灰源") { showUnblockSourceDialog = true }
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
                title = if (updating) "正在检查" else "检查更新",
                subtitle = sourceById(githubDownloadSource)?.name?.let { "$it · v${BuildConfig.VERSION_NAME}" }
                    ?: BuildConfig.VERSION_NAME,
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
                },
                trailingContent = {
                    GearButton("更换更新源") {
                        showGithubSourceDialog = true
                        testingGithubSources = true
                        coroutineScope.launch {
                            githubSourceStatuses = AppUpdateManager.measureDownloadSources()
                            testingGithubSources = false
                        }
                    }
                }
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
                bottom = bottomInset + 16.dp
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
            homeBg = homeBg,
            onHomeBgPick = { homeBgPicker.launch("image/*") },
            onHomeBgRemoved = { removeHomeBg() },
            onHomeBgTune = { startHomeBgEdit(homeBg, ownsFile = false) },
            homeBgTextColor = homeBgTextColor,
            onHomeBgTextColorChanged = { v ->
                coroutineScope.launch {
                    com.jussicodes.music.data.HomeBgTextStore.set(context, v)
                }
            },
            homeTransparentCard = homeTransparentCard,
            onHomeTransparentCardChange = {
                coroutineScope.launch {
                    com.jussicodes.music.data.HomeTransparentCardStore.set(context, it)
                }
            },
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
            onDeactivate = {
                apiBaseUrl = ""
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

    if (showDirectSourceDialog) {
        LocalSourceDialog(
            currentSource = directSource,
            onDismiss = { showDirectSourceDialog = false },
            onSourceSelected = { selected ->
                directSource = selected
                showDirectSourceDialog = false
                reloadCurrentSong()
            }
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

    if (bgSelectFailureReason != null) {
        AlertDialog(
            onDismissRequest = { bgSelectFailureReason = null },
            title = { Text("选取图片失败") },
            text = {
                Text(
                    text = "原因：${bgSelectFailureReason}\n\n多为该图片源不可用（文件被删/移动，或临时读授权失效），请重选一张。",
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = { bgSelectFailureReason = null }) { Text("知道了") }
            }
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = group.name,
                style = MaterialTheme.typography.titleSmall,
                color = colors.primary,
                modifier = Modifier.padding(start = 6.dp, bottom = 6.dp)
            )
            Spacer(Modifier.weight(1f))
            group.subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp, bottom = 6.dp)
                )
            }
        }
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer)
        ) {
            Column {
                group.note?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
                // 各选项之间不再画横向分割线（HorizontalDivider），靠行内 56dp 最小高度 +
                // 卡片底色区分；同组多行直接相邻堆叠。
                group.header?.let {
                    Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        it()
                    }
                }
                group.items.forEach { item ->
                    SettingRow(item)
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
        if (item.iconContent != null) {
            item.iconContent()
        } else {
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

/** 行尾齿轮：18dp 裸 Icon + clickable，footprint 与默认右箭头完全一致，故与其他箭头行右对齐。 */
@Composable
private fun GearButton(contentDescription: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Icon(
        imageVector = SettingsGear,
        contentDescription = contentDescription,
        modifier = Modifier
            .size(18.dp)
            .clickable(onClick = onClick),
        tint = colors.onSurfaceVariant
    )
}

data class SettingItemData(
    val title: String,
    val subtitle: String? = null,
    val imageVector: ImageVector,
    val onClick: (() -> Unit)? = null,
    val trailingContent: @Composable (() -> Unit)? = null,
    val danger: Boolean = false,
    val warning: String? = null,
    /** 自定义图标（头像等）；给了就用它，忽略 [imageVector]。 */
    val iconContent: (@Composable () -> Unit)? = null,
)

data class SettingGroupData(
    val name: String,
    val items: List<SettingItemData>,
    val note: String? = null,
    val subtitle: String? = null,
    /** 卡片上方/下方的自定义内容（如音源三选一小卡片）。 */
    val header: (@Composable () -> Unit)? = null
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
    ScaledDialogContent {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("更换更新源") },
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
    onDeactivate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var testing by remember { mutableStateOf(false) }
    var newServer by remember { mutableStateOf("") }
    val currentNotListed = currentServer.isNotBlank() && currentServer !in servers

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

    ScaledDialogContent {
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { if (!testing) onDeactivate() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentServer.isBlank(),
                        onClick = { if (!testing) onDeactivate() }
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            "不使用自建服务器",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "走官方直连 API，不经过任何自建/代理服务器",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
}

@Composable
private fun CookieEditDialog(
    currentCookie: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var editedCookie by rememberSaveable(currentCookie) { mutableStateOf(currentCookie) }

    ScaledDialogContent {
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
}

private fun String.toCookieHeader(): String {
    if (isBlank()) return ""
    return runCatching {
        json.decodeFromString<Map<String, String>>(this)
            .entries
            .joinToString("; ") { (key, value) -> "$key=$value" }
    }.getOrElse { this }
}
