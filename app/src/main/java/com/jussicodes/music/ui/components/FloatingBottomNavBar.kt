package com.jussicodes.music.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jussicodes.music.animation.MotionSpec
import com.jussicodes.music.animation.rememberTabSelectionVisuals
import com.jussicodes.music.constants.FloatingBottomNavBottomPadding
import com.jussicodes.music.constants.FloatingBottomNavHorizontalPadding
import com.jussicodes.music.constants.FloatingBottomNavTopPadding
import com.jussicodes.music.constants.FloatingBottomNavigationHeight

/**
 * Bettbox 1.19 风格悬浮药丸底栏（Floating Bottom Bar）：
 * 1. 悬浮胶囊外形（全圆角 30dp + 双层质感投影 + 细微边框 + 微透 surfaceContainer）；
 * 2. 内部滑动指示胶囊（滑块随手势/切页平滑移动，浅主色半透明背景）；
 * 3. 紧凑型「图标 (22dp) + 标签 (10.5sp)」纵向排版，选中态缩放与 primary 高亮联动；
 * 4. 点击触觉反馈（HapticFeedback）。
 */
@Composable
fun FloatingBottomNavBar(
    modifier: Modifier = Modifier,
    isHomeRoute: Boolean,
    homePage: Int,
    homePageScroll: Float,
    isTabSelected: (BottomBarTab) -> Boolean,
    onTabClick: (BottomBarTab) -> Unit,
) {
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = FloatingBottomNavHorizontalPadding,
                end = FloatingBottomNavHorizontalPadding,
                top = FloatingBottomNavTopPadding,
                bottom = FloatingBottomNavBottomPadding + bottomInset
            ),
        contentAlignment = Alignment.Center
    ) {
        // 药丸悬浮外壳
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(FloatingBottomNavigationHeight)
                .shadow(
                    elevation = 8.dp,
                    shape = CircleShape,
                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    ambientColor = Color.Black.copy(alpha = 0.12f)
                )
                .clip(CircleShape)
                .background(
                    MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.96f)
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                    shape = CircleShape
                )
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val count = tabs.size
                if (count > 0) {
                    val itemWidth = maxWidth / count
                    val pillHorizontalGap = 4.dp
                    val pillVerticalGap = 4.dp
                    val indicatorWidth = (itemWidth - pillHorizontalGap * 2).coerceAtLeast(0.dp)
                    val indicatorHeight = (FloatingBottomNavigationHeight - pillVerticalGap * 2).coerceAtLeast(0.dp)

                    val rawIndicatorOffset = itemWidth * if (isHomeRoute) homePageScroll else homePage.toFloat()
                    val indicatorOffset by animateDpAsState(
                        targetValue = rawIndicatorOffset,
                        animationSpec = if (isHomeRoute) {
                            snap()
                        } else {
                            MotionSpec.indicatorShift()
                        },
                        label = "FloatingTabIndicatorOffset"
                    )

                    // 背后滑动的连续高亮胶囊
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = indicatorOffset + pillHorizontalGap)
                            .width(indicatorWidth)
                            .height(indicatorHeight)
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            )
                    )

                    val haptic = LocalHapticFeedback.current

                    Row(Modifier.fillMaxSize()) {
                        tabs.forEachIndexed { tabIndex, item ->
                            val selected = isTabSelected(item)
                            val visuals = rememberTabSelectionVisuals(
                                isHomeRoute = isHomeRoute,
                                homePageScroll = homePageScroll,
                                index = tabIndex,
                                selected = selected
                            )

                            val itemColor = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        if (!selected) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                        onTabClick(item)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.graphicsLayer {
                                        scaleX = visuals.iconScale
                                        scaleY = visuals.iconScale
                                        alpha = visuals.iconAlpha
                                    }
                                ) {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = stringResource(id = item.titleRes),
                                        tint = itemColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = stringResource(item.titleRes),
                                        color = itemColor,
                                        fontSize = 10.5.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
