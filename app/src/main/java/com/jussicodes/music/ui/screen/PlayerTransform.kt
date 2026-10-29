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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalViewConfiguration
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
import com.jussicodes.music.animation.MotionSpring
import com.jussicodes.music.animation.PanelBoundsPlaceHolder
import com.jussicodes.music.animation.SharedArtwork
import com.jussicodes.music.constants.BottomNavigationHeight
import com.jussicodes.music.constants.FloatingBottomNavHorizontalPadding
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
    floatingNavBarEnabled: Boolean = false,
    onDragProgressChange: (Float) -> Unit = {},
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val viewConfiguration = LocalViewConfiguration.current

    var isExpanded by remember { mutableStateOf(false) }
    var fullPlayerSubView by remember { mutableIntStateOf(FULL_PLAYER) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenHeightPx = constraints.maxHeight.toFloat()
        val miniPlayerHeightPx = with(density) { MiniPlayerHeight.toPx() }
        val bottomPaddingPx = with(density) { bottomPadding.toPx() }
        val collapsedTargetY = (screenHeightPx - bottomPaddingPx - miniPlayerHeightPx).coerceAtLeast(0f)
        val navBarFullHeightPx = with(density) { (BottomNavigationHeight + bottomPadding).toPx() }

        val sheetTranslationY = remember { Animatable(collapsedTargetY) }

        // 收起：只有 animateTo 真正跑完（未被新动画/手势打断）才落定状态并回调。
        // 绝不能用 try/finally —— 手势 snapTo 打断动画时 finally 照样执行，
        // 会把「已经弹回全屏」的面板写成 isExpanded=false，导致返回键不拦截、下滑手势失效。
        fun collapseSheet(velocityY: Float = 0f) {
            isExpanded = false
            coroutineScope.launch {
                sheetTranslationY.animateTo(
                    targetValue = collapsedTargetY,
                    animationSpec = MotionSpring.sheetCollapse,
                    initialVelocity = velocityY
                )
                // 能走到这里说明动画正常结束；被打断会抛 CancellationException 从这里直接跳走
                onBackPressed()
            }
        }

        fun expandSheet(velocityY: Float = 0f) {
            isExpanded = true
            coroutineScope.launch {
                sheetTranslationY.animateTo(
                    targetValue = 0f,
                    animationSpec = MotionSpring.sheetExpand,
                    initialVelocity = velocityY
                )
                onClick()
            }
        }

        // 外部信号（如私信一起听进房成功、主页点击私人 FM 电台）请求展开全屏播放界面。
        // 初值恒为 0L：冷启动空播放器首次加载并展开时，expandSignal > 0 能被正确捕获触发。
        var lastExpanded by remember { mutableStateOf(0L) }
        LaunchedEffect(expandSignal) {
            if (expandSignal > lastExpanded) {
                lastExpanded = expandSignal
                expandSheet()
            }
        }

        val progress = if (collapsedTargetY > 0f) {
            ((collapsedTargetY - sheetTranslationY.value) / collapsedTargetY).coerceIn(0f, 1f)
        } else {
            if (isExpanded) 1f else 0f
        }

        // 物理态（唯一可信来源）：直接由位移反推，而不是信任 isExpanded 布尔值。
        // 手势/返回键/图层显隐全部以它为准，任何一次时序抖动都不会让面板「看得见却点不动」。
        val isFullyExpanded = progress >= 0.999f

        // 当底栏尺寸或屏幕旋转等导致 collapsedTargetY 变化时，仅在「静止且物理上确实停在收起位」时重新贴合。
        // 不能拿 isExpanded 当条件：收起动画一开始就会把它置 false，此时若 snapTo 会把动画瞬跳成终端值。
        LaunchedEffect(collapsedTargetY, isFullyExpanded) {
            if (!isFullyExpanded && !sheetTranslationY.isRunning &&
                sheetTranslationY.value >= collapsedTargetY * 0.85f
            ) {
                sheetTranslationY.snapTo(collapsedTargetY)
            }
        }

        SideEffect {
            onDragProgressChange(progress)
        }

        // 拦截系统返回键：展开时优先优雅滑下收起面板
        // 判定用「物理位移已达全屏 或 逻辑上已展开」——即便布尔值被一次动画打断写坏，
        // 返回键也一定有人拦截，绝不会退回桌面。
        BackHandler(enabled = isFullyExpanded || isExpanded) {
            if (fullPlayerSubView != FULL_PLAYER) {
                fullPlayerSubView = FULL_PLAYER
            } else {
                collapseSheet()
            }
        }

        fun onDrag(deltaY: Float) {
            coroutineScope.launch {
                val current = sheetTranslationY.value
                val target = (current + deltaY).coerceIn(0f, collapsedTargetY)
                sheetTranslationY.snapTo(target)
            }
        }

        fun onDragEnd(velocityY: Float, startedExpanded: Boolean) {
            coroutineScope.launch {
                val current = sheetTranslationY.value
                val dismissThresholdPx = with(density) { 80.dp.toPx() }
                if (startedExpanded) {
                    // 全屏状态下向下滑动退出：阈值 80dp 或向下的甩动速度，继承松手垂直初速度
                    val dragDistanceDown = current
                    val isDragDown = dragDistanceDown >= dismissThresholdPx
                    val isFlingDown = velocityY >= 400f
                    if (isDragDown || isFlingDown) {
                        collapseSheet(velocityY)
                    } else {
                        // 未达阈值平滑回弹全屏
                        sheetTranslationY.animateTo(
                            targetValue = 0f,
                            animationSpec = MotionSpring.sheetCollapse,
                            initialVelocity = velocityY
                        )
                    }
                } else {
                    // 收起状态下向上滑动展开：
                    // 1. 阈值调至 24dp 或比例 10%，轻松上滑即可触发展开；
                    // 2. 快速上滑甩动速度降低至 -200f，支持随手快速轻扫打开；
                    // 3. 接近顶部时（接近 0 或比例 >= 85%），顺畅吸附全屏。
                    val dragDistanceUp = collapsedTargetY - current
                    val expandThresholdPx = with(density) { 24.dp.toPx() }
                    val currentProgress = if (collapsedTargetY > 0f) {
                        ((collapsedTargetY - current) / collapsedTargetY).coerceIn(0f, 1f)
                    } else 0f
                    val isNearTop = current <= with(density) { 48.dp.toPx() } || currentProgress >= 0.85f
                    val isDragUp = dragDistanceUp >= expandThresholdPx || currentProgress >= 0.10f
                    val isFlingUp = velocityY <= -200f
                    if (isNearTop || isDragUp || isFlingUp) {
                        expandSheet(velocityY)
                    } else {
                        // 未达阈值平滑回弹收起
                        collapseSheet(velocityY)
                    }
                }
            }
        }

        // 悬浮胶囊底栏开启时：小播放器收起态为悬浮药丸卡片（全圆角 26dp、水平边距 14dp、6dp 阴影），与底栏共享圆角/材质/对齐逻辑；
        // 手势上滑展开时：水平边距与底部圆角在前 40% 程内平滑收敛为 0，顶部圆角在 100% 程内渐变至 0，无缝舒展为全屏播放页。
        // 经典模式下：保持经典贴边无左右边距与平底直角设计。
        val targetHorizontalMargin = if (floatingNavBarEnabled) FloatingBottomNavHorizontalPadding else 0.dp
        val horizontalMargin = lerp(targetHorizontalMargin, 0.dp, (progress * 2.5f).coerceIn(0f, 1f))

        val initialTopRadius = if (floatingNavBarEnabled) 26.dp else 16.dp
        val topCornerRadius = lerp(initialTopRadius, 0.dp, progress)

        val initialBottomRadius = if (floatingNavBarEnabled) 26.dp else 0.dp
        val bottomCornerRadius = lerp(initialBottomRadius, 0.dp, (progress * 2.5f).coerceIn(0f, 1f))

        val cardShape = RoundedCornerShape(
            topStart = topCornerRadius,
            topEnd = topCornerRadius,
            bottomStart = bottomCornerRadius,
            bottomEnd = bottomCornerRadius
        )

        // 联动效果：手势上滑展开时，底栏在 1/3 程（progress 0f ~ 0.33f）完全向下沉出屏幕；
        // 播放器卡片底端在 1/3 程内始终紧咬底栏上沿同步下沉，底栏退出后稳稳贴合屏幕底端。
        val navBarExitProgress = (progress * 3f).coerceIn(0f, 1f)
        val startBottomY = screenHeightPx - bottomPaddingPx
        val seamOverlapPx = with(density) { 1.dp.toPx() } * navBarExitProgress
        val bottomY = startBottomY + navBarExitProgress * bottomPaddingPx + seamOverlapPx
        val currentCardHeightPx = (bottomY - sheetTranslationY.value).coerceAtLeast(miniPlayerHeightPx)

        // 透明度曲线优化：
        // 1. 小播放器：0f ~ 0.28f 内平滑淡出；下滑时在 0.28f 提前平滑就绪，避免与全屏内容交接断层。
        val miniExitFraction = (progress / 0.28f).coerceIn(0f, 1f)
        val miniPlayerAlpha = 1f - miniExitFraction * miniExitFraction

        // 2. 全屏播放器：0.25f ~ 0.80f 柔和浮现（SmoothStep 曲线）。
        // 0.25f 前彻底留给小播放条和底栏退场；半屏（0.52f）平滑半透；接近全屏（0.80f）饱满实色，绝不提前抢戏。
        val fullEnterFraction = ((progress - 0.25f) / 0.55f).coerceIn(0f, 1f)
        val fullPlayerAlpha = fullEnterFraction * fullEnterFraction * (3f - 2f * fullEnterFraction)

        Surface(
            modifier = Modifier
                .offset { IntOffset(0, sheetTranslationY.value.roundToInt()) }
                .padding(horizontal = horizontalMargin)
                .fillMaxWidth()
                .height(with(density) { currentCardHeightPx.toDp() }),
            shape = cardShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            shadowElevation = if (floatingNavBarEnabled) 6.dp else 0.dp,
            border = if (floatingNavBarEnabled && progress < 0.99f) {
                BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(
                        alpha = (0.25f * (1f - progress)).coerceIn(0f, 0.25f)
                    )
                )
            } else null
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 磨砂玻璃底板
                ArtworkGlassBackdrop(
                    artwork = mediaMetadata.artworkUri,
                    style = if (progress > 0.5f) ArtworkBackdropStyle.FullScreen else ArtworkBackdropStyle.Compact,
                    modifier = Modifier.fillMaxSize()
                )

                // 全屏播放器层（带 touchSlop 下拉手势，绝不误拦截左上角返回按钮等子组件点击）
                // 仅在展开态或手势拖拽中挂载，收起态彻底卸载，绝不遮挡小播放器手势
                val shouldComposeFullPlayer = isFullyExpanded || progress > 0.001f
                if (shouldComposeFullPlayer) {
                    Box(
                        modifier = Modifier
                            .zIndex(if (progress >= 0.5f) 2f else 0f)
                            .fillMaxWidth()
                            .height(with(density) { screenHeightPx.toDp() })
                            .graphicsLayer {
                                alpha = fullPlayerAlpha
                                val scale = 0.94f + 0.06f * progress
                                scaleX = scale
                                scaleY = scale
                                // 视差位移：展开时从下方 36dp 伴随托盘升起优雅浮现，下滑退出时顺应手指流向自然下沉并淡出
                                translationY = with(density) { 36.dp.toPx() } * (1f - progress)
                            }
                            // 手势探测器只装一次（key=Unit）：用 pointerInput(isExpanded) 做 key 时，
                            // 状态一旦抖动就会中途重启手势协程，用户正在拖的那一次手势直接作废。
                            // 是否接管改为「按下瞬间」按物理位移判定，与布尔状态完全解耦。
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    // 全屏（位移贴近顶部）才接管下拉收起，收起态把事件让给小播放条
                                    val startedExpanded = sheetTranslationY.value <= 0.5f
                                    if (!startedExpanded) return@awaitEachGesture

                                    val velocityTracker = VelocityTracker()
                                    velocityTracker.addPosition(down.uptimeMillis, down.position)
                                    var totalDragY = 0f
                                    var isDraggingDown = false

                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed) break

                                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                                        val positionChange = change.positionChange()
                                        totalDragY += positionChange.y

                                        if (!isDraggingDown && totalDragY > viewConfiguration.touchSlop) {
                                            isDraggingDown = true
                                        }

                                        if (isDraggingDown) {
                                            change.consume()
                                            onDrag(positionChange.y)
                                        }
                                    }

                                    if (isDraggingDown) {
                                        val velocityY = velocityTracker.calculateVelocity().y
                                        onDragEnd(velocityY, startedExpanded)
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
                                        backHandlerEnabled = false,
                                        onAfterNavigate = {
                                            isExpanded = false
                                            coroutineScope.launch {
                                                sheetTranslationY.snapTo(collapsedTargetY)
                                            }
                                            onBackPressed()
                                        },
                                        imageModifier = Modifier.sharedElement(
                                            state = rememberSharedContentState(
                                                key = SharedArtwork.ofMetadata(mediaMetadata)
                                            ),
                                            animatedVisibilityScope = this,
                                            placeHolderSize = PanelBoundsPlaceHolder,
                                            boundsTransform = AlbumArtBoundsTransform,
                                        ),
                                        onBackPressed = {
                                            collapseSheet()
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

                // 小播放器层（MiniPlayer 顶栏，高度 64dp）
                // 声明在全屏层之后并赋予高优先级 zIndex，保证收起态小播放器绝对独占手势，绝不被任何底层组件劫持
                val showMiniPlayerContent = !isFullyExpanded
                if (showMiniPlayerContent) {
                    Box(
                        modifier = Modifier
                            .zIndex(if (progress < 0.5f) 2f else 0f)
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(MiniPlayerHeight)
                            .graphicsLayer {
                                alpha = miniPlayerAlpha
                            }
                    ) {
                        MiniPlayer(
                            mediaMetadata = mediaMetadata,
                            position = position,
                            duration = duration,
                            isPillProgress = floatingNavBarEnabled,
                            onClick = {
                                expandSheet()
                            },
                            onVerticalDrag = { deltaY -> onDrag(deltaY) },
                            onVerticalDragEnd = { velocityY -> onDragEnd(velocityY, startedExpanded = false) },
                            dragProgress = progress,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
