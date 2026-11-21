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
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.Tune
import android.content.Intent
import androidx.documentfile.provider.DocumentFile
import com.jussicodes.music.constants.downloadCoverKey
import com.jussicodes.music.constants.downloadCustomDirNameKey
import com.jussicodes.music.constants.downloadCustomDirUriKey
import com.jussicodes.music.constants.downloadLrcKey
import com.jussicodes.music.constants.downloadQualityKey
import com.jussicodes.music.constants.downloadWifiOnlyKey
import com.jussicodes.music.data.AccountAddFlow
import com.jussicodes.music.data.AccountStore
import com.jussicodes.music.data.SavedAccount
import com.jussicodes.music.data.download.DownloadStatus
import com.jussicodes.music.data.download.DownloadStore
import com.jussicodes.music.ui.components.AccountSwitcherSheet
import com.jussicodes.music.ui.components.DownloadQualityDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.jussicodes.music.utils.CoverImageSize
import com.jussicodes.music.utils.toCoverImageUrl
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.jussicodes.music.BuildConfig
import com.jussicodes.music.LocalUiScaleController
import com.jussicodes.music.R
import com.jussicodes.music.constants.MiniPlayerHeight
import com.jussicodes.music.constants.apiBaseUrlKey
import com.jussicodes.music.constants.apiServerListKey
import com.jussicodes.music.constants.audioQualityKey
import com.jussicodes.music.constants.playWithOthersKey
import com.jussicodes.music.constants.cacheAutoCleanEnabledKey
import com.jussicodes.music.constants.cacheAutoCleanThresholdKey
import com.jussicodes.music.constants.desktopLyricEnabledKey
import com.jussicodes.music.constants.desktopLyricFontSizeKey
import com.jussicodes.music.constants.desktopLyricLockedKey
import com.jussicodes.music.constants.ignoredUpdateVersionKey
import com.jussicodes.music.constants.audioSourceKey
import com.jussicodes.music.constants.githubDownloadSourceKey
import com.jussicodes.music.constants.resolveAllSongsKey
import com.jussicodes.music.constants.ncmCookieKey
import com.jussicodes.music.constants.pinnedAlbumsHiddenKey
import com.jussicodes.music.constants.themeColorSourceKey
import com.jussicodes.music.constants.themePrimaryColorKey
import com.jussicodes.music.constants.themeSecondaryColorKey
import com.jussicodes.music.constants.homeBgImageKey
import com.jussicodes.music.constants.unblockSourceKey
import com.jussicodes.music.constants.updateLastErrorKey
import com.jussicodes.music.lyric.DesktopLyricManager
import com.jussicodes.music.lyric.DesktopLyricDefaultFontSizeSp
import com.jussicodes.music.ui.components.ScaledDialogContent
import com.jussicodes.music.ui.components.PlayerGestureTutorialDialog
import com.jussicodes.music.ui.components.Dialog
import com.jussicodes.music.ui.components.ResponsiveAlertDialog
import com.jussicodes.music.ui.components.SongQualityDialog
import com.jussicodes.music.ui.components.UnblockSourceDialog
import com.jussicodes.music.ui.components.UpdateDialog
import com.jussicodes.music.ui.components.unblockSourceOptions
import com.jussicodes.music.ui.icons.Album
import com.jussicodes.music.ui.icons.AudioLines
import com.jussicodes.music.ui.icons.DesktopLyrics
import androidx.compose.material.icons.outlined.Dns
import com.jussicodes.music.ui.icons.Gesture
import com.jussicodes.music.ui.icons.Github
import com.jussicodes.music.ui.icons.ChevronDown
import com.jussicodes.music.ui.icons.Plus
import com.jussicodes.music.ui.icons.MusicNote
import com.jussicodes.music.ui.icons.Palette
import com.jussicodes.music.ui.icons.Remove
import com.jussicodes.music.ui.icons.Script
import com.jussicodes.music.ui.icons.UserRound
import com.jussicodes.music.ui.navigation.Screen
import com.jussicodes.music.ui.theme.ThemeColorSource
import com.jussicodes.music.utils.AppUpdateManager
import com.jussicodes.music.utils.CacheManager
import com.jussicodes.music.utils.SettingsAppearanceBus
import com.jussicodes.music.utils.NcmCookieNormalizer
import com.jussicodes.music.utils.ApiServerStatus
import com.jussicodes.music.utils.UpdateCheckCoordinator
import com.jussicodes.music.utils.UpdateDownloadPhase
import com.jussicodes.music.utils.UpdateDownloadService
import com.jussicodes.music.utils.UpdateDownloadStateStore
import com.jussicodes.music.utils.UpdateInfo
import com.jussicodes.music.utils.apiServers
import com.jussicodes.music.utils.downloadSources
import com.jussicodes.music.utils.normalizedSourceId
import com.jussicodes.music.utils.rememberEnumPreference
import com.jussicodes.music.utils.rememberPreference
import com.jussicodes.music.widget.WidgetDiagDialog
import com.rcmiku.ncmapi.api.account.AccountApi
import com.rcmiku.ncmapi.api.AudioSources
import com.rcmiku.ncmapi.api.player.PlayerApi
import com.jussicodes.music.lx.AudioSourceSwitcher
import com.jussicodes.music.ui.components.AudioSourceSelector
import com.jussicodes.music.ui.components.CacheManagerDialog
import com.jussicodes.music.ui.components.cacheSubtitle
import com.rcmiku.ncmapi.api.player.SongLevel
import com.rcmiku.ncmapi.utils.json
import com.rcmiku.ncmapi.utils.parseCookieString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.decodeFromString
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavHostController,
    onBackClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val dynamicColorAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    var desktopLyricEnabled by rememberPreference(desktopLyricEnabledKey, false)
    var desktopLyricFontSize by rememberPreference(desktopLyricFontSizeKey, DesktopLyricDefaultFontSizeSp)
    var desktopLyricLocked by rememberPreference(desktopLyricLockedKey, false)
    var audioQuality by rememberEnumPreference(audioQualityKey, defaultValue = SongLevel.STANDARD)
    // 与其他音频同时播放（默认开）：不申请音频焦点，可与其它应用同时出声。
    var playWithOthers by rememberPreference(playWithOthersKey, true)
    var themeColorSource by rememberEnumPreference(
        themeColorSourceKey,
        defaultValue = ThemeColorSource.WALLPAPER,
    )
    val themePrimaryColor by rememberPreference(themePrimaryColorKey, 0L)
    val themeSecondaryColor by rememberPreference(themeSecondaryColorKey, 0L)
    var homeBgPath by rememberPreference(homeBgImageKey, "")
    var ncmCookie by rememberPreference(ncmCookieKey, "")
    var pinnedAlbumsHidden by rememberPreference(pinnedAlbumsHiddenKey, false)
    var apiBaseUrl by rememberPreference(apiBaseUrlKey, "")
    var unblockSource by rememberPreference(unblockSourceKey, "AUTO")
    // 音源选择（LX / 代理）。
    var audioSource by rememberPreference(audioSourceKey, AudioSources.DEFAULT)
    LaunchedEffect(audioSource) {
        if (audioSource == AudioSources.DIRECT) {
            audioSource = AudioSources.DEFAULT
        }
    }
    var resolveAllSongs by rememberPreference(resolveAllSongsKey, false)
    // 两行子设置各自报「当前正在使用谁」，按 audioSource 档位走：
    val lxPlugins by com.jussicodes.music.lx.LxPluginManager.plugins.collectAsState(initial = emptyList())
    val enabledLxScripts = lxPlugins.filter { it.isEnabled }
    // 当前真正负责解析的脚本名：LX 档位取列表首个启用项（PlayerApi 也是按顺序轮询）
    val activeLxScriptName = enabledLxScripts.firstOrNull()?.name
    val inUseLabel = when (audioSource) {
        AudioSources.LX -> "LX源"
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
    val apiServerSummary = if (audioSource == AudioSources.PROXY) {
        apiBaseUrl.ifBlank { "未设置" }
    } else {
        "当前正在使用 $inUseLabel"
    }
    var ignoredUpdateVersion by rememberPreference(ignoredUpdateVersionKey, "")
    // 检查更新的可见状态：只留失败原因（失败不再静默）。
    var updateLastError by rememberPreference(updateLastErrorKey, "")
    var showGestureTutorialDialog by rememberSaveable { mutableStateOf(false) }
    var showWidgetDiagDialog by rememberSaveable { mutableStateOf(false) }
    val uiScaleController = LocalUiScaleController.current
    var githubDownloadSource by rememberPreference(
        githubDownloadSourceKey,
        downloadSources.first().id
    )
    // 主页背景编辑"放弃"后回来：自动进入外观页（一次性消费，手动进设置页不会重复导航）。
    LaunchedEffect(Unit) {
        SettingsAppearanceBus.requested.collectLatest { open ->
            if (open) {
                navController.navigate(Screen.AppearanceSettings.route) {
                    launchSingleTop = true
                }
                SettingsAppearanceBus.consume()
            }
        }
    }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showUnblockSourceDialog by remember { mutableStateOf(false) }
    var showCacheDialog by remember { mutableStateOf(false) }

    val downloadTasks by DownloadStore.tasks.collectAsState()
    val completedDownloadCount = remember(downloadTasks) {
        downloadTasks.count { it.status == DownloadStatus.COMPLETED }
    }
    val activeDownloadCount = remember(downloadTasks) {
        downloadTasks.count { it.status != DownloadStatus.COMPLETED }
    }
    val downloadSubtitle = when {
        activeDownloadCount > 0 -> "正在下载 $activeDownloadCount 首 · 已下载 $completedDownloadCount 首"
        completedDownloadCount > 0 -> "已下载 $completedDownloadCount 首歌曲"
        else -> "管理本地已下载歌曲"
    }

    // 「缓存」：进设置页先异步测一次当前占用，之后开弹窗/清理后刷新。
    var cacheSnapshot by remember { mutableStateOf<CacheManager.Snapshot?>(null) }
    var cacheMeasuring by remember { mutableStateOf(false) }
    var cacheClearing by remember { mutableStateOf(false) }
    var cacheAutoCleanEnabled by rememberPreference(cacheAutoCleanEnabledKey, false)
    var cacheAutoCleanThreshold by rememberPreference(cacheAutoCleanThresholdKey, 1024L * 1024 * 1024)
    val cacheSubtitle = cacheSubtitle(
        enabled = cacheAutoCleanEnabled,
        thresholdBytes = cacheAutoCleanThreshold,
        currentBytes = cacheSnapshot?.totalBytes ?: 0L,
    )
    fun refreshCache() {
        if (cacheMeasuring) return
        cacheMeasuring = true
        coroutineScope.launch {
            cacheSnapshot = CacheManager.measure(context)
            cacheMeasuring = false
        }
    }



    // 切换音源后重解析当前播放的歌：清掉 ResolvingDataSource 的解析缓存（cacheKey
    // 只含音质，不含音源），重新 prepare 当前 MediaItem 让它走一遍新的解析链。
    fun reloadCurrentSong() {
        coroutineScope.launch {
            // rememberPreference 是异步写盘，延迟一下让 DataStore 先落定，
            // 否则 JetMeloApp 的 collect 可能用旧值把 ncmapi 全局变量覆盖回去。
            kotlinx.coroutines.delay(150)
            AudioSourceSwitcher.apply(audioSource)
        }
    }
    var showCookieDialog by remember { mutableStateOf(false) }
    var showGithubSourceDialog by remember { mutableStateOf(false) }
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
    var updating by remember { mutableStateOf(false) }
    var pendingUpdateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var downloadingUpdate by remember { mutableStateOf(false) }
    var updateDownloadProgress by remember { mutableFloatStateOf(0f) }
    var updateDownloadText by remember { mutableStateOf<String?>(null) }
    var logout by rememberSaveable { mutableStateOf(false) }
    var showAccountSheet by rememberSaveable { mutableStateOf(false) }
    var accountToRemove by remember { mutableStateOf<SavedAccount?>(null) }
    val accounts by remember(context) { AccountStore.accounts(context) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val activeAccount by remember(context) { AccountStore.activeAccount(context) }
        .collectAsStateWithLifecycle(initialValue = null)
    var overlayPermissionGranted by remember {
        mutableStateOf(DesktopLyricManager.canDrawOverlays(context))
    }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var accountAvatar by rememberSaveable { mutableStateOf("") }
    var accountNickname by rememberSaveable { mutableStateOf("") }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayPermissionGranted = DesktopLyricManager.canDrawOverlays(context)
                // 授权页回来后按已保存的开关状态恢复；权限没变时这是空操作。
                DesktopLyricManager.syncService(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 当前账号资料加载完成后，补齐旧版本只保存 Cookie 的账号记录。
    LaunchedEffect(ncmCookie) {
        accountNickname = ""
        accountAvatar = ""
        if (ncmCookie.isBlank()) return@LaunchedEffect
        val profile = AccountApi.account().getOrNull()?.account?.profile
        accountNickname = profile?.nickname.orEmpty()
        accountAvatar = profile?.avatarUrl.orEmpty()
        val cookieMap = runCatching {
            json.decodeFromString<Map<String, String>>(ncmCookie)
        }.getOrNull()
        if (profile != null && profile.userId != 0L && cookieMap != null) {
            try {
                AccountStore.ensureMigrated(
                    context, cookieMap, profile.userId, profile.nickname.orEmpty(),
                    profile.avatarUrl.orEmpty(),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                Toast.makeText(context, "账号保存失败，请稍后重试", Toast.LENGTH_SHORT).show()
            }
        }
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
                        // 同一份下载只允许拉起一次安装器（主界面也 collect 同一个 store）。
                        if (UpdateDownloadStateStore.claimInstallLaunch()) {
                            AppUpdateManager.installApk(context, File(apkPath))
                                .onFailure { throwable ->
                                    Toast.makeText(
                                        context,
                                        throwable.message ?: "无法打开安装器",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
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
                UpdateDownloadPhase.CANCELLED -> {
                    // 用户主动取消：回到可重新下载状态。
                    downloadingUpdate = false
                    updateDownloadProgress = 0f
                    updateDownloadText = "下载已取消"
                }
            }
        }
    }

    // 「缓存」：进设置页先测一次当前占用（副标题显示），弹窗打开时再刷新一次。
    LaunchedEffect(Unit) { refreshCache() }
    LaunchedEffect(showCacheDialog) {
        if (showCacheDialog) refreshCache()
    }

    // 桌面歌词有效开关 = 存储开启 && 悬浮窗权限已授予
    val lyricOn = desktopLyricEnabled && overlayPermissionGranted

    val appearanceTitle = "外观"
    val appearanceSubtitle = if (themePrimaryColor != 0L || themeSecondaryColor != 0L) "正在使用自定义配色" else when (themeColorSource) {
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
    val appearanceWithScale = "$appearanceSubtitle · 界面 ${(uiScaleController.scale * 100).toInt()}% · 字体 ${(uiScaleController.fontScale * 100).toInt()}%"
    val accountCookie = remember(ncmCookie) { ncmCookie.toCookieHeader() }
    val accountCookieMap = remember(accountCookie) { parseCookieString(accountCookie) }
    val loggedOut = ncmCookie.isBlank()
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
                // 有账号记录时，无论当前是否登录都进抽屉：退出登录只是清掉当前凭证，
                // 账号列表还在，用户要能从这里直接切回去。
                title = when {
                    !loggedOut -> accountNickname.ifBlank {
                        activeAccount?.nickname.orEmpty().ifBlank { "多账号管理" }
                    }
                    accounts.isNotEmpty() -> "未登录"
                    else -> "多账号管理"
                },
                imageVector = Plus,
                subtitle = when {
                    loggedOut && accounts.isEmpty() -> "未登录"
                    loggedOut -> "共 ${accounts.size} 个账号 · 点击切换"
                    else -> "当前使用 · 共 ${accounts.size} 个账号"
                },
                iconContent = {
                    // 未登录但有账号时用第一条账号的头像，视觉上仍然是「账号分组」。
                    val avatar = if (!loggedOut) {
                        accountAvatar.ifBlank { activeAccount?.avatarUrl.orEmpty() }
                    } else {
                        accounts.firstOrNull()?.avatarUrl.orEmpty()
                    }
                    if (avatar.isNotBlank()) {
                        AsyncImage(
                            model = avatar.toCoverImageUrl(CoverImageSize.LIST),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Plus,
                                contentDescription = "登录账号",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                trailingContent = if (accounts.isEmpty()) null else {
                    {
                        Icon(
                            imageVector = ChevronDown,
                            contentDescription = "切换账号",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                onClick = {
                    if (accounts.isEmpty()) {
                        navController.navigate(Screen.Login.route)
                    } else {
                        showAccountSheet = true
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
                imageVector = DeepSettingPage.APPEARANCE.imageVector,
                onClick = { navController.navigate(Screen.AppearanceSettings.route) }
            ),
            SettingItemData(
                title = "桌面歌词",
                subtitle = (if (lyricOn) {
                    "已开启 · ${desktopLyricFontSize.toInt()}sp" +
                        if (desktopLyricLocked) " · 已锁定" else ""
                } else if (desktopLyricEnabled) {
                    "已开启但缺少悬浮窗权限"
                } else {
                    "已关闭"
                }) + " · 点击进入设置",
                imageVector = DeepSettingPage.DESKTOP_LYRICS.imageVector,
                trailingContent = {
                    Switch(
                        checked = lyricOn,
                        onCheckedChange = { on ->
                            coroutineScope.launch {
                                DesktopLyricManager.setEnabled(
                                    context = context,
                                    enabled = on,
                                    requestPermissionIfNeeded = on
                                )
                            }
                        }
                    )
                    Spacer(Modifier.width(12.dp))
                },
                onClick = { navController.navigate(Screen.DesktopLyricSettings.route) }
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
                title = "播放页手势指南",
                subtitle = "查看播放器常用手势说明",
                imageVector = Gesture,
                onClick = {
                    showGestureTutorialDialog = true
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
                title = "与其他音频同时播放",
                subtitle = if (playWithOthers) {
                    "不占用音频焦点，可与其它应用同时出声"
                } else {
                    "独占音频焦点，其它应用出声时会自动暂停"
                },
                imageVector = Icons.Outlined.Tune,
                trailingContent = {
                    Switch(
                        checked = playWithOthers,
                        onCheckedChange = { playWithOthers = it }
                    )
                    Spacer(Modifier.width(12.dp))
                },
                onClick = { playWithOthers = !playWithOthers }
            ),
            SettingItemData(
                title = "缓存",
                subtitle = cacheSubtitle,
                imageVector = Icons.Outlined.Storage,
                onClick = { showCacheDialog = true }
            )
        )
    )

    val downloadGroup = SettingGroupData(
        name = "下载",
        items = listOf(
            SettingItemData(
                title = "下载管理",
                subtitle = downloadSubtitle,
                imageVector = DeepSettingPage.DOWNLOADS.imageVector,
                onClick = { navController.navigate(Screen.DownloadManager.route) }
            )
        )
    )

    val advancedGroup = SettingGroupData(
        name = "音源设置",
        note = "两种音源只生效一个；切换后立即用新音源重新解析当前播放的歌",
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
                title = "全部歌曲走音源解析",
                subtitle = if (resolveAllSongs) "非 VIP 歌曲也优先通过音源解析高音质，未命中兜底官方" else "默认仅 VIP/无版权歌曲走音源解析",
                imageVector = MusicNote,
                trailingContent = {
                    Switch(
                        checked = resolveAllSongs,
                        onCheckedChange = { on ->
                            resolveAllSongs = on
                            com.rcmiku.ncmapi.api.AudioSourceSettings.RESOLVE_ALL = on
                            reloadCurrentSong()
                        }
                    )
                    Spacer(Modifier.width(12.dp))
                },
                onClick = {
                    resolveAllSongs = !resolveAllSongs
                    com.rcmiku.ncmapi.api.AudioSourceSettings.RESOLVE_ALL = resolveAllSongs
                    reloadCurrentSong()
                }
            ),
            SettingItemData(
                title = "LX 脚本管理",
                subtitle = lxScriptSummary,
                imageVector = DeepSettingPage.LX_PLUGINS.imageVector,
                onClick = { navController.navigate(Screen.LxPlugins.route) }
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
        items = buildList {
            addAll(listOf(
            SettingItemData(
                title = "jussichords",
                subtitle = "简洁的第三方网易云音乐客户端",
                imageVector = MusicNote,
                trailingContent = {
                    val uri = android.net.Uri.parse("https://github.com/easyTIDollar/jussichords")
                    Icon(
                        imageVector = Icons.Outlined.OpenInNew,
                        contentDescription = "打开项目主页",
                        modifier = Modifier
                            .size(18.dp)
                            .clickable {
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            uri
                                        )
                                    )
                                }
                            },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            ),
            SettingItemData(
                title = if (updating) "正在检查" else "检查更新",
                subtitle = "v${BuildConfig.VERSION_NAME}",
                // 上次检查失败的原因直接挂在这一行：之前失败是完全静默的，
                // 用户只会看到「没有更新提示」，不知道是被限流还是网络问题。
                warning = updateLastError.takeIf { it.isNotBlank() },
                imageVector = Github,
                onClick = {
                    if (!updating) {
                        updating = true
                        coroutineScope.launch {
                            // 手动检查强制联网（force=true），无视 6 小时节流窗口。
                            val outcome = UpdateCheckCoordinator.check(context, force = true)
                            updating = false
                            val info = outcome.updateInfo
                            if (outcome.error != null) {
                                Toast.makeText(
                                    context,
                                    "更新检查失败：${outcome.error}",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else if (info == null) {
                                Toast.makeText(context, "当前已是最新版本", Toast.LENGTH_SHORT).show()
                            } else {
                                pendingUpdateInfo = info
                            }
                        }
                    }
                },
                trailingContent = {
                    GearButton("更换更新源") {
                        showGithubSourceDialog = true
                    }
                }
            )
            ))
            add(
                SettingItemData(
                    title = "小组件诊断",
                    imageVector = Script,
                    onClick = { showWidgetDiagDialog = true },
                )
            )
        }
    )

    val groups = listOf(accountGroup, downloadGroup, uiGroup, playbackGroup, advancedGroup, aboutGroup)

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

    val deepMatches = remember(query, navController) {
        if (query.isEmpty()) {
            emptyList()
        } else {
            deepSettingIndex.flatMap { (page, entries) ->
                entries
                    .filter { entry ->
                        entry.title.lowercase().contains(query) ||
                            entry.keywords.any { it.lowercase().contains(query) }
                    }
                    .map { entry ->
                        SettingItemData(
                            title = entry.title,
                            subtitle = "${page.title} · 点按进入",
                            imageVector = page.imageVector,
                            onClick = { navController.navigate(page.route) }
                        )
                    }
            }
        }
    }
    val searchGroups = if (deepMatches.isEmpty()) {
        visibleGroups
    } else {
        visibleGroups + SettingGroupData(name = "其它设置", items = deepMatches)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (onBackClick != null) {
                            onBackClick()
                        } else {
                            navController.navigateUp()
                        }
                    }) {
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

            items(searchGroups) { group ->
                SettingGroupCard(group)
            }
        }
    }

    if (showQualityDialog) {
        SongQualityDialog(
            currentLevel = audioQuality,
            onDismiss = { showQualityDialog = false },
            onQualitySelected = { selectedLevel ->
                audioQuality = selectedLevel
                coroutineScope.launch {
                    kotlinx.coroutines.delay(100)
                    com.jussicodes.music.playback.PlaybackReloadBus.requestReload()
                }
            }
        )
    }

    if (showCacheDialog) {
        CacheManagerDialog(
            snapshot = cacheSnapshot,
            measuring = cacheMeasuring,
            clearing = cacheClearing,
            autoCleanEnabled = cacheAutoCleanEnabled,
            autoCleanThresholdBytes = cacheAutoCleanThreshold,
            onMeasure = { refreshCache() },
            onClear = {
                cacheClearing = true
                coroutineScope.launch {
                    val ok = CacheManager.clear(context)
                    cacheClearing = false
                    // 清理后再测一次，让环形图/副标题立刻反映新占用（含索引未即时释放的残余）。
                    cacheSnapshot = CacheManager.measure(context)
                    Toast.makeText(
                        context,
                        if (ok) "缓存已清理" else "部分缓存清理失败，可重试",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onAutoCleanEnabledChange = { cacheAutoCleanEnabled = it },
            onThresholdSelected = { cacheAutoCleanThreshold = it },
            onDismiss = { showCacheDialog = false },
        )
    }


    if (showAccountSheet) {
        AccountSwitcherSheet(
            accounts = accounts,
            activeUserId = activeAccount?.userId ?: Long.MIN_VALUE,
            loggedIn = !loggedOut,
            onDismiss = { showAccountSheet = false },
            onSwitch = { account ->
                coroutineScope.launch {
                    try {
                        AccountStore.setActive(context, account)
                        showAccountSheet = false
                        Toast.makeText(
                            context, "已切换到 ${account.nickname.ifBlank { "未命名账号" }}",
                            Toast.LENGTH_SHORT,
                        ).show()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        Toast.makeText(context, "切换账号失败，请稍后重试", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onRemove = { account ->
                showAccountSheet = false
                accountToRemove = account
            },
            onAddAccount = {
                showAccountSheet = false
                AccountAddFlow.addAccountMode = true
                navController.navigate(Screen.Login.route)
            },
            onLogout = {
                showAccountSheet = false
                logout = true
            },
        )
    }

    accountToRemove?.let { account ->
        Dialog(
            onConfirmation = {
                coroutineScope.launch {
                    try {
                        AccountStore.remove(context, account.userId)
                        accountToRemove = null
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        Toast.makeText(context, "移除账号失败，请稍后重试", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismissRequest = { accountToRemove = null },
            dialogTitle = "移除账号",
            dialogText = "确定移除 ${account.nickname.ifBlank { "未命名账号" }}？移除后需重新登录。",
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
            dialogTitle = "切到游客模式",
            dialogText = "只清除当前账号的登录态，账号列表会保留，可随时切回",
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

    if (showGestureTutorialDialog) {
        PlayerGestureTutorialDialog(
            onDismiss = { showGestureTutorialDialog = false }
        )
    }

    if (showWidgetDiagDialog) {
        WidgetDiagDialog(onDismiss = { showWidgetDiagDialog = false })
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
            currentSourceId = normalizedSourceId(githubDownloadSource),
            onDismiss = { showGithubSourceDialog = false },
            onSourceSelected = { githubDownloadSource = it }
        )
    }

    pendingUpdateInfo?.let { updateInfo ->
        UpdateDialog(
            updateInfo = updateInfo,
            isDownloading = downloadingUpdate,
            downloadProgress = if (downloadingUpdate) updateDownloadProgress else null,
            progressText = updateDownloadText,
            selectedSourceId = normalizedSourceId(githubDownloadSource),
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
                    normalizedSourceId(githubDownloadSource)
                )
            },
            onCancelDownload = {
                UpdateDownloadService.cancelDownload()
                downloadingUpdate = false
                updateDownloadProgress = 0f
                updateDownloadText = "下载已取消"
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
        painter = painterResource(R.drawable.ic_settings_gear),
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

private enum class DeepSettingPage(
    val title: String,
    val route: String,
    private val icon: () -> ImageVector
) {
    APPEARANCE("外观", Screen.AppearanceSettings.route, { Palette }),
    DESKTOP_LYRICS("桌面歌词", Screen.DesktopLyricSettings.route, { DesktopLyrics }),
    LX_PLUGINS("LX 脚本音源", Screen.LxPlugins.route, { Script }),
    DOWNLOADS("下载管理", Screen.DownloadManager.route, { Icons.Outlined.FileDownload });

    val imageVector: ImageVector get() = icon()
}

private data class DeepSettingEntry(val title: String, val keywords: List<String>)

private val deepSettingIndex by lazy {
    mapOf(
        DeepSettingPage.APPEARANCE to listOf(
            DeepSettingEntry("显示模式", listOf("主题模式", "日间夜间", "亮暗切换")),
            DeepSettingEntry("跟随系统", listOf("系统主题", "自动切换", "随系统")),
            DeepSettingEntry("浅色", listOf("亮色", "白天", "日间模式")),
            DeepSettingEntry("深色", listOf("暗色", "夜间", "暗黑模式")),
            DeepSettingEntry("AMOLED 深色", listOf("夜间", "纯黑", "黑色主题", "OLED")),
            DeepSettingEntry("主题颜色", listOf("主题配色", "界面颜色", "主题取色")),
            DeepSettingEntry("根据壁纸动态取色", listOf("壁纸取色", "系统原色", "动态配色")),
            DeepSettingEntry("跟随封面", listOf("从当前播放封面取色", "专辑配色", "歌曲封面")),
            DeepSettingEntry("提取主页", listOf("从主页背景图取色", "背景配色", "主页取色")),
            DeepSettingEntry("主色", listOf("主颜色", "主要配色", "primary")),
            DeepSettingEntry("副色", listOf("次要颜色", "辅助配色", "secondary")),
            // 外观页用色轮呈现调色盘，控件本身没有文字标签。
            DeepSettingEntry("调色盘", listOf("色轮", "自定义配色", "选色器")),
            DeepSettingEntry("明度", listOf("颜色亮度", "色彩深浅", "HSV")),
            DeepSettingEntry("恢复取色", listOf("重置颜色", "取消自定义配色", "恢复默认配色")),
            DeepSettingEntry("悬浮胶囊底栏", listOf("悬浮导航栏", "胶囊样式", "贴底通栏")),
            DeepSettingEntry("上滑背景高斯模糊", listOf("背景虚化", "动态模糊", "景深遮罩")),
            DeepSettingEntry("主页透明卡片", listOf("半透明卡片", "毛玻璃", "主页卡片透明度")),
            DeepSettingEntry("界面缩放", listOf("界面大小", "显示比例", "界面与字体缩放")),
            DeepSettingEntry("字体大小", listOf("字号", "字体缩放", "文字大小", "界面与字体缩放")),
            DeepSettingEntry("跟随默认", listOf("默认歌词字号", "歌词字体还原", "原有歌词大小")),
            DeepSettingEntry("歌词字号", listOf("歌词字体大小", "歌词缩放", "歌词大小")),
            DeepSettingEntry("字体颜色", listOf("文字配色", "主页文字效果", "主页文字颜色")),
            DeepSettingEntry("柔和轮廓", listOf("文字描边", "字体阴影", "主页文字效果")),
            DeepSettingEntry("主页背景", listOf("主页壁纸", "背景图", "首页背景")),
            DeepSettingEntry("选取图片", listOf("更换", "更换背景", "选择壁纸", "相册")),
            DeepSettingEntry("在主页调整", listOf("调整壁纸", "背景位置", "背景缩放")),
            DeepSettingEntry("移除", listOf("移除背景", "删除壁纸", "清除主页背景"))
        ),
        DeepSettingPage.DESKTOP_LYRICS to listOf(
            DeepSettingEntry("点预览换背景图", listOf("歌词预览", "预览背景", "更换预览图")),
            DeepSettingEntry("恢复默认", listOf("重置预览背景", "还原预览图", "移除预览图")),
            DeepSettingEntry("启用桌面歌词", listOf("开启悬浮歌词", "歌词开关", "悬浮窗权限")),
            DeepSettingEntry("锁定位置", listOf("固定歌词", "触摸穿透", "通知栏开锁")),
            DeepSettingEntry("重置位置", listOf("还原位置", "默认位置", "歌词位置")),
            DeepSettingEntry("歌词大小", listOf("桌面歌词字号", "桌面歌词字体大小", "歌词缩放")),
            DeepSettingEntry("描边粗细", listOf("描边宽度", "歌词轮廓", "边框厚度")),
            DeepSettingEntry("副行（翻译/下一句）随主行等比缩放", listOf("行数", "双行", "歌词翻译", "下一句")),
            DeepSettingEntry("字体颜色", listOf("桌面歌词字体", "文字颜色", "歌词配色")),
            DeepSettingEntry("描边颜色", listOf("歌词边框颜色", "轮廓配色", "描边色")),
            DeepSettingEntry("自定义颜色", listOf("调色盘", "自选颜色", "色轮")),
            DeepSettingEntry("亮度", listOf("明度", "色彩深浅", "颜色亮度"))
        ),
        DeepSettingPage.LX_PLUGINS to listOf(
            DeepSettingEntry("导入文件", listOf("本地脚本", "导入音源", "JS 文件")),
            DeepSettingEntry("通过链接导入", listOf("URL 导入", "脚本地址", "远程导入")),
            DeepSettingEntry("在线音源列表", listOf("在线脚本", "音源仓库", "下载音源")),
            DeepSettingEntry("长按卡片可拖动排序", listOf("音源优先级", "脚本顺序", "重新排序")),
            DeepSettingEntry("选择", listOf("批量管理", "多选脚本", "全选")),
            DeepSettingEntry("启用", listOf("开启脚本", "激活音源", "批量启用")),
            DeepSettingEntry("停用", listOf("禁用脚本", "关闭音源", "批量停用")),
            DeepSettingEntry("删除", listOf("移除脚本", "删除音源", "批量删除")),
            DeepSettingEntry("测试解析", listOf("音源多档位解析测试", "测试直链", "音源测速")),
            DeepSettingEntry(
                "在独立 WebView 沙盒里运行 LX Music 社区脚本，按列表顺序依次尝试解析直链。",
                listOf("LX 沙盒", "音源工作方式", "解析顺序")
            ),
            DeepSettingEntry(
                "内置音源仅作参考，不保证可用，请自行导入所需音源。",
                listOf("内置脚本", "音源说明", "第三方音源")
            )
        ),
        DeepSettingPage.DOWNLOADS to listOf(
            DeepSettingEntry("已下载", listOf("离线歌曲", "本地音乐", "下载完成")),
            DeepSettingEntry("下载中", listOf("下载任务", "传输队列", "下载进度")),
            DeepSettingEntry("搜索已下载歌曲", listOf("搜索本地歌曲", "查找离线音乐", "搜索下载")),
            DeepSettingEntry("打开系统文件夹", listOf("文件管理器", "查看下载目录", "下载文件夹")),
            DeepSettingEntry("下载偏好设置", listOf("下载选项", "下载配置", "下载设置")),
            DeepSettingEntry("默认下载音质", listOf("默认音质", "下载品质", "下载档位")),
            DeepSettingEntry("音源下载能力体检", listOf("音源体检", "检测下载音质", "测试下载")),
            DeepSettingEntry("存储目录", listOf("保存路径", "下载位置", "存放目录")),
            DeepSettingEntry("更改目录", listOf("修改下载路径", "选择下载文件夹", "更换存储位置")),
            DeepSettingEntry("同时下载任务数", listOf("同时下载数", "并发下载", "并行任务")),
            DeepSettingEntry("仅在 Wi-Fi 下下载", listOf("仅在 Wi-Fi 下载", "仅无线网下载", "移动数据限制")),
            DeepSettingEntry("内嵌歌词到音频文件", listOf("LRC", "歌词标签", "写入歌词")),
            DeepSettingEntry("下载歌词翻译", listOf("双语歌词", "下载译文", "歌词翻译")),
            DeepSettingEntry("内嵌高清封面到音频文件", listOf("嵌入专辑封面", "写入封面", "封面标签")),
            DeepSettingEntry("升级音质", listOf("重新下载", "提高下载音质", "批量升级")),
            DeepSettingEntry("加入分组", listOf("移至分组", "归类歌曲", "移动下载文件")),
            DeepSettingEntry("新建分组", listOf("创建分组", "本地文件夹", "下载分类")),
            DeepSettingEntry("导入外部歌单", listOf("导入本地音乐", "扫描文件夹", "外部播放列表")),
            DeepSettingEntry("批量删除已下载歌曲", listOf("删除选中项", "移除下载文件", "清理离线歌曲")),
            DeepSettingEntry("全部暂停", listOf("暂停所有任务", "批量暂停", "暂停下载")),
            DeepSettingEntry("全部开始", listOf("继续全部下载", "恢复下载", "批量开始")),
            DeepSettingEntry("换档重试", listOf("重试下载", "切换音质", "重新下载失败任务"))
        )
    )
}

@Composable
private fun GitHubDownloadSourceDialog(
    currentSourceId: String,
    onDismiss: () -> Unit,
    onSourceSelected: (String) -> Unit,
) {
    ScaledDialogContent {
    ResponsiveAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("更换更新源") },
        text = {
            Column {
                downloadSources.forEach { source ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSourceSelected(source.id) },
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
                                text = source.detail,
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
        }
    )
    }
}

/**
 * 可增删的 ncmapi 后端列表对话框。
 *
 * 列表持久化在 DataStore（apiServerListKey）：可添加/删除自定义地址。点行或单选
 * 即激活该服务器；「重新 Ping」并行 Ping 列表全部地址。当前活动服务器若不在列表
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
    ResponsiveAlertDialog(
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        server,
                                        style = MaterialTheme.typography.titleMedium,
                                        overflow = TextOverflow.Ellipsis,
                                        maxLines = 1
                                    )
                                    // 测速中：地址后面挂一个小转圈，不写「Ping 中…」文字。
                                    if (testing && status == null) {
                                        CircularProgressIndicator(
                                            modifier = Modifier
                                                .padding(start = 6.dp)
                                                .size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                    }
                                }
                                Text(
                                    text = when {
                                        status == null ->
                                            if (server == currentServer) "当前" else "未 Ping"
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
                Text("重新 Ping")
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
    ResponsiveAlertDialog(
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
