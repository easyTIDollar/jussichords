package com.jussicodes.music.ui.screen

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.jussicodes.music.R
import com.jussicodes.music.constants.SettingItemCorner
import com.jussicodes.music.constants.SettingItemHeight
import com.jussicodes.music.constants.SettingItemSubCorner
import com.jussicodes.music.constants.apiBaseUrlKey
import com.jussicodes.music.constants.audioQualityKey
import com.jussicodes.music.constants.autoSkipNextOnErrorKey
import com.jussicodes.music.constants.desktopLyricEnabledKey
import com.jussicodes.music.constants.dynamicThemeColorKey
import com.jussicodes.music.constants.ignoredUpdateVersionKey
import com.jussicodes.music.constants.ncmCookieKey
import com.jussicodes.music.constants.themeSeedColorKey
import com.jussicodes.music.constants.unblockSourceKey
import com.jussicodes.music.constants.use40DpIconKey
import com.jussicodes.music.lyric.DesktopLyricManager
import com.jussicodes.music.ui.components.Dialog
import com.jussicodes.music.ui.components.SongQualityDialog
import com.jussicodes.music.ui.components.ThemeSeedDialog
import com.jussicodes.music.ui.components.UnblockSourceDialog
import com.jussicodes.music.ui.components.UpdateDialog
import com.jussicodes.music.ui.components.UrlEditDialog
import com.jussicodes.music.ui.components.unblockSourceOptions
import com.jussicodes.music.ui.icons.Dns
import com.jussicodes.music.ui.icons.Github
import com.jussicodes.music.ui.icons.GraphicEq
import com.jussicodes.music.ui.icons.Login
import com.jussicodes.music.ui.icons.Logout
import com.jussicodes.music.ui.icons.PlayPause
import com.jussicodes.music.ui.icons.SkipNext
import com.jussicodes.music.ui.icons.UserRound
import com.jussicodes.music.ui.navigation.Screen
import com.jussicodes.music.ui.theme.AppThemeSeed
import com.jussicodes.music.utils.AppUpdateManager
import com.jussicodes.music.utils.UpdateDownloadPhase
import com.jussicodes.music.utils.UpdateDownloadService
import com.jussicodes.music.utils.UpdateDownloadStateStore
import com.jussicodes.music.utils.UpdateInfo
import com.jussicodes.music.utils.getItemShape
import com.jussicodes.music.utils.rememberEnumPreference
import com.jussicodes.music.utils.rememberPreference
import com.rcmiku.ncmapi.api.player.SongLevel
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

    var use40DpIcon by rememberPreference(use40DpIconKey, false)
    var desktopLyricEnabled by rememberPreference(desktopLyricEnabledKey, false)
    var audioQuality by rememberEnumPreference(audioQualityKey, defaultValue = SongLevel.STANDARD)
    var useDynamicThemeColor by rememberPreference(dynamicThemeColorKey, dynamicColorAvailable)
    var themeSeed by rememberEnumPreference(themeSeedColorKey, defaultValue = AppThemeSeed.PURPLE)
    var autoSkipNextOnError by rememberPreference(autoSkipNextOnErrorKey, false)
    var ncmCookie by rememberPreference(ncmCookieKey, "")
    var apiBaseUrl by rememberPreference(apiBaseUrlKey, "http://119.23.64.141:3000")
    var unblockSource by rememberPreference(unblockSourceKey, "AUTO")
    var ignoredUpdateVersion by rememberPreference(ignoredUpdateVersionKey, "")
    var showQualityDialog by remember { mutableStateOf(false) }
    var showThemeSeedDialog by remember { mutableStateOf(false) }
    var showApiUrlDialog by remember { mutableStateOf(false) }
    var showUnblockSourceDialog by remember { mutableStateOf(false) }
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
    val appearanceSubtitle = when {
        useDynamicThemeColor && dynamicColorAvailable -> "跟随系统取色"
        useDynamicThemeColor -> "跟随系统取色（当前设备不支持）"
        else -> "主题色：${themeSeed.label}"
    }

    val baseSettingItems = listOf(
        SettingItemData(
            title = appearanceTitle,
            subtitle = appearanceSubtitle,
            imageVector = GraphicEq,
            onClick = { showThemeSeedDialog = true }
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
            title = "桌面歌词",
            subtitle = when {
                desktopLyricEnabled && overlayPermissionGranted -> "已开启悬浮歌词"
                !overlayPermissionGranted -> "需要悬浮窗权限"
                else -> "已关闭"
            },
            imageVector = GraphicEq,
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
            title = stringResource(R.string.command_button),
            subtitle = stringResource(R.string.command_button_subtitle),
            imageVector = PlayPause,
            trailingContent = {
                Switch(
                    checked = use40DpIcon,
                    onCheckedChange = { use40DpIcon = it }
                )
                Spacer(Modifier.width(12.dp))
            },
            onClick = { use40DpIcon = !use40DpIcon }
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
            imageVector = GraphicEq,
            onClick = { showQualityDialog = true }
        ),
        SettingItemData(
            title = stringResource(R.string.auto_skip),
            imageVector = SkipNext,
            trailingContent = {
                Switch(
                    checked = autoSkipNextOnError,
                    onCheckedChange = { autoSkipNextOnError = it }
                )
                Spacer(Modifier.width(12.dp))
            },
            onClick = { autoSkipNextOnError = !autoSkipNextOnError }
        ),
        SettingItemData(
            title = stringResource(R.string.api_server),
            subtitle = apiBaseUrl,
            imageVector = Dns,
            onClick = { showApiUrlDialog = true }
        ),
        SettingItemData(
            title = "解灰音源",
            subtitle = unblockSourceOptions.firstOrNull { it.value == unblockSource }?.label
                ?: "AUTO",
            imageVector = Dns,
            onClick = { showUnblockSourceDialog = true },
            onLongClick = { showUnblockSourceDialog = true }
        ),
        SettingItemData(
            title = if (updating) "正在检查" else "检查版本更新",
            subtitle = when {
                updating -> "正在检查 GitHub Release"
                else -> "点按检查更新，下载最新 APK"
            },
            imageVector = Github,
            onClick = {
                if (!updating) {
                    updating = true
                    coroutineScope.launch {
                        val updateResult = AppUpdateManager.checkLatestRelease()
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
            title = "版本号",
            subtitle = BuildConfig.VERSION_NAME,
            imageVector = Github
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

    if (showThemeSeedDialog) {
        ThemeSeedDialog(
            currentSeed = themeSeed,
            dynamicColorAvailable = dynamicColorAvailable,
            dynamicColorEnabled = useDynamicThemeColor,
            onDismiss = { showThemeSeedDialog = false },
            onDynamicColorChange = { useDynamicThemeColor = it },
            onSeedSelected = { themeSeed = it }
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

    if (showApiUrlDialog) {
        UrlEditDialog(
            title = stringResource(R.string.api_server),
            currentUrl = apiBaseUrl,
            defaultUrl = "http://119.23.64.141:3000",
            onDismiss = { showApiUrlDialog = false },
            onConfirm = { apiBaseUrl = it }
        )
    }

    if (showUnblockSourceDialog) {
        UnblockSourceDialog(
            currentSource = unblockSource,
            onDismiss = { showUnblockSourceDialog = false },
            onSourceSelected = { unblockSource = it }
        )
    }

    pendingUpdateInfo?.let { updateInfo ->
        UpdateDialog(
            updateInfo = updateInfo,
            isDownloading = downloadingUpdate,
            downloadProgress = if (downloadingUpdate) updateDownloadProgress else null,
            progressText = updateDownloadText,
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
                UpdateDownloadService.start(context.applicationContext, updateInfo)
            }
        )
    }

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
                    color = MaterialTheme.colorScheme.secondary
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
