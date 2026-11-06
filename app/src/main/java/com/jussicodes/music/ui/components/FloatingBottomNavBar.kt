package com.jussicodes.music.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jussicodes.music.constants.FloatingBottomNavBottomPadding
import com.jussicodes.music.constants.FloatingBottomNavHorizontalPadding
import com.jussicodes.music.constants.FloatingBottomNavTopPadding
import com.jussicodes.music.constants.FloatingBottomNavigationHeight
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 悬浮胶囊底栏：
 * 1. 采用与小播放器统一的圆角体系与水平对齐边距（14dp），形成整齐协调的悬浮控制组；
 * 2. 纯图标纯净展示，去除多余文本，支持横向拖拽与平滑弹性吸附；
 * 3. 共享相同的表面处理材质（surfaceContainer 色调、微光边框与 6dp 阴影）；
 * 4. 胶囊外部完全透明，背景壁纸与列表穿透显现；
 * 5. 最右侧集成设置入口 Tab，支持多 Tab 平滑滑块动画。
 */
@Composable
fun FloatingBottomNavBar(
    modifier: Modifier = Modifier,
    displayTabs: List<BottomBarTab> = floatingNavTabs,
    isHomeRoute: Boolean,
    homePage: Int,
    homePageScroll: Float,
    isTabSelected: (BottomBarTab) -> Boolean,
    onTabClick: (BottomBarTab) -> Unit,
) {
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val count = displayTabs.size
    val selectedIndex = remember(isHomeRoute, homePageScroll, homePage, displayTabs) {
        if (isHomeRoute) {
            homePageScroll.roundToInt().coerceIn(0, (count - 1).coerceAtLeast(0))
        } else {
            val idx = displayTabs.indexOfFirst { isTabSelected(it) }
            if (idx >= 0) idx else 0
        }
    }

    val sliderPosition = remember { Animatable(selectedIndex.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    val navBarHeightDp = FloatingBottomNavigationHeight // 50dp
    val pillCornerRadiusDp = navBarHeightDp / 2 // 25dp 全圆角胶囊
    val pillShape = RoundedCornerShape(pillCornerRadiusDp)

    // 页面 pager 滑动跟手（仅在主页时跟手滑动）
    LaunchedEffect(homePageScroll, isHomeRoute) {
        if (isHomeRoute && !isDragging) {
            sliderPosition.snapTo(homePageScroll.coerceIn(0f, (count - 1).toFloat()))
        }
    }

    // 外部索引变更平滑过渡
    LaunchedEffect(selectedIndex) {
        if (!isDragging && abs(sliderPosition.value - selectedIndex.toFloat()) > 0.01f) {
            sliderPosition.animateTo(
                targetValue = selectedIndex.toFloat(),
                animationSpec = spring(
                    dampingRatio = 0.82f,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    val settingsAlpha = if (isHomeRoute) {
        (homePageScroll - 1f).coerceIn(0f, 1f)
    } else 1f
    val surfaceColor = MaterialTheme.colorScheme.surface
    val outlineVariantColor = MaterialTheme.colorScheme.outlineVariant

    // 最外层容器：主页/探索保持完全透明穿透，进入设置页平滑过渡为不透明实色底衬
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (settingsAlpha > 0.005f) {
                    Modifier.drawBehind {
                        drawRect(
                            color = surfaceColor.copy(alpha = settingsAlpha)
                        )
                        drawLine(
                            color = outlineVariantColor.copy(alpha = 0.20f * settingsAlpha),
                            start = Offset(0f, 0f),
                            end = Offset(size.width, 0f),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                } else Modifier
            )
            .padding(
                start = FloatingBottomNavHorizontalPadding,
                end = FloatingBottomNavHorizontalPadding,
                top = FloatingBottomNavTopPadding,
                bottom = FloatingBottomNavBottomPadding + bottomInset
            ),
        contentAlignment = Alignment.Center
    ) {
        var dragStartSlot by remember { mutableFloatStateOf(0f) }
        var currentDragOffsetPx by remember { mutableFloatStateOf(0f) }
        var lastHapticSlot by remember { mutableIntStateOf(selectedIndex) }

        // 胶囊药丸主体：与小播放器共享宽度、圆角体系、阴影与表面材质
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(navBarHeightDp)
                .shadow(
                    elevation = 6.dp,
                    shape = pillShape,
                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    ambientColor = Color.Black.copy(alpha = 0.12f)
                )
                .clip(pillShape)
                .background(
                    if (settingsAlpha > 0.5f) {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    }
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                    shape = pillShape
                )
        ) {
            val totalWidthDp = maxWidth
            val innerPaddingDp = 4.dp
            val availableWidthDp = (totalWidthDp - innerPaddingDp * 2).coerceAtLeast(0.dp)
            val slotWidthDp = if (count > 0) availableWidthDp / count else 0.dp
            val indicatorWidthDp = (slotWidthDp - 8.dp).coerceIn(40.dp, 120.dp)
            val indicatorHeightDp = 40.dp
            val indicatorCornerRadiusDp = indicatorHeightDp / 2

            val slotWidthPx = with(density) { slotWidthDp.toPx() }
            val innerPaddingPx = with(density) { innerPaddingDp.toPx() }
            val indicatorWidthPx = with(density) { indicatorWidthDp.toPx() }

            // 手势监听挂在整个胶囊条上
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(count) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                isDragging = true
                                dragStartSlot = sliderPosition.value
                                currentDragOffsetPx = 0f
                                lastHapticSlot = sliderPosition.value.roundToInt().coerceIn(0, count - 1)
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                currentDragOffsetPx += dragAmount
                                val slotOffset = if (slotWidthPx > 0f) currentDragOffsetPx / slotWidthPx else 0f
                                val rawSlot = dragStartSlot + slotOffset
                                val maxIndex = (count - 1).toFloat()
                                val clampedSlot = rawSlot.coerceIn(-0.15f, maxIndex + 0.15f)
                                coroutineScope.launch {
                                    sliderPosition.snapTo(clampedSlot)
                                }
                                val currentSnapped = clampedSlot.roundToInt().coerceIn(0, count - 1)
                                if (currentSnapped != lastHapticSlot) {
                                    lastHapticSlot = currentSnapped
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            },
                            onDragEnd = {
                                isDragging = false
                                val targetIndex = sliderPosition.value.roundToInt().coerceIn(0, count - 1)
                                coroutineScope.launch {
                                    sliderPosition.animateTo(
                                        targetIndex.toFloat(),
                                        spring(
                                            dampingRatio = 0.82f,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                }
                                if (targetIndex in displayTabs.indices) {
                                    onTabClick(displayTabs[targetIndex])
                                }
                            },
                            onDragCancel = {
                                isDragging = false
                                coroutineScope.launch {
                                    sliderPosition.animateTo(
                                        selectedIndex.toFloat(),
                                        spring(
                                            dampingRatio = 0.82f,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                }
                            }
                        )
                    }
            ) {
                // 滑动指示器胶囊
                val currentSlot = sliderPosition.value
                val currentIndicatorXPx = innerPaddingPx + (currentSlot * slotWidthPx) + (slotWidthPx - indicatorWidthPx) / 2f

                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .graphicsLayer {
                            translationX = currentIndicatorXPx
                        }
                        .width(indicatorWidthDp)
                        .height(indicatorHeightDp)
                        .clip(RoundedCornerShape(indicatorCornerRadiusDp))
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        )
                )

                // Tab 图标 Row
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = innerPaddingDp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    displayTabs.forEachIndexed { index, item ->
                        val distance = abs(currentSlot - index).coerceIn(0f, 1f)
                        val activeFraction = 1f - distance

                        val iconTint = lerp(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                            MaterialTheme.colorScheme.primary,
                            activeFraction
                        )
                        val iconScale = 1.0f + 0.08f * activeFraction

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    if (selectedIndex != index) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    onTabClick(item)
                                    coroutineScope.launch {
                                        sliderPosition.animateTo(
                                            index.toFloat(),
                                            spring(
                                                dampingRatio = 0.82f,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = stringResource(id = item.titleRes),
                                tint = iconTint,
                                modifier = Modifier
                                    .size(24.dp)
                                    .graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    }
                            )
                        }
                    }
                }
            }
        }
    }
}
