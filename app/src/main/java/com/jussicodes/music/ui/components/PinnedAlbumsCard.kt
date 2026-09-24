package com.jussicodes.music.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import com.jussicodes.music.LocalPlayerController
import com.jussicodes.music.constants.MediaSessionConstants
import com.jussicodes.music.constants.ThumbnailCornerRadius
import com.jussicodes.music.constants.pinnedAlbumColumnsKey
import com.jussicodes.music.constants.pinnedAlbumsHiddenKey
import com.jussicodes.music.data.PinnedAlbumStore
import com.jussicodes.music.extensions.playMediaAt
import com.jussicodes.music.extensions.setPlaylist
import com.jussicodes.music.ui.icons.PlayArrow
import com.jussicodes.music.ui.icons.Plus
import com.jussicodes.music.utils.CoverImageSize
import com.jussicodes.music.utils.rememberPreference
import com.jussicodes.music.utils.toCoverImageUrl
import com.rcmiku.ncmapi.api.album.AlbumApi
import com.rcmiku.ncmapi.model.Album
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyGridState
import sh.calvin.reorderable.rememberReorderableLazyGridState

private val CARD_GRID_SPACING = 8.dp

/**
 * 主页"置顶专辑墙"卡片（无标题栏）。
 *
 * - 默认 3 列封面网格（编辑模式可改 2~6 列，持久化）：单击进专辑页（系统双击窗口防误触），
 *   双击循环播放该专辑；**长按任意封面进入编辑模式**。
 * - 编辑模式：封面左上播放（磨砂圆钮）、右上删除（垃圾桶磨砂圆钮）、长按拖拽排序；
 *   顶部工具条出 刷新 / 列数 / 关闭（隐藏整张墙，设置页可恢复）/ 完成。
 * - 专辑为空时网格内是一个圆角矩形边框的加号格（占位格与封面等大），
 *   点它打开收藏专辑多选弹窗。
 */
@Composable
fun PinnedAlbumsCard(
    onOpenAlbum: (Album) -> Unit,
    onAddClick: () -> Unit
) {
    val albums by PinnedAlbumStore.albums.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current
    val mediaController = LocalPlayerController.current.controller
    var editing by remember { mutableStateOf(false) }
    var columns by rememberPreference(pinnedAlbumColumnsKey, 3)
    var pinnedAlbumsHidden by rememberPreference(pinnedAlbumsHiddenKey, false)
    var displayed by remember { mutableStateOf<List<Album>>(emptyList()) }
    var dragChanged by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val reorderableState = rememberReorderableLazyGridState(gridState) { from, to ->
        displayed = displayed.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        dragChanged = true
    }

    LaunchedEffect(albums, editing) {
        if (editing) {
            val storeIds = albums.mapTo(HashSet()) { it.id }
            displayed = displayed.filter { it.id in storeIds }
        } else {
            displayed = albums
        }
    }

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging && dragChanged) {
            dragChanged = false
            coroutineScope.launch {
                PinnedAlbumStore.reorder(displayed.map { it.id })
            }
        }
    }

    /** [loop]=true 用于双击循环播放；编辑模式左上按钮走 [loop]=false 普通播放。 */
    fun playAlbum(album: Album, loop: Boolean) {
        coroutineScope.launch {
            val songs = PinnedAlbumStore.getAlbumSongs(album.id)
                ?: AlbumApi.albumDetail(album.id).getOrNull()
                    ?.also { r -> PinnedAlbumStore.cacheAlbumSongs(album.id, r.songs) }
                    ?.songs
                ?: return@launch
            mediaController?.setPlaylist(
                songs,
                sourceId = album.id,
                sourceName = album.name,
                sourceType = MediaSessionConstants.SOURCE_TYPE_ALBUM,
                navId = album.id
            )
            mediaController?.playMediaAt()
            if (loop) mediaController?.repeatMode = Player.REPEAT_MODE_ALL
            Toast.makeText(
                context,
                if (loop) "《${album.name}》已循环播放" else "《${album.name}》开始播放",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (editing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        if (!refreshing) {
                            refreshing = true
                            coroutineScope.launch {
                                PinnedAlbumStore.sync(force = true)
                                delay(500)
                                refreshing = false
                            }
                        }
                    }) {
                        Text(text = if (refreshing) "刷新中…" else "刷新")
                    }
                    PinnedAlbumsColumnsPicker(columns = columns, onColumnsChange = { columns = it })
                    TextButton(onClick = {
                        pinnedAlbumsHidden = true
                        editing = false
                        Toast.makeText(
                            context, "专辑墙已关闭，可在设置中恢复", Toast.LENGTH_SHORT
                        ).show()
                    }) {
                        Text(text = "关闭")
                    }
                    TextButton(onClick = { editing = false }) {
                        Text(text = "完成")
                    }
                }
            }
            PinnedAlbumsGrid(
                albums = displayed,
                editing = editing,
                columns = columns,
                cardShape = MaterialTheme.shapes.extraLarge,
                reorderableState = reorderableState,
                gridState = gridState,
                view = view,
                showAddTile = displayed.isEmpty() || editing,
                onAddClick = onAddClick,
                onOpenAlbum = onOpenAlbum,
                onPlay = { album, loop -> playAlbum(album, loop) },
                onRemove = { album ->
                    coroutineScope.launch {
                        PinnedAlbumStore.togglePin(album)
                        Toast.makeText(context, "已删除《${album.name}》", Toast.LENGTH_SHORT).show()
                    }
                },
                onEnterEdit = {
                    ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.LONG_PRESS)
                    editing = true
                }
            )
        }
    }
}

/** 编辑模式的列数选择（2~6），house style = DropdownMenu。 */
@Composable
private fun PinnedAlbumsColumnsPicker(columns: Int, onColumnsChange: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Text(text = "列数：$columns")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            (2..6).forEach { n ->
                DropdownMenuItem(
                    text = { Text(text = "$n 列") },
                    onClick = {
                        onColumnsChange(n)
                        open = false
                    }
                )
            }
        }
    }
}

@Composable
private fun PinnedAlbumsGrid(
    albums: List<Album>,
    editing: Boolean,
    columns: Int,
    cardShape: Shape,
    reorderableState: ReorderableLazyGridState,
    gridState: LazyGridState,
    view: android.view.View,
    showAddTile: Boolean,
    onAddClick: () -> Unit,
    onOpenAlbum: (Album) -> Unit,
    onPlay: (Album, Boolean) -> Unit,
    onRemove: (Album) -> Unit,
    onEnterEdit: () -> Unit
) {
    val colCount = columns.coerceIn(2, 6)
    BoxWithConstraints {
        val totalCells = albums.size + if (showAddTile) 1 else 0
        val rowCount = (totalCells + colCount - 1) / colCount
        val coverSize =
            ((maxWidth - CARD_GRID_SPACING * (colCount - 1)) / colCount).coerceAtLeast(56.dp)
        val gridHeight =
            coverSize * rowCount + CARD_GRID_SPACING * ((rowCount - 1).coerceAtLeast(0))
        LazyVerticalGrid(
            columns = GridCells.Fixed(colCount),
            state = gridState,
            modifier = Modifier
                .fillMaxWidth()
                .height(gridHeight),
            userScrollEnabled = false,
            verticalArrangement = Arrangement.spacedBy(CARD_GRID_SPACING),
            horizontalArrangement = Arrangement.spacedBy(CARD_GRID_SPACING),
        ) {
            items(albums.size, key = { index -> albums[index].id }) { index ->
                val album = albums[index]
                ReorderableItem(
                    state = reorderableState,
                    key = album.id,
                    enabled = editing
                ) { isDragging ->
                    val dragModifier = if (editing) {
                        Modifier.longPressDraggableHandle(
                            onDragStarted = {
                                ViewCompat.performHapticFeedback(
                                    view, HapticFeedbackConstantsCompat.GESTURE_START
                                )
                            },
                            onDragStopped = {
                                ViewCompat.performHapticFeedback(
                                    view, HapticFeedbackConstantsCompat.GESTURE_END
                                )
                            }
                        )
                    } else {
                        Modifier
                    }
                    PinnedAlbumCover(
                        album = album,
                        editing = editing,
                        isDragging = isDragging,
                        cardShape = cardShape,
                        dragModifier = dragModifier,
                        onOpen = { onOpenAlbum(album) },
                        onPlay = { onPlay(album, false) },
                        onLoop = { onPlay(album, true) },
                        onRemove = { onRemove(album) },
                        onEnterEdit = onEnterEdit
                    )
                }
            }
            if (showAddTile) {
                item {
                    PinnedAlbumsAddTile(cardShape = cardShape, onClick = onAddClick)
                }
            }
        }
    }
}

@Composable
private fun PinnedAlbumCover(
    album: Album,
    editing: Boolean,
    isDragging: Boolean,
    cardShape: Shape,
    dragModifier: Modifier,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onLoop: () -> Unit,
    onRemove: () -> Unit,
    onEnterEdit: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(cardShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(dragModifier)
            .pointerInput(album.id, editing) {
                if (editing) return@pointerInput
                // 防误触靠系统双击窗口：onTap 要等 double-tap timeout（约 300ms）
                // 内确认没有第二下才触发；第二下落在窗口内则走 onDoubleTap。
                detectTapGestures(
                    onTap = { onOpen() },
                    onDoubleTap = { onLoop() },
                    onLongPress = { onEnterEdit() }
                )
            }
            .graphicsLayer {
                if (isDragging) {
                    scaleX = 1.05f
                    scaleY = 1.05f
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = album.picUrl.toCoverImageUrl(CoverImageSize.DETAIL),
            contentDescription = album.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (editing) {
            CoverActionChip(
                icon = PlayArrow,
                contentDescription = "播放整张",
                onClick = onPlay,
                modifier = Modifier.align(Alignment.TopStart),
                artwork = album.picUrl.toCoverImageUrl(CoverImageSize.DETAIL)
            )
            CoverActionChip(
                icon = Icons.Outlined.Delete,
                contentDescription = "删除",
                onClick = onRemove,
                modifier = Modifier.align(Alignment.TopEnd),
                tint = MaterialTheme.colorScheme.error,
                artwork = album.picUrl.toCoverImageUrl(CoverImageSize.DETAIL)
            )
        }
    }
}

/** 编辑模式封面角上的磨砂圆钮（亚克力：同一张封面 blur 一层垫底 + 半透明 surface + 细描边）。 */
@Composable
private fun CoverActionChip(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    artwork: Any? = null
) {
    Box(
        modifier = modifier
            .padding(4.dp)
            .size(26.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f))
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (artwork != null) {
            AsyncImage(
                model = artwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.6f)
                    .blur(14.dp)
            )
        }
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(15.dp)
        )
    }
}

/** 圆角矩形边框加号格：占位格与封面同网格单元（等大）。 */
@Composable
private fun PinnedAlbumsAddTile(cardShape: Shape, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .aspectRatio(1f)
            .border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(ThumbnailCornerRadius)
            )
            .clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = Plus,
            contentDescription = "添加置顶专辑",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(36.dp)
        )
    }
}
