package com.jussicodes.music.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.jussicodes.music.R
import com.jussicodes.music.constants.MiniPlayerHeight
import com.jussicodes.music.ui.components.DetailPillButton
import com.jussicodes.music.ui.components.MasterDetailLandscape
import com.jussicodes.music.ui.components.SongListItem
import com.jussicodes.music.ui.icons.LibraryAdd
import com.jussicodes.music.ui.icons.LibraryAddCheck
import com.jussicodes.music.ui.icons.ModeComment
import com.rcmiku.ncmapi.model.Song

/**
 * 横屏（矮屏）歌单详情页 —— 主从双栏（左 40% / 右 60%）。
 *
 * 与竖屏 [PlaylistScreen] 是两套布局：横屏只有 ~411dp 可视高度，
 * 堆叠布局会把曲目列表挤没，必须换成分栏。
 *
 * 左栏：顶部操作按钮行（返回 / 搜索 / 更多，由调用方给）+ 封面 + 歌单名 +
 * 歌单介绍 + 时间 + 3 枚药丸按钮（收藏 / 播放 / 评论）。
 * 右栏：曲目列表（复用 [SongListItem]，保留动态均衡器 / VIP 角标 /
 * 喜欢爱心 / 右侧操作菜单）+ 顶部搜索框。
 *
 * 注意：横屏列表**不做拖拽排序** —— 拖拽排序依赖
 * `rememberReorderableLazyListState(listState)` 与列表头部偏移量的对应关系，
 * 而横屏的头部在左栏、右栏列表没有头部 item，索引对不上会排错顺序。
 */
@Composable
fun PlaylistLandscapeBody(
    playlistName: String,
    metaText: String,
    description: String?,
    cover: @Composable () -> Unit,
    isSubscribed: Boolean,
    songs: List<Song>,
    isPlaying: Boolean,
    currentMediaId: Long?,
    likedSongIds: List<Long>,
    hasMiniPlayer: Boolean,
    listState: LazyListState,
    onToggleSubscribe: () -> Unit,
    onPlayAll: () -> Unit,
    onComments: () -> Unit,
    onSongClick: (Song) -> Unit,
    onSongMenu: (Song) -> Unit,
    modifier: Modifier = Modifier,
    topActions: @Composable RowScope.() -> Unit = {},
    listHeader: (@Composable () -> Unit)? = null,
) {
    // 注意：显式用局部 val + 显式接收者调用，避免命名实参与本地参数同名造成歧义。
    val actions = topActions
    val coverContent = cover
    MasterDetailLandscape(
        modifier = modifier,
        topActions = { actions(this) },
        cover = { coverContent() },
        title = playlistName,
        meta = metaText,
        description = description,
        pillActions = {
            DetailPillButton(
                text = stringResource(
                    if (isSubscribed) R.string.library_add_check else R.string.library_add
                ),
                icon = if (isSubscribed) LibraryAddCheck else LibraryAdd,
                onClick = onToggleSubscribe,
                modifier = Modifier.weight(1f)
            )
            DetailPillButton(
                text = stringResource(R.string.play),
                icon = Icons.Outlined.PlayArrow,
                onClick = onPlayAll,
                filled = true,
                modifier = Modifier.weight(1f)
            )
            DetailPillButton(
                text = "评论",
                icon = ModeComment,
                onClick = onComments,
                modifier = Modifier.weight(1f)
            )
        },
        listHeader = listHeader,
        listState = listState,
        listContent = {
            itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                SongListItem(
                    song = song,
                    isPlaying = isPlaying,
                    showLikedIcon = song.id in likedSongIds,
                    isActive = currentMediaId == song.id,
                    songIndex = index + 1,
                    modifier = Modifier.clickable { onSongClick(song) },
                    trailingContent = {
                        SongMenuIconButton(onClick = { onSongMenu(song) })
                    }
                )
            }
            if (hasMiniPlayer) {
                item { Spacer(Modifier.size(MiniPlayerHeight)) }
            }
        }
    )
}
