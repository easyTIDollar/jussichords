package com.jussicodes.music.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jussicodes.music.ui.icons.Down
import com.jussicodes.music.ui.icons.GraphicEq
import com.jussicodes.music.ui.icons.Up
import kotlinx.coroutines.launch

/**
 * 歌单右下角竖向排列的滚动定位按钮组：一键到顶 / 定位当前播放歌曲 / 一键到底。
 *
 * 各按钮按条件显示，不适用时淡出隐藏：
 * - [showTop]：列表处于顶部时传 false，不显示"到顶"。
 * - [showBottom]：列表处于底部时传 false，不显示"到底"。
 * - [showCurrentSong]：当前播放歌曲不在列表中时传 false。
 *
 * [currentSongIndex] 为当前播放歌曲在 LazyColumn 中的 item 下标（已含页头偏移）。
 */
@Composable
fun ScrollJumpFab(
    listState: LazyListState,
    showTop: Boolean,
    showCurrentSong: Boolean,
    showBottom: Boolean,
    currentSongIndex: Int?,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.End
    ) {
        AnimatedVisibility(
            visible = showTop,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column(horizontalAlignment = Alignment.End) {
                SmallFloatingActionButton(
                    onClick = { scope.launch { listState.animateScrollToItem(0) } },
                    contentDescription = "一键到顶"
                ) {
                    Icon(imageVector = Up, contentDescription = "一键到顶")
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        AnimatedVisibility(
            visible = showCurrentSong,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column(horizontalAlignment = Alignment.End) {
                SmallFloatingActionButton(
                    onClick = {
                        currentSongIndex?.let { index ->
                            scope.launch { listState.animateScrollToItem(index) }
                        }
                    },
                    contentDescription = "定位当前播放歌曲"
                ) {
                    Icon(imageVector = GraphicEq, contentDescription = "定位当前播放歌曲")
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        AnimatedVisibility(
            visible = showBottom,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            SmallFloatingActionButton(
                onClick = {
                    scope.launch {
                        listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
                    }
                },
                contentDescription = "一键到底"
            ) {
                Icon(imageVector = Down, contentDescription = "一键到底")
            }
        }
    }
}
