package com.jussicodes.music.ui.components

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
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
import com.jussicodes.music.ui.icons.GridView
import com.jussicodes.music.ui.icons.Plus
import com.jussicodes.music.utils.CoverImageSize
import com.jussicodes.music.utils.rememberPreference
import com.jussicodes.music.utils.toCoverImageUrl
import com.rcmiku.ncmapi.api.album.AlbumApi
import com.rcmiku.ncmapi.model.Album
import kotlin.math.roundToInt
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
 * - 编辑模式：顶部图标工具条 刷新 / 列数（弹窗滑杆调 2~6 列）/ 完成 / 关闭（红色叉，最右；
 *   隐藏整张墙，设置页可恢复）；封面右上角为实心圆删除钮（MD 风格，无磨砂）；
 *   长按拖拽排序；系统返回手势拦截为"退出编辑模式"。
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

    // 编辑态与正常态都全量跟随 store：弹窗"应用"/删除即时上墙（新专辑实时进入，
    // 不必先点"完成"）；拖拽排序由 PinnedAlbumStore.reorder 落盘后经 albums 流回流。
    LaunchedEffect(albums) {
        displayed = albums
    }

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging && dragChanged) {
            dragChanged = false
            coroutineScope.launch {
                PinnedAlbumStore.reorder(displayed.map { it.id })
            }
        }
    }

    // 编辑模式下系统返回（手势/按键）先退出编辑模式，而不是直接退到桌面
    BackHandler(enabled = editing) { editing = false }

    /** [loop]=true 用于双击循环播放。 */
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

    var showColumnsDialog by remember { mutableStateOf(false) }

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
                    IconButton(onClick = {
                        if (!refreshing) {
                            refreshing = true
                            coroutineScope.launch {
                                PinnedAlbumStore.sync(force = true)
                                delay(500)
                                refreshing = false
                            }
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = if (refreshing) "刷新中" else "刷新",
                            tint = if (refreshing) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { showColumnsDialog = true }) {
                        Icon(
                            imageVector = GridView,
                            contentDescription = "调整列数"
                        )
                    }
                    IconButton(onClick = { editing = false }) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = "完成"
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Row(
                        modifier = Modifier.clickable {
                            pinnedAlbumsHidden = true
                            editing = false
                            Toast.makeText(
                                context, "专辑墙已关闭，可在设置中恢复", Toast.LENGTH_SHORT
                            ).show()
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "关闭专辑墙",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "关闭专辑墙",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
                if (showColumnsDialog) {
                    PinnedAlbumsColumnsDialog(
                        columns = columns,
                        onColumnsChange = { columns = it },
                        onDismiss = { showColumnsDialog = false }
                    )
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

/** 列数弹窗：滑杆 2~6 列（5 个档位与当前可调范围一致），拖动即时生效，确定/取消关闭。 */
@Composable
private fun PinnedAlbumsColumnsDialog(
    columns: Int,
    onColumnsChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "封面列数") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = columns.toFloat(),
                    onValueChange = { onColumnsChange(it.roundToInt()) },
                    valueRange = 2f..6f,
                    steps = 3
                )
                Text(
                    text = "${columns} 列",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(text = "完成") }
        }
    )
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
            // MD 风格删除钮：实心 error 圆 + 白色图标（去掉磨砂背景），置顶右上；
            // 放大 150%（圆 33dp / 图标 21dp）并留 6dp 内边距，不贴封面圆角边界。
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(33.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "删除",
                    tint = Color.White,
                    modifier = Modifier.size(21.dp)
                )
            }
        }
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
