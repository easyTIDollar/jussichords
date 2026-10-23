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
 * 横屏沉浸式：**横屏一律隐藏状态栏**，竖屏恢复。
 *
 * 横屏（矮屏）可视高度本来就紧张（914x411dp），状态栏那一截全是浪费；
 * 而且双栏布局（歌单 / 专辑详情页）的顶栏要压到「比图标略高」才放得下
 * 内容 —— 前提就是状态栏已经被隐藏、顶栏不用给它留位置。
 *
 * 用 [WindowInsetsControllerCompat] 而不是系统 UI flag：旋转 / 离开时由
 * [DisposableEffect] 的 onDispose 复原，不污染别的页面状态。
 *
 * 挂在 MainActivity 顶层（而不是各个页面里）：横屏是**全局**沉浸式，
 * 任何页面转到横屏都不该再看到状态栏。
 */
@Composable
fun LandscapeStatusBarImmersive() {
    val view = LocalView.current
    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    if (view.isInEditMode) return
    DisposableEffect(isLandscape) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (isLandscape) {
            controller?.hide(WindowInsetsCompat.Type.statusBars())
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller?.show(WindowInsetsCompat.Type.statusBars())
        }
        onDispose {
            // 离开横屏（或整个 composition 结束）时把状态栏放回来。
            controller?.show(WindowInsetsCompat.Type.statusBars())
        }
    }
}
