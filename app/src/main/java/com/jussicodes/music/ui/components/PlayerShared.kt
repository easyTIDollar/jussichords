package com.jussicodes.music.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jussicodes.music.animation.MotionSpring
import com.jussicodes.music.ui.icons.PauseFill
import com.jussicodes.music.ui.icons.Repeat
import com.jussicodes.music.ui.icons.RepeatOne
import com.jussicodes.music.ui.icons.Shuffle
import com.jussicodes.music.ui.icons.SkipNextFill
import com.jussicodes.music.ui.icons.SkipPreviousFill
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 播放控制三键（上一首 / 播放暂停 / 下一首）。
 *
 * 竖屏播放器、横屏播放器、私人 FM 三处共用这一份实现 —— 按钮样式
 * （FilledTonal 侧键 + Filled 主键）与点击反馈完全一致，避免同一功能
 * 在不同页面长得不一样。
 */
@Composable
fun PlayerControlButtons(
    isPlaying: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier,
    playButtonSize: Dp = 72.dp,
    sideButtonSize: Dp = 48.dp,
    spacing: Dp = 20.dp,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(spacing),
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = horizontalArrangement,
    ) {
        FilledTonalIconButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onPrevious()
            },
            modifier = Modifier.size(sideButtonSize)
        ) {
            Icon(SkipPreviousFill, contentDescription = null)
        }
        FilledIconButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onTogglePlay()
            },
            modifier = Modifier.size(playButtonSize)
        ) {
            Icon(
                imageVector = if (isPlaying) PauseFill else Icons.Filled.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(playButtonSize * 0.5f)
            )
        }
        FilledTonalIconButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onNext()
            },
            modifier = Modifier.size(sideButtonSize)
        ) {
            Icon(SkipNextFill, contentDescription = null)
        }
    }
}

/**
 * 统一的细进度条：3dp 细轨道 + 竖条小滑块。
 *
 * 竖屏播放器 / 横屏播放器 / 私人 FM 共用，保证三处视觉一致。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerProgressSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
) {
    Slider(
        value = value,
        valueRange = valueRange,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        colors = SliderDefaults.colors(
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
            thumbColor = MaterialTheme.colorScheme.primary,
        ),
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                modifier = Modifier.height(3.dp),
                thumbTrackGapSize = 0.dp,
                drawStopIndicator = null,
            )
        },
        thumb = {
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = 16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        },
        modifier = modifier.height(28.dp),
    )
}

/** 播放模式（循环 + 随机合并成一个按钮）的状态。 */
enum class PlaybackModeState {
    /** 顺序播放 */
    SEQUENTIAL,

    /** 列表循环 */
    REPEAT_ALL,

    /** 单曲循环 */
    REPEAT_ONE,

    /** 随机播放 */
    SHUFFLE,
}

/** 由播放器的真实状态推导当前播放模式。 */
fun currentPlaybackMode(shuffleOn: Boolean, repeatMode: Int): PlaybackModeState = when {
    shuffleOn -> PlaybackModeState.SHUFFLE
    repeatMode == 2 -> PlaybackModeState.REPEAT_ALL
    repeatMode == 1 -> PlaybackModeState.REPEAT_ONE
    else -> PlaybackModeState.SEQUENTIAL
}

/** 播放模式按钮的循环顺序：顺序 → 列表循环 → 单曲循环 → 随机 → 顺序。 */
fun nextPlaybackMode(state: PlaybackModeState): PlaybackModeState = when (state) {
    PlaybackModeState.SEQUENTIAL -> PlaybackModeState.REPEAT_ALL
    PlaybackModeState.REPEAT_ALL -> PlaybackModeState.REPEAT_ONE
    PlaybackModeState.REPEAT_ONE -> PlaybackModeState.SHUFFLE
    PlaybackModeState.SHUFFLE -> PlaybackModeState.SEQUENTIAL
}

/** 播放模式对应的图标。 */
fun playbackModeIcon(state: PlaybackModeState): ImageVector = when (state) {
    PlaybackModeState.SEQUENTIAL -> Repeat
    PlaybackModeState.REPEAT_ALL -> Repeat
    PlaybackModeState.REPEAT_ONE -> RepeatOne
    PlaybackModeState.SHUFFLE -> Shuffle
}

/**
 * 播放模式按钮：把「循环模式切换」和「随机开关」合并成一个按钮，
 * 点击在 顺序 / 列表循环 / 单曲循环 / 随机 之间依次切换。
 */
@Composable
fun PlaybackModeButton(
    state: PlaybackModeState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    touchSize: Dp = 40.dp,
) {
    val primary = MaterialTheme.colorScheme.primary
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val (icon, tint) = when (state) {
        PlaybackModeState.SEQUENTIAL -> Repeat to dim
        PlaybackModeState.REPEAT_ALL -> Repeat to primary
        PlaybackModeState.REPEAT_ONE -> RepeatOne to primary
        PlaybackModeState.SHUFFLE -> Shuffle to primary
    }
    Box(
        modifier = modifier
            .size(touchSize)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size)
        )
    }
}

/**
 * 封面手势状态：横向拖动切歌、纵向拖动下拉收起。
 *
 * 从竖屏播放器抽出来的公共实现，横屏播放器直接复用（横屏不接点击进歌词页，
 * 由调用方决定要不要挂 onTap）。
 */
@Stable
class PlayerCoverGestures internal constructor(
    private val scope: CoroutineScope,
    private val haptics: HapticFeedback,
    private val swipeThresholdPx: Float,
    private val coverDragLimitPx: Float,
    private val swipeEdgeHapticPx: Float,
    private val dismissThresholdPx: Float,
    private val coverDismissLimitPx: Float,
    private val dragDirectionSlopPx: Float,
) {
    /** 切下一首的回调（每次重组更新，避免手势状态被重建）。 */
    internal var onSwipeNext: () -> Unit = {}
    internal var onSwipePrevious: () -> Unit = {}
    internal var onDismiss: () -> Unit = {}
    /** 横向拖动位移（封面自身平移 + 轻微旋转）。 */
    var offsetX by mutableFloatStateOf(0f)

    /** 纵向拖动位移（整体下拉）。 */
    var offsetY by mutableFloatStateOf(0f)

    private var swipeThresholdHapticFired = false
    private var swipeCancelHapticFired = false
    private var offsetXJob: Job? = null
    private var offsetYJob: Job? = null

    /** 拖动进度（0..1），用于做缩放等视觉反馈。 */
    val dragProgress: Float
        get() = maxOf(
            abs(offsetX) / swipeThresholdPx,
            offsetY / dismissThresholdPx
        ).coerceIn(0f, 1f)

    private fun animateOffsetXTo(target: Float) {
        offsetXJob?.cancel()
        offsetXJob = scope.launch {
            animate(
                initialValue = offsetX,
                targetValue = target,
                animationSpec = MotionSpring.artworkHorizontal
            ) { value, _ -> offsetX = value }
        }
    }

    private fun animateOffsetYTo(target: Float) {
        offsetYJob?.cancel()
        offsetYJob = scope.launch {
            animate(
                initialValue = offsetY,
                targetValue = target,
                animationSpec = MotionSpring.artworkDismiss
            ) { value, _ -> offsetY = value }
        }
    }

    /** 横向拖动手势：左右滑动切歌。 */
    fun coverSwipeGestures(): Modifier = Modifier
        .pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = {
                    offsetXJob?.cancel()
                    swipeThresholdHapticFired = false
                    swipeCancelHapticFired = false
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                onHorizontalDrag = { change, dragAmount ->
                    change.consume()
                    offsetX = (offsetX + dragAmount).coerceIn(-coverDragLimitPx, coverDragLimitPx)
                    if (abs(offsetX) >= swipeEdgeHapticPx && !swipeThresholdHapticFired) {
                        swipeThresholdHapticFired = true
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                },
                onDragEnd = {
                    val switched = when {
                        offsetX <= -swipeThresholdPx -> {
                            onSwipeNext(); true
                        }
                        offsetX >= swipeThresholdPx -> {
                            onSwipePrevious(); true
                        }
                        else -> false
                    }
                    if (switched) {
                        swipeThresholdHapticFired = false
                        swipeCancelHapticFired = false
                    } else if (swipeThresholdHapticFired && !swipeCancelHapticFired) {
                        swipeCancelHapticFired = true
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    animateOffsetXTo(0f)
                },
                onDragCancel = {
                    swipeThresholdHapticFired = false
                    swipeCancelHapticFired = false
                    animateOffsetXTo(0f)
                }
            )
        }

    /** 纵向拖动手势：下拉收起播放器。 */
    fun coverDismissGestures(enabled: Boolean): Modifier = Modifier
        .pointerInput(enabled, dismissThresholdPx) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                offsetYJob?.cancel()

                var totalDragX = 0f
                var totalDragY = 0f
                var isVerticalDrag: Boolean? = null
                var isDismissed = false

                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break

                    val positionChange = change.positionChange()
                    totalDragX += positionChange.x
                    totalDragY += positionChange.y

                    if (isVerticalDrag == null &&
                        (abs(totalDragX) > dragDirectionSlopPx || abs(totalDragY) > dragDirectionSlopPx)
                    ) {
                        isVerticalDrag = abs(totalDragY) > abs(totalDragX)
                    }

                    if (isVerticalDrag == false) break

                    if (isVerticalDrag == true) {
                        offsetY = (offsetY + positionChange.y).coerceIn(0f, coverDismissLimitPx)
                        if (offsetY > 0f) change.consume()
                    }
                }

                if (isVerticalDrag == true) {
                    if (offsetY >= dismissThresholdPx) {
                        offsetY = 0f
                        isDismissed = true
                        onDismiss()
                    } else {
                        animateOffsetYTo(0f)
                    }
                }

                if (!isDismissed && isVerticalDrag != true && offsetY > 0f) {
                    animateOffsetYTo(0f)
                }
            }
        }

    /** 封面自身的变换：横向平移 + 旋转 + 缩放。 */
    fun coverGraphics(): Modifier = Modifier.graphicsLayer {
        translationX = offsetX
        rotationZ = (offsetX / swipeThresholdPx).coerceIn(-1f, 1f) * 6f
        val scale = 1f - dragProgress * 0.04f
        scaleX = scale
        scaleY = scale
    }

    /** 整体下拉时的位移 + 轻微缩放（挂在左栏容器上）。 */
    fun dismissGraphics(): Modifier = Modifier.graphicsLayer {
        translationY = offsetY
        val scale = 1f - (offsetY / dismissThresholdPx).coerceIn(0f, 1f) * 0.02f
        scaleX = scale
        scaleY = scale
    }
}

/** 创建封面手势状态。阈值与竖屏播放器保持一致。 */
@Composable
fun rememberPlayerCoverGestures(
    onSwipeNext: () -> Unit,
    onSwipePrevious: () -> Unit,
    onDismiss: () -> Unit,
): PlayerCoverGestures {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val gestures = remember {
        val swipeThresholdPx = with(density) { 72.dp.toPx() }
        val dismissThresholdPx = with(density) { 96.dp.toPx() }
        PlayerCoverGestures(
            scope = scope,
            haptics = haptics,
            swipeThresholdPx = swipeThresholdPx,
            coverDragLimitPx = swipeThresholdPx * 1.6f,
            swipeEdgeHapticPx = swipeThresholdPx * 0.7f,
            dismissThresholdPx = dismissThresholdPx,
            coverDismissLimitPx = dismissThresholdPx * 1.1f,
            dragDirectionSlopPx = with(density) { 3.dp.toPx() },
        )
    }
    // 回调每次重组刷新，但手势状态对象本身保持稳定（否则拖动中会被重建）。
    gestures.onSwipeNext = onSwipeNext
    gestures.onSwipePrevious = onSwipePrevious
    gestures.onDismiss = onDismiss
    return gestures
}
