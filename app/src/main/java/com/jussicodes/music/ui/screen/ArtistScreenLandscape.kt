package com.jussicodes.music.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import coil3.compose.AsyncImage
import com.jussicodes.music.R
import com.jussicodes.music.constants.MiniPlayerHeight
import com.jussicodes.music.ui.components.NavigationTitle
import com.jussicodes.music.ui.components.PlayingIndicatorBox
import com.jussicodes.music.ui.icons.FavoriteFill
import com.jussicodes.music.utils.CoverImageSize
import com.jussicodes.music.utils.extractAmbientColor
import com.jussicodes.music.utils.makeTimeString
import com.jussicodes.music.utils.toCoverImageUrl
import com.rcmiku.ncmapi.model.Album
import com.rcmiku.ncmapi.model.SearchArtist
import com.rcmiku.ncmapi.model.Song
import kotlinx.coroutines.launch

/**
 * 横屏（矮屏）歌手详情页 —— 左右分栏（左 ~37% / 右 ~63%）。
 *
 * 竖屏是「顶部横幅 + 下方三 tab」的单列堆叠；横屏只有 ~411dp 可视高度，
 * 横幅会把列表挤没，因此换成**双栏**：
 *
 * - **左栏（固定不滚动）= 视觉沉浸区**：整栏铺一层艺人封面（放大 + 羽化渐变遮罩），
 *   中间放一张大尺寸方形海报卡片（圆角 + 玻璃描边 + 阴影），海报下方是
 *   艺人名 / 粉丝数 / 关注天数，以及「收藏」和「播放全部」两枚药丸按钮。
 *   左上角固定「返回」按钮。
 * - **右栏（独立垂直滚动）= 内容浏览区**：顶部吸顶 tab（探索 / 歌曲 / 专辑），
 *   歌曲 tab 下再吸顶一排过滤芯片（热门 Top 50 / 全部歌曲 / 排序）。
 *   歌曲行信息更密（序号 + 歌名 + 专辑 + 音质 + 时长 + 更多），
 *   专辑在宽栏下走自适应网格。
 *
 * 全屏底色用艺人封面的主色调做一层很弱的径向环境光晕（[extractAmbientColor]），
 * 右栏再叠一层半透明深色表面容器，让图文分层。
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
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { tabTitles.size })
    val scope = rememberCoroutineScope()

    // 艺人封面主色调 → 全屏环境光晕（弱径向渐变），取不到就退回主题主色。
    val ambientColor by produceState<Color?>(initialValue = null, heroUrl) {
        value = extractAmbientColor(heroUrl?.toCoverImageUrl(CoverImageSize.LIST))
    }
    val glowColor = ambientColor ?: MaterialTheme.colorScheme.primary

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 径向光晕的中心/半径要的是 px：dp 先换算
        val density = LocalDensity.current
        val glowCenter = Offset(
            x = with(density) { (maxWidth * 0.16f).toPx() },
            y = with(density) { (maxHeight * 0.08f).toPx() }
        )
        val glowRadius = with(density) { (maxOf(maxWidth, maxHeight) * 0.8f).toPx() }
        // 环境光晕：主色调的极弱径向渐变，保持整体暗黑模式的连贯感
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(glowColor.copy(alpha = 0.26f), Color.Transparent),
                        center = glowCenter,
                        radius = glowRadius
                    )
                )
        )
        // 底部压暗：让右栏列表底部与背景自然过渡
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.62f to Color.Transparent,
                            1.0f to MaterialTheme.colorScheme.background.copy(alpha = 0.5f)
                        )
                    )
                )
        )

        Row(modifier = Modifier.fillMaxSize()) {
            // ── 左栏：视觉沉浸区（固定不滚动）────────────────────
            Column(
                modifier = Modifier
                    .weight(ArtistLandscapeLeftFraction)
                    .fillMaxHeight()
                    .padding(horizontal = 14.dp)
            ) {
                // 返回按钮：固定在左上角安全区
                IconButton(
                    onClick = onBack,
                    colors = IconButtonDefaults.filledIconButtonColors()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null
                    )
                }
                // 海报 + 艺人信息（垂直居中，整体不滚动）
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 海报区（不再铺模糊的艺人封面背景，只留玻璃卡 + 方形海报）
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        BoxWithConstraints(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            val side = minOf(maxWidth, maxHeight) * 0.78f
                            val posterShape = RoundedCornerShape(18.dp)
                            Box(
                                modifier = Modifier
                                    .size(side)
                                    .shadow(18.dp, posterShape)
                                    .clip(posterShape)
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.28f))
                                    .border(
                                        width = 1.dp,
                                        color = Color.White.copy(alpha = 0.16f),
                                        shape = posterShape
                                    )
                                    .clickable(enabled = posterUrl != null) {
                                        posterUrl?.let(onPosterClick)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = posterUrl?.toCoverImageUrl(CoverImageSize.LARGE),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    // 艺人名
                    Text(
                        text = artistName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    // 粉丝数 / 关注天数
                    val metaParts = listOfNotNull(
                        fansText?.takeIf { it.isNotBlank() },
                        followText?.takeIf { it.isNotBlank() }
                    )
                    if (metaParts.isNotEmpty()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = metaParts.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    // 操作区：收藏 + 播放全部（播放全部是显眼的大药丸）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onToggleSubscribe,
                            enabled = subscribeEnabled,
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                            modifier = Modifier.weight(1f)
                        ) {
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
                    }
                }
            }

            // ── 右栏：内容浏览区（独立垂直滚动）──────────────────
            Column(
                modifier = Modifier
                    .weight(1f - ArtistLandscapeLeftFraction)
                    .fillMaxHeight()
                    .padding(top = 6.dp, bottom = 6.dp, end = 10.dp)
            ) {
                // 半透明深色表面容器：与左栏的沉浸背景分层
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.34f))
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.06f),
                            shape = RoundedCornerShape(20.dp)
                        )
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // 顶部吸顶 tab
                        ArtistLandscapeTabRow(
                            titles = tabTitles,
                            selectedIndex = pagerState.currentPage,
                            onSelect = { index ->
                                scope.launch { pagerState.animateScrollToPage(index) }
                            }
                        )
                        // 过滤芯片：只在「歌曲」页出现，同样吸顶
                        if (pagerState.currentPage == 1) {
                            ArtistLandscapeFilterRow(
                                songMode = songMode,
                                onSongModeChange = onSongModeChange,
                                sortOrder = sortOrder,
                                onSortOrderChange = onSortOrderChange
                            )
                        }
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
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
                }
            }
        }
    }
}

/** 横屏歌手页左栏宽度占比（沉浸区 ~37%，右栏 ~63%）。 */
private const val ArtistLandscapeLeftFraction = 0.37f

/**
 * 右栏顶部的吸顶 tab 行（自绘，避免实验性 TabRow 的高度抖动）。
 * 选中项用主色 + 底部短横线表示，横向均分。
 */
@Composable
private fun ArtistLandscapeTabRow(
    titles: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        titles.forEachIndexed { index, title ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .height(2.dp)
                        .width(if (selected) 26.dp else 0.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(2.dp)
                        )
                )
            }
        }
    }
}

/** 右栏吸顶的过滤芯片行：热门 Top 50 / 全部歌曲 / 排序。 */
@Composable
private fun ArtistLandscapeFilterRow(
    songMode: String,
    onSongModeChange: (String) -> Unit,
    sortOrder: String,
    onSortOrderChange: (String) -> Unit,
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
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
            DropdownMenu(
                expanded = sortMenuExpanded,
                onDismissRequest = { sortMenuExpanded = false }
            ) {
                listOf(
                    "hot" to stringResource(R.string.artist_sort_hot),
                    "time" to stringResource(R.string.artist_sort_time)
                ).forEach { (order, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        leadingIcon = {
                            Checkbox(checked = sortOrder == order, onCheckedChange = null)
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

/** 右栏「探索」页：艺人简介 + 相似歌手。 */
@Composable
private fun ArtistLandscapeExplorePage(
    briefDesc: String?,
    simiArtists: List<SearchArtist>,
    onSimilarArtistClick: (SearchArtist) -> Unit,
    hasMiniPlayer: Boolean,
) {
    // 相似歌手：四列网格（探索页整体用网格，简介卡片跨满整行）
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
                    .padding(horizontal = 10.dp)
                    .padding(bottom = 10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                        .copy(alpha = 0.85f)
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
    topSongs: List<Song>,
    allSongs: LazyPagingItems<Song>,
    likedSongIds: Set<Long>,
    isPlaying: Boolean,
    currentMediaId: Long?,
    hasMiniPlayer: Boolean,
    onSongClick: (Song) -> Unit,
    onSongMenu: (Song) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (songMode == "top") {
            items(
                count = topSongs.size,
                key = { index -> "top-song-${topSongs[index].id}" }
            ) { index ->
                val song = topSongs[index]
                ArtistLandscapeSongRow(
                    song = song,
                    index = index + 1,
                    isActive = currentMediaId == song.id,
                    isPlaying = isPlaying,
                    showLiked = song.id in likedSongIds,
                    onClick = { onSongClick(song) },
                    onMenu = { onSongMenu(song) }
                )
            }
        } else {
            items(
                count = allSongs.itemCount,
                key = { index -> "all-song-${allSongs.peek(index)?.id ?: index}" }
            ) { index ->
                allSongs[index]?.let { song ->
                    ArtistLandscapeSongRow(
                        song = song,
                        index = index + 1,
                        isActive = currentMediaId == song.id,
                        isPlaying = isPlaying,
                        showLiked = song.id in likedSongIds,
                        onClick = { onSongClick(song) },
                        onMenu = { onSongMenu(song) }
                    )
                }
            }
        }
        if (hasMiniPlayer) {
            item { Spacer(Modifier.height(MiniPlayerHeight)) }
        }
    }
}

/**
 * 横屏歌曲行：信息更密的一行 —— 序号/播放指示 + 歌名 + 专辑·歌手 + 音质 + 时长 + 更多。
 */
@Composable
private fun ArtistLandscapeSongRow(
    song: Song,
    index: Int,
    isActive: Boolean,
    isPlaying: Boolean,
    showLiked: Boolean,
    onClick: () -> Unit,
    onMenu: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 歌曲封面（专辑小图）
        AsyncImage(
            model = song.al.picUrl.toCoverImageUrl(CoverImageSize.LIST),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
        )
        Spacer(Modifier.width(10.dp))
        // 序号 / 正在播放的动态均衡器
        Box(
            modifier = Modifier.width(26.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isActive) {
                PlayingIndicatorBox(
                    isActive = true,
                    playWhenReady = isPlaying,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Text(
                    text = "$index",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        ) {
            Text(
                text = song.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val artists = song.ar.joinToString("/") { it.name }
            val subtitle = listOfNotNull(
                song.al.name.takeIf { it.isNotBlank() },
                artists.takeIf { it.isNotBlank() }
            ).joinToString(" · ")
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (showLiked) {
            Icon(
                imageVector = FavoriteFill,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .size(14.dp)
                    .padding(end = 2.dp)
            )
        }
        // 音质标识
        qualityLabel(song)?.let { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
        // 时长
        Text(
            text = makeTimeString(song.dt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(42.dp)
        )
        IconButton(onClick = onMenu) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.more)
            )
        }
    }
}

/** 音质标签：按 NCM 的 maxbr 粗略映射。 */
private fun qualityLabel(song: Song): String? {
    val maxbr = song.privilege?.maxbr ?: return null
    return when {
        maxbr >= 999_000 -> "无损"
        maxbr >= 320_000 -> "320k"
        maxbr >= 128_000 -> "128k"
        else -> null
    }
}

/** 右栏「专辑」页：宽栏下用自适应网格展示卡片。 */
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
            start = 10.dp, end = 10.dp, top = 6.dp, bottom = 8.dp
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
