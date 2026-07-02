package com.jussicodes.music.viewModel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jussicodes.music.data.searchHistoryDataStore
import com.rcmiku.ncmapi.api.playlist.PlaylistApi
import com.rcmiku.ncmapi.api.search.SearchApi
import com.rcmiku.ncmapi.api.search.SearchType
import com.rcmiku.ncmapi.model.Playlist
import com.rcmiku.ncmapi.model.SearchResources
import com.rcmiku.ncmapi.model.SearchSuggestKeywordResponse
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

@HiltViewModel
class SearchViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _searchType = MutableStateFlow(SearchType.Song)
    val searchType: StateFlow<SearchType> = _searchType.asStateFlow()

    private val _searchValue = MutableStateFlow("")
    private val _searchResults = MutableStateFlow<List<SearchResources>>(emptyList())
    val searchResults: StateFlow<List<SearchResources>> = _searchResults.asStateFlow()

    private val _topLists = MutableStateFlow<List<Playlist>>(emptyList())
    val topLists: StateFlow<List<Playlist>> = _topLists.asStateFlow()

    private val _suggestKeywordResponse = MutableStateFlow<SearchSuggestKeywordResponse?>(null)
    val suggestKeywordResponse: StateFlow<SearchSuggestKeywordResponse?> =
        _suggestKeywordResponse.asStateFlow()
    private var suggestJob: Job? = null

    init {
        fetchTopLists()
    }

    fun updateSearchType(searchType: SearchType) {
        _searchType.value = searchType
        fetchSearchResults()
    }

    fun updateSearchValue(searchValue: String) {
        _searchValue.value = searchValue
        saveSearch(searchValue)
        fetchSearchResults()
    }

    fun fetchSearchKeyword(searchValue: String) {
        suggestJob?.cancel()
        suggestJob = viewModelScope.launch {
            delay(300)
            _suggestKeywordResponse.value = SearchApi.searchSuggestKeyword(searchValue).getOrNull()
        }
    }

    private fun fetchSearchResults() {
        val keyword = _searchValue.value.trim()
        if (keyword.isEmpty()) {
            _searchResults.value = emptyList()
            return
        }

        viewModelScope.launch {
            _searchResults.value = SearchApi.search(
                offset = 0,
                limit = 100,
                keyword = keyword,
                type = _searchType.value
            ).getOrNull()?.data?.resources.orEmpty()
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
