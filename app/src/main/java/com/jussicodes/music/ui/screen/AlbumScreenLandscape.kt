package com.jussicodes.music.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jussicodes.music.R
import com.jussicodes.music.constants.MiniPlayerHeight
import com.jussicodes.music.ui.components.MasterDetailLandscape
import com.jussicodes.music.ui.components.SongListItem
import com.jussicodes.music.ui.icons.LibraryAdd
import com.jussicodes.music.ui.icons.LibraryAddCheck
import com.jussicodes.music.ui.icons.ModeComment
import com.jussicodes.music.ui.icons.Search
import com.rcmiku.ncmapi.model.Song

/**
 * 横屏（矮屏）专辑详情页 —— 主从双栏（左 40% / 右 60%）。
 *
 * 与竖屏 [AlbumScreen] 是两套布局：横屏只有 ~411dp 可视高度，
 * 「封面在上、列表在下」的堆叠会把曲目列表挤没，必须换成分栏。
 *
 * 左栏：顶部操作按钮行（返回 / 搜索 / 更多，与歌单页横屏一致）+ 封面 +
 * 名称 + 艺术家 + 发行时间 + 3 枚药丸按钮（收藏 / 播放 / 评论）。
 * 右栏：曲目列表（搜索框在列表上方），直接复用 [SongListItem] ——
 * 正在播放的动态均衡器、VIP 角标、喜欢爱心、右侧操作菜单全部保留。
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
    searchActive: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    searchFocusRequester: FocusRequester,
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
    var menuExpanded by remember { mutableStateOf(false) }
    MasterDetailLandscape(
        modifier = modifier,
        topActions = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onToggleSearch) {
                Icon(
                    imageVector = Search,
                    contentDescription = stringResource(R.string.search)
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.more)
                    )
                }
                AlbumOverflowMenu(
                    expanded = menuExpanded,
                    onDismiss = { menuExpanded = false },
                    isPinned = isPinned,
                    onTogglePin = {
                        menuExpanded = false
                        onTogglePin()
                    },
                    onShare = {
                        menuExpanded = false
                        onShare()
                    }
                )
            }
        },
        cover = cover,
        // 专辑页：专辑名 + 歌手 + 日期都在封面上方，纯展示（无简介浮层）。
        // 专辑名与歌手要能一眼分开：专辑名用较大字号 + 加粗 + onSurface，
        // 歌手用小字号 + 主色（可点）；发布时间跟歌手同一行，不再单起一行。
        headerContent = {
            Text(
                text = albumName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                overflow = TextOverflow.Ellipsis,
                maxLines = 2
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = artistName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    modifier = Modifier.clickable(onClick = onArtistClick)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "·",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = publishTimeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1
                )
            }
        },
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
        listHeader = {
            if (searchActive) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text(stringResource(R.string.search)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(searchFocusRequester)
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        },
        listContent = {
            itemsIndexed(songs) { index, song ->
                SongListItem(
                    isPlaying = isPlaying,
                    isActive = currentMediaId == song.id,
                    showLikedIcon = song.id in likedSongIds,
                    song = song,
                    // 专辑页同一张封面重复 N 次没有意义：左侧已有大封面，
                    // 列表左侧改显示曲目序号（albumIndex 走纯数字那条分支），
                    // 正在播放的动态均衡器依然保留。
                    albumIndex = index + 1,
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
