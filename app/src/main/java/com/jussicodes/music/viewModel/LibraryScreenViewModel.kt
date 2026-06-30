package com.jussicodes.music.viewModel

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jussicodes.music.constants.libraryFavoriteSongCacheKey
import com.jussicodes.music.constants.libraryUserInfoCacheKey
import com.jussicodes.music.constants.libraryUserPlaylistsCacheKey
import com.jussicodes.music.constants.pinnedAlbumIdsKey
import com.jussicodes.music.constants.pinnedAlbumsCacheKey
import com.jussicodes.music.utils.dataStore
import com.rcmiku.ncmapi.api.account.AccountApi
import com.rcmiku.ncmapi.api.account.UserPlaylistType
import com.rcmiku.ncmapi.api.album.AlbumApi
import com.rcmiku.ncmapi.model.Album
import com.rcmiku.ncmapi.model.FavoriteSongResponse
import com.rcmiku.ncmapi.model.Playlist
import com.rcmiku.ncmapi.model.UserInfoBatch
import com.rcmiku.ncmapi.utils.json
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import javax.inject.Inject

@HiltViewModel
class LibraryScreenViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _userInfo = MutableStateFlow<UserInfoBatch?>(null)
    val userInfo: StateFlow<UserInfoBatch?> = _userInfo.asStateFlow()

    private val _favoriteSong = MutableStateFlow<FavoriteSongResponse?>(null)
    val favoriteSong: StateFlow<FavoriteSongResponse?> = _favoriteSong.asStateFlow()

    private val _userPlaylists = MutableStateFlow<List<Playlist>>(emptyList())
    val userPlaylists: StateFlow<List<Playlist>> = _userPlaylists.asStateFlow()

    private val _pinnedAlbums = MutableStateFlow<List<Album>>(emptyList())
    val pinnedAlbums: StateFlow<List<Album>> = _pinnedAlbums.asStateFlow()
    private val _pinnedAlbumsCacheLoaded = MutableStateFlow(false)
    val pinnedAlbumsCacheLoaded: StateFlow<Boolean> = _pinnedAlbumsCacheLoaded.asStateFlow()
    private var loadedPinnedAlbumIds: List<Long> = emptyList()

    init {
        viewModelScope.launch {
            loadCachedLibrary()
        }
        observeUserIdChanges()
    }

    fun fetchUserInfo() {
        viewModelScope.launch {
            AccountApi.accountInfo().getOrNull()?.let {
                _userInfo.value = it
                context.dataStore.edit { prefs ->
                    prefs[libraryUserInfoCacheKey] = json.encodeToString(it)
                }
            }
        }
    }

    private fun fetchFavoriteSong(userId: Long) {
        viewModelScope.launch {
            AccountApi.favoriteSong(userId).getOrNull()?.let {
                _favoriteSong.value = it
                context.dataStore.edit { prefs ->
                    prefs[libraryFavoriteSongCacheKey] = json.encodeToString(it)
                }
            }
        }
    }

    private fun fetchUserPlaylists(userId: Long) {
        viewModelScope.launch {
            val playlists = AccountApi.userPlaylist(
                userId = userId,
                userPlaylistType = UserPlaylistType.CREATE
            ).getOrNull()?.data?.playlist
            if (playlists != null) {
                _userPlaylists.value = playlists
                context.dataStore.edit { prefs ->
                    prefs[libraryUserPlaylistsCacheKey] = json.encodeToString(playlists)
                }
            }
        }
    }

    fun clear() {
        _userInfo.value = null
        _favoriteSong.value = null
        _userPlaylists.value = emptyList()
    }

    fun fetchPinnedAlbums(albumIds: List<Long>) {
        if (!_pinnedAlbumsCacheLoaded.value) return
        if (albumIds == loadedPinnedAlbumIds && _pinnedAlbums.value.isNotEmpty()) return
        loadedPinnedAlbumIds = albumIds
        viewModelScope.launch {
            if (albumIds.isEmpty()) {
                _pinnedAlbums.value = emptyList()
                context.dataStore.edit {
                    it[pinnedAlbumsCacheKey] = ""
                }
                return@launch
            }
            val albums = albumIds.mapNotNull { albumId ->
                AlbumApi.albumDetail(albumId).getOrNull()?.album
            }
            if (albums.isNotEmpty()) {
                _pinnedAlbums.value = albums
                context.dataStore.edit {
                    it[pinnedAlbumsCacheKey] = json.encodeToString(albums)
                }
            }
        }
    }

    private suspend fun loadCachedLibrary() {
        val prefs = context.dataStore.data.first()
        _userInfo.value = decodeCache(prefs[libraryUserInfoCacheKey])
        _favoriteSong.value = decodeCache(prefs[libraryFavoriteSongCacheKey])
        _userPlaylists.value = decodeCache<List<Playlist>>(prefs[libraryUserPlaylistsCacheKey]).orEmpty()
        val cachedPinnedAlbums = decodeCache<List<Album>>(prefs[pinnedAlbumsCacheKey]).orEmpty()
        _pinnedAlbums.value = cachedPinnedAlbums
        loadedPinnedAlbumIds = when {
            cachedPinnedAlbums.isNotEmpty() -> cachedPinnedAlbums.map { it.id }
            else -> prefs[pinnedAlbumIdsKey]
                .orEmpty()
                .split(",")
                .mapNotNull { it.toLongOrNull() }
        }
        _pinnedAlbumsCacheLoaded.value = true
    }

    private inline fun <reified T> decodeCache(cache: String?): T? {
        return runCatching {
            cache?.takeIf { it.isNotBlank() }?.let { json.decodeFromString<T>(it) }
        }.getOrNull()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeUserIdChanges() {
        viewModelScope.launch {
            userInfo
                .mapLatest { it?.account?.profile?.userId }
                .distinctUntilChanged()
                .collectLatest { userId ->
                    if (userId == null) {
                        _favoriteSong.value = null
                        _userPlaylists.value = emptyList()
                    } else {
                        fetchFavoriteSong(userId)
                        fetchUserPlaylists(userId)
                    }
                }
        }
    }
}
