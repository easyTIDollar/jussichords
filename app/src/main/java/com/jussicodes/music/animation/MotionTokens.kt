package com.jussicodes.music.animation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.PathEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp

/**
 * 全 App 动效 Token。
 *
 * 分层原则：
 * - [MotionDuration] 只回答「这个动效走多久」，按**语义场景**命名，不再有含义模糊的 DURATION_EXIT。
 * - [MotionEasing] 只回答「用什么曲线」，按**方向**（进入减速 / 退出加速 / 共享元素路径）命名。
 * - [MotionSpring] 只回答「手势松手后怎么回弹」，物理参数集中一处，Player 与 Roam 共用。
 *
 * 旧的 `constants.Dimensions` 常量（DURATION / DURATION_ENTER / ...）保留并委托到这里，
 * 保证本次重构**不改变任何现有动画行为**；调用方迁移完成后旧常量再逐步退场。
 */
object MotionDuration {
    /** 二级页推入 / 返回时的页面淡入。 */
    const val ScreenEnter: Int = 400

    /** 二级页退出 / 页面淡出。刻意比 [ScreenEnter] 短，避免"退场比入场还慢"的拖沓感。 */
    const val ScreenExit: Int = 100

    /**
     * 播放面板状态切换（Mini ↔ Full ↔ Queue ↔ Lyric）的淡入时长。
     * 数值与旧 DURATION_EXIT 相同：旧代码把它当"进入延迟"用，这里按语义拆开，
     * 真正当"时长"用的场景见 [MotionSpec.panelFadeIn]。
     */
    const val PanelFadeIn: Int = 200

    /** 播放面板状态切换的淡出时长。 */
    const val PanelFadeOut: Int = 100

    /**
     * 共享封面 bounds 变形时长（列表封面 → 详情封面、mini 封面 → 全屏封面共用）。
     *
     * 当前保持旧值 600 以维持行为不变。已知的失步问题：页面淡入 [ScreenEnter] 是 400ms，
     * 而封面变形要 600ms，用户会看到"内容已到位、封面还在飞"。
     * 校准到 ~450ms 属于步骤 4（实机验收）的工作，不在纯重构阶段动。
     */
    const val SharedArtworkBounds: Int = 600

    /** 底栏 / NavigationRail 选中态（缩放 + 透明度）过渡。 */
    const val NavSelection: Int = 200

    /** 布局位移动画，如小播放器贴底间距的上下滑移。 */
    const val LayoutShift: Int = 200

    /** 局部微反馈：时间选择器颜色、图标缩放等。 */
    const val MicroFeedback: Int = 300

    /** 缓存进度条等较长但仍属于"读数跟随"的动画。 */
    const val ProgressIndicator: Int = 600
}

object MotionEasing {
    /** 进入 / 淡入：Material 3 Emphasized Decelerate。 */
    val decelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** 退出 / 淡出：Material 3 Emphasized Accelerate。 */
    val accelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** 中性、线性偏稳：布局位移这类"物理上只是挪一下"的动画。 */
    val standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    private val emphasizedPathGeometry = Path().apply {
        moveTo(0f, 0f)
        cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f)
        cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f)
    }

    /** 共享元素 bounds 变形：M3 emphasized path 曲线。 */
    val emphasizedPath: Easing = PathEasing(emphasizedPathGeometry)
}

object MotionSpring {
    /** 选中态这类"轻推一下"的手感：略带回弹，不过分。 */
    val navSelection = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    /** 小播放器跟手拖拽的 XY 回弹：明显弹性，手指松手有明确的"甩回去"手感。 */
    val dragOffset = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    /** 封面横向拖拽回弹（Player 与 RoamScreen 共用，此前两份拷贝）。 */
    val artworkHorizontal = spring<Float>(
        dampingRatio = 0.85f,
        stiffness = 450f
    )

    /** 封面纵向滑出回弹（Player 与 RoamScreen 共用，此前两份拷贝）。 */
    val artworkDismiss = spring<Float>(
        dampingRatio = 0.85f,
        stiffness = 480f
    )
}

/** 常用动画 spec 工厂，避免各处重复拼 tween 的四个参数。 */
object MotionSpec {
    /** 页面淡入。 */
    fun <T> screenEnter(): FiniteAnimationSpec<T> =
        tween(durationMillis = MotionDuration.ScreenEnter, easing = MotionEasing.decelerate)

    /** 页面淡出。 */
    fun <T> screenExit(): FiniteAnimationSpec<T> =
        tween(durationMillis = MotionDuration.ScreenExit, easing = MotionEasing.accelerate)

    /**
     * 播放面板淡入，可选延迟（等退场内容先淡出）。
     *
     * 注意 duration 沿用 [MotionDuration.ScreenEnter]（400ms）而非 [MotionDuration.PanelFadeIn]：
     * 旧代码的 `tweenEnter()` 语义是「延迟 200ms + 淡入 400ms」，其中 200 那个常量名叫
     * DURATION_EXIT 但实际只当 delay 用。新代码请用语义化的 delayMillis 参数表达。
     */
    fun <T> panelFadeIn(
        delayMillis: Int = 0,
        durationMillis: Int = MotionDuration.ScreenEnter
    ): FiniteAnimationSpec<T> =
        tween(
            delayMillis = delayMillis,
            durationMillis = durationMillis,
            easing = MotionEasing.decelerate
        )

    /** 播放面板淡出。 */
    fun <T> panelFadeOut(): FiniteAnimationSpec<T> =
        tween(durationMillis = MotionDuration.PanelFadeOut, easing = MotionEasing.accelerate)

    /** 共享封面 bounds 变形。 */
    fun <T> sharedBounds(): FiniteAnimationSpec<T> =
        tween(
            durationMillis = MotionDuration.SharedArtworkBounds,
            easing = MotionEasing.emphasizedPath
        )

    /** 布局位移。 */
    fun <T> layoutShift(): FiniteAnimationSpec<T> =
        tween(durationMillis = MotionDuration.LayoutShift, easing = MotionEasing.standard)

    /**
     * 导航指示胶囊的非首页位移。
     *
     * 首页用 snap()（零延迟贴手指），非首页用这一条（补一段位移）。
     * 单独给出 Dp 版本是因为 animateDpAsState 需要 AnimationSpec<Dp>，
     * 与泛型 <T> 版本在 if/else 里容易推断失败。
     */
    fun indicatorShift(): FiniteAnimationSpec<Dp> =
        tween(durationMillis = MotionDuration.LayoutShift, easing = MotionEasing.standard)
}