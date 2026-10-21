package com.jussicodes.music.animation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope

/**
 * 播放面板（mini ↔ full ↔ queue ↔ lyric）的共用转场配置。
 *
 * PlayerTransform（底部小播放器展开成全屏）与 RoamScreen（漫游播放页内部面板切换）
 * 是两套布局完全不同的实现，但转场**意图**相同：面板容器 bounds 连续变形 + 封面
 * 同一元素移动 + 内容交叉淡入淡出。此前两份代码各写一遍 key 字符串和淡入淡出参数，
 * 容易改一处漏一处，这里收成一份。
 *
 * 边界：这里只放动画配置与 key 规则，**不**涉及面板内容、状态来源、队列或歌词逻辑。
 * 手势跟手展开属于后续步骤，不在本文件范围内。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
object PlayerPanelTransition {

    /**
     * 面板容器的共享 key。mini 与 full 共用同一个容器元素，展开时整体 bounds 变形。
     *
     * 注意：两处必须处于**同一个 SharedTransitionLayout 实例**内才能真正共享。
     * PlayerTransform 与 RoamScreen 各自拥有独立的 SharedTransitionLayout，
     * 因此它们共享的是**参数**而非同一个元素实例——这是布局决定的，不要假设能跨页共享。
     */
    const val CONTAINER_KEY: String = "player-panel-container"

    /** Roam 页面板容器的 key（独立布局，用独立命名空间避免与 PlayerTransform 混淆）。 */
    const val ROAM_CONTAINER_KEY: String = "roam-player-panel-container"

    /**
     * 面板进入的淡入延迟：等退场内容先淡出，避免两个面板同时可见导致的重影。
     */
    val panelEnterDelay: Int get() = MotionDuration.ScreenExit

    /**
     * mini → full 方向比反向更"沉"，用更长的延迟让 mini 先退场；
     * 反方向（收起成 mini）则立即开始，避免收起过程有等待感。
     */
    val collapseIntoMiniDelay: Int get() = MotionDuration.PanelFadeIn

    /** 面板容器的 placeholder 策略：按目标尺寸整体形变。 */
    val containerPlaceHolder: SharedTransitionScope.PlaceHolderSize
        get() = PanelBoundsPlaceHolder
}

/**
 * 播放面板封面在面板之间移动时使用的 bounds 配置。
 *
 * 与列表封面 → 详情封面共用 [AlbumArtBoundsTransform] 的曲线与默认时长；
 * 但它们**不要求**时长完全一致——页面级共享元素与手势级封面变形是两件事，
 * 校准页面级的 600ms 属于步骤 4。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val PlayerPanelArtworkBounds = AlbumArtBoundsTransform