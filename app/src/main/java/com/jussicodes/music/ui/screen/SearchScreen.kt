package com.jussicodes.music.ui.screen

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.jussicodes.music.LocalPlayerController
import com.jussicodes.music.LocalPlayerState
import com.jussicodes.music.R
import com.jussicodes.music.constants.MiniPlayerHeight
import com.jussicodes.music.constants.isLandscapePhone
import com.jussicodes.music.constants.searchTopListFilterIdsKey
import com.jussicodes.music.constants.topListEnabledKey
import com.jussicodes.music.extensions.addSong
import com.jussicodes.music.ui.components.AlbumListItem
import com.jussicodes.music.ui.components.ArtistListItem
import com.jussicodes.music.ui.components.JussiDropdownMenu
import com.jussicodes.music.ui.components.JussiMenuItem
import com.jussicodes.music.ui.components.PlaylistListItem
import com.jussicodes.music.ui.components.SongListItem
import com.jussicodes.music.ui.components.SongMenuBottomSheet
import com.jussicodes.music.ui.components.SwipableTabPager
import com.jussicodes.music.ui.components.VoiceListItem
import com.jussicodes.music.ui.icons.ArrowInsert
import com.jussicodes.music.ui.icons.Delete
import com.jussicodes.music.ui.icons.FilterList
import com.jussicodes.music.ui.icons.History
import com.jussicodes.music.ui.icons.Leaderboard
import com.jussicodes.music.ui.icons.Remove
import com.jussicodes.music.ui.icons.Search
import com.jussicodes.music.ui.navigation.AlbumNav
import com.jussicodes.music.ui.navigation.ArtistNav
import com.jussicodes.music.ui.navigation.PlaylistNav
import com.jussicodes.music.ui.navigation.RadioNav
import com.jussicodes.music.ui.navigation.UserNav
import com.jussicodes.music.utils.rememberPreference
import com.jussicodes.music.viewModel.SearchViewModel
import com.rcmiku.ncmapi.api.search.SearchType
import com.rcmiku.ncmapi.model.Playlist
import com.rcmiku.ncmapi.model.SearchSuggestItem
import com.rcmiku.ncmapi.model.Song
import com.rcmiku.ncmapi.model.toAlbumList
import com.rcmiku.ncmapi.model.toPlaylist
import com.rcmiku.ncmapi.model.toSearchArtist
import com.rcmiku.ncmapi.model.toSearchUser
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    navController: NavHostController,
    searchViewModel: SearchViewModel = hiltViewModel()
) {
    var searchValue by rememberSaveable { mutableStateOf("") }
    // 标识是否已按下回车或点击候选词提交了搜索
    var isSubmitted by rememberSaveable { mutableStateOf(false) }

    val searchResults by searchViewModel.searchResults.collectAsState()
    val topLists by searchViewModel.topLists.collectAsState()
    val mediaController = LocalPlayerController.current.controller
    val playerState = LocalPlayerState.current
    val isPlaying = playerState?.isPlaying == true
    val currentMediaId = playerState?.currentMediaItem?.mediaId?.toLongOrNull()
    val hasMiniPlayer = (playerState?.player?.mediaItemCount ?: 0) != 0

    val searchHistoryState by searchViewModel.searchHistory.collectAsState(initial = emptyList())
    val suggestKeywordResponseState by searchViewModel.suggestKeywordResponse.collectAsState()
    val rawSuggests = suggestKeywordResponseState?.data?.suggests.orEmpty()

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val haptics = LocalHapticFeedback.current
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    val configuration = LocalConfiguration.current
    val isLandscape = isLandscapePhone() || configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // 方案1核心：进入搜索页面自动聚焦并唤起软键盘，免去二次点击
    LaunchedEffect(Unit) {
        delay(120)
        focusRequester.requestFocus()
    }

    // 方案1核心：手指在列表滑动时，自动清除焦点并收起软键盘，丝滑让位给浏览
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (abs(available.y) > 1f) {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }
                return Offset.Zero
            }
        }
    }

    val tab = listOf(
        SearchType.Song to stringResource(R.string.song),
        SearchType.Playlist to stringResource(R.string.playlists),
        SearchType.VoiceList to stringResource(R.string.voice_list),
        SearchType.Artist to stringResource(R.string.artist),
        SearchType.Album to stringResource(R.string.album),
        SearchType.User to "用户"
    )

    // 下拉框范围：-1 代表“所有”(默认)，0..5 代表具体分类
    val searchScopeOptions = listOf(
        -1 to "所有",
        0 to stringResource(R.string.song),
        1 to stringResource(R.string.playlists),
        2 to stringResource(R.string.voice_list),
        3 to stringResource(R.string.artist),
        4 to stringResource(R.string.album),
        5 to "用户"
    )
    var selectedScopeIndex by rememberSaveable { mutableIntStateOf(-1) }

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

    val trimmedQuery = searchValue.trim()

    // 建议词排序优化与去重：
    // 1. 过滤掉与用户当前输入完全一致的词（因其已被置顶显示）
    // 2. distinctBy 去重，彻底防止重复 keyword 引发 Compose LazyColumn 重复 key 闪退
    // 3. 精确匹配项置前，前缀匹配项次之，解决精准想要搜的东西排在最后的问题
    val reorderedSuggests = remember(rawSuggests, trimmedQuery) {
        if (trimmedQuery.isEmpty()) {
            emptyList()
        } else {
            rawSuggests
                .filterNot { it.keyword.equals(trimmedQuery, ignoreCase = true) }
                .distinctBy { it.keyword.trim().lowercase() }
                .sortedWith(
                    compareByDescending<SearchSuggestItem> {
                        it.keyword.equals(trimmedQuery, ignoreCase = true)
                    }.thenByDescending {
                        it.keyword.startsWith(trimmedQuery, ignoreCase = true)
                    }
                )
        }
    }

    // 状态判定：
    // 1. isSubmitted == true 且 searchValue NotBlank: 展示搜索结果 (分类 Tab + 对应结果)
    // 2. !isSubmitted 且 searchValue.isNotBlank(): 展示联想建议列表 (含置顶直接搜索项)
    // 3. 其余 (空白或未搜索): 展示历史记录 + 排行榜
    val showSearchResults = isSubmitted && searchValue.isNotBlank()
    val showSuggests = !isSubmitted && searchValue.isNotBlank()

    val executeSearch = { query: String ->
        if (query.isNotBlank()) {
            searchValue = query
            isSubmitted = true
            searchViewModel.updateSearchValue(query)
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .semantics { isTraversalGroup = true },
        topBar = {
            // 统一样式搜索顶栏（类型下拉选择 + 胶囊输入框 + 返回键 + 清除键）
            SearchTopBar(
                query = searchValue,
                onQueryChange = {
                    searchValue = it
                    isSubmitted = false
                    if (it.isNotBlank()) {
                        searchViewModel.fetchSearchKeyword(it)
                    }
                },
                onSearch = {
                    executeSearch(searchValue)
                },
                onClear = {
                    searchValue = ""
                    isSubmitted = false
                },
                onBack = {
                    navController.navigateUp()
                },
                focusRequester = focusRequester,
                isLandscape = isLandscape,
                scopeOptions = searchScopeOptions,
                selectedScopeIndex = selectedScopeIndex,
                onScopeSelect = { scopeIndex ->
                    selectedScopeIndex = scopeIndex
                    if (scopeIndex >= 0) {
                        coroutineScope.launch {
                            pagerState.scrollToPage(scopeIndex)
                        }
                        searchViewModel.updateSearchType(tab[scopeIndex].first)
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(nestedScrollConnection)
        ) {
            when {
                // 展示联想建议列表（第1项固定置顶精确搜索，下方智能重排去重，无重复 key）
                showSuggests -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .semantics { traversalIndex = 1f }
                    ) {
                        // 1. 固定置顶项：直接搜索当前输入的关键词
                        item {
                            DirectSearchSuggestItem(
                                query = trimmedQuery,
                                onClick = { executeSearch(trimmedQuery) }
                            )
                        }

                        // 2. 联想词列表（优先排前缀/精确匹配项，不传易重复的 key 避免闪退）
                        items(reorderedSuggests) { item ->
                            SuggestItem(
                                history = false,
                                query = item.keyword,
                                onInsert = {
                                    searchValue = item.keyword
                                    searchViewModel.fetchSearchKeyword(item.keyword)
                                },
                                onClick = {
                                    executeSearch(item.keyword)
                                }
                            )
                        }

                        item {
                            if (hasMiniPlayer) Spacer(modifier = Modifier.height(MiniPlayerHeight))
                        }
                        item {
                            Spacer(Modifier.navigationBarsPadding())
                        }
                    }
                }

                // 展示搜索分类与结果
                showSearchResults -> {
                    SwipableTabPager(
                        titles = tab.map { it.second },
                        pagerState = pagerState,
                        content = { page ->
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .semantics { traversalIndex = 1f }
                            ) {
                                when (page) {
                                    0 -> {
                                        items(searchResults.size) { index ->
                                            searchResults.getOrNull(index)?.let { resource ->
                                                resource.song?.let { song ->
                                                    SongListItem(
                                                        isPlaying = isPlaying,
                                                        isActive = currentMediaId == song.id,
                                                        song = song,
                                                        songIndex = index + 1,
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
                                            }
                                        }
                                    }

                                    1 -> {
                                        items(searchResults.size) { index ->
                                            searchResults.getOrNull(index)?.let { resource ->
                                                resource.toPlaylist()?.let {
                                                    PlaylistListItem(playlist = it, Modifier.clickable {
                                                        navController.navigate(
                                                            PlaylistNav(
                                                                playlistId = it.id,
                                                                limit = it.trackCount
                                                            )
                                                        )
                                                    })
                                                }
                                            }
                                        }
                                    }

                                    2 -> {
                                        items(searchResults.size) { index ->
                                            searchResults.getOrNull(index)?.let { resource ->
                                                resource.baseInfo?.let { voice ->
                                                    VoiceListItem(voice = voice, modifier = Modifier.clickable {
                                                        navController.navigate(
                                                            RadioNav(radioId = voice.id)
                                                        )
                                                    })
                                                }
                                            }
                                        }
                                    }

                                    3 -> {
                                        items(searchResults.size) { index ->
                                            searchResults.getOrNull(index)?.let { resource ->
                                                resource.toSearchArtist()?.let {
                                                    ArtistListItem(artist = it, modifier = Modifier.clickable {
                                                        navController.navigate(ArtistNav(artistId = it.id))
                                                    })
                                                }
                                            }
                                        }
                                    }

                                    4 -> {
                                        items(searchResults.size) { index ->
                                            searchResults.getOrNull(index)?.let { resource ->
                                                resource.toAlbumList()?.let {
                                                    AlbumListItem(album = it, modifier = Modifier.clickable {
                                                        navController.navigate(AlbumNav(albumId = it.id))
                                                    })
                                                }
                                            }
                                        }
                                    }

                                    5 -> {
                                        items(searchResults.size) { index ->
                                            searchResults.getOrNull(index)?.let { resource ->
                                                resource.toSearchUser()?.let { user ->
                                                    ArtistListItem(
                                                        artist = com.rcmiku.ncmapi.model.SearchArtist(
                                                            id = user.id,
                                                            name = user.nickname,
                                                            picUrl = user.avatarUrl,
                                                            briefDesc = user.signature
                                                        ),
                                                        modifier = Modifier.clickable {
                                                            navController.navigate(UserNav(userId = user.id))
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                item {
                                    if (hasMiniPlayer) Spacer(modifier = Modifier.height(MiniPlayerHeight))
                                }

                                item {
                                    Spacer(Modifier.navigationBarsPadding())
                                }
                            }
                        }
                    )
                }

                // 初始空白态：搜索历史 + 排行榜 (适配横竖屏)
                else -> {
                    if (isLandscape) {
                        // 横屏双栏：左 38% 搜索历史，右 62% 排行榜
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp)
                        ) {
                            // 左栏：搜索历史
                            Column(
                                modifier = Modifier
                                    .weight(0.38f)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                if (searchHistoryState.isNotEmpty()) {
                                    SearchHistoryChips(
                                        historyList = searchHistoryState,
                                        onChipClick = executeSearch,
                                        onChipDelete = { query ->
                                            searchViewModel.deleteSearchQuery(query)
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onClearAll = {
                                            searchViewModel.clearSearchHistory()
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                    )
                                }
                                if (hasMiniPlayer) Spacer(modifier = Modifier.height(MiniPlayerHeight))
                                Spacer(Modifier.navigationBarsPadding())
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            // 右栏：排行榜
                            LazyColumn(
                                modifier = Modifier
                                    .weight(0.62f)
                                    .fillMaxHeight()
                            ) {
                                item {
                                    TopListsHeader(
                                        topListEnabled = topListEnabled,
                                        onToggleEnabled = { topListEnabled = it },
                                        menuExpanded = topListMenuExpanded,
                                        onMenuExpandedChange = { topListMenuExpanded = it },
                                        topLists = topLists,
                                        selectedTopListIds = selectedTopListIds,
                                        defaultTopLists = defaultTopLists,
                                        onSelectionChange = { selectedTopListIdsText = it }
                                    )
                                }

                                if (topListEnabled) {
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
                                }

                                item {
                                    if (hasMiniPlayer) Spacer(modifier = Modifier.height(MiniPlayerHeight))
                                }

                                item {
                                    Spacer(Modifier.navigationBarsPadding())
                                }
                            }
                        }
                    } else {
                        // 竖屏：上下流式单列
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .semantics { traversalIndex = 1f }
                        ) {
                            if (searchHistoryState.isNotEmpty()) {
                                item {
                                    SearchHistoryChips(
                                        historyList = searchHistoryState,
                                        onChipClick = executeSearch,
                                        onChipDelete = { query ->
                                            searchViewModel.deleteSearchQuery(query)
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onClearAll = {
                                            searchViewModel.clearSearchHistory()
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                    )
                                }
                            }

                            item {
                                TopListsHeader(
                                    topListEnabled = topListEnabled,
                                    onToggleEnabled = { topListEnabled = it },
                                    menuExpanded = topListMenuExpanded,
                                    onMenuExpandedChange = { topListMenuExpanded = it },
                                    topLists = topLists,
                                    selectedTopListIds = selectedTopListIds,
                                    defaultTopLists = defaultTopLists,
                                    onSelectionChange = { selectedTopListIdsText = it }
                                )
                            }

                            if (topListEnabled) {
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
                            }

                            item {
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
    }

    SongMenuBottomSheet(
        navController = navController,
        song = selectSong,
        onDismiss = { openBottomSheet = false },
        openBottomSheet = openBottomSheet
    )
}

/**
 * 现代胶囊搜索顶栏（类型下拉选择 + 返回 + 搜索胶囊框 + 清除）
 */
@Composable
private fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    focusRequester: FocusRequester,
    isLandscape: Boolean,
    scopeOptions: List<Pair<Int, String>>,
    selectedScopeIndex: Int,
    onScopeSelect: (Int) -> Unit
) {
    val barHeight = if (isLandscape) 48.dp else 56.dp
    var typeDropdownExpanded by remember { mutableStateOf(false) }
    val currentScopeName = scopeOptions.find { it.first == selectedScopeIndex }?.second ?: "所有"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isLandscape) Modifier else Modifier.statusBarsPadding())
            .height(barHeight)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = null
            )
        }

        // 统一外形胶囊输入容器
        Row(
            modifier = Modifier
                .weight(1f)
                .height(if (isLandscape) 36.dp else 40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 搜索类型下拉选择入口（默认“所有”）
            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { typeDropdownExpanded = true }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currentScopeName,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = ArrowDropDown,
                        contentDescription = "选择搜索范围",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                JussiDropdownMenu(
                    expanded = typeDropdownExpanded,
                    onDismissRequest = { typeDropdownExpanded = false }
                ) {
                    scopeOptions.forEach { (index, name) ->
                        JussiMenuItem(
                            text = name,
                            selected = index == selectedScopeIndex,
                            onClick = {
                                typeDropdownExpanded = false
                                onScopeSelect(index)
                            }
                        )
                    }
                }
            }

            // 分割竖线
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .width(1.dp)
                    .height(16.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            )

            // 输入框
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                    ) {
                        if (query.isEmpty()) {
                            Text(
                                text = stringResource(R.string.search),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        innerTextField()
                    }
                }
            )

            if (query.isNotEmpty()) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "清空",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))
    }
}

/**
 * 紧凑现代的搜索历史标签流
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun SearchHistoryChips(
    historyList: List<String>,
    onChipClick: (String) -> Unit,
    onChipDelete: (String) -> Unit,
    onClearAll: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "搜索记录",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(
                onClick = onClearAll,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Delete,
                    contentDescription = "清空记录",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            historyList.forEach { query ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .border(
                            width = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .combinedClickable(
                            onClick = { onChipClick(query) },
                            onLongClick = { onChipDelete(query) }
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = query,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * 排行榜标题与筛选菜单
 */
@Composable
private fun TopListsHeader(
    topListEnabled: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    topLists: List<Playlist>,
    selectedTopListIds: Set<Long>,
    defaultTopLists: List<Playlist>,
    onSelectionChange: (String) -> Unit
) {
    if (topListEnabled) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "排行榜",
                style = MaterialTheme.typography.titleMedium
            )

            Box {
                IconButton(onClick = { onMenuExpandedChange(true) }) {
                    Icon(
                        imageVector = FilterList,
                        contentDescription = "筛选榜单"
                    )
                }

                JussiDropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { onMenuExpandedChange(false) }
                ) {
                    JussiMenuItem(
                        text = "关闭排行榜",
                        icon = Remove,
                        onClick = {
                            onToggleEnabled(false)
                            onMenuExpandedChange(false)
                        }
                    )
                    topLists.forEach { playlist ->
                        val selectedIds = selectedTopListIds.ifEmpty {
                            defaultTopLists.map { it.id }.toSet()
                        }
                        val checked = playlist.id in selectedIds
                        JussiMenuItem(
                            text = playlist.name,
                            icon = Leaderboard,
                            selected = checked,
                            trailing = {
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
                                onSelectionChange(nextSelectedIds.toTopListIdsText())
                            }
                        )
                    }
                }
            }
        }
    } else {
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
            TextButton(onClick = { onToggleEnabled(true) }) {
                Text(text = "开启")
            }
        }
    }
}

private fun isDefaultTopList(playlist: Playlist): Boolean {
    val defaultNames = listOf("飙升榜", "新歌榜", "原创榜", "热歌榜")
    return defaultNames.any { playlist.name.contains(it) }
}

private fun String.toTopListIdSet(): Set<Long> =
    split(",").mapNotNull { it.toLongOrNull() }.toSet()

private fun Set<Long>.toTopListIdsText(): String = joinToString(",")

/**
 * 联想搜索列表首行：固定置顶的“直接搜索 ‘关键词’”项
 */
@Composable
private fun DirectSearchSuggestItem(
    query: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp)
    ) {
        Icon(
            imageVector = Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(12.dp)
                .size(20.dp)
        )
        Text(
            text = "搜索 “$query”",
            style = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

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
            .height(56.dp)
            .combinedClickable(onClick = {
                onClick()
            }, onLongClick = {
                onDelete()
            })
            .padding(horizontal = 8.dp)
    ) {
        Icon(
            imageVector = if (history) History else Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(12.dp)
                .size(20.dp)
        )
        Text(
            text = query,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        IconButton(
            onClick = onInsert,
        ) {
            Icon(
                imageVector = ArrowInsert,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * 下拉箭头图标（本地绘制，零外部依赖）
 */
private val ArrowDropDown: ImageVector by lazy {
    ImageVector.Builder(
        name = "ArrowDropDown",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(7f, 10f)
            lineTo(12f, 15f)
            lineTo(17f, 10f)
            close()
        }
    }.build()
}
