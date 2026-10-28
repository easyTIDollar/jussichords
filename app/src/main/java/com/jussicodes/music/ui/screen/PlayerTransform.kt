package com.jussicodes.music.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.zIndex
import androidx.media3.common.MediaMetadata
import androidx.navigation.NavHostController
import com.jussicodes.music.animation.AlbumArtBoundsTransform
import com.jussicodes.music.animation.MotionDuration
import com.jussicodes.music.animation.MotionSpec
import com.jussicodes.music.animation.PanelBoundsPlaceHolder
import com.jussicodes.music.animation.SharedArtwork
import com.jussicodes.music.constants.BottomNavigationHeight
import com.jussicodes.music.constants.MiniPlayerHeight
import com.jussicodes.music.ui.components.ArtworkBackdropStyle
import com.jussicodes.music.ui.components.ArtworkGlassBackdrop
import com.jussicodes.music.ui.components.Lyric
import com.jussicodes.music.ui.components.MiniPlayer
import com.jussicodes.music.ui.components.Player
import com.jussicodes.music.ui.components.PlayerQueue
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

const val FULL_PLAYER = 0
const val PLAY_QUEUE = 1
const val MINI_PLAYER = 2
const val LYRIC_VIEW = 3

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PlayerTransform(
    onClick: () -> Unit = {},
    onBackPressed: () -> Unit = {},
    mediaMetadata: MediaMetadata,
    position: Long,
    duration: Long,
    onPositionUpdate: (Long) -> Unit,
    navController: NavHostController,
    expandSignal: Long = 0L,
    bottomPadding: Dp = 0.dp,
    onDragProgressChange: (Float) -> Unit = {},
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    var isExpanded by remember { mutableStateOf(false) }
    var fullPlayerSubView by remember { mutableIntStateOf(FULL_PLAYER) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenHeightPx = constraints.maxHeight.toFloat()
        val miniPlayerHeightPx = with(density) { MiniPlayerHeight.toPx() }
        val bottomPaddingPx = with(density) { bottomPadding.toPx() }
        val collapsedTargetY = (screenHeightPx - bottomPaddingPx - miniPlayerHeightPx).coerceAtLeast(0f)
        val navBarFullHeightPx = with(density) { (BottomNavigationHeight + bottomPadding).toPx() }

        val sheetTranslationY = remember { Animatable(collapsedTargetY) }

        // 当底栏尺寸或屏幕旋转等导致 collapsedTargetY 变化时，如果在收起态则同步调整位移
        LaunchedEffect(collapsedTargetY, isExpanded) {
            if (!isExpanded) {
                sheetTranslationY.snapTo(collapsedTargetY)
            }
        }

        // 外部信号（如私信一起听进房成功）请求展开全屏播放界面
        var lastExpanded by remember { mutableStateOf(expandSignal) }
        LaunchedEffect(expandSignal) {
            if (expandSignal > lastExpanded) {
                lastExpanded = expandSignal
                isExpanded = true
                coroutineScope.launch {
                    sheetTranslationY.animateTo(
                        0f,
                        spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
                    )
                }
                onClick()
            }
        }

        val progress = if (collapsedTargetY > 0f) {
            ((collapsedTargetY - sheetTranslationY.value) / collapsedTargetY).coerceIn(0f, 1f)
        } else {
            if (isExpanded) 1f else 0f
        }

        SideEffect {
            onDragProgressChange(progress)
        }

        // 拦截系统返回键：展开时优先优雅滑下收起面板
        BackHandler(enabled = isExpanded) {
            if (fullPlayerSubView != FULL_PLAYER) {
                fullPlayerSubView = FULL_PLAYER
            } else {
                isExpanded = false
                coroutineScope.launch {
                    sheetTranslationY.animateTo(
                        collapsedTargetY,
                        spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                    )
                    onBackPressed()
                }
            }
        }

        fun onDrag(deltaY: Float) {
            coroutineScope.launch {
                val current = sheetTranslationY.value
                val target = (current + deltaY).coerceIn(0f, collapsedTargetY)
                sheetTranslationY.snapTo(target)
            }
        }

        fun onDragEnd(velocityY: Float) {
            coroutineScope.launch {
                val minDragThresholdPx = with(density) { 40.dp.toPx() }
                val velocityThreshold = 250f

                if (isExpanded) {
                    // 全屏状态下向下滑动退出：结合位移与惯性速度（PixelPlayer 方案）
                    val dragDistanceDown = sheetTranslationY.value
                    val isFlingDown = velocityY >= velocityThreshold
                    val isDragDown = dragDistanceDown >= minDragThresholdPx

                    if (velocityY <= -velocityThreshold) {
                        // 向上甩动快速回弹全屏
                        sheetTranslationY.animateTo(
                            0f,
                            spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow),
                            initialVelocity = velocityY
                        )
                    } else if (isDragDown || isFlingDown) {
                        isExpanded = false
                        sheetTranslationY.animateTo(
                            collapsedTargetY,
                            spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow),
                            initialVelocity = velocityY
                        )
                        onBackPressed()
                    } else {
                        // 未达阈值平滑回弹全屏
                        sheetTranslationY.animateTo(
                            0f,
                            spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow),
                            initialVelocity = velocityY
                        )
                    }
                } else {
                    // 收起状态下向上滑动展开
                    val dragDistanceUp = collapsedTargetY - sheetTranslationY.value
                    val isFlingUp = velocityY <= -velocityThreshold
                    val isDragUp = dragDistanceUp >= minDragThresholdPx

                    if (velocityY >= velocityThreshold) {
                        // 向下甩动快速收回
                        sheetTranslationY.animateTo(
                            collapsedTargetY,
                            spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow),
                            initialVelocity = velocityY
                        )
                        onBackPressed()
                    } else if (isDragUp || isFlingUp) {
                        isExpanded = true
                        sheetTranslationY.animateTo(
                            0f,
                            spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow),
                            initialVelocity = velocityY
                        )
                        onClick()
                    } else {
                        sheetTranslationY.animateTo(
                            collapsedTargetY,
                            spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow),
                            initialVelocity = velocityY
                        )
                        onBackPressed()
                    }
                }
            }
        }

        val cornerRadius = lerp(16.dp, 0.dp, progress)
        // 联动效果：手势上滑展开时，底栏在 1/3 程（progress 0f ~ 0.33f）完全向下沉出屏幕；
        // 播放器卡片底端在 1/3 程内始终紧咬底栏上沿同步下沉，底栏退出后稳稳贴合屏幕底端。
        val navBarExitProgress = (progress * 3f).coerceIn(0f, 1f)
        val startBottomY = screenHeightPx - bottomPaddingPx
        val seamOverlapPx = with(density) { 1.dp.toPx() } * navBarExitProgress
        val bottomY = startBottomY + navBarExitProgress * bottomPaddingPx + seamOverlapPx
        val currentCardHeightPx = (bottomY - sheetTranslationY.value).coerceAtLeast(miniPlayerHeightPx)

        // 透明度与视差（PixelPlayer 方案）：
        // 1. 小播放器：0f ~ 0.50f 线性淡出，半屏以下（progress < 0.5f）zIndex 在上
        val miniPlayerAlpha = (1f - progress * 2f).coerceIn(0f, 1f)
        val miniPlayerZIndex = if (progress < 0.5f) 1f else 0f

        // 2. 全屏播放器：0.25f 之前完全不可见（alpha = 0）；0.25f ~ 1.0f 线性淡入，并伴随由下至上的视差位移
        val fullContentAlpha = ((progress - 0.25f) / 0.75f).coerceIn(0f, 1f)
        val initialFullPlayerOffsetY = with(density) { 32.dp.toPx() }
        val fullPlayerTranslationY = initialFullPlayerOffsetY * (1f - fullContentAlpha)
        val fullPlayerZIndex = if (progress >= 0.5f) 1f else 0f

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { currentCardHeightPx.toDp() })
                .offset { IntOffset(0, sheetTranslationY.value.roundToInt()) },
            shape = RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 磨砂玻璃底板
                ArtworkGlassBackdrop(
                    artwork = mediaMetadata.artworkUri,
                    style = if (progress > 0.5f) ArtworkBackdropStyle.FullScreen else ArtworkBackdropStyle.Compact,
                    modifier = Modifier.fillMaxSize()
                )

                // 小播放器层（MiniPlayer 顶栏，高度 64dp）
                if (progress < 0.99f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(MiniPlayerHeight)
                            .graphicsLayer {
                                alpha = miniPlayerAlpha
                            }
                            .zIndex(miniPlayerZIndex)
                    ) {
                        MiniPlayer(
                            mediaMetadata = mediaMetadata,
                            position = position,
                            duration = duration,
                            onClick = {
                                coroutineScope.launch {
                                    isExpanded = true
                                    sheetTranslationY.animateTo(
                                        0f,
                                        spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow)
                                    )
                                    onClick()
                                }
                            },
                            onVerticalDrag = { deltaY -> onDrag(deltaY) },
                            onVerticalDragEnd = { velocityY -> onDragEnd(velocityY) },
                            dragProgress = progress,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // 全屏播放器层（全屏任意区域下拉手势 + 视差滑移退出动效）
                if (progress > 0.01f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(with(density) { screenHeightPx.toDp() })
                            .graphicsLayer {
                                alpha = fullContentAlpha
                                translationY = fullPlayerTranslationY
                            }
                            .zIndex(fullPlayerZIndex)
                            .pointerInput(isExpanded) {
                                if (isExpanded) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        val velocityTracker = VelocityTracker()
                                        velocityTracker.addPosition(down.uptimeMillis, down.position)

                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            if (!change.pressed) break

                                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                                            val positionChange = change.positionChange()

                                            if (positionChange.y > 0f || sheetTranslationY.value > 0f) {
                                                change.consume()
                                                onDrag(positionChange.y)
                                            }
                                        }

                                        val velocityY = velocityTracker.calculateVelocity().y
                                        onDragEnd(velocityY)
                                    }
                                }
                            }
                    ) {
                        SharedTransitionLayout {
                            AnimatedContent(
                                targetState = fullPlayerSubView,
                                transitionSpec = {
                                    fadeIn(
                                        MotionSpec.panelFadeIn<Float>(delayMillis = MotionDuration.ScreenExit)
                                    ) togetherWith fadeOut(
                                        MotionSpec.panelFadeOut<Float>()
                                    )
                                }
                            ) { subView ->
                                when (subView) {
                                    FULL_PLAYER -> Player(
                                        navController = navController,
                                        mediaMetadata = mediaMetadata,
                                        duration = duration,
                                        position = position,
                                        imageModifier = Modifier.sharedElement(
                                            state = rememberSharedContentState(
                                                key = SharedArtwork.ofMetadata(mediaMetadata)
                                            ),
                                            animatedVisibilityScope = this,
                                            placeHolderSize = PanelBoundsPlaceHolder,
                                            boundsTransform = AlbumArtBoundsTransform,
                                        ),
                                        onBackPressed = {
                                            coroutineScope.launch {
                                                isExpanded = false
                                                sheetTranslationY.animateTo(
                                                    collapsedTargetY,
                                                    spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                                                )
                                                onBackPressed()
                                            }
                                        },
                                        onClick = { fullPlayerSubView = LYRIC_VIEW },
                                        onContainerClick = { fullPlayerSubView = PLAY_QUEUE },
                                        onPositionUpdate = onPositionUpdate
                                    )

                                    PLAY_QUEUE -> PlayerQueue(
                                        mediaMetadata = mediaMetadata,
                                        imageModifier = Modifier.sharedElement(
                                            state = rememberSharedContentState(
                                                key = SharedArtwork.ofMetadata(mediaMetadata)
                                            ),
                                            animatedVisibilityScope = this,
                                            placeHolderSize = PanelBoundsPlaceHolder,
                                            boundsTransform = AlbumArtBoundsTransform,
                                        ),
                                        onBackPressed = { fullPlayerSubView = FULL_PLAYER }
                                    )

                                    LYRIC_VIEW -> Lyric(
                                        position = position,
                                        mediaMetadata = mediaMetadata,
                                        imageModifier = Modifier.sharedElement(
                                            state = rememberSharedContentState(
                                                key = SharedArtwork.ofMetadata(mediaMetadata)
                                            ),
                                            animatedVisibilityScope = this,
                                            placeHolderSize = PanelBoundsPlaceHolder,
                                            boundsTransform = AlbumArtBoundsTransform,
                                        ),
                                        onBackPressed = { fullPlayerSubView = FULL_PLAYER }
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
