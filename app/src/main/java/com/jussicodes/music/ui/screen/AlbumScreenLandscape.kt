package com.jussicodes.music.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jussicodes.music.R
import com.jussicodes.music.constants.MiniPlayerHeight
import com.jussicodes.music.ui.components.DetailCircleIconButton
import com.jussicodes.music.ui.components.DetailPillButton
import com.jussicodes.music.ui.components.MasterDetailLandscape
import com.jussicodes.music.ui.components.SongListItem
import com.jussicodes.music.ui.icons.LibraryAdd
import com.jussicodes.music.ui.icons.LibraryAddCheck
import com.jussicodes.music.ui.icons.ModeComment
import com.jussicodes.music.ui.icons.PushPin
import com.jussicodes.music.ui.icons.PushPinFill
import com.rcmiku.ncmapi.model.Song

/**
 * 横屏（矮屏）专辑详情页 —— 主从双栏（左 40% / 右 60%）。
 *
 * 与竖屏 [AlbumScreen] 是两套布局：横屏只有 ~411dp 可视高度，
 * 「封面在上、列表在下」的堆叠会把曲目列表挤没，必须换成分栏。
 *
 * 左栏：顶部操作按钮行（返回 / 置顶 / 分享）+ 封面 + 名称 + 艺术家 +
 * 发行时间 + 3 枚药丸按钮（收藏 / 播放 / 评论）。
 * 右栏：曲目列表，直接复用 [SongListItem] —— 正在播放的动态均衡器、
 * VIP 角标、喜欢爱心、右侧操作菜单全部保留。
 */
@Composable
fun AlbumLandscapeBody(
    albumName: String,
    artistName: String,
    publishTimeText: String,
    cover: @Composable () -> Unit,
    isSubscribed: Boolean,
    isPinned: Boolean,
    songs: List<Song>,
    isPlaying: Boolean,
    currentMediaId: Long?,
    likedSongIds: List<Long>,
    hasMiniPlayer: Boolean,
    onBack: () -> Unit,
    onArtistClick: () -> Unit,
    onToggleSubscribe: () -> Unit,
    onTogglePin: () -> Unit,
    onShare: () -> Unit,
    onPlayAll: () -> Unit,
    onComments: () -> Unit,
    onSongClick: (Song) -> Unit,
    onSongMenu: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    MasterDetailLandscape(
        modifier = modifier,
        topActions = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
            Spacer(Modifier.weight(1f))
            if (isPinned) {
                FilledTonalIconButton(onClick = onTogglePin) {
                    Icon(
                        imageVector = PushPinFill,
                        contentDescription = "取消固定",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                IconButton(onClick = onTogglePin) {
                    Icon(imageVector = PushPin, contentDescription = "固定到主页")
                }
            }
            IconButton(onClick = onShare) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = stringResource(R.string.share)
                )
            }
        },
        cover = cover,
        title = albumName,
        subtitle = {
            Text(
                text = artistName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clickable(onClick = onArtistClick)
            )
        },
        meta = publishTimeText,
        // 横排：主操作「播放」胶囊在最左（浅绿填充、自适应宽度），
        // 「已收藏」「评论」转为等高正圆线框图标按钮
        pillActions = {
            DetailPillButton(
                text = stringResource(R.string.play),
                icon = Icons.Outlined.PlayArrow,
                onClick = onPlayAll
            )
            DetailCircleIconButton(
                icon = if (isSubscribed) LibraryAddCheck else LibraryAdd,
                onClick = onToggleSubscribe,
                contentDescription = stringResource(
                    if (isSubscribed) R.string.library_add_check else R.string.library_add
                )
            )
            DetailCircleIconButton(
                icon = ModeComment,
                onClick = onComments,
                contentDescription = "评论"
            )
        },
        listContent = {
            itemsIndexed(songs) { index, song ->
                SongListItem(
                    isPlaying = isPlaying,
                    isActive = currentMediaId == song.id,
                    showLikedIcon = song.id in likedSongIds,
                    song = song,
                    // 用 songIndex（标题前缀序号）而不是 albumIndex：albumIndex 会让
                    // 缩略图位置显示纯数字、看不到封面，也看不到正在播放的动态均衡器。
                    // 需求里要的是「封面 + 频谱 + VIP 角标 + 爱心」，所以走封面那一条。
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

/** 列表行右侧的「更多」按钮（与竖屏一致）。 */
@Composable
fun SongMenuIconButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = stringResource(R.string.more)
        )
    }
}
