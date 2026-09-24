package com.jussicodes.music.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jussicodes.music.data.PinnedAlbumStore
import com.jussicodes.music.ui.icons.LibraryAddCheck
import com.jussicodes.music.utils.CoverImageSize
import com.jussicodes.music.utils.toCoverImageUrl
import com.rcmiku.ncmapi.api.account.AccountApi
import com.rcmiku.ncmapi.model.Album
import com.rcmiku.ncmapi.model.SubAlbum
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val SUB_ALBUM_PAGE_SIZE = 25

/** 弹窗里选中的收藏专辑 → 专辑墙桩数据（/album/sublist 字段足够展示，详情由 Store 补拉）。 */
private fun SubAlbum.toSeedAlbum() = Album(
    id = id,
    name = name,
    picUrl = picUrl,
    artists = artists,
    size = size
)

/**
 * "收藏的专辑"多选弹窗（/album/sublist 分页，25/页，滚到底自动续页，失败可重试）。
 * 已置顶的专辑带勾选标记；"全选/清空"作用于已加载页；应用 →
 * [PinnedAlbumStore.applyPinned]（保留已有顺序、新增追加、未勾移除）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinnedAlbumPickDialog(onDismiss: () -> Unit) {
    val currentPinnedIds = PinnedAlbumStore.albums.value.mapTo(mutableSetOf()) { it.id }
    var albums by remember { mutableStateOf<List<SubAlbum>>(emptyList()) }
    var loadedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var hasMore by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<Long>>(currentPinnedIds) }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val selectedCount = selected.size

    suspend fun loadPage(offset: Int): Boolean {
        val response = AccountApi.albumSublist(offset = offset, limit = SUB_ALBUM_PAGE_SIZE)
        val page = response.getOrNull() ?: return false
        val fresh = page.data.filter { it.id !in loadedIds }
        loadedIds += fresh.map { it.id }.toSet()
        albums += fresh
        hasMore = page.hasMore && fresh.isNotEmpty()
        return true
    }

    LaunchedEffect(Unit) {
        if (!loadPage(0)) failed = true
    }

    // 滚到底（前 3 行内）自动续页，debounce 防止快速滚动触发多次
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= albums.size - 3
        }.distinctUntilChanged().debounce(150).collectLatest { shouldLoad ->
            if (shouldLoad && hasMore && !loadingMore && !failed) {
                loadingMore = true
                loadPage(loadedIds.size)
                loadingMore = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(modifier = Modifier.padding(top = 8.dp)) {
            Text(
                text = "收藏的专辑",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Text(
                text = "勾选要置顶到主页的专辑（可多选）",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            if (albums.isEmpty() && failed) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    TextButton(onClick = {
                        failed = false
                        coroutineScope.launch {
                            if (!loadPage(0)) failed = true
                        }
                    }) {
                        Text(text = "加载失败，点击重试")
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    items(albums, key = { it.id }) { album ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                .clickable {
                                    selected = if (album.id in selected) {
                                        selected - album.id
                                    } else {
                                        selected + album.id
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = album.picUrl.toCoverImageUrl(CoverImageSize.LIST),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .size(44.dp)
                            )
                            Column(
                                modifier = Modifier
                                    .padding(start = 10.dp)
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = album.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = buildString {
                                        append("${album.size}首")
                                        val artist = album.artists.firstOrNull()?.name
                                        if (!artist.isNullOrBlank()) {
                                            append(" · ")
                                            append(artist)
                                        }
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (album.id in selected) {
                                Icon(
                                    imageVector = LibraryAddCheck,
                                    contentDescription = "已选",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .padding(start = 12.dp)
                                        .size(20.dp)
                                )
                            }
                        }
                    }
                    if (loadingMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = {
                    selected = albums.mapTo(mutableSetOf()) { it.id }
                }) {
                    Text(text = "全选")
                }
                TextButton(onClick = {
                    selected = emptySet()
                }) {
                    Text(text = "清空")
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "已选 $selectedCount",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = {
                    coroutineScope.launch {
                        PinnedAlbumStore.applyPinned(
                            finalIds = selected.toList(),
                            seeds = albums.filter { it.id in selected }.map { it.toSeedAlbum() }
                        )
                    }
                    onDismiss()
                }) {
                    Text(text = "应用")
                }
            }
        }
    }
}
