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
        const val FIRST_PAGE_SIZE = 15
        const val PAGE_SIZE = 30
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

    private var hasMoreUsers = false
    private var hasMoreArtists = false
    private var activeUserId: Long = 0L
    private var activeType: UserFollowType? = null
    private var nextUserOffset = 0
    private var nextArtistOffset = 0
    private var fetchJob: Job? = null

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

    fun fetch(userId: Long, type: UserFollowType) {
        fetchJob?.cancel()
        activeUserId = userId
        activeType = type
        hasMoreUsers = false
        hasMoreArtists = false
        nextUserOffset = 0
        nextArtistOffset = 0
        _hasMore.value = false
        fetchJob = viewModelScope.launch {
            _isLoading.value = true
            _isLoadingMore.value = false
            when (type) {
                UserFollowType.ARTISTS -> {
                    _follows.value = null
                    _users.value = emptyList()
                    loadCachedArtists()
                    val result = ArtistApi.artistSublist(offset = 0, limit = FIRST_PAGE_SIZE).getOrNull()
                    if (result != null) {
                        applyArtistPage(result, replace = true)
                        context.dataStore.edit { prefs ->
                            prefs[artistFirstPageCacheKey] = json.encodeToString(result)
                        }
                    }
                }
                UserFollowType.FOLLOWS,
                UserFollowType.FOLLOWEDS -> {
                    val result = fetchUserPage(userId, type, offset = 0)
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
                }
            }
            _isLoading.value = false
        }
    }

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

    fun clear() {
        fetchJob?.cancel()
        activeUserId = 0L
        activeType = null
        hasMoreUsers = false
        hasMoreArtists = false
        nextUserOffset = 0
        nextArtistOffset = 0
        _hasMore.value = false
        _follows.value = null
        _users.value = emptyList()
        _artists.value = emptyList()
        _isLoading.value = false
        _isLoadingMore.value = false
    }

    private suspend fun fetchUserPage(
        userId: Long,
        type: UserFollowType,
        offset: Int
    ): UserFollowResponse? {
        return when (type) {
            UserFollowType.FOLLOWS -> AccountApi.userFollows(userId, limit = PAGE_SIZE, offset = offset)
            UserFollowType.FOLLOWEDS -> AccountApi.userFolloweds(userId, limit = PAGE_SIZE, offset = offset)
            UserFollowType.ARTISTS -> null
        }?.getOrNull()
    }

    private suspend fun loadMoreUsers(userId: Long, type: UserFollowType) {
        if (!hasMoreUsers) return
        _isLoadingMore.value = true
        try {
            val result = fetchUserPage(userId, type, offset = nextUserOffset)
            if (result != null) {
                hasMoreUsers = result.hasMore
                _hasMore.value = hasMoreUsers
                val nextUsers = when (type) {
                    UserFollowType.FOLLOWS -> result.follows
                    UserFollowType.FOLLOWEDS -> result.followeds
                    UserFollowType.ARTISTS -> emptyList()
                }
                nextUserOffset += nextUsers.size
                if (nextUsers.isNotEmpty()) {
                    _users.value = (_users.value + nextUsers).distinctBy { it.id }
                }
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
                applyArtistPage(result, replace = false)
            }
        } finally {
            _isLoadingMore.value = false
        }
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

    private fun applyArtistPage(response: ArtistSublistResponse, replace: Boolean) {
        hasMoreArtists = response.hasMore || response.more
        _hasMore.value = hasMoreArtists
        nextArtistOffset = if (replace) {
            response.data.size
        } else {
            nextArtistOffset + response.data.size
        }
        val artists = if (replace) {
            response.data
        } else {
            _artists.value + response.data
        }
        _artists.value = artists
            .distinctBy { it.id }
            .let(ArtistCollectionSyncBus::mergeInto)
    }
}
