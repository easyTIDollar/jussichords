package com.jussicodes.music

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
import com.jussicodes.music.utils.UpdateCheckCoordinator
import com.jussicodes.music.utils.UpdateDownloadPhase
import com.jussicodes.music.utils.UpdateDownloadService
import com.jussicodes.music.utils.UpdateDownloadStateStore
import com.jussicodes.music.utils.UpdateInfo
import com.jussicodes.music.utils.downloadSources
import com.jussicodes.music.utils.normalizedSourceId
import com.jussicodes.music.utils.dataStore
import com.jussicodes.music.utils.rememberPreference
import com.rcmiku.ncmapi.utils.FileProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.delay
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
            var uiScale by rememberPreference(uiScaleKey, 1f)
            var liveUiScale by remember { mutableFloatStateOf(uiScale) }
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
                // 每次打开都检查更新（不看节流窗口）。
                // 启动关键路径让路首屏：延迟 600ms 等主界面首帧与各列表完全渲染落定，
                // 再启动后台更新检查与私信缓存预热，消除冷启动争抢 CPU 导致的初次操作顿挫。
                lifecycleScope.launch {
                    delay(600)
                    UpdateCheckCoordinator.cachedUpdate(applicationContext)?.let { info ->
                        if (info.versionName != ignoredVersion) {
                            pendingUpdateInfo = info
                        }
                    }
                    val outcome = UpdateCheckCoordinator.check(applicationContext, force = true)
                    val updateInfo = outcome.updateInfo
                    if (updateInfo != null && updateInfo.versionName != ignoredVersion) {
                        pendingUpdateInfo = updateInfo
                    }
                }

                // 私信会话缓存预热：同样错峰 600ms，之后分享菜单/消息页直接读缓存
                lifecycleScope.launch {
                    delay(600)
                    MsgSessionCache.ensureLoaded(lifecycleScope)
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
                                // 同一份下载只允许拉起一次安装器：
                                // 主界面和设置页都 collect 这个 store，否则会弹两个安装窗口。
                                if (UpdateDownloadStateStore.claimInstallLaunch()) {
                                    AppUpdateManager.installApk(applicationContext, File(apkPath))
                                        .onFailure { throwable ->
                                            Toast.makeText(
                                                applicationContext,
                                                throwable.message ?: "无法打开安装器",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
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
                        UpdateDownloadPhase.CANCELLED -> {
                            // 用户主动取消：回到可重新下载状态，保留已下载的提示信息。
                            isDownloadingUpdate = false
                            downloadProgress = 0f
                            downloadProgressText = "下载已取消"
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
            val scaledDensity = remember(baseDensity, liveUiScale) {
                val scale = liveUiScale.coerceIn(0.7f, 1.1f)
                Density(
                    density = baseDensity.density * scale,
                    fontScale = baseDensity.fontScale * scale
                )
            }
            CompositionLocalProvider(LocalDensity provides scaledDensity) {
                JetMeloTheme(artwork = playerState?.mediaMetadata?.artworkUri) {
                    CompositionLocalProvider(
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
                        MainScreen()
                    pendingUpdateInfo?.let { updateInfo ->
                        UpdateDialog(
                            updateInfo = updateInfo,
                            isDownloading = isDownloadingUpdate,
                            downloadProgress = if (isDownloadingUpdate) downloadProgress else null,
                            progressText = downloadProgressText,
                            selectedSourceId = normalizedSourceId(githubDownloadSource),
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
                                    normalizedSourceId(githubDownloadSource)
                                )
                            },
                            onCancelDownload = {
                                UpdateDownloadService.cancelDownload()
                                isDownloadingUpdate = false
                                downloadProgress = 0f
                                downloadProgressText = "下载已取消"
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
