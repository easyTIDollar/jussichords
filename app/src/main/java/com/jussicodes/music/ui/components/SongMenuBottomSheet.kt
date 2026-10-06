package com.jussicodes.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.jussicodes.music.LocalPlayerController
import com.jussicodes.music.R
import com.jussicodes.music.data.favoriteSongIdsDatastore
import com.jussicodes.music.extensions.addToPlaylist
import com.jussicodes.music.extensions.insertToPlaylist
import com.jussicodes.music.ui.icons.Album
import com.jussicodes.music.ui.icons.Artist
import com.jussicodes.music.ui.icons.Delete
import com.jussicodes.music.ui.icons.Favorite
import com.jussicodes.music.ui.icons.FavoriteFill
import com.jussicodes.music.ui.icons.PlaylistAdd
import com.jussicodes.music.ui.icons.PlaylistInsert
import com.jussicodes.music.ui.icons.SongListAdd
import com.jussicodes.music.ui.navigation.AlbumNav
import com.jussicodes.music.ui.navigation.ArtistNav
import com.jussicodes.music.utils.CoverImageSize
import com.jussicodes.music.utils.FavoriteSongAction
import com.jussicodes.music.utils.MenuSnackbarBus
import com.jussicodes.music.utils.makeTimeString
import com.jussicodes.music.utils.toCoverImageUrl
import com.jussicodes.music.ui.theme.JetMeloTheme
import com.rcmiku.ncmapi.api.account.AccountApi
import com.rcmiku.ncmapi.api.account.PlayManipulateType
import com.rcmiku.ncmapi.model.Song
import com.rcmiku.ncmapi.model.Artist as NcmArtist
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 歌曲操作菜单（模态底部面板）。
 *
 * 布局：歌曲头（封面 + 两行信息/标签）→ 4 个等宽快捷按钮 → 三组普通行（细分割线分隔）。
 * 默认半展开，底部加导航栏 inset 内边距；点击任一项先关闭菜单，再用全局 Snackbar 反馈。
 * 快捷项与列表项抽成独立数据列表，渲染与数据分离。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongMenuBottomSheet(
    song: Song?,
    openBottomSheet: Boolean,
    onDismiss: () -> Unit,
    navController: NavHostController,
    /** 所属用户自建歌单 id；非 null 时显示「从歌单中移除」。 */
    playlistId: Long? = null,
    /** 从歌单移除成功后回调（供页面刷新本地列表）。 */
    onSongRemoved: (Long) -> Unit = {},
) {
    var openArtistPickerBottomSheet by rememberSaveable { mutableStateOf(false) }
    var openSongListBottomSheet by rememberSaveable { mutableStateOf(false) }
    var openShareSheet by rememberSaveable { mutableStateOf(false) }

    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val mediaController = LocalPlayerController.current.controller
    val songIds by context.favoriteSongIdsDatastore.data.map { it.songIdsList }
        .collectAsState(emptyList())
    val scope = rememberCoroutineScope()

    LaunchedEffect(openBottomSheet) {
        if (openBottomSheet) {
            bottomSheetState.show()
        } else {
            bottomSheetState.hide()
        }
    }

    val s = song
    if (openBottomSheet && s != null) {
        // 面板随本歌封面动态取色（artworkForced 强制用封面，不随全局取色来源设置），
        // 容器用 surfaceContainerHigh 带上适量强调色，和整体不割裂。
        JetMeloTheme(artwork = s.al.picUrl.toCoverImageUrl(CoverImageSize.LIST), artworkForced = true) {
            ModalBottomSheet(
                onDismissRequest = onDismiss,
                sheetState = bottomSheetState,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                SongMenuContent(
                song = s,
                onDismiss = onDismiss,
                navController = navController,
                playlistId = playlistId,
                onSongRemoved = onSongRemoved,
                openArtistPicker = { openArtistPickerBottomSheet = it },
                openSongListPicker = { openSongListBottomSheet = it },
                openShare = { openShareSheet = it },
                isLiked = s.id in songIds,
                onLikeToggle = { targetLiked ->
                    scope.launch {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDismiss()
                        FavoriteSongAction.setLiked(context, s.id, targetLiked, s)
                        MenuSnackbarBus.show(
                            if (targetLiked) "已添加到喜欢的音乐" else "已取消喜欢",
                            onUndo = { FavoriteSongAction.setLiked(context, s.id, !targetLiked, s) }
                        )
                    }
                },
                onPlayNext = {
                    scope.launch {
                        onDismiss()
                        mediaController?.insertToPlaylist(s)
                        MenuSnackbarBus.show("已添加到下一首播放")
                    }
                },
                onAddToQueue = {
                    scope.launch {
                        onDismiss()
                        mediaController?.addToPlaylist(s)
                        MenuSnackbarBus.show("已加入播放队列")
                    }
                },
                onRemoveFromPlaylist = {
                    val pid = playlistId
                    scope.launch {
                        if (pid == null) return@launch
                        onDismiss()
                        AccountApi.playlistManipulate(
                            playlistId = pid,
                            songIds = listOf(s.id),
                            manipulateType = PlayManipulateType.DEL,
                        ).onSuccess {
                            onSongRemoved(s.id)
                            MenuSnackbarBus.show(
                                "已从歌单中移除",
                                onUndo = {
                                    AccountApi.playlistManipulate(
                                        playlistId = pid,
                                        songIds = listOf(s.id),
                                        manipulateType = PlayManipulateType.ADD,
                                    )
                                }
                            )
                        }.onFailure {
                            MenuSnackbarBus.show("移除失败")
                        }
                    }
                },
            )
            }
        }
    }

    s?.let {
        ArtistPickerBottomSheet(
            artists = it.ar,
            openBottomSheet = openArtistPickerBottomSheet,
            onDismiss = { openArtistPickerBottomSheet = false },
            onClick = { artist -> navController.navigate(ArtistNav(artistId = artist.id)) },
        )
        SongListBottomSheet(
            song = it,
            onDismiss = { openSongListBottomSheet = false },
            openBottomSheet = openSongListBottomSheet,
        )
        ShareSheet(
            payload = SharePayload.SongShare(it),
            openBottomSheet = openShareSheet,
            onDismiss = { openShareSheet = false },
        )
    }
}

/** 歌曲头 + 快捷按钮 + 列表行。纯渲染，数据由调用方以回调形式注入。 */
@Composable
private fun SongMenuContent(
    song: Song,
    onDismiss: () -> Unit,
    navController: NavHostController,
    playlistId: Long?,
    onSongRemoved: (Long) -> Unit,
    isLiked: Boolean,
    onLikeToggle: (Boolean) -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onRemoveFromPlaylist: () -> Unit,
    openArtistPicker: (Boolean) -> Unit,
    openSongListPicker: (Boolean) -> Unit,
    openShare: (Boolean) -> Unit,
) {

    // ---- 数据列表：快捷项（上图标、下文字、等宽）----
    val heartTint = if (isLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val quickItems = listOf(
        SongQuickItem(
            label = stringResource(if (isLiked) R.string.unlike else R.string.like),
            icon = if (isLiked) FavoriteFill else Favorite,
            tint = heartTint,
            onClick = { onLikeToggle(!isLiked) },
        ),
        SongQuickItem(
            label = stringResource(R.string.insert_to_playlist),
            icon = PlaylistInsert,
            tint = MaterialTheme.colorScheme.onSurface,
            onClick = { onDismiss(); onPlayNext() },
        ),
        SongQuickItem(
            label = stringResource(R.string.add_to_songList),
            icon = SongListAdd,
            tint = MaterialTheme.colorScheme.onSurface,
            onClick = { onDismiss(); openSongListPicker(true) },
        ),
        SongQuickItem(
            label = stringResource(R.string.share),
            icon = Icons.Outlined.Share,
            tint = MaterialTheme.colorScheme.onSurface,
            onClick = { onDismiss(); openShare(true) },
        ),
    )

    // ---- 数据列表：普通行（按三组分隔）----
    val artistLabel = song.ar.joinToString("/") { it.name }
    val group1 = listOf(
        SongMenuItem(
            label = stringResource(R.string.add_to_playlist),
            icon = PlaylistAdd,
            trailing = null,
            danger = false,
            onClick = { onDismiss(); onAddToQueue() },
        ),
    )
    val group2 = listOf(
        SongMenuItem(
            label = stringResource(R.string.view_artist),
            icon = Artist,
            trailing = artistLabel.takeIf { it.isNotBlank() },
            danger = false,
            onClick = {
                onDismiss()
                if (song.ar.size > 1) {
                    openArtistPicker(true)
                } else {
                    song.ar.firstOrNull()?.let {
                        navController.navigate(ArtistNav(artistId = it.id))
                    }
                }
            },
        ),
        SongMenuItem(
            label = stringResource(R.string.view_album),
            icon = Album,
            trailing = song.al?.name?.takeIf { it.isNotBlank() },
            danger = false,
            onClick = {
                onDismiss()
                song.al?.id?.takeIf { it > 0 }?.let {
                    navController.navigate(AlbumNav(albumId = it))
                }
            },
        ),
    )
    val group3 = if (playlistId != null) {
        listOf(
            SongMenuItem(
                label = stringResource(R.string.remove_from_songList),
                icon = Delete,
                trailing = null,
                danger = true,
                onClick = { onDismiss(); onRemoveFromPlaylist() },
            ),
        )
    } else {
        emptyList()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        SongHeader(song = song)
        Spacer(Modifier.height(12.dp))

        // 快捷按钮区
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            quickItems.forEach { RowQuickAction(it) }
        }
        Spacer(Modifier.height(8.dp))

        // 三组普通行
        MenuGroup {
            group1.forEach { MenuRow(it) }
        }
        MenuGroup {
            group2.forEach { MenuRow(it) }
        }
        if (group3.isNotEmpty()) {
            MenuGroup {
                group3.forEach { MenuRow(it) }
            }
        }
    }
}

/** 歌曲头：56dp 圆角封面 + 歌名(单行省略) + 「歌手 · 时长 [标签]」。 */
@Composable
private fun SongHeader(song: Song) {
    val tags = buildList {
        if (song.fee == 1) add("VIP")
        if (song.fee == 4) add("无版权")
    }
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = song.al.picUrl.toCoverImageUrl(CoverImageSize.LIST),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp)),
        )
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.size(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val subtitle = buildList {
                    add(song.ar.joinToString("/") { it.name })
                    add(makeTimeString(song.dt).ifEmpty { "未知时长" })
                }.joinToString(" · ")
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .basicMarquee(),
                )
                if (tags.isNotEmpty()) {
                    Spacer(Modifier.size(6.dp))
                    tags.forEach { tag ->
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 等宽快捷按钮（Row 内 weight(1f)），图标在上、文字在下，圆角 18dp、surfaceVariant 底。 */
@Composable
private fun RowScope.RowQuickAction(item: SongQuickItem) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = item.onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            modifier = Modifier.size(22.dp),
            tint = item.tint,
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 一组普通行：首尾无、组间用细分割线分隔（padding 让分割线不贴边）。 */
@Composable
private fun MenuGroup(content: @Composable () -> Unit) {
    Column {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .height(1.dp)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
        )
        content()
    }
}

/** 普通行：52dp 高、图标 22dp、左右 12dp；danger 行用 error 色；trailing 灰字靠右。 */
@Composable
private fun MenuRow(item: SongMenuItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(onClick = item.onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            modifier = Modifier.size(22.dp),
            tint = if (item.danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.size(16.dp))
        Text(
            text = item.label,
            style = MaterialTheme.typography.titleMedium,
            color = if (item.danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        item.trailing?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
                    .basicMarquee(),
            )
        }
    }
}

private data class SongQuickItem(
    val label: String,
    val icon: ImageVector,
    val tint: Color,
    val onClick: () -> Unit,
)

private data class SongMenuItem(
    val label: String,
    val icon: ImageVector,
    val trailing: String?,
    val danger: Boolean,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtistPickerBottomSheet(
    artists: List<NcmArtist>,
    openBottomSheet: Boolean,
    onDismiss: () -> Unit,
    onClick: (NcmArtist) -> Unit
) {
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(openBottomSheet) {
        if (openBottomSheet) {
            bottomSheetState.show()
        } else {
            bottomSheetState.hide()
        }
    }

    if (openBottomSheet) {
        ModalBottomSheet(
            modifier = Modifier.statusBarsPadding(),
            onDismissRequest = onDismiss,
            sheetState = bottomSheetState
        ) {
            Column(
                Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 16.dp),
            ) {
                artists.forEach { artist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onDismiss()
                                onClick(artist)
                            }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Artist,
                            contentDescription = null,
                            Modifier.size(22.dp),
                        )
                        Spacer(Modifier.size(16.dp))
                        Text(
                            text = artist.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}
