package com.jussicodes.music.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import coil3.compose.AsyncImage
import com.jussicodes.music.R
import com.jussicodes.music.constants.MiniPlayerHeight
import com.jussicodes.music.ui.components.MasterDetailLandscape
import com.jussicodes.music.ui.components.JussiDropdownMenu
import com.jussicodes.music.ui.components.JussiMenuItem
import com.jussicodes.music.ui.components.NavigationTitle
import com.jussicodes.music.ui.components.SongListItem
import com.jussicodes.music.ui.components.SwipableTabPager
import com.jussicodes.music.ui.icons.LibraryAdd
import com.jussicodes.music.ui.icons.LibraryAddCheck
import com.jussicodes.music.utils.CoverImageSize
import com.jussicodes.music.utils.toCoverImageUrl
import com.rcmiku.ncmapi.model.Album
import com.rcmiku.ncmapi.model.SearchArtist
import com.rcmiku.ncmapi.model.Song

/**
 * 横屏（矮屏）歌手详情页 —— 与横屏歌单页**同一套主从双栏骨架**
 * （[MasterDetailLandscape]），唯一区别是右栏：歌单页右栏是单曲列表，
 * 歌手页右栏换成「探索 / 歌曲 / 专辑」三个 tab。
 *
 * - **左栏**：顶部横跨全宽的返回按钮行 + 圆角方形封面 + 歌手名 + 粉丝/关注信息
 *   + 两枚药丸按钮（收藏 / 播放全部）—— 与歌单页横屏完全同款。
 * - **右栏**：吸顶 tab（探索 / 歌曲 / 专辑）+ 各 tab 内容：
 *   探索 = 简介 + 相似歌手（四列网格），歌曲 = 过滤芯片 + 歌曲列表，
 *   专辑 = 圆角方形封面网格。
 *
 * 功能入口与竖屏页保持一致：收藏、播放全部、封面点击预览、歌曲点击/更多菜单、
 * 专辑点击、相似歌手点击。
 */
@Composable
fun ArtistLandscapeBody(
    artistName: String,
    briefDesc: String?,
    fansText: String?,
    followText: String?,
    isSubscribed: Boolean,
    subscribeEnabled: Boolean,
    onToggleSubscribe: () -> Unit,
    heroUrl: String?,
    posterUrl: String?,
    onPosterClick: (String) -> Unit,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    songMode: String,
    onSongModeChange: (String) -> Unit,
    sortOrder: String,
    onSortOrderChange: (String) -> Unit,
    topSongs: List<Song>,
    allSongs: LazyPagingItems<Song>,
    albums: LazyPagingItems<Album>,
    simiArtists: List<SearchArtist>,
    likedSongIds: Set<Long>,
    isPlaying: Boolean,
    currentMediaId: Long?,
    hasMiniPlayer: Boolean,
    onSongClick: (Song) -> Unit,
    onSongMenu: (Song) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onSimilarArtistClick: (SearchArtist) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabTitles = listOf(
        stringResource(R.string.explore),
        stringResource(R.string.song),
        stringResource(R.string.album)
    )
    val coverModel = posterUrl ?: heroUrl

    MasterDetailLandscape(
        modifier = modifier,
        // 顶部横跨全宽的操作行：返回在左端（与歌单页横屏一致）
        topActions = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null
                )
            }
        },
        cover = {
            AsyncImage(
                model = coverModel.toCoverImageUrl(CoverImageSize.LARGE),
                contentDescription = artistName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(enabled = coverModel != null) {
                        coverModel?.let(onPosterClick)
                    }
            )
        },
        title = artistName,
        // 封面上方：歌手名 + 粉丝/关注信息
        headerContent = {
            Text(
                text = artistName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                overflow = TextOverflow.Ellipsis,
                maxLines = 2
            )
            val metaParts = listOfNotNull(
                fansText?.takeIf { it.isNotBlank() },
                followText?.takeIf { it.isNotBlank() }
            )
            if (metaParts.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = metaParts.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        // 药丸按钮：收藏 / 播放全部（与歌单页同款样式）
        pillActions = {
            OutlinedButton(
                onClick = onToggleSubscribe,
                enabled = subscribeEnabled,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (isSubscribed) LibraryAddCheck else LibraryAdd,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(
                    text = stringResource(
                        if (isSubscribed) R.string.library_add_check else R.string.library_add
                    ),
                    maxLines = 1
                )
            }
            Button(
                onClick = onPlayAll,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Outlined.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(text = stringResource(R.string.play), maxLines = 1)
            }
        },
        // 右栏：三个 tab（探索 / 歌曲 / 专辑）
        rightContent = {
            SwipableTabPager(
                titles = tabTitles,
                defaultPage = 1,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> ArtistLandscapeExplorePage(
                        briefDesc = briefDesc,
                        simiArtists = simiArtists,
                        onSimilarArtistClick = onSimilarArtistClick,
                        hasMiniPlayer = hasMiniPlayer
                    )

                    1 -> ArtistLandscapeSongsPage(
                        songMode = songMode,
                        onSongModeChange = onSongModeChange,
                        sortOrder = sortOrder,
                        onSortOrderChange = onSortOrderChange,
                        topSongs = topSongs,
                        allSongs = allSongs,
                        likedSongIds = likedSongIds,
                        isPlaying = isPlaying,
                        currentMediaId = currentMediaId,
                        hasMiniPlayer = hasMiniPlayer,
                        onSongClick = onSongClick,
                        onSongMenu = onSongMenu
                    )

                    else -> ArtistLandscapeAlbumsPage(
                        albums = albums,
                        hasMiniPlayer = hasMiniPlayer,
                        onAlbumClick = onAlbumClick
                    )
                }
            }
        }
    )
}

/** 右栏「探索」页：艺人简介 + 相似歌手（四列网格）。 */
@Composable
private fun ArtistLandscapeExplorePage(
    briefDesc: String?,
    simiArtists: List<SearchArtist>,
    onSimilarArtistClick: (SearchArtist) -> Unit,
    hasMiniPlayer: Boolean,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 8.dp)
    ) {
        item(key = "explore-brief", span = { GridItemSpan(maxLineSpan) }) {
            NavigationTitle(title = stringResource(R.string.artist_info))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Text(
                    text = if (!briefDesc.isNullOrBlank()) {
                        briefDesc.trimIndent()
                    } else {
                        stringResource(R.string.no_brief)
                    },
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        if (simiArtists.isNotEmpty()) {
            item(key = "explore-simi-title", span = { GridItemSpan(maxLineSpan) }) {
                NavigationTitle(title = "相似歌手")
            }
            items(
                count = simiArtists.size,
                key = { index -> "simi-artist-${simiArtists[index].id}-$index" }
            ) { index ->
                val artist = simiArtists[index]
                ArtistLandscapeSimilarItem(
                    artist = artist,
                    modifier = Modifier
                        .padding(6.dp)
                        .clickable { onSimilarArtistClick(artist) }
                )
            }
        }
        if (hasMiniPlayer) {
            item(key = "explore-bottom", span = { GridItemSpan(maxLineSpan) }) {
                Spacer(Modifier.height(MiniPlayerHeight))
            }
        }
    }
}

/** 相似歌手网格单元：圆形头像 + 名字（两行内截断）。 */
@Composable
private fun ArtistLandscapeSimilarItem(
    artist: SearchArtist,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = artist.cover.toCoverImageUrl(CoverImageSize.LIST),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** 右栏「歌曲」页：热门 Top 50 或全部歌曲（分页）。 */
@Composable
private fun ArtistLandscapeSongsPage(
    songMode: String,
    onSongModeChange: (String) -> Unit,
    sortOrder: String,
    onSortOrderChange: (String) -> Unit,
    topSongs: List<Song>,
    allSongs: LazyPagingItems<Song>,
    likedSongIds: Set<Long>,
    isPlaying: Boolean,
    currentMediaId: Long?,
    hasMiniPlayer: Boolean,
    onSongClick: (Song) -> Unit,
    onSongMenu: (Song) -> Unit,
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // 过滤芯片：热门 Top 50 / 全部歌曲 / 排序（与竖屏页一致，放在列表顶部）
        item(key = "song-toolbar") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = songMode == "top",
                    onClick = { onSongModeChange("top") },
                    label = { Text(stringResource(R.string.artist_song_top50)) }
                )
                FilterChip(
                    selected = songMode == "all",
                    onClick = { onSongModeChange("all") },
                    label = { Text(stringResource(R.string.artist_song_all)) }
                )
                Spacer(Modifier.weight(1f))
                Box {
                    FilterChip(
                        selected = sortMenuExpanded,
                        enabled = songMode == "all",
                        onClick = { sortMenuExpanded = true },
                        label = { Text(stringResource(R.string.artist_sort)) }
                    )
                    JussiDropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false }
                    ) {
                        listOf(
                            "hot" to stringResource(R.string.artist_sort_hot),
                            "time" to stringResource(R.string.artist_sort_time)
                        ).forEach { (order, label) ->
                            val checked = sortOrder == order
                            JussiMenuItem(
                                text = label,
                                icon = if (order == "hot") {
                                    Icons.Outlined.LocalFireDepartment
                                } else {
                                    Icons.Outlined.Schedule
                                },
                                selected = checked,
                                trailing = {
                                    Checkbox(checked = checked, onCheckedChange = null)
                                },
                                onClick = {
                                    onSortOrderChange(order)
                                    sortMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
        if (songMode == "top") {
            items(
                count = topSongs.size,
                key = { index -> "top-song-${topSongs[index].id}" }
            ) { index ->
                val song = topSongs[index]
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
        } else {
            items(
                count = allSongs.itemCount,
                key = { index -> "all-song-${allSongs.peek(index)?.id ?: index}" }
            ) { index ->
                allSongs[index]?.let { song ->
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
            }
        }
        if (hasMiniPlayer) {
            item { Spacer(Modifier.height(MiniPlayerHeight)) }
        }
    }
}

/** 右栏「专辑」页：宽栏下用自适应网格展示圆角方形封面。 */
@Composable
private fun ArtistLandscapeAlbumsPage(
    albums: LazyPagingItems<Album>,
    hasMiniPlayer: Boolean,
    onAlbumClick: (Album) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp
        )
    ) {
        items(
            count = albums.itemCount,
            key = { index -> "album-${albums.peek(index)?.id ?: index}" }
        ) { index ->
            albums[index]?.let { album ->
                val shape = RoundedCornerShape(14.dp)
                Column(
                    modifier = Modifier
                        .padding(6.dp)
                        .clickable { onAlbumClick(album) }
                ) {
                    AsyncImage(
                        model = album.picUrl.toCoverImageUrl(CoverImageSize.GRID),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(shape)
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = album.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = album.artists.joinToString("/") { it.name }
                            .ifBlank { album.artist.name },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (hasMiniPlayer) {
            item { Spacer(Modifier.height(MiniPlayerHeight)) }
        }
    }
}
