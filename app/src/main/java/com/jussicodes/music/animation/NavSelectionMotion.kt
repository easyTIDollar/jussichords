package com.jussicodes.music.animation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * 底栏（Compact/Medium）与左侧 NavigationRail（Expanded）共用的选中态视觉参数。
 *
 * 两条驱动路径，刻意不合并：
 * - **首页（Library ↔ Explore）**：由 HorizontalPager 的连续进度 `homePageScroll` 直接驱动，
 *   手指滑到哪图标就跟到哪，不做任何补间（补间会在跟手阶段产生滞后感）。
 * - **非首页路由（Roam / Messages / Record…）**：由离散布尔 `selected` 驱动，
 *   此前是 0.92↔1.18 / 0.68↔1.0 瞬切，视觉上像"啪"地跳一下；这里补上过渡。
 *
 * 两种导航形态共用同一套数值与 spec，各自只保留自己的布局（横条 vs 竖列）。
 */
object TabSelectionMotion {
    /** 未选中时的图标缩放。 */
    const val IconScaleRest: Float = 0.92f

    /** 选中时的图标缩放（= IconScaleRest + IconScaleDelta）。 */
    const val IconScaleSelected: Float = 1.18f

    private const val ICON_SCALE_DELTA = IconScaleSelected - IconScaleRest

    /** 未选中时的图标透明度。 */
    const val IconAlphaRest: Float = 0.68f

    /** 选中时的图标透明度。 */
    const val IconAlphaSelected: Float = 1f

    private const val ICON_ALPHA_DELTA = IconAlphaSelected - IconAlphaRest

    /** 首页跟手路径：由 pager 连续进度直接算，不动画。 */
    fun homeIconScale(homeProgress: Float): Float = IconScaleRest + ICON_SCALE_DELTA * homeProgress

    fun homeIconAlpha(homeProgress: Float): Float = IconAlphaRest + ICON_ALPHA_DELTA * homeProgress
}

/** 一次选中态计算的结果，底栏与 rail 直接取用。 */
data class TabSelectionVisuals(
    val iconScale: Float,
    val iconAlpha: Float,
)

/**
 * 选中态视觉状态。首页走 [homePageScroll] 跟手直算，非首页走 spring 过渡。
 *
 * @param isHomeRoute 当前是否停在首页（Library/Explore）。
 * @param homePageScroll pager 连续位置，仅首页有意义。
 * @param index 当前 tab 在 tabs 中的下标。
 * @param selected 非首页路由下该 tab 是否为当前路由。
 */
@Composable
fun rememberTabSelectionVisuals(
    isHomeRoute: Boolean,
    homePageScroll: Float,
    index: Int,
    selected: Boolean,
): TabSelectionVisuals {
    if (isHomeRoute) {
        val progress = (1f - abs(homePageScroll - index)).coerceIn(0f, 1f)
        return TabSelectionVisuals(
            iconScale = TabSelectionMotion.homeIconScale(progress),
            iconAlpha = TabSelectionMotion.homeIconAlpha(progress),
        )
    }

    // 非首页：离散状态切换补上过渡，避免 icon 瞬跳。
    val scale by animateFloatAsState(
        targetValue = if (selected) TabSelectionMotion.IconScaleSelected else TabSelectionMotion.IconScaleRest,
        animationSpec = MotionSpring.navSelection,
        label = "TabIconScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (selected) TabSelectionMotion.IconAlphaSelected else TabSelectionMotion.IconAlphaRest,
        animationSpec = MotionSpring.navSelection,
        label = "TabIconAlpha"
    )
    return TabSelectionVisuals(iconScale = scale, iconAlpha = alpha)
}

/** 选中背景（secondaryContainer）颜色过渡，与图标过渡用同一条节奏。 */
@Composable
fun rememberTabIndicatorColor(
    visible: Boolean,
    selectedColor: Color,
    unselectedColor: Color = Color.Transparent,
): State<Color> = animateColorAsState(
    targetValue = if (visible) selectedColor else unselectedColor,
    animationSpec = tween(MotionDuration.NavSelection),
    label = "TabIndicatorColor"
)