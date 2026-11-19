package com.jussicodes.music

import android.os.Build
import android.os.Bundle
import android.view.ViewTreeObserver
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.withResumed
import androidx.media3.common.util.UnstableApi
import com.jussicodes.music.constants.ignoredUpdateVersionKey
import com.jussicodes.music.constants.githubDownloadSourceKey
import com.jussicodes.music.constants.uiScaleKey
import com.jussicodes.music.constants.fontScaleKey
import com.jussicodes.music.data.MsgSessionCache
import android.Manifest
import android.content.Intent
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
import com.jussicodes.music.utils.AppLaunchFlags
import com.jussicodes.music.utils.UpdateCheckCoordinator
import com.jussicodes.music.utils.UpdateDownloadPhase
import com.jussicodes.music.utils.UpdateDownloadService
import com.jussicodes.music.utils.UpdateDownloadStateStore
import com.jussicodes.music.utils.UpdateInfo
import com.jussicodes.music.utils.downloadSources
import com.jussicodes.music.utils.normalizedSourceId
import com.jussicodes.music.utils.dataStore
import com.jussicodes.music.utils.rememberPreference
import com.jussicodes.music.widget.WidgetUpdater
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.io.File

@UnstableApi
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private lateinit var playerController: PlayerController
    private var playerState by mutableStateOf<PlayerState?>(null)
    private var homeFullyDrawnReported = false
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        playerController = PlayerController
        consumeWidgetIntent(intent)
        // 在安装内容前登记绘制监听，避免错过首页首次绘制。
        val firstDraw = lifecycleScope.async(start = CoroutineStart.UNDISPATCHED) {
            awaitFirstDraw()
        }
        lifecycleScope.launch {
            // 正常首帧后立即请求；绘制异常时最多等待 5 秒，且只在前台请求。
            withTimeoutOrNull(5000) { firstDraw.await() }
            lifecycle.withResumed { requestNotificationPermission() }
        }
        setContent {
            var pendingUpdateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
            var isDownloadingUpdate by remember { mutableStateOf(false) }
            var downloadProgress by remember { mutableStateOf<Float?>(0f) }
            var downloadProgressText by remember { mutableStateOf<String?>(null) }
            var uiScale by rememberPreference(uiScaleKey, com.jussicodes.music.ui.components.ScaledDialogDensity.scale)
            var liveUiScale by remember { mutableFloatStateOf(com.jussicodes.music.ui.components.ScaledDialogDensity.scale) }
            var fontScale by rememberPreference(fontScaleKey, com.jussicodes.music.ui.components.ScaledDialogDensity.fontScale)
            var liveFontScale by remember { mutableFloatStateOf(com.jussicodes.music.ui.components.ScaledDialogDensity.fontScale) }
            var githubDownloadSource by rememberPreference(
                githubDownloadSourceKey,
                downloadSources.first().id
            )

            LaunchedEffect(uiScale, fontScale) {
                liveUiScale = uiScale
                liveFontScale = fontScale
                // 同步给进程级 holder：弹窗是独立窗口，不继承这里的缩放 Density，
                // 靠 ScaledDialogContent 读这个值跨窗口复刻缩放。
                com.jussicodes.music.ui.components.ScaledDialogDensity.scale = uiScale
                com.jussicodes.music.ui.components.ScaledDialogDensity.fontScale = fontScale
            }

            LaunchedEffect(Unit) {
                // 启动任务共用一个顺序入口，避免更新检查与私信预热同时唤醒。
                firstDraw.await()
                // 首帧已绘制完成，立即连接播放服务，不再额外等 1 秒（小播放器要尽早出现）。
                playerController.init(applicationContext)
                delay(1000)

                val ignoredVersion = withContext(Dispatchers.IO) {
                    dataStore.data.first()[ignoredUpdateVersionKey].orEmpty()
                }
                // 每次打开都检查更新；保留覆盖安装后首次启动的跳过规则。
                if (!AppLaunchFlags.justUpdated) {
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

                // 更新检查完成后再预热；退到后台取消，回到前台时允许重试。
                lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    MsgSessionCache.ensureLoaded(this)?.join()
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
            val scaledDensity = remember(baseDensity, liveUiScale, liveFontScale) {
                val scale = liveUiScale.coerceIn(0.7f, 1.1f)
                val fScale = liveFontScale.coerceIn(0.8f, 1.3f)
                Density(
                    density = baseDensity.density * scale,
                    fontScale = baseDensity.fontScale * scale * fScale
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
                            },
                            fontScale = liveFontScale,
                            setFontScale = {
                                val fScale = it.coerceIn(0.8f, 1.3f)
                                liveFontScale = fScale
                                fontScale = fScale
                                com.jussicodes.music.ui.components.ScaledDialogDensity.fontScale = fScale
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

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted &&
                !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    /** 首页状态已进入组合后，再等待一次实际绘制才报告完全显示。 */
    fun reportHomeFullyDrawn() {
        if (homeFullyDrawnReported) return
        homeFullyDrawnReported = true
        lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
            window.decorView.invalidate()
            awaitFirstDraw()
            reportFullyDrawn()
        }
    }

    override fun onDestroy() {
        playerState?.dispose()
        playerState = null
        super.onDestroy()
    }

    private suspend fun awaitFirstDraw() = suspendCancellableCoroutine<Unit> { continuation ->
        val view = window.decorView
        var posted = false
        val listener = object : ViewTreeObserver.OnDrawListener {
            override fun onDraw() {
                if (posted) return
                posted = true
                // onDraw 中不能移除监听，投递到本次绘制遍历完成之后处理。
                view.post {
                    if (view.viewTreeObserver.isAlive) {
                        view.viewTreeObserver.removeOnDrawListener(this)
                    }
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
        }
        view.viewTreeObserver.addOnDrawListener(listener)
        continuation.invokeOnCancellation {
            view.post {
                if (view.viewTreeObserver.isAlive) {
                    view.viewTreeObserver.removeOnDrawListener(listener)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 回到前台时校准组件的登录 / 红心标记：登录状态可能在 App 内刚刚变化，
        // 没有播放事件也要跟上（内部会比对，未变化就不重绘）。
        lifecycleScope.launch { WidgetUpdater.syncFavoriteFlags(this@MainActivity) }
        // 兜底再对齐一次播放态：进程被系统回收过、或服务刚恢复时，组件可能还停在旧快照。
        // onResume 可能先于 Compose 里 playerState 的赋值：player 为 null 时 refresh
        // 会静默返回，冷启动回前台的这次对齐就失效了——等一拍再取一次。
        lifecycleScope.launch {
            val player = playerState?.player ?: run { delay(300); playerState?.player }
            WidgetUpdater.refresh(this@MainActivity, player)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeWidgetIntent(intent)
    }

    /** 桌面组件点封面/文字时带 OPEN_PLAYER 进来：请求把迷你播放条展开成全屏播放页。 */
    private fun consumeWidgetIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_PLAYER, false) == true) {
            intent.removeExtra(EXTRA_OPEN_PLAYER)
            com.jussicodes.music.utils.PlayerExpandBus.expand()
        }
    }

    companion object {
        const val EXTRA_OPEN_PLAYER = "com.jussicodes.music.extra.OPEN_PLAYER"
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
    val fontScale: Float = 0.9f,
    val setFontScale: (Float) -> Unit = {},
)

val LocalUiScaleController = staticCompositionLocalOf<UiScaleController> {
    UiScaleController(
        scale = 1f,
        setScale = {},
        fontScale = 0.9f,
        setFontScale = {},
    )
}
