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
        // 关注用户/粉丝单页 100（接口支持且已实测）；歌手保持 50（文档默认值）
        const val USER_PAGE_SIZE = 100
        const val ARTIST_PAGE_SIZE = 50
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

    // /user/followeds 接口有 bug：offset/limit 被忽略，永远只返回前 30 条。
    // 当 total(size) > 实际拿到条数时标记受限，UI 显示提示，不再空转分页
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

    // 内存缓存（进入主页 prefetch 填充）：只存第一页，命中后从记录的分页点继续自动加载
    private data class CachedUsers(
        val users: List<SearchUser>,
        val hasMore: Boolean,
        val nextOffset: Int,
        val limited: Boolean
    )

    private data class CachedArtists(
        val artists: List<SearchArtist>,
        val hasMore: Boolean,
        val nextOffset: Int
    )

    private val _userCache = MutableStateFlow<Map<Pair<Long, UserFollowType>, CachedUsers>>(emptyMap())
    private val _artistCache = MutableStateFlow<Map<Long, CachedArtists>>(emptyMap())

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
     * 预加载：进入用户主页时并行拉第一页，命中后列表秒开，后续页仍按需自动加载
     */
    fun prefetch(userId: Long) {
        if (userId <= 0) return
        viewModelScope.launch {
            val followJob = async { AccountApi.userFollows(userId, limit = USER_PAGE_SIZE) }
            val followedsJob = async { AccountApi.userFolloweds(userId, limit = USER_PAGE_SIZE) }
            val artistsJob = async { ArtistApi.artistSublist(offset = 0, limit = ARTIST_PAGE_SIZE) }

            followJob.await().getOrNull()?.let { res ->
                val list = res.follows
                _userCache.value = _userCache.value + (
                    Pair(userId, UserFollowType.FOLLOWS) to
                        CachedUsers(list, res.hasMore, list.size, limited = false)
                    )
            }
            followedsJob.await().getOrNull()?.let { res ->
                val list = res.followeds
                val limited = res.size > list.size
                _userCache.value = _userCache.value + (
                    Pair(userId, UserFollowType.FOLLOWEDS) to
                        CachedUsers(list, res.hasMore && !limited, list.size, limited)
                    )
            }
            // 歌手接口偶发返回空（服务端问题），空结果不写缓存，避免覆盖有效旧缓存
            artistsJob.await().getOrNull()?.let { res ->
                if (res.data.isNotEmpty()) {
                    _artistCache.value = _artistCache.value +
                        (userId to CachedArtists(res.data, res.hasMore, res.data.size))
                    context.dataStore.edit { prefs ->
                        prefs[artistFirstPageCacheKey] = json.encodeToString(res)
                    }
                }
            }
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
                    // 先查内存缓存（prefetch 填充），命中直接从记录的分页点继续
                    _artistCache.value[userId]?.let { cached ->
                        if (cached.artists.isNotEmpty()) {
                            _artists.value = cached.artists
                            nextArtistOffset = cached.nextOffset
                            hasMoreArtists = cached.hasMore
                            _hasMore.value = cached.hasMore
                            _isLoading.value = false
                            return@launch
                        }
                    }
                    // 再查 dataStore 旧缓存（秒开首屏）
                    loadCachedArtists()
                    nextArtistOffset = _artists.value.size
                    if (!_artists.value.isNullOrEmpty()) {
                        hasMoreArtists = true
                        _hasMore.value = true
                    }
                    val result = ArtistApi.artistSublist(offset = 0, limit = ARTIST_PAGE_SIZE).getOrNull()
                    if (result != null) {
                        applyArtistPage(result, replace = true)
                        nextArtistOffset = result.data.size
                        hasMoreArtists = result.hasMore
                        _hasMore.value = hasMoreArtists
                        context.dataStore.edit { prefs ->
                            prefs[artistFirstPageCacheKey] = json.encodeToString(result)
                        }
                    } else if (_artists.value.isNotEmpty()) {
                        // 网络失败：保留旧缓存，继续从缓存条数处分页
                        nextArtistOffset = _artists.value.size
                    }
                }
                UserFollowType.FOLLOWS,
                UserFollowType.FOLLOWEDS -> {
                    // 先查内存缓存（prefetch 填充），命中直接从记录的分页点继续
                    _userCache.value[Pair(userId, type)]?.let { cached ->
                        _users.value = cached.users
                        nextUserOffset = cached.nextOffset
                        hasMoreUsers = cached.hasMore
                        _isFollowedsLimited.value = cached.limited
                        _hasMore.value = hasMoreUsers
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
                    val pageUsers = when (type) {
                        UserFollowType.FOLLOWS -> result?.follows.orEmpty()
                        UserFollowType.FOLLOWEDS -> result?.followeds.orEmpty()
                        UserFollowType.ARTISTS -> emptyList()
                    }
                    nextUserOffset = pageUsers.size
                    _users.value = pageUsers
                    hasMoreUsers = result?.hasMore == true
                    // followeds 接口 bug：total(size) > 返回条数时停止分页并提示
                    if (type == UserFollowType.FOLLOWEDS && result != null && result.size > pageUsers.size) {
                        hasMoreUsers = false
                        _isFollowedsLimited.value = true
                    }
                    _hasMore.value = hasMoreUsers
                    if (result != null) {
                        _userCache.value = _userCache.value + (
                            Pair(userId, type) to
                                CachedUsers(
                                    users = pageUsers,
                                    hasMore = hasMoreUsers,
                                    nextOffset = nextUserOffset,
                                    limited = _isFollowedsLimited.value
                                )
                            )
                    }
                }
            }
            _isLoading.value = false
        }
    }

    /**
     * 滑到底部自动触发（也可由手动按钮调用），加载下一页
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
     * 下拉刷新：清掉对应缓存后重新拉取
     */
    fun refresh(userId: Long, type: UserFollowType) {
        if (userId <= 0) return
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
                // followeds bug 防护：offset 被接口忽略时，返回的仍是同一批数据 → 停止
                if (type == UserFollowType.FOLLOWEDS && nextUserOffset > 0) {
                    val existingIds = _users.value.mapTo(HashSet()) { it.id }
                    if (nextUsers.isEmpty() || nextUsers.all { it.id in existingIds }) {
                        hasMoreUsers = false
                        _hasMore.value = false
                        _isFollowedsLimited.value = true
                        return
                    }
                }
                nextUserOffset += nextUsers.size
                val merged = (_users.value + nextUsers).distinctBy { it.id }
                _users.value = merged
                hasMoreUsers = result.hasMore
                _hasMore.value = hasMoreUsers
                _userCache.value = _userCache.value + (
                    Pair(userId, type) to
                        CachedUsers(
                            users = merged,
                            hasMore = hasMoreUsers,
                            nextOffset = nextUserOffset,
                            limited = type == UserFollowType.FOLLOWEDS && result.size > merged.size
                        )
                    )
            }
        } finally {
            _isLoadingMore.value = false
        }
    }

    private suspend fun loadMoreArtists() {
        if (!hasMoreArtists) return
        _isLoadingMore.value = true
        try {
            val result = ArtistApi.artistSublist(offset = nextArtistOffset, limit = ARTIST_PAGE_SIZE).getOrNull()
            if (result != null) {
                nextArtistOffset += result.data.size
                applyArtistPage(result)
                hasMoreArtists = result.hasMore
                _hasMore.value = hasMoreArtists
                if (activeUserId > 0) {
                    _artistCache.value = _artistCache.value + (
                        activeUserId to
                            CachedArtists(_artists.value, hasMoreArtists, nextArtistOffset)
                        )
                    context.dataStore.edit { prefs ->
                        prefs[artistFirstPageCacheKey] = json.encodeToString(result)
                    }
                }
            }
        } finally {
            _isLoadingMore.value = false
        }
    }

    private fun applyArtistPage(result: ArtistSublistResponse, replace: Boolean = false) {
        _artists.value = if (replace) result.data else _artists.value + result.data
    }

    private suspend fun fetchUserPage(userId: Long, type: UserFollowType, offset: Int): Result<UserFollowResponse> =
        when (type) {
            UserFollowType.FOLLOWS -> AccountApi.userFollows(userId, limit = USER_PAGE_SIZE, offset = offset)
            UserFollowType.FOLLOWEDS -> AccountApi.userFolloweds(userId, limit = USER_PAGE_SIZE, offset = offset)
            UserFollowType.ARTISTS -> Result.failure(
                IllegalArgumentException("Artists are not user follow pages")
            )
        }

    private suspend fun loadCachedArtists() {
        val cached = context.dataStore.data.first()[artistFirstPageCacheKey]
            ?.takeIf { it.isNotBlank() }
            ?.let { cache ->
                runCatching { json.decodeFromString<ArtistSublistResponse>(cache) }.getOrNull()
            }
            ?: return
        applyArtistPage(cached, replace = true)
    }
}
