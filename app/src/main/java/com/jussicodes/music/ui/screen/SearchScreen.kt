package com.jussicodes.music.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.input.ImeAction
import coil3.compose.AsyncImage
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.jussicodes.music.LocalPlayerController
import com.jussicodes.music.LocalPlayerState
import com.jussicodes.music.R
import com.jussicodes.music.constants.MiniPlayerHeight
import com.jussicodes.music.constants.searchTopListFilterIdsKey
import com.jussicodes.music.constants.topListEnabledKey
import com.jussicodes.music.extensions.addSong
import com.jussicodes.music.ui.components.AlbumListItem
import com.jussicodes.music.ui.components.ListItem
import com.jussicodes.music.ui.components.ArtistListItem
import com.jussicodes.music.ui.components.PlaylistListItem
import com.jussicodes.music.ui.components.SongListItem
import com.jussicodes.music.ui.components.SongMenuBottomSheet
import com.jussicodes.music.ui.components.SwipableTabPager
import com.jussicodes.music.ui.icons.ArrowInsert
import com.jussicodes.music.ui.icons.FilterList
import com.jussicodes.music.ui.icons.History
import com.jussicodes.music.ui.icons.PlayArrow
import com.jussicodes.music.ui.icons.Remove
import com.jussicodes.music.ui.icons.Search
import com.jussicodes.music.ui.navigation.AlbumNav
import com.jussicodes.music.ui.navigation.ArtistNav
import com.jussicodes.music.ui.navigation.PlaylistNav
import com.jussicodes.music.ui.navigation.RadioNav
import com.jussicodes.music.ui.navigation.UserNav
import com.jussicodes.music.constants.ListThumbnailSize
import com.jussicodes.music.utils.CoverImageSize
import com.jussicodes.music.utils.rememberPreference
import com.jussicodes.music.utils.toCoverImageUrl
import com.jussicodes.music.viewModel.SearchViewModel
import com.rcmiku.ncmapi.api.search.SearchType
import com.rcmiku.ncmapi.model.Album
import com.rcmiku.ncmapi.model.Playlist
import com.rcmiku.ncmapi.model.SearchArtist
import com.rcmiku.ncmapi.model.SearchResources
import com.rcmiku.ncmapi.model.Song
import com.rcmiku.ncmapi.model.toAlbumList
import com.rcmiku.ncmapi.model.toPlaylist
import com.rcmiku.ncmapi.model.toSearchArtist
import com.rcmiku.ncmapi.model.toSearchUser
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 搜索页。一个统一搜索框，两种形态：
 * - 预测形态（未提交搜索）：输入即预测，歌手/专辑/单曲三组各 3 条 + 底部「查看全部结果」；空输入显示搜索记录。
 * - 结果形态（已提交搜索）：顶部分类切换 全部/单曲/专辑/歌手，列表可滚动。
 * 键盘 ↑↓ 在预测条目间移动，回车确认（未选中时回车等价于「查看全部结果」）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    navController: NavHostController,
    searchViewModel: SearchViewModel = hiltViewModel()
) {

    var searchValue by rememberSaveable { mutableStateOf("") }
    // 已提交的搜索词；非空 = 结果形态，空 = 预测形态
    var submittedKeyword by rememberSaveable { mutableStateOf("") }
    val resultsByType by searchViewModel.resultsByType.collectAsState()
    val topLists by searchViewModel.topLists.collectAsState()
    val suggestions by searchViewModel.suggestions.collectAsState()
    val mediaController = LocalPlayerController.current.controller
    val playerState = LocalPlayerState.current
    val isPlaying = playerState?.isPlaying == true
    val currentMediaId = playerState?.currentMediaItem?.mediaId?.toLongOrNull()
    // 小播放器是否显示（有歌在队列）：列表末尾避让空白块只在有歌时留
    val hasMiniPlayer = (playerState?.player?.mediaItemCount ?: 0) != 0
    val searchHistoryState by searchViewModel.searchHistory.collectAsState(initial = emptyList())
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val haptics = LocalHapticFeedback.current
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    val historyLabel = stringResource(R.string.search_history)
    val allLabel = stringResource(R.string.all)
    val songLabel = stringResource(R.string.song)
    val albumLabel = stringResource(R.string.album)
    val artistLabel = stringResource(R.string.artist)

    val tab = listOf(
        SearchType.All to allLabel,
        SearchType.Song to songLabel,
        SearchType.Album to albumLabel,
        SearchType.Artist to artistLabel
    )
    // 搜索类型 tab：SwipableTabPager（跟手滑动 tab）。
    // 注意不能用 collectAsState 的 searchType 驱动切页——searchType 是 VM 内的
    // MutableStateFlow，切页期间它保持旧值直到请求完成，用它会卡死在旧页。
    // 改为以 UI 的 pagerState 为唯一驱动源，切页即更新 VM 的 searchType。
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { tab.size })
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collectLatest { page ->
            tab.getOrNull(page)?.let { searchViewModel.updateSearchType(it.first) }
        }
    }
    var openBottomSheet by rememberSaveable { mutableStateOf(false) }
    var selectSong by remember { mutableStateOf<Song?>(null) }
    var topListMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var topListEnabled by rememberPreference(topListEnabledKey, true)
    var selectedTopListIdsText by rememberPreference(searchTopListFilterIdsKey, "")
    val selectedTopListIds = remember(selectedTopListIdsText) {
        selectedTopListIdsText.toTopListIdSet()
    }
    val defaultTopLists = remember(topLists) { topLists.filter(::isDefaultTopList) }
    val visibleTopLists = remember(topLists, selectedTopListIds) {
        val selectedIds = selectedTopListIds.ifEmpty { defaultTopLists.map { it.id }.toSet() }
        topLists.filter { it.id in selectedIds }
    }
    val showResults = submittedKeyword.isNotBlank()

    // ---------- 预测面板数据 ----------
    val panelRows: List<PanelRow> = remember(searchValue, suggestions, searchHistoryState) {
        if (searchValue.isBlank()) {
            buildList<PanelRow> {
                if (searchHistoryState.isNotEmpty()) {
                    add(PanelRow.Header(historyLabel, isHistory = true))
                    searchHistoryState.forEach { add(PanelRow.History(it)) }
                }
            }
        } else {
            buildList<PanelRow> {
                if (suggestions.artists.isNotEmpty()) {
                    add(PanelRow.Header(artistLabel))
                    suggestions.artists.forEach { add(PanelRow.Artist(it)) }
                }
                if (suggestions.albums.isNotEmpty()) {
                    add(PanelRow.Header(albumLabel))
                    suggestions.albums.forEach { add(PanelRow.Album(it)) }
                }
                if (suggestions.songs.isNotEmpty()) {
                    add(PanelRow.Header(songLabel))
                    suggestions.songs.forEach { add(PanelRow.Song(it)) }
                }
                add(PanelRow.ViewAll(searchValue))
            }
        }
    }
    // 可选中条目在 panelRows 中的下标（表头不可选）
    val focusablePositions = remember(panelRows) {
        panelRows.indices.filter { panelRows[it] !is PanelRow.Header }
    }
    var selectedFocusIndex by remember { mutableIntStateOf(-1) }
    // 关键词/预测结果变化后重置选中
    LaunchedEffect(searchValue, suggestions) {
        selectedFocusIndex = -1
    }
    val panelListState = rememberLazyListState()
    // 进入搜索页自动聚焦并弹出键盘（原 SearchBar 展开态的行为）
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    // 键盘移动选中项时把它滚进视野
    LaunchedEffect(selectedFocusIndex) {
        val pos = focusablePositions.getOrNull(selectedFocusIndex) ?: return@LaunchedEffect
        panelListState.animateScrollToItem(pos)
    }

    /** 提交搜索：切到结果形态，回到「全部」分类。 */
    fun submitSearch(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return
        searchValue = trimmed
        submittedKeyword = trimmed
        scope.launch { pagerState.animateScrollToPage(0) }
        searchViewModel.submitSearch(trimmed)
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    /** 回到预测形态（清空/编辑已提交的搜索）。 */
    fun backToPredict() {
        submittedKeyword = ""
        searchViewModel.clearSearchResults()
    }

    /** 回车：选中条目则执行其动作，否则等价于「查看全部结果」。 */
    fun confirmSelection() {
        val row = focusablePositions.getOrNull(selectedFocusIndex)?.let { panelRows[it] }
        when (row) {
            is PanelRow.Artist -> {
                searchValue = row.artist.name
                submitSearch(row.artist.name)
            }

            is PanelRow.Album -> {
                searchValue = row.album.name
                submitSearch(row.album.name)
            }

            is PanelRow.Song -> {
                mediaController?.addSong(row.song)
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }

            is PanelRow.History -> submitSearch(row.query)
            is PanelRow.ViewAll -> submitSearch(row.keyword)
            else -> if (searchValue.isNotBlank()) submitSearch(searchValue)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .semantics { isTraversalGroup = true }
            // ↑↓ / 回车：搜索框有焦点时也能操作预测面板
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // 结果形态下不劫持方向键/回车，交给分类列表
                if (showResults) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionDown -> {
                        if (focusablePositions.isEmpty()) return@onPreviewKeyEvent false
                        selectedFocusIndex =
                            (selectedFocusIndex + 1).coerceAtMost(focusablePositions.size - 1)
                        true
                    }

                    Key.DirectionUp -> {
                        if (selectedFocusIndex < 0) return@onPreviewKeyEvent false
                        selectedFocusIndex = (selectedFocusIndex - 1).coerceAtLeast(0)
                        true
                    }

                    Key.Enter -> {
                        confirmSelection()
                        true
                    }

                    else -> false
                }
            }) {
        Column(
            Modifier
                .fillMaxSize()
        ) {
        // 顶部统一搜索框
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.navigateUp() }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = null
                )
            }
            TextField(
                value = searchValue,
                onValueChange = {
                    searchValue = it
                    if (it.isNotBlank()) {
                        searchViewModel.fetchSuggestions(it)
                        // 编辑已提交的关键词 → 回到预测形态
                        if (submittedKeyword.isNotBlank() && it.trim() != submittedKeyword) {
                            submittedKeyword = ""
                        }
                    } else {
                        searchViewModel.fetchSuggestions("")
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search)) },
                leadingIcon = {
                    Icon(imageVector = Search, contentDescription = null)
                },
                trailingIcon = {
                    if (searchValue.isNotEmpty()) {
                        IconButton(onClick = {
                            searchValue = ""
                            searchViewModel.fetchSuggestions("")
                            backToPredict()
                            focusManager.clearFocus()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.clear)
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submitSearch(searchValue) })
            )
        }

        Column(Modifier.weight(1f)) {
            if (showResults) {
                // ---------- 完整结果页：顶部分类切换 ----------
                SwipableTabPager(
                    titles = tab.map { it.second },
                    pagerState = pagerState,
                    content = { page ->
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .semantics { traversalIndex = 1f }
                        ) {
                            // 页号 = tab 顺序：0 全部 / 1 单曲 / 2 专辑 / 3 歌手
                            val pageResults = resultsByType[tab[page].first].orEmpty()
                            itemsIndexed(pageResults) { index, resource ->
                                when (page) {
                                    // 全部：按资源自身类型渲染（综合搜索混合列表）
                                    0 -> SearchResultAnyItem(
                                        resource = resource,
                                        isPlaying = isPlaying,
                                        currentMediaId = currentMediaId,
                                        modifier = Modifier.clickable {
                                            navigateToResource(navController, resource) { song ->
                                                mediaController?.addSong(song)
                                            }
                                        },
                                        onSongMenu = { song ->
                                            selectSong = song
                                            openBottomSheet = true
                                        }
                                    )

                                    1 -> resource.song?.let { song ->
                                        SongListItem(
                                            isPlaying = isPlaying,
                                            isActive = currentMediaId == song.id,
                                            song = song,
                                            modifier = Modifier.clickable {
                                                mediaController?.addSong(song)
                                            },
                                            trailingContent = {
                                                IconButton(onClick = {
                                                    selectSong = song
                                                    openBottomSheet = true
                                                }) {
                                                    Icon(
                                                        imageVector = Icons.Default.MoreVert,
                                                        contentDescription = stringResource(R.string.more)
                                                    )
                                                }
                                            }
                                        )
                                    }

                                    2 -> resource.toAlbumList()?.let { album ->
                                        AlbumListItem(
                                            album = album,
                                            modifier = Modifier.clickable {
                                                navController.navigate(AlbumNav(albumId = album.id))
                                            }
                                        )
                                    }

                                    else -> resource.toSearchArtist()?.let { artist ->
                                        ArtistListItem(
                                            artist = artist,
                                            modifier = Modifier.clickable {
                                                navController.navigate(ArtistNav(artistId = artist.id))
                                            }
                                        )
                                    }
                                }
                            }

                            item {
                                // 小播放器避让：有歌时末尾垫 64dp，最后一行不被悬浮播放器盖住
                                if (hasMiniPlayer) Spacer(modifier = Modifier.height(MiniPlayerHeight))
                            }

                            item {
                                Spacer(Modifier.navigationBarsPadding())
                            }
                        }
                    }
                )
            } else {
                // ---------- 预测面板 ----------
                LazyColumn(
                    state = panelListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics { traversalIndex = 1f }
                ) {
                    itemsIndexed(panelRows) { index, row ->
                        val isSelected = index == focusablePositions.getOrNull(selectedFocusIndex)
                        PanelRowItem(
                            row = row,
                            isSelected = isSelected,
                            modifier = Modifier.panelRowBackground(isSelected),
                            onClick = {
                                selectedFocusIndex = focusablePositions.indexOf(index)
                                confirmSelection()
                            },
                            onPlay = { song ->
                                mediaController?.addSong(song)
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onInsert = { keyword ->
                                searchValue = keyword
                                searchViewModel.fetchSuggestions(keyword)
                                focusManager.clearFocus()
                            },
                            onDelete = { query ->
                                searchViewModel.deleteSearchQuery(query)
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onClearHistory = {
                                searchViewModel.clearSearchHistory()
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        )
                    }

                    // 空输入时在搜索记录下方继续展示排行榜
                    if (searchValue.isBlank() && topListEnabled) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.top_list),
                                    style = MaterialTheme.typography.titleMedium
                                )

                                Box {
                                    IconButton(onClick = { topListMenuExpanded = true }) {
                                        Icon(
                                            imageVector = FilterList,
                                            contentDescription = stringResource(R.string.more)
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = topListMenuExpanded,
                                        onDismissRequest = { topListMenuExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(text = "关闭排行榜") },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Remove,
                                                    contentDescription = null
                                                )
                                            },
                                            onClick = {
                                                topListEnabled = false
                                                topListMenuExpanded = false
                                            }
                                        )
                                        topLists.forEach { playlist ->
                                            val selectedIds = selectedTopListIds.ifEmpty {
                                                defaultTopLists.map { it.id }.toSet()
                                            }
                                            val checked = playlist.id in selectedIds
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = playlist.name,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                },
                                                leadingIcon = {
                                                    Checkbox(
                                                        checked = checked,
                                                        onCheckedChange = null
                                                    )
                                                },
                                                onClick = {
                                                    val nextSelectedIds = if (checked) {
                                                        selectedIds - playlist.id
                                                    } else {
                                                        selectedIds + playlist.id
                                                    }
                                                    selectedTopListIdsText = nextSelectedIds.toTopListIdsText()
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        items(visibleTopLists, key = { it.id }) { playlist ->
                            PlaylistListItem(
                                playlist = playlist,
                                modifier = Modifier.clickable {
                                    navController.navigate(
                                        PlaylistNav(
                                            playlistId = playlist.id,
                                            limit = playlist.trackCount
                                        )
                                    )
                                }
                            )
                        }
                    } else if (searchValue.isBlank() && !topListEnabled) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "排行榜已关闭",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = {
                                    topListEnabled = true
                                }) {
                                    Text(text = "开启")
                                }
                            }
                        }
                    }

                    item {
                        // 小播放器避让：有歌时末尾垫 64dp，最后一行不被悬浮播放器盖住
                        if (hasMiniPlayer) Spacer(modifier = Modifier.height(MiniPlayerHeight))
                    }

                    item {
                        Spacer(Modifier.navigationBarsPadding())
                    }
                }
            }
        }

        }
    }

    SongMenuBottomSheet(
        navController = navController,
        song = selectSong,
        onDismiss = { openBottomSheet = false },
        openBottomSheet = openBottomSheet
    )
}

/** 预测面板的一行：分组表头 / 歌手 / 专辑 / 单曲 / 历史 / 查看全部结果。 */
private sealed interface PanelRow {
    data class Header(val title: String, val isHistory: Boolean = false) : PanelRow
    data class Artist(val artist: SearchArtist) : PanelRow
    data class Album(val album: com.rcmiku.ncmapi.model.Album) : PanelRow
    data class Song(val song: com.rcmiku.ncmapi.model.Song) : PanelRow
    data class History(val query: String) : PanelRow
    data class ViewAll(val keyword: String) : PanelRow
}

@Composable
private fun PanelRowItem(
    row: PanelRow,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onPlay: (Song) -> Unit = {},
    onInsert: (String) -> Unit = {},
    onDelete: (String) -> Unit = {},
    onClearHistory: () -> Unit = {},
) {
    when (row) {
        is PanelRow.Header -> Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = row.title, style = MaterialTheme.typography.titleSmall)
            if (row.isHistory) {
                TextButton(onClick = onClearHistory) {
                    Icon(imageVector = Remove, contentDescription = null)
                    Text(text = stringResource(R.string.clear))
                }
            }
        }

        is PanelRow.Artist -> ListItem(
            title = row.artist.name,
            subtitle = row.artist.briefDesc.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.artist),
            modifier = modifier.clickable(onClick = onClick),
            thumbnailContent = {
                AsyncImage(
                    model = row.artist.cover.toCoverImageUrl(CoverImageSize.LIST),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .clip(CircleShape)
                        .size(ListThumbnailSize)
                )
            }
        )

        is PanelRow.Album -> AlbumListItem(
            album = row.album,
            modifier = modifier.clickable(onClick = onClick)
        )

        is PanelRow.Song -> SongListItem(
            isPlaying = false,
            isActive = false,
            song = row.song,
            modifier = modifier.clickable(onClick = onClick),
            trailingContent = {
                IconButton(onClick = { onPlay(row.song) }) {
                    Icon(
                        imageVector = PlayArrow,
                        contentDescription = stringResource(R.string.play),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        )

        is PanelRow.History -> SuggestItem(
            history = true,
            modifier = modifier,
            query = row.query,
            onInsert = { onInsert(row.query) },
            onClick = onClick,
            onDelete = { onDelete(row.query) }
        )

        is PanelRow.ViewAll -> Row(
            modifier = modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.view_all_results),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** 键盘选中态的背景色。 */
@Composable
private fun Modifier.panelRowBackground(isSelected: Boolean): Modifier =
    if (isSelected) background(MaterialTheme.colorScheme.surfaceVariant) else this

/** 综合搜索结果里的一条：按资源自身类型渲染。 */
@Composable
private fun SearchResultAnyItem(
    resource: SearchResources,
    isPlaying: Boolean,
    currentMediaId: Long?,
    modifier: Modifier = Modifier,
    onSongMenu: (Song) -> Unit,
) {
    val song = resource.song
    if (song != null) {
        SongListItem(
            isPlaying = isPlaying,
            isActive = currentMediaId == song.id,
            song = song,
            modifier = modifier,
            trailingContent = {
                IconButton(onClick = { onSongMenu(song) }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.more)
                    )
                }
            }
        )
        return
    }
    resource.toAlbumList()?.let { album ->
        AlbumListItem(album = album, modifier = modifier)
        return
    }
    resource.toSearchArtist()?.let { artist ->
        ArtistListItem(artist = artist, modifier = modifier)
        return
    }
    resource.toPlaylist()?.let { playlist ->
        PlaylistListItem(playlist = playlist, modifier = modifier)
        return
    }
    resource.toSearchUser()?.let { user ->
        ArtistListItem(
            artist = SearchArtist(
                id = user.id,
                name = user.nickname,
                picUrl = user.avatarUrl,
                briefDesc = user.signature
            ),
            modifier = modifier
        )
    }
}

/**
 * 综合搜索结果 → 目标页面。单曲不进详情页（点击直接播放）。
 * mediaController 由调用方传入：非 @Composable 函数不能读 LocalPlayerController。
 */
private fun navigateToResource(
    navController: NavHostController,
    resource: SearchResources,
    onPlaySong: (Song) -> Unit
) {
    resource.album?.let { navController.navigate(AlbumNav(albumId = it.id)); return }
    resource.artist?.let { navController.navigate(ArtistNav(artistId = it.id)); return }
    resource.playlist?.let {
        navController.navigate(PlaylistNav(playlistId = it.id, limit = it.trackCount)); return
    }
    resource.user?.let { navController.navigate(UserNav(userId = it.id)); return }
    resource.baseInfo?.let { navController.navigate(RadioNav(radioId = it.id)); return }
    resource.song?.let { song -> onPlaySong(song) }
}

private fun isDefaultTopList(playlist: Playlist): Boolean {
    val defaultNames = listOf("飙升榜", "新歌榜", "原创榜", "热歌榜")
    return defaultNames.any { playlist.name.contains(it) }
}

private fun String.toTopListIdSet(): Set<Long> =
    split(",").mapNotNull { it.toLongOrNull() }.toSet()

private fun Set<Long>.toTopListIdsText(): String = joinToString(",")


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SuggestItem(
    history: Boolean,
    modifier: Modifier = Modifier,
    query: String,
    onClick: () -> Unit,
    onDelete: () -> Unit = {},
    onInsert: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .combinedClickable(onClick = {
                onClick()
            }, onLongClick = {
                onDelete()
            })
    ) {
        Icon(
            imageVector = if (history) History else Search,
            contentDescription = null,
            modifier = Modifier
                .fillMaxHeight()
                .padding(12.dp)
        )
        Text(
            text = query,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        IconButton(
            onClick = onInsert,
        ) {
            Icon(
                imageVector = ArrowInsert,
                contentDescription = null
            )
        }
    }
}