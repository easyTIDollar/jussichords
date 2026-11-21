package com.jussicodes.music.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.zIndex
import androidx.media3.common.MediaMetadata
import androidx.navigation.NavHostController
import com.jussicodes.music.animation.AlbumArtBoundsTransform
import com.jussicodes.music.animation.MotionDuration
import com.jussicodes.music.animation.MotionEasing
import com.jussicodes.music.animation.MotionSpec
import com.jussicodes.music.animation.MotionSpring
import com.jussicodes.music.animation.PanelBoundsPlaceHolder
import com.jussicodes.music.animation.SharedArtwork
import com.jussicodes.music.constants.FloatingBottomNavHorizontalPadding
import com.jussicodes.music.constants.MiniPlayerHeight
import com.jussicodes.music.ui.components.ArtworkBackdropStyle
import com.jussicodes.music.ui.components.ArtworkGlassBackdrop
import com.jussicodes.music.ui.components.Lyric
import com.jussicodes.music.ui.components.MiniPlayer
import com.jussicodes.music.ui.components.Player
import com.jussicodes.music.ui.components.PlayerQueue
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

const val FULL_PLAYER = 0
const val PLAY_QUEUE = 1
const val MINI_PLAYER = 2
const val LYRIC_VIEW = 3

// 小播放器首次出现时从下方向上滑入的距离。
private val MiniPlayerEnterOffsetY = 16.dp
private const val MiniPlayerEnterInterruptProgress = 0.02f

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
    floatingNavBarEnabled: Boolean = true,
    onDragProgressChange: (Float) -> Unit = {},
    miniPlayerEnterAnimation: Boolean = true,
    onMiniPlayerEnterAnimationPlayed: () -> Unit = {},
    miniPlayerDockOffsetY: () -> Dp = { 0.dp },
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val viewConfiguration = LocalViewConfiguration.current

    var isExpanded by remember { mutableStateOf(false) }
    var dragInProgress by remember { mutableStateOf(false) }
    var fullPlayerSubView by remember { mutableIntStateOf(FULL_PLAYER) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenHeightPx = constraints.maxHeight.toFloat()
        // 横屏判据用窗口 dp 宽高比（平板自由窗口/分屏拉宽时 Configuration.orientation 可能仍报竖屏）。
        val isLandscapeWindow = constraints.maxWidth > constraints.maxHeight
        val miniPlayerHeightPx = with(density) { MiniPlayerHeight.toPx() }
        val bottomPaddingPx = with(density) { bottomPadding.toPx() }
        val collapsedTargetY = (screenHeightPx - bottomPaddingPx - miniPlayerHeightPx).coerceAtLeast(0f)

        // 位移语义：**从收起位向上的偏移量**，0 = 收起，collapsedTargetY = 全屏。
        //
        // 不能存绝对屏幕 Y。横竖屏旋转、进出小窗、底栏显隐都会改停靠高度
        // （collapsedTargetY 从 2017 变到 881 这种），旧 Y 配新高度必然错位：
        // 进度算出虚假值、卡片跑到屏幕外、小播放器整块消失、底栏沉下去不回来。
        // 存偏移量之后，收起态偏移量恒为 0，视觉位置由 collapsedTargetY 决定，
        // 停靠高度怎么变都自动跟得上 —— 这类错位从根上不可能发生。
        val sheetOffsetY = remember { Animatable(0f) }

        // 收起：只有 animateTo 真正跑完（未被新动画/手势打断）才落定状态并回调。
        // 绝不能用 try/finally —— 手势 snapTo 打断动画时 finally 照样执行，
        // 会把「已经弹回全屏」的面板写成 isExpanded=false，导致返回键不拦截、下滑手势失效。
        fun collapseSheet(velocityY: Float = 0f) {
            isExpanded = false
            coroutineScope.launch {
                sheetOffsetY.animateTo(
                    targetValue = 0f,
                    animationSpec = MotionSpring.sheetCollapse,
                    // 屏幕 Y 向下为正，偏移量向下为负，符号要翻过来
                    initialVelocity = -velocityY
                )
                // 动画结束守卫：若在弹簧运行期间底栏尺寸发生突变（如切入二级页），确保最终精确对齐
                if (!isExpanded) {
                    sheetOffsetY.snapTo(0f)
                }
                onBackPressed()
            }
        }

        fun expandSheet(velocityY: Float = 0f) {
            isExpanded = true
            coroutineScope.launch {
                // 初速按剩余距离限幅：展开弹簧是欠阻尼（0.86），带着整段甩动速度冲过顶
                // 会让面板整体上移几十到上百 px（顶部露底、底下漏缝），再落回来 ——
                // 就是肉眼看到的上下弹簧。远距离仍保留甩动动量，贴顶松手时几乎不过冲。
                val remaining = (collapsedTargetY - sheetOffsetY.value).coerceAtLeast(0f)
                val maxInitialSpeed = (remaining * 18f).coerceAtMost(3000f)
                sheetOffsetY.animateTo(
                    targetValue = collapsedTargetY,
                    animationSpec = MotionSpring.sheetExpand,
                    // 屏幕 Y 向上为负，偏移量向上为正，符号要翻过来
                    initialVelocity = (-velocityY).coerceIn(0f, maxInitialSpeed)
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

        // 连续进度：只以 State 形式向下传，**绝不在组合期解包成局部 Float**。
        // 拖动时它每帧都变，一旦在本函数体里读，整棵 PlayerTransform（全屏 Player 树、
        // SharedTransitionLayout、AnimatedContent、MiniPlayer）每帧都要重组。
        // 需要连续值的地方（卡片高度/圆角、两层透明度、视差缩放）全部下沉到 layout/draw 阶段读。
        val progressState = remember(collapsedTargetY) {
            derivedStateOf {
                if (collapsedTargetY > 0f) {
                    (sheetOffsetY.value / collapsedTargetY).coerceIn(0f, 1f)
                } else {
                    if (isExpanded) 1f else 0f
                }
            }
        }

        // 物理态（唯一可信来源）：直接由位移反推，而不是信任 isExpanded 布尔值。
        // 手势/返回键/图层显隐全部以它为准，任何一次时序抖动都不会让面板「看得见却点不动」。
        // 离散派生：只在跨过 0.999 时翻转一次，组合期读它不会每帧重组。
        val isFullyExpanded by remember {
            derivedStateOf { progressState.value >= 0.999f }
        }

        // 图层上下关系同样是离散的：只在跨过半屏时换一次 zIndex。
        val fullPlayerOnTop by remember {
            derivedStateOf { progressState.value >= 0.5f }
        }

        // 磨砂底板样式只在跨过半屏时切换，避免每帧重建 ArtworkGlassBackdrop。
        val glassStyle by remember {
            derivedStateOf {
                if (progressState.value > 0.5f) ArtworkBackdropStyle.FullScreen
                else ArtworkBackdropStyle.Compact
            }
        }

        // 当底栏尺寸、屏幕旋转或路由切换导致底部停靠目标高度（collapsedTargetY）改变时，
        // 收起态要立刻贴合最新停靠高度。
        //
        // 这里不能用 LaunchedEffect(collapsedTargetY)：miniBottomPadding 是 animateDpAsState，
        // 首页↔二级页切换时它逐帧变化，key 每帧变一次就把上一帧的 snapTo 协程
        // 取消掉，位移追不上，面板就停在半路 —— progress 卡出非零值，表现是背景一直模糊、
        // 底栏一直下沉、小播放器悬在半空。改成 snapshotFlow 串行消费，只跟随最新值。
        val currentCollapsedTargetY = rememberUpdatedState(collapsedTargetY)
        LaunchedEffect(Unit) {
            snapshotFlow { collapsedTargetY }.collect { target ->
                // 停靠高度变了（旋转、切小窗、底栏显隐）：
                // 收起态偏移量恒为 0，视觉位置自动跟随新高度，不需要任何动作 ——
                // 这正是改成偏移量语义的最大收益。
                // 只有展开态要把偏移量同步到新高度，否则进度不再是 1，全屏会露出底下一条。
                if (isExpanded && target > 0f && !sheetOffsetY.isRunning) {
                    sheetOffsetY.snapTo(target)
                }
            }
        }

        // 兜底自愈：位移静止 250ms 仍停在半路，按逻辑态补完 —— 展开态补完展开，
        // 收起态贴合收起位。拖动/动画期间（value 每帧在变）不会触发。
        //
        // 之前只处理收起方向，展开态直接跳过：一旦「松手判为展开、但展开动画被打断」
        // （再摸一下、缩放手势、切换页面都会打断），isExpanded 卡在 true、位移卡在半路，
        // 就再也没有人来收尾 —— 表现就是小播放器半透明消失、底栏沉下去不回来、
        // 背景一直模糊（诊断里 p=0.344 ty=1323 卡死不动）。
        LaunchedEffect(Unit) {
            snapshotFlow { sheetOffsetY.value to dragInProgress }
                .collectLatest { (_, isDragging) ->
                    if (isDragging) return@collectLatest
                    delay(250)
                    if (dragInProgress) return@collectLatest
                    if (sheetOffsetY.isRunning) return@collectLatest
                    val target = currentCollapsedTargetY.value
                    if (target <= 0f) return@collectLatest
                    val value = sheetOffsetY.value
                    val progress = (value / target).coerceIn(0f, 1f)
                    // 已经贴住收起位，或已经全屏展开，都不动
                    if (progress <= 0.001f || progress >= 0.999f) return@collectLatest
                    if (isExpanded) {
                        // 必须用外部 scope：直接在 collectLatest 里 animateTo，
                        // 动画自己每帧改 value，会把自己取消掉。
                        coroutineScope.launch {
                            sheetOffsetY.animateTo(target, MotionSpring.sheetExpand)
                            onClick()
                        }
                    } else {
                        sheetOffsetY.snapTo(0f)
                    }
                }
        }

        // 进度回调走 snapshotFlow 单协程：只在值真的变化时发一次。
        // 不能用 SideEffect 驱动 —— 组合一旦被收敛（本函数体不再每帧重组），
        // SideEffect 也就不会每帧执行，回调会漏帧、底栏下沉和背景压暗会断掉。
        val currentOnDragProgressChange = rememberUpdatedState(onDragProgressChange)
        LaunchedEffect(Unit) {
            snapshotFlow { progressState.value }.collect { currentOnDragProgressChange.value(it) }
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

        // 手势位移：单协程消费累积增量。
        // 原来每个 pointer 事件都 launch 一个新协程做 snapTo —— 输入采样能到 120~240Hz，
        // 协程在主线程排队，且 Animatable 的 MutatorMutex 会互相取消，位移稳定落后手指 1~3 帧。
        // 现在只把增量累加进一个普通数组（不参与重组），用 CONFLATED 通道唤醒唯一的消费者：
        // 一帧内不管来多少个事件，都只落一次 snapTo，且增量一个不丢。
        val dragAccumulator = remember { FloatArray(1) }
        val dragSignal = remember { Channel<Unit>(Channel.CONFLATED) }

        LaunchedEffect(Unit) {
            while (true) {
                dragSignal.receive()
                val delta = dragAccumulator[0]
                if (delta == 0f) continue
                dragAccumulator[0] = 0f
                val limit = currentCollapsedTargetY.value
                val current = sheetOffsetY.value
                // 屏幕 Y 向下为正，偏移量向上为正：取反
                sheetOffsetY.snapTo((current - delta).coerceIn(0f, limit))
            }
        }

        fun onDrag(deltaY: Float) {
            // 收起动画正在跑时只吞掉向下的输入。
            // 这条守卫原本是给歌词区 fling 加的（松手后的惯性会 snapTo 打断收起动画，
            // 让面板卡在半屏），但它没区分方向，把上滑也一起吞了 ——
            // 下拉收起后立刻再上滑，弹簧和尾部 snapTo 那几百毫秒里手势完全没反应。
            // 上滑（deltaY < 0）直接放行，由 snapTo 打断收起动画、立刻回到跟手状态。
            if (deltaY > 0f &&
                !isExpanded &&
                sheetOffsetY.isRunning &&
                sheetOffsetY.targetValue == 0f
            ) {
                return
            }
            dragAccumulator[0] += deltaY
            dragSignal.trySend(Unit)
        }

        fun onDragEnd(velocityY: Float, startedExpanded: Boolean) {
            dragInProgress = false
            coroutineScope.launch {
                // 最后几个事件的增量可能还在累加器里没落盘，先并入再判阈值，
                // 否则松手瞬间会用上一帧的旧位移算距离。
                val pending = dragAccumulator[0]
                if (pending != 0f) {
                    dragAccumulator[0] = 0f
                    val current = sheetOffsetY.value
                    sheetOffsetY.snapTo((current - pending).coerceIn(0f, collapsedTargetY))
                }
                val offset = sheetOffsetY.value
                val dismissThresholdPx = with(density) { 80.dp.toPx() }
                if (startedExpanded) {
                    // 全屏状态下向下滑动退出：阈值 80dp 或向下的甩动速度，继承松手垂直初速度
                    val dragDistanceDown = collapsedTargetY - offset
                    val isDragDown = dragDistanceDown >= dismissThresholdPx
                    val isFlingDown = velocityY >= 400f
                    if (isDragDown || isFlingDown) {
                        collapseSheet(velocityY)
                    } else {
                        // 未达阈值平滑回弹全屏
                        sheetOffsetY.animateTo(
                            targetValue = collapsedTargetY,
                            animationSpec = MotionSpring.sheetCollapse,
                            initialVelocity = -velocityY
                        )
                    }
                } else {
                    // 收起状态下向上滑动展开：
                    // 1. 阈值调至 24dp 或比例 10%，轻松上滑即可触发展开；
                    // 2. 快速上滑甩动速度降低至 -200f，支持随手快速轻扫打开；
                    // 3. 接近顶部时（接近 0 或比例 >= 85%），顺畅吸附全屏。
                    val dragDistanceUp = offset
                    val expandThresholdPx = with(density) { 24.dp.toPx() }
                    val currentProgress = if (collapsedTargetY > 0f) {
                        (offset / collapsedTargetY).coerceIn(0f, 1f)
                    } else 0f
                    val isNearTop = (collapsedTargetY - offset) <= with(density) { 48.dp.toPx() } ||
                        currentProgress >= 0.85f
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

        // Animatable 本身不是 State，包一层 asState 交给 PlayerSheet；
        // remember 住实例，避免每次重组新建 State 导致壳层参数永远不相等。
        val sheetOffsetState = remember(sheetOffsetY) { sheetOffsetY.asState() }

        // 卡片的水平边距 / 圆角 / 高度 / 描边全部依赖每帧变化的进度，
        // 统一收进 PlayerSheet：它只重组自己这一层，content（全屏 Player 树、MiniPlayer）
        // 的参数稳定，重组时直接跳过，不再跟着手指每帧重跑整棵播放器。
        PlayerSheet(
            offsetY = sheetOffsetState,
            collapsedTargetY = collapsedTargetY,
            progressState = progressState,
            screenHeightPx = screenHeightPx,
            bottomPaddingPx = bottomPaddingPx,
            miniPlayerHeightPx = miniPlayerHeightPx,
            floatingNavBarEnabled = floatingNavBarEnabled,
            miniPlayerDockOffsetY = miniPlayerDockOffsetY
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 磨砂玻璃底板
                ArtworkGlassBackdrop(
                    artwork = mediaMetadata.artworkUri,
                    style = glassStyle,
                    modifier = Modifier.fillMaxSize()
                )

                // 全屏播放器层（带 touchSlop 下拉手势，绝不误拦截左上角返回按钮等子组件点击）
                // 优化冷启动与上滑体验：0~20% 属于手势起手/小播放器淡出阶段（此时 fullPlayerAlpha 恒为 0，视觉完全不可见），
                // 仅在拖拽过 20% 或确认展开（点击/信号）时才挂载巨型 Player 树，消灭刚启动/轻碰小播放器时的首帧组合卡死；
                // 一旦挂载后在拖动过程中保持稳定存活，直到完全收起才卸载释放资源。
                var fullPlayerActive by remember { mutableStateOf(false) }
                // 挂载/卸载判定改用 snapshotFlow：连续进度不再参与组合期判断，
                // 否则每帧都要重跑这个 if，等于什么都没省。这里只在跨过阈值时翻转一次。
                LaunchedEffect(Unit) {
                    snapshotFlow {
                        FullPlayerMountInput(
                            progress = progressState.value,
                            running = sheetOffsetY.isRunning,
                            targetValue = sheetOffsetY.targetValue,
                            expanded = isExpanded || isFullyExpanded
                        )
                    }.collect { input ->
                        if (input.expanded || input.progress >= 0.20f) {
                            fullPlayerActive = true
                        } else if (input.progress <= 0.001f && !input.running) {
                            fullPlayerActive = false
                        }
                    }
                }
                val shouldComposeFullPlayer = fullPlayerActive
                if (shouldComposeFullPlayer) {
                    Box(
                        modifier = Modifier
                            .zIndex(if (fullPlayerOnTop) 2f else 0f)
                            .fillMaxWidth()
                            .then(
                                // 横屏必须用 requiredHeight 把内容高度钉死在全屏高度：
                                // height() 会被父级（随手指收缩的卡片）的最大高度夹住，内容跟着
                                // 重新测量 —— 封面越滑越小、歌名/歌手跟着重排，看着就像抽屉。
                                // 钉死后内容整块随卡片平移，只是底部被卡片裁掉，封面尺寸不变。
                                if (isLandscapeWindow) {
                                    Modifier.requiredHeight(with(density) { screenHeightPx.toDp() })
                                } else {
                                    Modifier.height(with(density) { screenHeightPx.toDp() })
                                }
                            )
                            .graphicsLayer {
                                // 透明度/缩放/视差都在 draw 阶段读进度：只失效重绘，不触发重组。
                                val p = progressState.value
                                alpha = fullPlayerAlphaOf(p)
                                // 横屏不做缩放和视差：下滑时封面不许变小、圆角不许跟着缩，
                                // 歌名/歌手也不许重排 —— 整块随背景平移滑出即可。
                                if (!isLandscapeWindow) {
                                    val scale = 0.94f + 0.06f * p
                                    scaleX = scale
                                    scaleY = scale
                                    // 视差位移：展开时从下方 36dp 伴随托盘升起优雅浮现，下滑退出时顺应手指流向自然下沉并淡出
                                    translationY = 36.dp.toPx() * (1f - p)
                                }
                            }
                            // 手势探测器只装一次（key=Unit）：用 pointerInput(isExpanded) 做 key 时，
                            // 状态一旦抖动就会中途重启手势协程，用户正在拖的那一次手势直接作废。
                            // 是否接管改为「按下瞬间」按物理位移判定，与布尔状态完全解耦。
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    // 全屏（偏移量贴近满值）才接管下拉收起，收起态把事件让给小播放条
                                    val startedExpanded = sheetOffsetY.value >= collapsedTargetY - 0.5f
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
                                        // 歌词栏的纵向手势已经接管并消费了这次拖拽：位移读出来是 0，
                                        // 这里立刻让位，避免同一次下拉被两层各算一遍。
                                        if (change.isConsumed && !isDraggingDown) break
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
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceContainer),
                                transitionSpec = {
                                    fadeIn(
                                        animationSpec = tween(
                                            durationMillis = MotionDuration.PanelFadeIn,
                                            easing = MotionEasing.decelerate
                                        )
                                    ) togetherWith fadeOut(
                                        animationSpec = tween(
                                            durationMillis = 150,
                                            easing = MotionEasing.accelerate
                                        )
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
                                                sheetOffsetY.snapTo(0f)
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
                                        onPositionUpdate = onPositionUpdate,
                                        // 横屏歌词栏的纵向手势驱动同一个 sheet 位移，
                                        // 松手速度按「展开态下滑」判定收起还是回弹。
                                        onSwipeDownDrag = { deltaY -> onDrag(deltaY) },
                                        onSwipeDownEnd = { velocityY ->
                                            onDragEnd(velocityY, startedExpanded = true)
                                        }
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

                // 保留首次挂载时的入场进度，标记置位和全屏切换都不重置它。
                val miniEnterProgress = remember {
                    Animatable(if (miniPlayerEnterAnimation) 0f else 1f)
                }
                val currentProgressState by rememberUpdatedState(progressState)
                LaunchedEffect(miniEnterProgress) {
                    if (miniEnterProgress.value >= 1f) return@LaunchedEffect

                    onMiniPlayerEnterAnimationPlayed()
                    val enterAnimation = launch {
                        miniEnterProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = MotionDuration.MiniPlayerEnter,
                                easing = MotionEasing.emphasizedPath
                            )
                        )
                    }
                    snapshotFlow {
                        miniEnterProgress.value >= 1f ||
                            currentProgressState.value > MiniPlayerEnterInterruptProgress
                    }.first { it }
                    if (miniEnterProgress.value < 1f) {
                        enterAnimation.cancelAndJoin()
                        miniEnterProgress.snapTo(1f)
                    }
                }

                // 小播放器层（MiniPlayer 顶栏，高度 64dp）
                // 声明在全屏层之后并赋予高优先级 zIndex，保证收起态小播放器绝对独占手势，绝不被任何底层组件劫持
                val showMiniPlayerContent = !isFullyExpanded
                if (showMiniPlayerContent) {
                    Box(
                        modifier = Modifier
                            .zIndex(if (fullPlayerOnTop) 0f else 2f)
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(MiniPlayerHeight)
                            .graphicsLayer {
                                // 小播放器在 0f ~ 0.28f 内平滑淡出；draw 阶段读进度，不触发重组
                                val enterProgress = miniEnterProgress.value
                                val miniExitFraction = (progressState.value / 0.28f).coerceIn(0f, 1f)
                                alpha = enterProgress * (1f - miniExitFraction * miniExitFraction)
                                translationY = (1f - enterProgress) * MiniPlayerEnterOffsetY.toPx()
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
                            onVerticalDragStart = { dragInProgress = true },
                            onVerticalDrag = { deltaY -> onDrag(deltaY) },
                            onVerticalDragEnd = { velocityY -> onDragEnd(velocityY, startedExpanded = false) },
                            dragProgress = progressState,
                            sheetDragOffset = sheetOffsetState,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

/** 全屏层挂载判定的输入快照：数据类做等值比较，同一帧重复值不会触发 emit。 */
private data class FullPlayerMountInput(
    val progress: Float,
    val running: Boolean,
    val targetValue: Float,
    val expanded: Boolean,
)

/** 全屏播放器的浮现透明度：0.25f ~ 0.80f 之间 SmoothStep 渐入。 */
private fun fullPlayerAlphaOf(progress: Float): Float {
    val fullEnterFraction = ((progress - 0.25f) / 0.55f).coerceIn(0f, 1f)
    return fullEnterFraction * fullEnterFraction * (3f - 2f * fullEnterFraction)
}

/**
 * 播放器卡片外壳。
 *
 * 只负责随手势变化的几何量（水平边距、圆角、高度、描边）与材质，content 以 slot 传入。
 * 进度在**这里**解包，所以每帧重组只发生在本函数内；content 的参数稳定，Compose 会
 * 整段跳过，不会连带重跑全屏 Player 树与 MiniPlayer —— 这是上滑不再掉帧的关键。
 */
@Composable
private fun PlayerSheet(
    offsetY: State<Float>,
    collapsedTargetY: Float,
    progressState: State<Float>,
    screenHeightPx: Float,
    bottomPaddingPx: Float,
    miniPlayerHeightPx: Float,
    floatingNavBarEnabled: Boolean,
    miniPlayerDockOffsetY: () -> Dp,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val progress = progressState.value

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

    Surface(
        // 位移、高度、水平边距必须在同一个 measure 阶段读出。
        // 之前位移走 offset（布局阶段）、高度走 height（组合阶段），动画帧里两者会差一拍：
        // 位移已经上冲、高度还是上一帧的，底边就跟着上移，底下漏出一条能看到主页的缝。
        modifier = Modifier.playerSheetBounds(
            offsetY = offsetY,
            collapsedTargetY = collapsedTargetY,
            progressState = progressState,
            screenHeightPx = screenHeightPx,
            bottomPaddingPx = bottomPaddingPx,
            miniPlayerHeightPx = miniPlayerHeightPx,
            horizontalMarginPx = with(density) { horizontalMargin.toPx() },
            seamPx = with(density) { 1.dp.toPx() },
            miniPlayerDockOffsetY = miniPlayerDockOffsetY
        ),
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
        content()
    }
}

/**
 * 播放器卡片的尺寸与位置。
 *
 * 节点尺寸**就是卡片本身**（宽 = 屏宽，高 = 卡片高），位移交给 graphicsLayer 在绘制阶段平移。
 *
 * 之前是「节点虚报整屏高 + place 到 ty」：layout 报 2400 高、内容只有 168 高、又被 place 到
 * y=2017 —— 绘制边界和实际内容对不上，卡片整块画不出来（状态全对，屏幕上却什么都没有，
 * 只剩一个和背景同色的空壳，看起来就是"小播放器消失了"）。
 *
 * 现在高度仍在 measure 阶段算（和位移同一个值，不会漏缝），位移在绘制阶段读 State，
 * 两者都不经过重组。
 */
private fun Modifier.playerSheetBounds(
    offsetY: State<Float>,
    collapsedTargetY: Float,
    progressState: State<Float>,
    screenHeightPx: Float,
    bottomPaddingPx: Float,
    miniPlayerHeightPx: Float,
    horizontalMarginPx: Float,
    seamPx: Float,
    miniPlayerDockOffsetY: () -> Dp,
): Modifier = this
    .layout { measurable, constraints ->
        val ty = (collapsedTargetY - offsetY.value).coerceAtLeast(0f)
        val navBarExitProgress = (progressState.value * 3f).coerceIn(0f, 1f)
        val startBottomY = screenHeightPx - bottomPaddingPx
        val bottomY = startBottomY + navBarExitProgress * bottomPaddingPx + seamPx * navBarExitProgress
        val heightPx = (bottomY - ty).coerceAtLeast(miniPlayerHeightPx).roundToInt()
        val widthPx = (constraints.maxWidth - 2f * horizontalMarginPx).roundToInt().coerceAtLeast(0)
        val placeable = measurable.measure(Constraints.fixed(widthPx, heightPx))
        layout(constraints.maxWidth, heightPx) {
            placeable.place(horizontalMarginPx.roundToInt(), 0)
        }
    }
    .graphicsLayer {
        // 绘制阶段读偏移量：只失效重绘，不触发重组，也不会和高度差拍。
        // 停靠补偿随底部留白一起消退，展开后仍贴合全屏。
        val navBarExitProgress = (progressState.value * 3f).coerceIn(0f, 1f)
        val dockOffsetPx = miniPlayerDockOffsetY().toPx() * (1f - navBarExitProgress)
        translationY = (collapsedTargetY - offsetY.value + dockOffsetPx).coerceAtLeast(0f)
    }
