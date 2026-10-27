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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
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
                if (isExpanded) {
                    // 全屏状态下向下滑动退出：阈值 15px 或向下的速度
                    val dragDistanceDown = sheetTranslationY.value
                    val isDragDown = dragDistanceDown >= 15f
                    val isFlingDown = velocityY >= 150f
                    if (isDragDown || isFlingDown) {
                        isExpanded = false
                        sheetTranslationY.animateTo(
                            collapsedTargetY,
                            spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                        )
                        onBackPressed()
                    } else {
                        // 未达阈值平滑回弹全屏
                        sheetTranslationY.animateTo(
                            0f,
                            spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
                        )
                    }
                } else {
                    // 收起状态下向上滑动展开：阈值 10px 或向上的速度
                    val dragDistanceUp = collapsedTargetY - sheetTranslationY.value
                    val isDragUp = dragDistanceUp >= 10f
                    val isFlingUp = velocityY <= -150f
                    if (isDragUp || isFlingUp) {
                        isExpanded = true
                        sheetTranslationY.animateTo(
                            0f,
                            spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
                        )
                        onClick()
                    } else {
                        // 未达阈值平滑回弹收起
                        sheetTranslationY.animateTo(
                            collapsedTargetY,
                            spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
                        )
                        onBackPressed()
                    }
                }
            }
        }

        val cornerRadius = lerp(16.dp, 0.dp, progress)
        val bottomOvershootPx = with(density) { 64.dp.toPx() }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { (screenHeightPx + bottomOvershootPx).toDp() })
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
                                alpha = (1f - progress * 2.5f).coerceIn(0f, 1f)
                            }
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
                                        spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
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

                // 全屏播放器层（全屏任意区域下拉手势 + 优雅退出动效）
                if (progress > 0.01f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(with(density) { screenHeightPx.toDp() })
                            .graphicsLayer {
                                alpha = ((progress - 0.20f) / 0.80f).coerceIn(0f, 1f)
                            }
                            .pointerInput(isExpanded) {
                                if (isExpanded) {
                                    detectVerticalDragGestures(
                                        onVerticalDrag = { change, dragAmount ->
                                            if (dragAmount > 0f || sheetTranslationY.value > 0f) {
                                                change.consume()
                                                onDrag(dragAmount)
                                            }
                                        },
                                        onDragEnd = {
                                            onDragEnd(0f)
                                        }
                                    )
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
