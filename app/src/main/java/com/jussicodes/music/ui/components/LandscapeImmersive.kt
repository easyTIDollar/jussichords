package com.jussicodes.music.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** 沿 ContextWrapper 链找到 Activity，拿它的 Window 做沉浸式系统栏控制。 */
internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * 横屏沉浸式：**横屏一律隐藏状态栏 + 导航栏（含手势小白条）**，竖屏恢复。
 *
 * 横屏（矮屏）可视高度本来就紧张（914x411dp），状态栏那一截全是浪费；
 * 而且双栏布局（歌单 / 专辑详情页）的顶栏要压到「比图标略高」才放得下
 * 内容 —— 前提就是状态栏已经被隐藏、顶栏不用给它留位置。
 * 导航栏那条手势提示条同理：横屏底部不该再留一截白条。
 *
 * 实现要点（踩过两次才通）：
 * 1. 必须 `hide(systemBars())` 整组隐藏，单独 `hide(navigationBars())` 在
 *    Android 11+ 手势导航下会被系统忽略，小白条照样在；
 * 2. insets controller 要绑 `window.decorView`，绑 ComposeView 拿到的控制器
 *    在部分机型上不生效；
 * 3. 首次组合时窗口可能还没拿到焦点，系统会丢掉 hide 请求 —— 等一帧再补一次。
 *
 * 挂在 MainActivity 顶层（而不是各个页面里）：横屏是**全局**沉浸式，
 * 任何页面转到横屏都不该再看到状态栏/小白条。页面自己不要再 hide/show，
 * 否则 onDispose 里的 show 会把这里的隐藏状态撤掉。
 */
@Composable
fun LandscapeStatusBarImmersive() {
    val view = LocalView.current
    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    if (view.isInEditMode) return
    DisposableEffect(isLandscape) {
        val window = view.context.findActivity()?.window
        val decor = window?.decorView ?: view
        val controller = window?.let { WindowCompat.getInsetsController(it, decor) }
        var disposed = false

        fun applyBars() {
            if (disposed) return
            controller ?: return
            if (isLandscape) {
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }

        applyBars()
        // 首帧窗口未聚焦时 hide 会被系统忽略，下一帧与稍后再各补一次。
        decor.post { applyBars() }
        decor.postDelayed({ applyBars() }, 350L)

        onDispose {
            disposed = true
            // 离开横屏（或整个 composition 结束）时把系统栏放回来。
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
