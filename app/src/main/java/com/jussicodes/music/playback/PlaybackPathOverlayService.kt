package com.jussicodes.music.playback

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.ViewTreeLifecycleOwner
import androidx.lifecycle.ViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import android.view.View
import com.jussicodes.music.lyric.DesktopLyricManager
import com.jussicodes.music.ui.components.PlaybackPathOverlayContent
import com.jussicodes.music.ui.theme.JetMeloTheme

/**
 * 「可视化播放解析」的置顶小窗。
 *
 * 用系统悬浮窗（TYPE_APPLICATION_OVERLAY）而不是应用内浮层：面板的用途是「边听边看」，
 * 用户会切去别的 App；只有系统级窗口能跨应用留在最上层。权限复用桌面歌词那套
 * （[DesktopLyricManager.canDrawOverlays] / requestOverlayPermission），不额外引导。
 *
 * Compose 不在 Activity 里，所以这里自己当 Lifecycle / SavedState / ViewModel 三个 Owner，
 * 退出时按顺序销毁，避免 ComposeView 泄漏。
 */
class PlaybackPathOverlayService : Service(),
    LifecycleOwner,
    SavedStateRegistryOwner,
    ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore = ViewModelStore()

    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (!DesktopLyricManager.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        addWindow()
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun onDestroy() {
        composeView?.let { runCatching { windowManager.removeView(it) } }
        composeView = null
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
        super.onDestroy()
    }

    private fun addWindow() {
        val metrics = resources.displayMetrics
        val width = minOf((metrics.widthPixels * 0.92f).toInt(), (340 * metrics.density).toInt())
        val layoutParams = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (metrics.widthPixels - width) / 2
            y = (metrics.heightPixels * 0.16f).toInt()
        }

        val view = ComposeView(this).apply {
            ViewTreeLifecycleOwner.set(this, this@PlaybackPathOverlayService)
            ViewTreeViewModelStoreOwner.set(this, this@PlaybackPathOverlayService)
            attachSavedStateOwner(this)
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnLifecycleDestroyed(this@PlaybackPathOverlayService)
            )
            setContent {
                JetMeloTheme {
                    PlaybackPathOverlayContent(
                        onClose = { stopSelf() },
                        onDrag = { dx, dy -> moveBy(dx, dy) },
                    )
                }
            }
        }

        runCatching { windowManager.addView(view, layoutParams) }
            .onFailure {
                stopSelf()
                return
            }
        composeView = view
        params = layoutParams
    }

    /**
     * 挂 SavedStateRegistryOwner。
     *
     * `ViewTreeSavedStateRegistryOwner` 在部分 Compose 组合里只跟 savedstate-ktx 一起下发，
     * 这里用反射挂：拿不到也不影响小窗（内容里没有 rememberSaveable），拿得到就正常。
     */
    private fun attachSavedStateOwner(view: View) {
        runCatching {
            val cls = Class.forName("androidx.savedstate.ViewTreeSavedStateRegistryOwner")
            cls.getMethod("set", View::class.java, SavedStateRegistryOwner::class.java)
                .invoke(null, view, this)
        }
    }

    /** 拖动：只在标题条上响应，窗口跟着手指走并夹在屏幕内。 */
    private fun moveBy(dx: Float, dy: Float) {
        val lp = params ?: return
        val view = composeView ?: return
        val metrics = resources.displayMetrics
        lp.x = (lp.x + dx.toInt()).coerceIn(-lp.width / 3, metrics.widthPixels - lp.width * 2 / 3)
        lp.y = (lp.y + dy.toInt()).coerceIn(0, (metrics.heightPixels - view.height).coerceAtLeast(0))
        runCatching { windowManager.updateViewLayout(view, lp) }
    }
}
