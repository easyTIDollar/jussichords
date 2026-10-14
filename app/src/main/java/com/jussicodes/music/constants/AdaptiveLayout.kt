package com.jussicodes.music.constants

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 全局唯一的窗口宽度分级来源。所有响应式判断都读这里，不要在别处另写阈值。
 *
 * 判定只看**内容可用宽度**（dp），不看物理分辨率——同一台平板竖屏与横屏的 dp 宽度不同，
 * 横屏才是真正吃得下双栏的场景。
 *
 * 阈值沿用 Material3 WindowSizeClass 约定：
 * - Compact  < 600dp：手机竖屏、折叠屏外屏
 * - Medium   600..839dp：小折叠屏内屏 / 平板竖屏 / 手机分屏
 * - Expanded >= 840dp：平板横屏 / TV / 车机
 */
enum class WindowWidthClass {
    Compact,
    Medium,
    Expanded;

    val isCompact: Boolean get() = this == Compact
    val isMedium: Boolean get() = this == Medium
    val isExpanded: Boolean get() = this == Expanded

    /** 中等及以上：内容区值得限宽，避免一行文字横跨 1280dp。 */
    val isAtLeastMedium: Boolean get() = this != Compact
}

/**
 * 大屏预览用的**模拟内容宽度**（dp）。
 *
 * 手机上没有平板/TV 可测时，用它把布局「假装」成目标宽度，从而在手机上直接看到大屏形态。
 * 0 表示不模拟，走真实窗口宽度。
 */
val LocalSimulatedWidthDp = staticCompositionLocalOf { 0 }

/** 设置页「大屏预览」单选的候选项（dp，0 = 关闭预览）。 */
val simulatedWidthOptions: List<Int> = listOf(0, 600, 840, 1280)

/** 当前生效的内容宽度：预览开启时用模拟值，否则用真实窗口宽度。 */
@Composable
@ReadOnlyComposable
fun currentContentWidthDp(): Int {
    val simulated = LocalSimulatedWidthDp.current
    if (simulated > 0) return simulated
    return LocalConfiguration.current.screenWidthDp
}

/** 当前窗口宽度分级（受大屏预览影响）。 */
@Composable
@ReadOnlyComposable
fun currentWindowWidthClass(): WindowWidthClass =
    when (val w = currentContentWidthDp()) {
        in 0 until 600 -> WindowWidthClass.Compact
        in 600 until 840 -> WindowWidthClass.Medium
        else -> WindowWidthClass.Expanded
    }

/**
 * 内容区最大宽度。Compact 返回 null（不限宽，手机行为逐像素不变）。
 *
 * 数值取「读起来不费眼」的上限：正文行长控制在 ~60 汉字，列表/网格再居中。
 */
@Composable
@ReadOnlyComposable
fun contentMaxWidth(): Dp? = when (currentWindowWidthClass()) {
    WindowWidthClass.Compact -> null
    WindowWidthClass.Medium -> 640.dp
    WindowWidthClass.Expanded -> 840.dp
}

/**
 * 网格/列表的最小单元宽度（dp），配合 [androidx.compose.foundation.lazy.grid.GridCells.Adaptive]
 * 使用。Compact 下自然退化成单列，无需额外分支。
 */
@Composable
@ReadOnlyComposable
fun adaptiveMinCellWidth(): Dp = when (currentWindowWidthClass()) {
    WindowWidthClass.Compact -> 320.dp
    WindowWidthClass.Medium -> 280.dp
    WindowWidthClass.Expanded -> 240.dp
}
