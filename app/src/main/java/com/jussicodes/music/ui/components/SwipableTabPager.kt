package com.jussicodes.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * 可左右滑动的 tab 页：HorizontalPager + 跟手指示器。
 * 指示器由 pagerState 连续位置驱动（currentPage + currentPageOffsetFraction），
 * 手指滑到哪、指示器就跟到哪，不滞后。
 *
 * @param pagerState 可选：外部持有的 pagerState。传入后本组件不自行 remember，
 *   调用方即可监听 currentPage 变化驱动数据加载（关注列表 / 歌手页）。
 *   为 null 时内部 remember 一个默认 page 0 的 state。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipableTabPager(
    titles: List<String>,
    modifier: Modifier = Modifier,
    defaultPage: Int = 0,
    pagerState: PagerState? = null,
    indicatorColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable (page: Int) -> Unit
) {
    val state = pagerState
        ?: rememberPagerState(initialPage = defaultPage, pageCount = { titles.size })
    val scope = rememberCoroutineScope()
    var rowWidthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    // 跟手连续页位置（foundation 源码验证）：拖拽中 currentPageOffsetFraction 从 0→±1，
    // currentPage + fraction 即手指所在的连续页位置，clamp 到 [0, size-1]。
    val continuousPos = (state.currentPage + state.currentPageOffsetFraction)
        .coerceIn(0f, (titles.size - 1).toFloat())

    Column(modifier = modifier) {
        // Tab 栏 + 跟手指示器
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { rowWidthPx = it.width }
        ) {
            SecondaryTabRow(
                selectedTabIndex = state.currentPage,
                indicator = {} // 禁用内置指示器，自绘跟手指示器
            ) {
                titles.forEachIndexed { index, title ->
                    Tab(
                        selected = state.currentPage == index,
                        onClick = {
                            scope.launch {
                                state.animateScrollToPage(index)
                            }
                        },
                        text = { Text(title, maxLines = 1) }
                    )
                }
            }
            // 跟手指示器：
            // m3 SecondaryTabRow 的 tab 等宽平分整行（槽宽 = 行宽 / tab 数，源码确认）。
            // 指示器宽度 = 一个槽位宽（onSizeChanged 回的是 px，必须按密度换算成 dp，
            // 之前直接 px 当 dp 用导致 3x 屏上宽度 = 整行、指示器失效）；
            // 偏移 = 连续位置 × 槽宽（px），拖拽逐帧跟手。
            if (rowWidthPx > 0 && titles.size > 1) {
                val slotWidthPx = rowWidthPx / titles.size
                // onSizeChanged 回的是 px；必须按密度换算成 dp 再交给 Modifier.width，
                // 之前直接 px 当 dp 用 → 3x 屏指示器宽度 = 整行，视觉上"没有指示"。
                val slotWidthDp = (slotWidthPx / density.density).dp
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset { IntOffset((continuousPos * slotWidthPx).toInt(), 0) }
                        .width(slotWidthDp)
                        .padding(horizontal = 5.dp)
                        .height(3.dp)
                        .padding(bottom = 5.dp)
                        .background(indicatorColor, RoundedCornerShape(5.dp))
                )
            }
        }
        // 可滑动内容区
        HorizontalPager(
            state = state,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { page ->
            content(page)
        }
    }
}
