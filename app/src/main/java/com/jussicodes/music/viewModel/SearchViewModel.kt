package com.jussicodes.music.viewModel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jussicodes.music.data.searchHistoryDataStore
import com.jussicodes.music.utils.ApiServerErrorNotifier
import com.rcmiku.ncmapi.api.playlist.PlaylistApi
import com.rcmiku.ncmapi.api.search.SearchApi
import com.rcmiku.ncmapi.api.search.SearchType
import com.rcmiku.ncmapi.model.Album
import com.rcmiku.ncmapi.model.Playlist
import com.rcmiku.ncmapi.model.SearchArtist
import com.rcmiku.ncmapi.model.SearchResources
import com.rcmiku.ncmapi.model.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

// 预测面板每组最多展示的条目数
private const val SUGGEST_LIMIT = 3

// 预测时向后端索取的原始条数（重排后取前 SUGGEST_LIMIT 条）
private const val SUGGEST_FETCH_LIMIT = 30

/** 预测面板内容：歌手 / 专辑 / 单曲三组。 */
data class SearchSuggestions(
    val keyword: String = "",
    val artists: List<SearchArtist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val songs: List<Song> = emptyList()
) {
    val isEmpty: Boolean
        get() = artists.isEmpty() && albums.isEmpty() && songs.isEmpty()
}

/**
 * 名称与关键词的匹配度排序键，越小越靠前：
 * 0 = 精确相等，1 = 前缀匹配，2 = 包含（模糊），3 = 不匹配（保底排在最后）。
 */
private fun String.matchRank(keyword: String): Int {
    val name = this.trim().lowercase()
    val key = keyword.trim().lowercase()
    if (key.isEmpty()) return 3
    return when {
        name == key -> 0
        name.startsWith(key) -> 1
        name.contains(key) -> 2
        else -> 3
    }
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _searchType = MutableStateFlow(SearchType.Song)
    val searchType: StateFlow<SearchType> = _searchType.asStateFlow()

    private val _searchValue = MutableStateFlow("")
    // 每个分类一份结果：分类切换时不清空其它页的数据，
    // 否则「全部」页会显示成上一个分类（单曲）的结果。
    private val _resultsByType = MutableStateFlow<Map<SearchType, List<SearchResources>>>(emptyMap())
    val resultsByType: StateFlow<Map<SearchType, List<SearchResources>>> = _resultsByType.asStateFlow()

    private val _topLists = MutableStateFlow<List<Playlist>>(emptyList())
    val topLists: StateFlow<List<Playlist>> = _topLists.asStateFlow()

    // 预测面板数据：一次综合搜索(type=101)拿到歌手/专辑/单曲，再按匹配度重排。
    private val _suggestions = MutableStateFlow(SearchSuggestions())
    val suggestions: StateFlow<SearchSuggestions> = _suggestions.asStateFlow()
    private var suggestJob: Job? = null
    private var suggestKeyword: String = ""

    init {
        fetchTopLists()
    }

    fun updateSearchType(searchType: SearchType) {
        _searchType.value = searchType
        // 该分类已有当前关键词的结果就不重发
        val keyword = _searchValue.value.trim()
        if (keyword.isNotEmpty() && _resultsByType.value.containsKey(searchType)) return
        fetchSearchResults()
    }

    /**
     * 提交一次搜索：词 + 分类（回到「全部」）一起设置，只发一次请求。
     * 分开调用 updateSearchType/updateSearchValue 会发两次请求，且两次结果
     * 到达顺序不确定，可能让「全部」页显示成上一个分类的数据。
     */
    fun submitSearch(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return
        _searchType.value = SearchType.All
        _searchValue.value = trimmed
        saveSearch(trimmed)
        _resultsByType.value = emptyMap()
        fetchSearchResults()
    }

    /** 清空结果（回到预测形态），不写搜索历史。 */
    fun clearSearchResults() {
        _searchValue.value = ""
        _resultsByType.value = emptyMap()
    }

    /**
     * 输入即预测：防抖 200ms 后打一次综合搜索。
     * 命中歌手/专辑/单曲各取前 3 条，按 精确 > 前缀 > 模糊 排序。
     */
    fun fetchSuggestions(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) {
            suggestJob?.cancel()
            suggestKeyword = ""
            _suggestions.value = SearchSuggestions()
            return
        }
        // 防抖期间不取消上一个已发出的请求之外的任何状态；只延迟发起新请求。
        suggestJob?.cancel()
        suggestJob = viewModelScope.launch {
            delay(200)
            suggestKeyword = trimmed
            val result = SearchApi.search(
                offset = 0,
                limit = SUGGEST_FETCH_LIMIT,
                keyword = trimmed,
                type = SearchType.All
            )
            result.onFailure {
                ApiServerErrorNotifier.notifyServerUnreachable(context)
            }
            // 竞态：请求返回时输入已经变了，直接丢弃这次结果
            if (trimmed != suggestKeyword) return@launch
            _suggestions.value = SearchSuggestions(
                keyword = trimmed,
                artists = result.getOrNull()?.data?.resources.orEmpty()
                    .mapNotNull { it.artist }
                    .sortedBy { it.name.matchRank(trimmed) }
                    .take(SUGGEST_LIMIT),
                albums = result.getOrNull()?.data?.resources.orEmpty()
                    .mapNotNull { it.album }
                    .sortedBy { it.name.matchRank(trimmed) }
                    .take(SUGGEST_LIMIT),
                songs = result.getOrNull()?.data?.resources.orEmpty()
                    .mapNotNull { it.song }
                    .sortedBy { it.name.matchRank(trimmed) }
                    .take(SUGGEST_LIMIT)
            )
        }
    }

    private var resultsJob: Job? = null

    private fun fetchSearchResults() {
        val keyword = _searchValue.value.trim()
        val type = _searchType.value
        if (keyword.isEmpty()) {
            resultsJob?.cancel()
            _resultsByType.value = emptyMap()
            return
        }

        // 取消上一次未完成的请求：否则先发后到的旧结果会覆盖新结果
        resultsJob?.cancel()
        resultsJob = viewModelScope.launch {
            val result = SearchApi.search(
                offset = 0,
                limit = 100,
                keyword = keyword,
                type = type
            )
            // 搜索请求失败 => 已配置的 API 服务器大概率失效/填错，
            // 弹一次节流的 toast 引导用户去设置更换后端（不刷屏）。
            result.onFailure {
                ApiServerErrorNotifier.notifyServerUnreachable(context)
            }
            // 过期响应（关键词或分类已变）直接丢弃
            if (keyword != _searchValue.value.trim() || type != _searchType.value) return@launch
            _resultsByType.value = _resultsByType.value + (type to result.getOrNull()?.data?.resources.orEmpty())
        }
    }

    private fun fetchTopLists() {
        viewModelScope.launch {
            _topLists.value = PlaylistApi.topList().getOrNull()?.list.orEmpty()
        }
    }

    val searchHistory: Flow<List<String>> = getSearchHistory(context).stateIn(
        viewModelScope,
        SharingStarted.Lazily,
        emptyList()
    )

    private fun saveSearch(query: String) {
        viewModelScope.launch {
            saveSearchQuery(context, query)
        }
    }

    private suspend fun saveSearchQuery(context: Context, query: String) {
        context.searchHistoryDataStore.updateData { currentHistory ->
            val historyList = currentHistory.historyList.toMutableList()
            historyList.remove(query)
            historyList.add(0, query)
            if (historyList.size > 10) historyList.removeAt(historyList.lastIndex)
            currentHistory.toBuilder().clearHistory().addAllHistory(historyList).build()
        }
    }

    private fun getSearchHistory(context: Context): Flow<List<String>> {
        return context.searchHistoryDataStore.data.map { it.historyList }
    }

    fun deleteSearchQuery(query: String) {
        viewModelScope.launch {
            removeSearchQuery(context, query)
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            context.searchHistoryDataStore.updateData { currentHistory ->
                currentHistory.toBuilder().clearHistory().build()
            }
        }
    }

    private suspend fun removeSearchQuery(context: Context, query: String) {
        context.searchHistoryDataStore.updateData { currentHistory ->
            val historyList = currentHistory.historyList.toMutableList()
            historyList.remove(query)
            currentHistory.toBuilder().clearHistory().addAllHistory(historyList).build()
        }
    }
}
