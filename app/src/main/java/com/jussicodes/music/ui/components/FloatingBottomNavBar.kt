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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.shadow
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
import com.jussicodes.music.constants.FloatingBottomNavTopPadding
import com.jussicodes.music.constants.FloatingBottomNavigationHeight
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 悬浮胶囊底栏：
 * 1. 紧凑精致药丸造型，居中悬浮；
 * 2. 纯图标纯净展示，去除文字，收窄 Tab 宽度；
 * 3. 内部滑块支持左右拖拽手势实时跟手，松手阻尼吸附，点击与切页带有弹性平滑滑动动画；
 * 4. 外部容器完全透明，不遮挡底层壁纸与内容。
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
    val count = tabs.size
    val selectedIndex = remember(isHomeRoute, homePageScroll, homePage) {
        if (isHomeRoute) {
            homePageScroll.roundToInt().coerceIn(0, (count - 1).coerceAtLeast(0))
        } else {
            tabs.indexOfFirst { isTabSelected(it) }.coerceAtLeast(0)
        }
    }

    val sliderPosition = remember { Animatable(selectedIndex.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    // 单个 tab 宽度 66dp，滑块 58dp
    val tabWidthDp = 66.dp
    val tabHeightDp = FloatingBottomNavigationHeight // 50dp
    val indicatorWidthDp = 58.dp
    val indicatorHeightDp = 40.dp
    val outerPaddingDp = 4.dp
    val totalPillWidthDp = outerPaddingDp * 2 + tabWidthDp * count
    val indicatorStartGapDp = outerPaddingDp + (tabWidthDp - indicatorWidthDp) / 2

    val tabWidthPx = with(density) { tabWidthDp.toPx() }
    val indicatorStartGapPx = with(density) { indicatorStartGapDp.toPx() }

    // 页面 pager 滑动跟手
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

    // 最外层容器：背景完全透明，负责底部避让定位与小播放器间隙
    Box(
        modifier = modifier
            .padding(
                top = FloatingBottomNavTopPadding,
                bottom = FloatingBottomNavBottomPadding + bottomInset
            ),
        contentAlignment = Alignment.Center
    ) {
        var dragStartSlot by remember { mutableFloatStateOf(0f) }
        var currentDragOffsetPx by remember { mutableFloatStateOf(0f) }
        var lastHapticSlot by remember { mutableIntStateOf(selectedIndex) }

        // 胶囊药丸主体
        Box(
            modifier = Modifier
                .width(totalPillWidthDp)
                .height(tabHeightDp)
                .shadow(
                    elevation = 8.dp,
                    shape = CircleShape,
                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    ambientColor = Color.Black.copy(alpha = 0.12f)
                )
                .clip(CircleShape)
                .background(
                    MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.95f)
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                    shape = CircleShape
                )
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
                            val slotOffset = if (tabWidthPx > 0f) currentDragOffsetPx / tabWidthPx else 0f
                            val rawSlot = dragStartSlot + slotOffset
                            val maxIndex = (count - 1).toFloat()
                            val clampedSlot = rawSlot.coerceIn(-0.25f, maxIndex + 0.25f)
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
                            if (targetIndex in tabs.indices) {
                                onTabClick(tabs[targetIndex])
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
            val currentIndicatorXPx = indicatorStartGapPx + (currentSlot * tabWidthPx)

            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .graphicsLayer {
                        translationX = currentIndicatorXPx
                    }
                    .width(indicatorWidthDp)
                    .height(indicatorHeightDp)
                    .clip(RoundedCornerShape(indicatorHeightDp / 2))
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    )
            )

            // 图标行
            Row(
                modifier = Modifier
                    .padding(horizontal = outerPaddingDp)
                    .height(tabHeightDp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, item ->
                    val distance = abs(currentSlot - index).coerceIn(0f, 1f)
                    val activeFraction = 1f - distance

                    val iconTint = lerp(
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        MaterialTheme.colorScheme.primary,
                        activeFraction
                    )
                    val iconScale = 1.0f + 0.08f * activeFraction

                    Box(
                        modifier = Modifier
                            .width(tabWidthDp)
                            .height(tabHeightDp)
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
