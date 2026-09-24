package com.jussicodes.music.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
import com.jussicodes.music.LocalPlayerState
import com.jussicodes.music.constants.MediaSessionConstants
import com.jussicodes.music.constants.pinnedAlbumColumnsKey
import com.jussicodes.music.data.PinnedAlbumStore
import com.jussicodes.music.extensions.playMediaAt
import com.jussicodes.music.extensions.setPlaylist
import com.jussicodes.music.ui.icons.Pencil
import com.jussicodes.music.ui.icons.Plus
import com.jussicodes.music.ui.icons.PushPin
import com.jussicodes.music.ui.icons.RepeatOn
import com.jussicodes.music.utils.CoverImageSize
import com.jussicodes.music.utils.rememberPreference
import com.jussicodes.music.utils.toCoverImageUrl
import com.rcmiku.ncmapi.api.album.AlbumApi
import com.rcmiku.ncmapi.model.Album
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyGridState
import sh.calvin.reorderable.rememberReorderableLazyGridState

private const val SINGLE_TAP_DELAY_MS = 300L
private val CARD_GRID_SPACING = 8.dp

/**
 * 主页"置顶专辑墙"卡片（跟"创建/收藏的歌单"卡片同款样式）。
 *
 * - 非编辑：默认 3 列（编辑模式改的列数持久化后全局生效）封面网格，单击进专辑页
 *   （300ms 防误触窗口，第二下判定为双击 → 循环播放该专辑：切队列 +
 *   REPEAT_MODE_ALL），正在循环播放的专辑封面带 RepeatOn 角标；专辑为空时
 *   网格内是一个虚线边框加号（占位格与封面等大），点它打开收藏专辑多选弹窗。
 * - 编辑：标题栏出 刷新 / 列数(2~6, 持久化) / 完成；封面长按拖拽排序（放下才落盘）、
 *   左上循环播放整张、右上取消置顶；加号跟在最后一张专辑后面。
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
    val playerState = LocalPlayerState.current
    var editing by remember { mutableStateOf(false) }
    var columns by rememberPreference(pinnedAlbumColumnsKey, 3)
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

    val loopingAlbumId: Long? = if (playerState?.repeatMode == Player.REPEAT_MODE_ALL) {
        val extras = playerState?.currentMediaItem?.mediaMetadata?.extras
        val sourceId = extras?.getLong(MediaSessionConstants.EXTRA_SOURCE_ID, 0L)
        if (
            extras?.getString(MediaSessionConstants.EXTRA_SOURCE_TYPE) ==
                MediaSessionConstants.SOURCE_TYPE_ALBUM && sourceId != null && sourceId != 0L
        ) sourceId else null
    } else null

    fun loopPlay(album: Album) {
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
            mediaController?.repeatMode = Player.REPEAT_MODE_ALL
            Toast.makeText(context, "《${album.name}》已循环播放", Toast.LENGTH_SHORT).show()
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pinned Albums",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                if (editing) {
                    TextButton(
                        onClick = {
                            if (!refreshing) {
                                refreshing = true
                                coroutineScope.launch {
                                    PinnedAlbumStore.sync(force = true)
                                    delay(500)
                                    refreshing = false
                                }
                            }
                        }
                    ) {
                        Text(text = if (refreshing) "刷新中…" else "刷新")
                    }
                    PinnedAlbumsColumnsPicker(columns = columns, onColumnsChange = { columns = it })
                    TextButton(onClick = { editing = false }) {
                        Text(text = "完成")
                    }
                } else {
                    IconButton(onClick = { editing = true }) {
                        Icon(
                            imageVector = Pencil,
                            contentDescription = "编辑",
                            modifier = Modifier.size(20.dp)
                        )
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
                onLoopPlay = ::loopPlay,
                onUnpin = { album ->
                    coroutineScope.launch {
                        PinnedAlbumStore.togglePin(album)
                        Toast.makeText(
                            context, "已取消置顶《${album.name}》", Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                loopingAlbumId = loopingAlbumId
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
    onLoopPlay: (Album) -> Unit,
    onUnpin: (Album) -> Unit,
    loopingAlbumId: Long?
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
                        looping = album.id == loopingAlbumId,
                        onOpen = { onOpenAlbum(album) },
                        onLoop = { onLoopPlay(album) },
                        onUnpin = { onUnpin(album) }
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
    looping: Boolean,
    onOpen: () -> Unit,
    onLoop: () -> Unit,
    onUnpin: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
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
                    onDoubleTap = { onLoop() }
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
        if (looping) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = RepeatOn,
                    contentDescription = "循环播放中",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        if (editing) {
            Box(modifier = Modifier.align(Alignment.TopStart).padding(2.dp)) {
                IconButton(onClick = onLoop) {
                    Icon(
                        imageVector = RepeatOn,
                        contentDescription = "播放整张",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(2.dp)) {
                IconButton(onClick = onUnpin) {
                    Icon(
                        imageVector = PushPin,
                        contentDescription = "取消置顶",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/** 虚线边框加号：占位格与封面同网格单元（等大），边框用 dash path effect 画。 */
@Composable
private fun PinnedAlbumsAddTile(cardShape: Shape, onClick: () -> Unit) {
    val dashColor = MaterialTheme.colorScheme.outline
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .aspectRatio(1f)
            .clip(cardShape)
            .drawBehind {
                val stroke = with(drawContext.density) { 2.dp.toPx() }
                val dashes = with(drawContext.density) {
                    floatArrayOf(10.dp.toPx(), 8.dp.toPx())
                }
                val dashed = PathEffect.dashPathEffect(dashes, 0f)
                drawRoundRect(
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = Size(size.width - stroke, size.height - stroke),
                    color = dashColor,
                    style = Stroke(width = stroke, pathEffect = dashed)
                )
            }
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
