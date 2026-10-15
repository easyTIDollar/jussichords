package com.jussicodes.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jussicodes.music.constants.contentMaxWidth

/**
 * 大屏内容限宽容器。
 *
 * 手机竖屏（Compact）下 maxWidth 为 null，本容器等价于普通 fillMaxSize ——
 * 手机行为逐像素不变。中屏及以上把内容居中并限制最大宽度。
 *
 * 手机横屏（矮屏）也**不限宽**：横向 914dp 正是双栏需要的空间，此时限宽会把
 * 两栏挤扁。判定见 `isLandscapePhone()`。
 *
 * 两层结构的原因：不能写成 `fillMaxSize().widthIn(max = …)` —— fillMaxSize
 * 先把 min/max 都钉成全宽，后面的 widthIn 只能把 min 降下来而 max 依旧是全宽，
 * 限宽静默失效。所以外层撑满并居中，内层才限宽。
 */
@Composable
fun AdaptiveContent(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val maxWidth = contentMaxWidth()
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

/** 限宽 + 底色版本：限宽之外用 surface 色填充，避免大屏上背景层与内容边界脱节。 */
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

/** 侧边导航栏（横屏手机 / 平板）宽度，竖排图标 + 文字。 */
val NavigationRailWidth = 88.dp