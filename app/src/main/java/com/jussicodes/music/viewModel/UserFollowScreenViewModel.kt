package com.jussicodes.music.viewModel

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jussicodes.music.utils.ArtistCollectionSyncBus
import com.jussicodes.music.utils.dataStore
import com.rcmiku.ncmapi.api.account.AccountApi
import com.rcmiku.ncmapi.api.artist.ArtistApi
import com.rcmiku.ncmapi.model.ArtistSublistResponse
import com.rcmiku.ncmapi.model.SearchArtist
import com.rcmiku.ncmapi.model.SearchUser
import com.rcmiku.ncmapi.model.UserFollowResponse
import com.rcmiku.ncmapi.utils.json
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import javax.inject.Inject

enum class UserFollowType {
    FOLLOWS,
    FOLLOWEDS,
    ARTISTS
}

@HiltViewModel
class UserFollowScreenViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {
    private companion object {
        const val FIRST_PAGE_SIZE = 100
        const val PAGE_SIZE = 100
        val artistFirstPageCacheKey = stringPreferencesKey("artistFirstPageCache")
    }

    private val _follows = MutableStateFlow<UserFollowResponse?>(null)
    val follows: StateFlow<UserFollowResponse?> = _follows.asStateFlow()

    private val _users = MutableStateFlow<List<SearchUser>>(emptyList())
    val users: StateFlow<List<SearchUser>> = _users.asStateFlow()

    private val _artists = MutableStateFlow<List<SearchArtist>>(emptyList())
    val artists: StateFlow<List<SearchArtist>> = _artists.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    // followeds API 有 bug，可能永远 more=true，用一个特殊标记提示
    private val _isFollowedsLimited = MutableStateFlow(false)
    val isFollowedsLimited: StateFlow<Boolean> = _isFollowedsLimited.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var hasMoreUsers = false
    private var hasMoreArtists = false
    private var activeUserId: Long = 0L
    private var activeType: UserFollowType? = null
    private var nextUserOffset = 0
    private var nextArtistOffset = 0
    private var fetchJob: Job? = null

    // 内存缓存：按 userId + type 存储已加载数据，避免重复请求
    private val _userCache = MutableStateFlow<Map<Pair<Long, UserFollowType>, List<SearchUser>>>(emptyMap())
    private val _artistCache = MutableStateFlow<Map<Long, List<SearchArtist>>>(emptyMap())

    init {
        viewModelScope.launch {
            ArtistCollectionSyncBus.events.collect { event ->
                _artists.value = if (event.collected) {
                    if (_artists.value.any { it.id == event.artist.id }) {
                        _artists.value
                    } else {
                        _artists.value + event.artist
                    }
                } else {
                    _artists.value.filterNot { it.id == event.artist.id }
                }
            }
        }
    }

    /**
     * 预加载：进入用户主页时并行拉取三张表存入缓存
     */
    fun prefetch(userId: Long) {
        if (userId <= 0) return
        viewModelScope.launch {
            val followJob = async { AccountApi.userFollows(userId, limit = PAGE_SIZE) }
            val followedsJob = async { AccountApi.userFolloweds(userId, limit = PAGE_SIZE) }
            val artistsJob = async { ArtistApi.artistSublist(offset = 0, limit = PAGE_SIZE) }

            try {
                val followsResult = followJob.await().getOrNull()
                followsResult?.follows?.let {
                    _userCache.value = _userCache.value + (Pair(userId, UserFollowType.FOLLOWS) to it)
                }
            } catch (_: Exception) {}
            try {
                val followedsResult = followedsJob.await().getOrNull()
                followedsResult?.followeds?.let {
                    _userCache.value = _userCache.value + (Pair(userId, UserFollowType.FOLLOWEDS) to it)
                }
            } catch (_: Exception) {}
            try {
                val artistsResult = artistsJob.await().getOrNull()
                artistsResult?.data?.let {
                    _artistCache.value = _artistCache.value + (userId to it)
                }
                // 同时存第一页到 dataStore
                if (artistsResult != null) {
                    context.dataStore.edit { prefs ->
                        prefs[artistFirstPageCacheKey] = json.encodeToString(artistsResult)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun fetch(userId: Long, type: UserFollowType) {
        fetchJob?.cancel()
        activeUserId = userId
        activeType = type
        hasMoreUsers = false
        hasMoreArtists = false
        nextUserOffset = 0
        nextArtistOffset = 0
        _hasMore.value = false
        _isFollowedsLimited.value = false
        fetchJob = viewModelScope.launch {
            _isLoading.value = true
            _isLoadingMore.value = false
            _errorMessage.value = null
            when (type) {
                UserFollowType.ARTISTS -> {
                    _follows.value = null
                    _users.value = emptyList()
                    // 先查缓存
                    val cachedArtists = _artistCache.value[userId]
                    if (cachedArtists != null) {
                        _artists.value = cachedArtists
                        hasMoreArtists = false
                        _hasMore.value = false
                        _isLoading.value = false
                        return@launch
                    }
                    // 查 dataStore 旧缓存
                    loadCachedArtists()
                    if (!_artists.value.isNullOrEmpty()) {
                        _hasMore.value = true // 旧缓存不完整，标记还有更多
                    }
                    val result = ArtistApi.artistSublist(offset = nextArtistOffset, limit = FIRST_PAGE_SIZE).getOrNull()
                    if (result != null) {
                        applyArtistPage(result, replace = true)
                        context.dataStore.edit { prefs ->
                            prefs[artistFirstPageCacheKey] = json.encodeToString(result)
                        }
                    }
                }
                UserFollowType.FOLLOWS,
                UserFollowType.FOLLOWEDS -> {
                    // 先查缓存
                    val cachedUsers = _userCache.value[Pair(userId, type)]
                    if (cachedUsers != null) {
                        _users.value = cachedUsers
                        hasMoreUsers = false
                        _hasMore.value = false
                        _follows.value = null
                        _artists.value = emptyList()
                        _isLoading.value = false
                        return@launch
                    }
                    val pageResult = fetchUserPage(userId, type, offset = 0)
                    val result = pageResult.getOrNull()
                    if (pageResult.isFailure) {
                        val detail = pageResult.exceptionOrNull()?.message.orEmpty()
                        _errorMessage.value = if (
                            detail.contains("未公开") ||
                            detail.contains("隐私") ||
                            detail.contains("权限")
                        ) {
                            "对方未公开关注列表"
                        } else {
                            "关注列表加载失败，请稍后重试"
                        }
                    }
                    _follows.value = result
                    _artists.value = emptyList()
                    hasMoreUsers = result?.hasMore == true
                    _hasMore.value = hasMoreUsers
                    val pageUsers = when (type) {
                        UserFollowType.FOLLOWS -> result?.follows.orEmpty()
                        UserFollowType.FOLLOWEDS -> result?.followeds.orEmpty()
                        UserFollowType.ARTISTS -> emptyList()
                    }
                    nextUserOffset = pageUsers.size
                    _users.value = pageUsers

                    // followeds API bug 处理：如果 size > len(followeds) 且 more=true，说明 API 不支持分页
                    if (type == UserFollowType.FOLLOWEDS && result?.size != null && result.size > pageUsers.size) {
                        hasMoreUsers = false
                        _hasMore.value = false
                        _isFollowedsLimited.value = true
                    }
                }
            }
            _isLoading.value = false
        }
    }

    /**
     * 自动加载下一页（滑到底部触发，不需要手动点击）
     */
    fun loadMore() {
        val type = activeType ?: return
        if (_isLoading.value || _isLoadingMore.value) {
            return
        }
        viewModelScope.launch {
            when (type) {
                UserFollowType.ARTISTS -> loadMoreArtists()
                UserFollowType.FOLLOWS,
                UserFollowType.FOLLOWEDS -> {
                    val userId = activeUserId.takeIf { it > 0 } ?: return@launch
                    loadMoreUsers(userId, type)
                }
            }
        }
    }

    /**
     * 下滑刷新：清空对应缓存并重新拉取
     */
    fun refresh(userId: Long, type: UserFollowType) {
        if (userId <= 0) return
        // 清除该类型缓存
        _userCache.value = _userCache.value - Pair(userId, type)
        if (type == UserFollowType.ARTISTS) {
            _artistCache.value = _artistCache.value - userId
        }
        fetch(userId, type)
    }

    fun clear() {
        fetchJob?.cancel()
        activeUserId = 0L
        activeType = null
        hasMoreUsers = false
        hasMoreArtists = false
        nextUserOffset = 0
        nextArtistOffset = 0
        _hasMore.value = false
        _errorMessage.value = null
        _follows.value = null
        _users.value = emptyList()
        _artists.value = emptyList()
        _isLoading.value = false
        _isLoadingMore.value = false
        _isFollowedsLimited.value = false
    }

    private suspend fun loadMoreUsers(userId: Long, type: UserFollowType) {
        if (!hasMoreUsers) return
        _isLoadingMore.value = true
        try {
            val result = fetchUserPage(userId, type, offset = nextUserOffset).getOrNull()
            if (result != null) {
                val nextUsers = when (type) {
                    UserFollowType.FOLLOWS -> result.follows
                    UserFollowType.FOLLOWEDS -> result.followeds
                    UserFollowType.ARTISTS -> emptyList()
                }
                // API bug 防护：followeds 返回的数据与已加载的完全重复 → 停止分页
                if (type == UserFollowType.FOLLOWEDS && nextUserOffset > 0) {
                    val existingIds = _users.value.map { it.id }.toSet()
                    val newIds = nextUsers.map { it.id }.toSet()
                    if (newIds.isEmpty() || newIds.all { it in existingIds }) {
                        hasMoreUsers = false
                        _hasMore.value = false
                        _isFollowedsLimited.value = true
                        return
                    }
                }
                nextUserOffset += nextUsers.size
                _users.value = (_users.value + nextUsers).distinctBy { it.id }
                hasMoreUsers = result.hasMore
                _hasMore.value = hasMoreUsers
            }
        } finally {
            _isLoadingMore.value = false
        }
    }

    private suspend fun loadMoreArtists() {
        if (!hasMoreArtists) return
        _isLoadingMore.value = true
        try {
            val result = ArtistApi.artistSublist(offset = nextArtistOffset, limit = PAGE_SIZE).getOrNull()
            if (result != null) {
                nextArtistOffset += result.data.size
                applyArtistPage(result)
                hasMoreArtists = result.hasMore
                _hasMore.value = hasMoreArtists
                context.dataStore.edit { prefs ->
                    prefs[artistFirstPageCacheKey] = json.encodeToString(result)
                }
            }
        } finally {
            _isLoadingMore.value = false
        }
    }

    private fun applyArtistPage(result: ArtistSublistResponse, replace: Boolean = false) {
        if (replace) {
            _artists.value = result.data
        } else {
            _artists.value = _artists.value + result.data
        }
    }

    private suspend fun fetchUserPage(userId: Long, type: UserFollowType, offset: Int) =
        when (type) {
            UserFollowType.FOLLOWS -> AccountApi.userFollows(userId, limit = PAGE_SIZE, offset = offset)
            UserFollowType.FOLLOWEDS -> AccountApi.userFolloweds(userId, limit = PAGE_SIZE, offset = offset)
            UserFollowType.ARTISTS -> throw IllegalArgumentException("Invalid type for fetchUserPage")
        }

    private suspend fun loadCachedArtists() {
        try {
            val cached: ArtistSublistResponse? = context.dataStore.preferences
                .first()[artistFirstPageCacheKey]?.let { json.decodeFromString(it) }
            _artists.value = cached?.data ?: emptyList()
        } catch (_: Exception) {
            _artists.value = emptyList()
        }
    }
}
