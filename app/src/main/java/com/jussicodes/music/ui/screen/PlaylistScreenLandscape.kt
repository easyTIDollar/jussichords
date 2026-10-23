package com.jussicodes.music.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.jussicodes.music.R
import com.jussicodes.music.constants.MiniPlayerHeight
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
    onToggleSubscribe: () -> Unit,
    onPlayAll: () -> Unit,
    onComments: () -> Unit,
    onSongClick: (Song) -> Unit,
    onSongMenu: (Song) -> Unit,
    modifier: Modifier = Modifier,
    topActions: @Composable RowScope.() -> Unit = {},
    listHeader: (@Composable () -> Unit) = {},
    onTitleClick: () -> Unit = {},
    descriptionOverlayVisible: Boolean? = null,
    onDescriptionOverlayDismiss: () -> Unit = {},
) {
    // 注意：topActions / cover 是扩展函数类型或普通 composable 类型的**值**，
    // 直接作为实参透传，不要写成 `{ actions(this) }` / `{ cover() }`：
    // 对扩展函数类型显式传接收者调用会被 Compose 编译器拒绝
    // （「@Composable invocations can only happen from the context of a @Composable function」）。
    MasterDetailLandscape(
        modifier = modifier,
        topActions = topActions,
        cover = cover,
        title = playlistName,
        meta = metaText,
        description = description,
        // 封面上方：仅歌单名，点击展开全屏简介浮层（日期/播放数在浮层内）
        headerContent = {
            Text(
                text = playlistName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                overflow = TextOverflow.Ellipsis,
                maxLines = 2,
                modifier = Modifier.clickable(onClick = onTitleClick)
            )
        },
        onTitleClick = onTitleClick,
        descriptionOverlayVisible = descriptionOverlayVisible,
        onDescriptionOverlayDismiss = onDescriptionOverlayDismiss,
        // 复用竖屏页三按钮：收藏 / 播放 / 评论（顺序与竖屏一致）
        pillActions = {
            OutlinedButton(
                onClick = onToggleSubscribe,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (isSubscribed) LibraryAddCheck else LibraryAdd,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(text = stringResource(
                    if (isSubscribed) R.string.library_add_check else R.string.library_add
                ))
            }
            Button(
                onClick = onPlayAll,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(text = stringResource(R.string.play))
            }
            OutlinedButton(
                onClick = onComments,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = ModeComment,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(text = "评论")
            }
        },
        listHeader = listHeader,
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
