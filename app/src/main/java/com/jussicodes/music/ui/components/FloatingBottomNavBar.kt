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
    /**
     * 该次 pager 滚动是否由「点/拖底栏」触发的程序化补间（而非用户手指在内容区滑页）。
     *
     * 程序化补间期间 homePageScroll 会从**起点页**开始连续爬向目标页；如果此时仍让指示器
     * 跟它跟手，松手后刚起手的吸附动画就会被反向拽回起点（用户感知为「像磁铁往左吸住」）。
     * 因此程序化补间期间指示器只由自身 spring 负责，等 pager 落页后再做一次精确对齐。
     */
    isPagerAutoScroll: Boolean = false,
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
    // 手指拖拽期间的实时槽位：拖拽只用普通状态驱动指示器，绝不逐个 move 事件去 launch 一个
    // snapTo —— 那种写法会让 snapTo 协程排队，松手后队列里残留的旧值会把指示器拽回手指
    // 早先的位置（也是「往左吸」的一个来源）。
    var dragSlot by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
    // 指示器自身 spring 收尾中：这段时间同样不接受 pager 位置覆盖，
    // 否则 pager 补间先结束时会把还没走完的 spring 硬怼到终点。
    var isSettling by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    val navBarHeightDp = FloatingBottomNavigationHeight // 50dp
    val pillCornerRadiusDp = navBarHeightDp / 2 // 25dp 全圆角胶囊
    val pillShape = RoundedCornerShape(pillCornerRadiusDp)

    // 页面 pager 滑动跟手（仅在主页时跟手滑动）
    // 关键：程序化补间（点/拖底栏后 pager 从起点页滚向目标页）期间不跟手，
    // 否则会把刚起手的吸附动画按 pager 的中间值覆盖回起点。
    LaunchedEffect(homePageScroll, isHomeRoute, isPagerAutoScroll) {
        if (isHomeRoute && !isDragging && !isSettling && !isPagerAutoScroll) {
            sliderPosition.snapTo(homePageScroll.coerceIn(0f, (count - 1).toFloat()))
        }
    }

    // 外部索引变更平滑过渡
    LaunchedEffect(selectedIndex, isPagerAutoScroll) {
        if (!isDragging && !isSettling && !isPagerAutoScroll &&
            abs(sliderPosition.value - selectedIndex.toFloat()) > 0.01f
        ) {
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
                        // 手势内的临时量：只在本 gesture 生命周期内有效
                        var dragAccumPx = 0f
                        var dragStartSlot = 0f
                        detectHorizontalDragGestures(
                            onDragStart = {
                                isDragging = true
                                dragStartSlot = sliderPosition.value
                                dragSlot = dragStartSlot
                                dragAccumPx = 0f
                                lastHapticSlot = sliderPosition.value.roundToInt().coerceIn(0, count - 1)
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                dragAccumPx += dragAmount
                                val slotOffset = if (slotWidthPx > 0f) dragAccumPx / slotWidthPx else 0f
                                val rawSlot = dragStartSlot + slotOffset
                                val maxIndex = (count - 1).toFloat()
                                val clampedSlot = rawSlot.coerceIn(-0.15f, maxIndex + 0.15f)
                                // 纯状态写入：指示器在下一帧直接渲染到手指位置，零延迟、无协程排队
                                dragSlot = clampedSlot
                                val currentSnapped = clampedSlot.roundToInt().coerceIn(0, count - 1)
                                if (currentSnapped != lastHapticSlot) {
                                    lastHapticSlot = currentSnapped
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            },
                            onDragEnd = {
                                val releasedSlot = dragSlot
                                val targetIndex = releasedSlot.roundToInt().coerceIn(0, count - 1)
                                isSettling = true
                                coroutineScope.launch {
                                    try {
                                        // 先把 Animatable 对齐到手指松开位置，再切回它作为渲染源（零跳变），
                                        // 从该位置弹性吸附到最近 tab；不再先松手再补 snap，避免一帧回跳。
                                        sliderPosition.snapTo(releasedSlot)
                                        isDragging = false
                                        if (targetIndex in displayTabs.indices) {
                                            // 触发 pager 切页；MainScreen 会把它标记成程序化滚动，
                                            // 指示器在 pager 补间期间不被 homePageScroll 反向覆盖。
                                            onTabClick(displayTabs[targetIndex])
                                        }
                                        sliderPosition.animateTo(
                                            targetIndex.toFloat(),
                                            spring(
                                                dampingRatio = 0.82f,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                    } finally {
                                        isSettling = false
                                    }
                                }
                            },
                            onDragCancel = {
                                val releasedSlot = dragSlot
                                isSettling = true
                                coroutineScope.launch {
                                    try {
                                        sliderPosition.snapTo(releasedSlot)
                                        isDragging = false
                                        sliderPosition.animateTo(
                                            selectedIndex.toFloat(),
                                            spring(
                                                dampingRatio = 0.82f,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                    } finally {
                                        isSettling = false
                                    }
                                }
                            }
                        )
                    }
            ) {
                // 滑动指示器胶囊：拖拽期间直接读 dragSlot（零延迟跟手），其余时刻读 Animatable
                val currentSlot = if (isDragging) dragSlot else sliderPosition.value
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
                                    isSettling = true
                                    coroutineScope.launch {
                                        try {
                                            sliderPosition.animateTo(
                                                index.toFloat(),
                                                spring(
                                                    dampingRatio = 0.82f,
                                                    stiffness = Spring.StiffnessMediumLow
                                                )
                                            )
                                        } finally {
                                            isSettling = false
                                        }
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
