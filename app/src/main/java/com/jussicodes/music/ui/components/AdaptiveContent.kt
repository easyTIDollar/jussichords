package com.jussicodes.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.jussicodes.music.constants.LocalSimulatedWidthDp
import com.jussicodes.music.constants.contentMaxWidth

/**
 * 大屏内容限宽容器。
 *
 * 手机（Compact）下 maxWidth 为 null，本容器等价于普通 Box —— 手机行为逐像素不变。
 * 中屏及以上把内容居中并限制最大宽度，避免行/卡片被拉到 1280dp 宽。
 *
 * 用法：替换页面/列表根节点的 `Modifier.fillMaxSize()`，内容仍包在本容器里。
 * 背景请交给外层（Scaffold / 页面自己 paint），本容器不画底色。
 */
@Composable
fun AdaptiveContent(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val maxWidth = contentMaxWidth()
    // 两层：外层撑满并居中，内层才限宽。
    // 不能写成 `fillMaxSize().widthIn(max = …)` —— fillMaxSize 先把 min/max 都钉成全宽，
    // 后面的 widthIn 只能把 min 降下来而 max 依旧是全宽，限宽完全失效。
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = if (maxWidth != null) {
                Modifier.widthIn(max = maxWidth).fillMaxHeight()
            } else {
                Modifier.fillMaxSize()
            },
            content = content,
        )
    }
}

/**
 * 限宽 + 两侧留白底色版本：内容居中限宽，限宽之外用 surface 色填充，
 * 避免大屏上背景层（壁纸/渐变）与内容边界脱节。
 */
@Composable
fun AdaptiveSurface(
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.surface,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize().background(background)) {
        AdaptiveContent { content() }
    }
}

/**
 * 大屏预览宿主：把整个 app 根内容按「模拟宽度」真实布局，再整体缩放贴合当前屏幕。
 *
 * 为什么真给宽度而不是只注入一个数值：只注入数值的话只有我们自己写的分支会变，
 * 组件内部的 fillMaxWidth/固定 dp 仍按手机宽度排，等于没预览。这里是真的给内容区
 * [simulatedWidthDp] dp 宽，让所有布局按大屏规则完整走一遍，再用 graphicsLayer 缩放显示。
 *
 * 关闭预览（0dp）时本包装完全透传，Modifier 不变，手机行为零影响。
 *
 * 注意：Dialog / ModalBottomSheet / 悬浮窗是独立窗口，不受本包装影响。
 */
@Composable
fun LargeScreenPreviewHost(
    simulatedWidthDp: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (simulatedWidthDp <= 0) {
        Box(modifier = modifier) { content() }
        return
    }
    val density = LocalDensity.current
    // 全部用 dp 算：Modifier.width/height 只接受 Dp（传 px 的 Float 编译不过）。
    val targetWidth = simulatedWidthDp.dp
    // 目标高度按手机常见宽高比（20:9）反推，只为让缩放后完整落在屏幕内。
    val targetHeight = targetWidth * 9f / 20f
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val scale = with(density) {
        val w = screenWidth.toPx() / targetWidth.toPx()
        val h = screenHeight.toPx() / targetHeight.toPx()
        minOf(w, h).coerceAtMost(1f)
    }
    val bg = MaterialTheme.colorScheme.surface
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(targetWidth)
                .height(targetHeight)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin.Center
                }
        ) {
            CompositionLocalProvider(LocalSimulatedWidthDp provides simulatedWidthDp) {
                content()
            }
        }
    }
}

/** 侧边导航栏（Expanded）宽度，竖排图标 + 文字。 */
val NavigationRailWidth = 88.dp
