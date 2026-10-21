package com.jussicodes.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jussicodes.music.animation.rememberTabIndicatorColor
import com.jussicodes.music.animation.rememberTabSelectionVisuals
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 大屏（Expanded）左侧竖排导航栏。
 *
 * 与底栏 [com.jussicodes.music.ui.screen.MainScreen] 里的横条共用同一份 [tabs] 数据与
 * 「主页 pager 位置 → 选中项」的映射规则，只是排布方向不同：指示胶囊从横条改为竖列。
 * 选中判定仍以 homePageScroll 的连续值算缩放/透明度，横滑切页时视觉与手机一致。
 */
@Composable
fun AdaptiveNavigationRail(
    currentRoute: String?,
    isHomeRoute: Boolean,
    homePageScroll: Float,
    onTabClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(NavigationRailWidth)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        // 顶部留白与底栏的 NavigationBar 内边距观感对齐，避免第一项贴顶。
        Box(Modifier.height(16.dp))
        tabs.forEachIndexed { index, item ->
            val homeProgress = if (isHomeRoute) {
                (1f - abs(homePageScroll - index)).coerceIn(0f, 1f)
            } else {
                0f
            }
            val selected = if (isHomeRoute) {
                // 与底栏同一套取整规则（roundToInt），避免 0.5 边界上两边高亮错位。
                tabs.getOrNull(homePageScroll.roundToInt())?.route == item.route
            } else {
                // rail 只在 tab 路由下出现（showNavigationBar = isTabRoute），
                // 此时 currentDestination 就是 tab 本身，直接比 route 即可。
                currentRoute == item.route
            }
            // 与底栏共用同一套选中态视觉：首页跟手直算，非首页走过渡（此前是瞬跳）。
            val visuals = rememberTabSelectionVisuals(
                isHomeRoute = isHomeRoute,
                homePageScroll = homePageScroll,
                index = index,
                selected = selected
            )
            val indicatorVisible = selected || (isHomeRoute && homeProgress > 0.5f)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(vertical = 6.dp)
                    .width(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        rememberTabIndicatorColor(
                            visible = indicatorVisible,
                            selectedColor = MaterialTheme.colorScheme.secondaryContainer
                        ).value
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onTabClick(item.route) }
                    .padding(vertical = 8.dp),
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = stringResource(id = item.titleRes),
                    tint = if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .size(28.dp)
                        .graphicsLayer {
                            scaleX = visuals.iconScale
                            scaleY = visuals.iconScale
                            alpha = visuals.iconAlpha
                        }
                )
                Text(
                    text = stringResource(id = item.titleRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        Box(Modifier.height(8.dp))
    }
}
