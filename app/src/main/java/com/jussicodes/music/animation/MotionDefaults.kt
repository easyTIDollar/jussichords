package com.jussicodes.music.animation

import androidx.compose.foundation.pager.PagerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 零散但跨调用点的动画辅助函数。
 *
 * 只放"确实被多处复用"的逻辑；组件内部的局部动画（播放柱、歌词滚动、时间选择器配色等）
 * 继续留在各自组件里，不要往这里搬。
 */
object MotionDefaults {

    /**
     * 点击底栏 / NavigationRail 切首页 tab：把 pager 补间到目标页。
     *
     * 底栏与 rail 此前各写了一份完全相同的 `animateScrollToPage(tween(400, Decelerate))`，
     * 现在统一到 [MotionSpec.screenEnter]，改节奏只需改一处。
     *
     * 已在目标页且无滑动偏移时不启动动画，避免重复点击引发无意义的补间。
     */
    fun animatePagerToPage(
        pagerState: PagerState,
        page: Int,
        scope: CoroutineScope,
    ) {
        if (pagerState.targetPage == page && pagerState.currentPageOffsetFraction == 0f) return
        scope.launch {
            pagerState.animateScrollToPage(page = page, animationSpec = MotionSpec.screenEnter())
        }
    }
}
