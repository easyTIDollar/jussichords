package com.jussicodes.music.constants

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 全局唯一的窗口宽度分级来源。所有响应式判断都读这里，不要在别处另写阈值。
 *
 * 判定只看**内容可用宽度**（dp），不看物理分辨率 —— 同一台平板竖屏与横屏的 dp 宽度不同。
 *
 * 阈值沿用 Material3 WindowSizeClass 约定：
 * - Compact  < 600dp：手机竖屏、折叠屏外屏
 * - Medium   600..839dp：小折叠屏内屏 / 平板竖屏 / 手机分屏
 * - Expanded >= 840dp：平板横屏 / TV / 车机
 *
 * **注意**：手机横屏的宽度也是 ~900dp，会落进 Expanded。但它高度只有 ~411dp，
 * 属于「宽而矮」，必须配合 [WindowHeightClass] 一起判断 —— 见 [isLandscapePhone]。
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
 * 窗口**高度**分级。只看宽度会把「手机横屏」和「平板」混为一谈 ——
 * 两者宽度都是 ~900dp，但高度差一个数量级：
 *
 * - 手机竖屏  411 x 914dp
 * - 手机横屏  914 x 411dp   ← 宽度像平板，高度是灾难区
 * - 平板横屏 1280 x 800dp
 *
 * 横屏手机是本项目最需要单独处理的形态：宽向够放双栏，纵向只剩 411dp，
 * 任何 `aspectRatio(1f) + fillMaxWidth` 的封面都会撑破布局。
 *
 * 阈值：< 480dp 视为「矮屏」（手机横屏/分屏横条），>= 480dp 可正常纵向堆叠。
 */
enum class WindowHeightClass {
    /** < 480dp：手机横屏。纵向必须压缩或转双栏。 */
    Short,
    /** 480..899dp：手机竖屏、平板竖屏。 */
    Normal,
    /** >= 900dp：高密度大屏、TV。 */
    Tall;

    val isShort: Boolean get() = this == Short
    val isNormal: Boolean get() = this == Normal
    val isTall: Boolean get() = this == Tall
}

/** 当前内容宽度（dp）。 */
@Composable
@ReadOnlyComposable
fun currentContentWidthDp(): Int = LocalConfiguration.current.screenWidthDp

/** 当前内容高度（dp）。 */
@Composable
@ReadOnlyComposable
fun currentContentHeightDp(): Int = LocalConfiguration.current.screenHeightDp

/**
 * 手机横屏（矮屏）的判定：内容高度不足 480dp。
 *
 * 这不是「大屏」，恰恰相反 —— 宽而矮。响应式布局里必须把它当独立形态处理，
 * 不能因为宽度到了 914dp 就走平板的 Expanded 分支（竖排导航栏 + 限宽套在
 * 411dp 高的手机上，内容可视高度会被压到无法使用）。
 */
@Composable
@ReadOnlyComposable
fun isLandscapePhone(): Boolean = currentContentHeightDp() < 480

/** 宽度分级（受 DPI 模拟影响）。 */
@Composable
@ReadOnlyComposable
fun currentWindowWidthClass(): WindowWidthClass =
    when (val w = currentContentWidthDp()) {
        in 0 until 600 -> WindowWidthClass.Compact
        in 600 until 840 -> WindowWidthClass.Medium
        else -> WindowWidthClass.Expanded
    }

/** 高度分级（受 DPI 模拟影响）。 */
@Composable
@ReadOnlyComposable
fun currentWindowHeightClass(): WindowHeightClass =
    when (currentContentHeightDp()) {
        in 0 until 480 -> WindowHeightClass.Short
        in 480 until 900 -> WindowHeightClass.Normal
        else -> WindowHeightClass.Tall
    }

/**
 * 是否使用左侧竖排导航栏。
 *
 * 手机横屏（矮屏）**要** rail：411dp 的高度放底部横条会吃掉 1/6 可视高度，
 * 而 914dp 的宽度足够并排放 rail + 内容。平板/TV 同样要 rail。
 * 只有手机竖屏（Compact 且不矮）继续用底部栏。
 */
@Composable
@ReadOnlyComposable
fun useNavigationRailLayout(): Boolean {
    val widthClass = currentWindowWidthClass()
    return if (isLandscapePhone()) {
        // 矮屏：只要宽度够放两栏就用 rail
        widthClass != WindowWidthClass.Compact
    } else {
        // 正常高度：只有真正的大屏才 rail
        widthClass == WindowWidthClass.Expanded
    }
}

/**
 * 内容区最大宽度。Compact 返回 null（不限宽，手机行为逐像素不变）。
 *
 * 矮屏（手机横屏）**不限宽**：横向 914dp 正是双栏需要的空间，此时限宽反而
 * 会把两栏挤扁。
 */
@Composable
@ReadOnlyComposable
fun contentMaxWidth(): Dp? {
    if (isLandscapePhone()) return null
    return when (currentWindowWidthClass()) {
        WindowWidthClass.Compact -> null
        WindowWidthClass.Medium -> 640.dp
        WindowWidthClass.Expanded -> 840.dp
    }
}

/** 双栏布局里侧栏/主区的宽度配比（仅矮屏使用）。 */
const val LandscapeSidePanelFraction = 0.46f

/**
 * 横屏详情页「主从双栏」左栏宽度占比：**左 40% / 右 60%**。
 *
 * 专辑/歌单详情页在横屏下换成黄金比例双栏：左栏放封面 + 文案 + 3 枚药丸按钮，
 * 右栏留给可滚动的曲目列表。左栏占比再大就会把列表挤到放不下行标题。
 */
const val LandscapeDetailLeftFraction = 0.4f

/**
 * 详情页大封面边长（歌单/专辑/电台头图）。
 *
 * 固定 200dp 在横屏手机上会吃掉大部分可视高度：矮屏总高仅 411dp，减去 200dp 封面
 * 再加标题和操作按钮，歌曲列表的首屏几乎看不到东西。所以矮屏必须缩小。
 */
@Composable
@ReadOnlyComposable
fun detailCoverSize(): Dp = when {
    // 矮屏（手机横屏）：纵向是稀缺资源，封面压到 120dp 给列表让位
    isLandscapePhone() -> 120.dp
    currentWindowWidthClass() == WindowWidthClass.Compact -> 200.dp
    currentWindowWidthClass() == WindowWidthClass.Medium -> 200.dp
    else -> 240.dp
}

/** 网格封面边长（探索页专辑墙、用户歌单网格）。 */
@Composable
@ReadOnlyComposable
fun gridCoverSize(): Dp = when (currentWindowWidthClass()) {
    WindowWidthClass.Compact -> 144.dp
    WindowWidthClass.Medium -> 144.dp
    WindowWidthClass.Expanded -> 160.dp
}

/** 列表项缩略图边长（各列表行内的小图）。固定 48dp 在所有形态下都合理，不参与响应式。 */
val listThumbnailSize: Dp get() = ListThumbnailSize

/** 网格/列表的最小单元宽度（dp），配合 GridCells.Adaptive 使用。 */
@Composable
@ReadOnlyComposable
fun adaptiveMinCellWidth(): Dp = when (currentWindowWidthClass()) {
    WindowWidthClass.Compact -> 320.dp
    WindowWidthClass.Medium -> 280.dp
    WindowWidthClass.Expanded -> 240.dp
}