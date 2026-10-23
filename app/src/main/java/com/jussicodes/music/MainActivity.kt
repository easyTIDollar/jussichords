package com.jussicodes.music

import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.jussicodes.music.constants.ignoredUpdateVersionKey
import com.jussicodes.music.constants.githubDownloadSourceKey
import com.jussicodes.music.constants.LocalDensityScaleFactor
import com.jussicodes.music.constants.ScreenOrientationLock
import com.jussicodes.music.constants.densityScaleKey
import com.jussicodes.music.constants.orientationLockKey
import com.jussicodes.music.constants.uiScaleKey
import com.jussicodes.music.data.MsgSessionCache
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import com.jussicodes.music.extensions.init
import com.jussicodes.music.playback.PlayerController
import com.jussicodes.music.playback.PlayerState
import com.jussicodes.music.playback.state
import com.jussicodes.music.ui.components.LandscapeStatusBarImmersive
import com.jussicodes.music.ui.components.UpdateDialog
import com.jussicodes.music.ui.components.formatFileSize
import com.jussicodes.music.ui.screen.MainScreen
import com.jussicodes.music.ui.theme.JetMeloTheme
import com.jussicodes.music.utils.AppUpdateManager
import com.jussicodes.music.utils.SongListUtil
import com.jussicodes.music.utils.UpdateDownloadPhase
import com.jussicodes.music.utils.UpdateDownloadService
import com.jussicodes.music.utils.UpdateDownloadStateStore
import com.jussicodes.music.utils.UpdateInfo
import com.jussicodes.music.utils.UpdateSourceStatus
import com.jussicodes.music.utils.downloadSources
import com.jussicodes.music.utils.dataStore
import com.jussicodes.music.utils.rememberPreference
import com.rcmiku.ncmapi.utils.FileProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@UnstableApi
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private lateinit var playerController: PlayerController
    private var playerState by mutableStateOf<PlayerState?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        playerController = PlayerController
        // Android 13+ 通知权限：媒体通知是前台服务的可见凭证，权限被拒会显著提升
        // 系统杀后台概率。仅在「用户还没永久拒绝」时弹一次系统授权框，之后不再骚扰。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted &&
                !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
            ) {
                registerForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { }.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        setContent {
            var pendingUpdateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
            var isDownloadingUpdate by remember { mutableStateOf(false) }
            var downloadProgress by remember { mutableStateOf<Float?>(0f) }
            var downloadProgressText by remember { mutableStateOf<String?>(null) }
            var updateSourceStatuses by remember {
                mutableStateOf<List<UpdateSourceStatus>>(emptyList())
            }
            var isMeasuringUpdateSources by remember { mutableStateOf(false) }
            var uiScale by rememberPreference(uiScaleKey, 1f)
            var liveUiScale by remember { mutableFloatStateOf(uiScale) }
            // DPI 模拟（调试用）：覆盖 density 让 dp 视口变成别的屏幕的尺寸。
            // 1.0 = 关闭。配合下面的方向锁定，可模拟低密度大屏 / 横屏。
            var densityScale by rememberPreference(densityScaleKey, 1f)
            var orientationLock by rememberPreference(orientationLockKey, "unlocked")
            var githubDownloadSource by rememberPreference(
                githubDownloadSourceKey,
                downloadSources.first().id
            )

            LaunchedEffect(uiScale) {
                liveUiScale = uiScale
                // 同步给进程级 holder：弹窗是独立窗口，不继承这里的缩放 Density，
                // 靠 ScaledDialogContent 读这个值跨窗口复刻缩放。
                com.jussicodes.music.ui.components.ScaledDialogDensity.scale = uiScale
            }

            LaunchedEffect(Unit) {
                awaitFrame()
                FileProvider.init(cacheDir.resolve("ncm"))
                SongListUtil.init(filesDir.resolve("playlist"))
                playerController.init(applicationContext)

                val ignoredVersion = withContext(Dispatchers.IO) {
                    dataStore.data.first()[ignoredUpdateVersionKey].orEmpty()
                }
                val updateResult = AppUpdateManager.checkUpdate()
                val updateInfo = updateResult.getOrNull()
                if (updateInfo != null && updateInfo.versionName != ignoredVersion) {
                    pendingUpdateInfo = updateInfo
                }

                // 私信会话缓存预热：并行拉 /msg/private + /msg/recentcontact，
                // 之后分享菜单/消息页首屏直接读缓存，不再现场等网络
                MsgSessionCache.ensureLoaded(lifecycleScope)
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
                            isDownloadingUpdate = true
                            // 拿不到 apkSize（totalBytes=0）时给不确定进度条（progress=null）
                            downloadProgress =
                                if (snapshot.totalBytes > 0L) {
                                    snapshot.downloadedBytes.toFloat() / snapshot.totalBytes
                                } else {
                                    null
                                }
                            downloadProgressText =
                                if (snapshot.totalBytes > 0L) {
                                    "${formatFileSize(snapshot.downloadedBytes)} / ${formatFileSize(snapshot.totalBytes)}"
                                } else {
                                    "已下载 ${formatFileSize(snapshot.downloadedBytes)}"
                                }
                        }
                        UpdateDownloadPhase.COMPLETED -> {
                            isDownloadingUpdate = false
                            downloadProgress = 1f
                            downloadProgressText = "下载完成，正在打开安装器"
                            snapshot.apkPath?.let { apkPath ->
                                AppUpdateManager.installApk(applicationContext, File(apkPath))
                                    .onFailure { throwable ->
                                        Toast.makeText(
                                            applicationContext,
                                            throwable.message ?: "无法打开安装器",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                            }
                            pendingUpdateInfo = null
                        }
                        UpdateDownloadPhase.FAILED -> {
                            isDownloadingUpdate = false
                            downloadProgress = 0f
                            downloadProgressText = snapshot.message ?: "下载失败"
                            Toast.makeText(
                                applicationContext,
                                snapshot.message ?: "下载失败",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }

            playerController.controller?.run {
                if (playerState?.player !== this) {
                    playerState?.dispose()
                    init(applicationContext)
                    playerState = state(applicationContext)
                }
            }
            val baseDensity = LocalDensity.current
            // DPI 模拟 × 界面缩放，两者叠乘。densityScale=1 时与旧行为完全一致。
            val densityScaleSafe = densityScale.coerceIn(0.4f, 1.0f)
            val scaledDensity = remember(baseDensity, liveUiScale, densityScaleSafe) {
                val scale = liveUiScale.coerceIn(0.7f, 1.1f)
                Density(
                    density = baseDensity.density * scale * densityScaleSafe,
                    fontScale = baseDensity.fontScale * scale
                )
            }
            // 屏幕方向锁定：走 Activity 的 requestedOrientation，是**真转屏**
            // （系统重建 configuration），不是 UI 层假装。设置里改即时生效。
            val orientation = ScreenOrientationLock.fromKey(orientationLock)
            LaunchedEffect(orientation) {
                requestedOrientation = when (orientation) {
                    ScreenOrientationLock.Unlocked ->
                        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    ScreenOrientationLock.Portrait ->
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    ScreenOrientationLock.Landscape ->
                        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                }
            }
            CompositionLocalProvider(LocalDensity provides scaledDensity) {
                JetMeloTheme(artwork = playerState?.mediaMetadata?.artworkUri) {
                    CompositionLocalProvider(
                        // DPI 模拟因子：响应式分级函数读它来算 dp 视口。
                        LocalDensityScaleFactor provides densityScaleSafe,
                        LocalPlayerController provides playerController,
                        LocalPlayerState provides playerState,
                        LocalUiScaleController provides UiScaleController(
                            scale = liveUiScale,
                            setScale = {
                                val scale = it.coerceIn(0.7f, 1.1f)
                                liveUiScale = scale
                                uiScale = scale
                                // 同步进程级 holder（可观察 state）：主窗口拖动「界面缩放」
                                // 滑块时，已打开的弹窗窗口（独立 composition）读到的
                                // ScaledDialogDensity.scale 实时变化，弹窗内 dp/sp 即时
                                // 跟随缩放，无需关弹窗重开。
                                com.jussicodes.music.ui.components.ScaledDialogDensity.scale = scale
                            }
                        )
                    ) {
                        // 横屏沉浸式：横屏一律隐藏状态栏（全局），竖屏恢复。
                        // 放在 composition 顶层，任何页面转到横屏都生效。
                        LandscapeStatusBarImmersive()
                        // DPI 模拟：覆盖 LocalDensity.density，dp 视口随之变化，
                        // 所有布局按「目标屏幕」真实走一遍（不是整体缩小）。
                        MainScreen()
                    pendingUpdateInfo?.let { updateInfo ->
                        UpdateDialog(
                            updateInfo = updateInfo,
                            isDownloading = isDownloadingUpdate,
                            downloadProgress = if (isDownloadingUpdate) downloadProgress else null,
                            progressText = downloadProgressText,
                            selectedSourceId = githubDownloadSource,
                            sourceStatuses = updateSourceStatuses,
                            isMeasuringSources = isMeasuringUpdateSources,
                            onMeasureSources = {
                                if (!isMeasuringUpdateSources) {
                                    isMeasuringUpdateSources = true
                                    lifecycleScope.launch {
                                        updateSourceStatuses =
                                            AppUpdateManager.measureDownloadSources()
                                        isMeasuringUpdateSources = false
                                    }
                                }
                            },
                            onSourceSelected = { githubDownloadSource = it },
                            onDismiss = { pendingUpdateInfo = null },
                            onIgnoreVersion = {
                                pendingUpdateInfo = null
                                isDownloadingUpdate = false
                                downloadProgress = 0f
                                downloadProgressText = null
                                lifecycleScope.launch {
                                    dataStore.edit { prefs ->
                                        prefs[ignoredUpdateVersionKey] = updateInfo.versionName
                                    }
                                }
                            },
                            onDownload = {
                                if (isDownloadingUpdate) return@UpdateDialog
                                isDownloadingUpdate = true
                                downloadProgress = 0f
                                downloadProgressText = "准备开始下载更新"
                                UpdateDownloadService.start(
                                    applicationContext,
                                    updateInfo,
                                    githubDownloadSource
                                )
                            }
                        )
                    }
                }
                }
            }
        }
    }

    override fun onDestroy() {
        playerState?.dispose()
        playerState = null
        super.onDestroy()
    }
}

val LocalPlayerController = staticCompositionLocalOf<PlayerController> {
    error("No PlayerController provided")
}

val LocalPlayerState = staticCompositionLocalOf<PlayerState?> {
    error("No PlayerState provided")
}

data class UiScaleController(
    val scale: Float,
    val setScale: (Float) -> Unit,
)

val LocalUiScaleController = staticCompositionLocalOf<UiScaleController> {
    UiScaleController(
        scale = 1f,
        setScale = {},
    )
}
