package com.jussicodes.music.viewModel

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jussicodes.music.constants.libraryFavoriteSongCacheKey
import com.jussicodes.music.constants.libraryPlaylistRefreshTokenKey
import com.jussicodes.music.constants.libraryUserInfoCacheKey
import com.jussicodes.music.constants.libraryUserPlaylistsCacheKey
import com.jussicodes.music.constants.pinnedAlbumIdsKey
import com.jussicodes.music.constants.pinnedAlbumsCacheKey
import com.jussicodes.music.constants.userIdKye
import com.jussicodes.music.data.favoriteSongIdsDatastore
import com.jussicodes.music.utils.FavoriteSongSyncBus
import com.jussicodes.music.utils.PlaylistCollectionSyncBus
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
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import javax.inject.Inject

private const val USER_INFO_REFRESH_INTERVAL_MS = 5 * 60 * 1000L
private const val LIKED_PLAYLIST_SPECIAL_TYPE = 5
private const val LIKED_PLAYLIST_NAME_FRAGMENT = "\u559c\u6b22"

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
    private var baseUserPlaylists: List<Playlist> = emptyList()

    private val _pinnedAlbums = MutableStateFlow<List<Album>>(emptyList())
    val pinnedAlbums: StateFlow<List<Album>> = _pinnedAlbums.asStateFlow()
    private val _pinnedAlbumsCacheLoaded = MutableStateFlow(false)
    val pinnedAlbumsCacheLoaded: StateFlow<Boolean> = _pinnedAlbumsCacheLoaded.asStateFlow()
    private var loadedPinnedAlbumIds: List<Long> = emptyList()
    private var lastUserInfoRefreshAt = 0L
    private var lastUserInfoCookieHash = 0
    private var favoriteSongCount = 0
    private val _isAvatarUploading = MutableStateFlow(false)
    val isAvatarUploading: StateFlow<Boolean> = _isAvatarUploading.asStateFlow()
    private val _avatarCacheVersion = MutableStateFlow(0L)
    val avatarCacheVersion: StateFlow<Long> = _avatarCacheVersion.asStateFlow()

    init {
        viewModelScope.launch {
            loadCachedLibrary()
        }
        observeUserIdChanges()
        observePlaylistRefreshToken()
        observePlaylistCollectionEvents()
        observeFavoriteSongEvents()
        observeFavoriteSongIds()
    }

    fun fetchUserInfo(cookie: String? = null, force: Boolean = false) {
        val now = System.currentTimeMillis()
        val cookieHash = cookie?.hashCode() ?: 0
        val shouldUseCurrentInfo = !force &&
            _userInfo.value != null &&
            lastUserInfoCookieHash == cookieHash &&
            now - lastUserInfoRefreshAt < USER_INFO_REFRESH_INTERVAL_MS
        if (shouldUseCurrentInfo) return

        lastUserInfoRefreshAt = now
        lastUserInfoCookieHash = cookieHash
        viewModelScope.launch {
            refreshUserInfo()
        }
    }

    private suspend fun refreshUserInfo() {
        AccountApi.accountInfo().getOrNull()?.let {
            _userInfo.value = it
            context.dataStore.edit { prefs ->
                prefs[libraryUserInfoCacheKey] = json.encodeToString(it)
                prefs[userIdKye] = it.account.profile.userId
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
            val playlists = AccountApi.userPlaylists(userId)
                .getOrNull()
                ?.data
                ?.playlist
                ?.let(PlaylistCollectionSyncBus::mergeInto)
            if (playlists != null) {
                baseUserPlaylists = playlists
                _userPlaylists.value = mergeFavoritePlaylistState(baseUserPlaylists)
                context.dataStore.edit { prefs ->
                    prefs[libraryUserPlaylistsCacheKey] = json.encodeToString(baseUserPlaylists)
                }
            }
        }
    }

    fun uploadAvatar(file: File, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        if (_isAvatarUploading.value) return
        viewModelScope.launch {
            _isAvatarUploading.value = true
            val result = AccountApi.uploadAvatar(file)
            val response = result.getOrNull()
            val success = response?.isSuccess == true
            if (success) {
                _avatarCacheVersion.value = System.currentTimeMillis()
                refreshUserInfo()
            }
            _isAvatarUploading.value = false
            onResult(success, result.exceptionOrNull()?.message ?: response?.errorMessage)
        }
    }

    private fun applyPlaylistCollectionEvent(event: PlaylistCollectionSyncBus.Event) {
        val current = baseUserPlaylists
        val updated = if (event.collected) {
            if (current.any { it.id == event.playlist.id }) current else current + event.playlist
        } else {
            current.filterNot { it.id == event.playlist.id }
        }
        baseUserPlaylists = updated
        _userPlaylists.value = mergeFavoritePlaylistState(baseUserPlaylists)
        viewModelScope.launch {
            context.dataStore.edit { prefs ->
                prefs[libraryUserPlaylistsCacheKey] = json.encodeToString(baseUserPlaylists)
            }
        }
    }

    private fun applyFavoriteSongEvent(event: FavoriteSongSyncBus.Event) {
        _userPlaylists.value = mergeFavoritePlaylistState(baseUserPlaylists)
    }

    private fun isFavoritePlaylist(playlist: Playlist, userId: Long): Boolean {
        return playlist.specialType == LIKED_PLAYLIST_SPECIAL_TYPE ||
            (playlist.creator?.userId == userId && playlist.name.contains(LIKED_PLAYLIST_NAME_FRAGMENT))
    }

    private fun mergeFavoritePlaylistState(playlists: List<Playlist>): List<Playlist> {
        val userId = _userInfo.value?.account?.profile?.userId ?: 0L
        return playlists.map { playlist ->
            if (isFavoritePlaylist(playlist, userId)) {
                FavoriteSongSyncBus.mergeIntoFavoritePlaylist(playlist, favoriteSongCount)
            } else {
                playlist
            }
        }
    }

    fun clear() {
        _userInfo.value = null
        _favoriteSong.value = null
        baseUserPlaylists = emptyList()
        _userPlaylists.value = emptyList()
        lastUserInfoRefreshAt = 0L
        lastUserInfoCookieHash = 0
        viewModelScope.launch {
            context.dataStore.edit { prefs ->
                prefs[userIdKye] = 0L
            }
        }
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
        baseUserPlaylists = decodeCache<List<Playlist>>(prefs[libraryUserPlaylistsCacheKey]).orEmpty()
        _userPlaylists.value = mergeFavoritePlaylistState(baseUserPlaylists)
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
                        baseUserPlaylists = emptyList()
                        _userPlaylists.value = emptyList()
                    } else {
                        fetchFavoriteSong(userId)
                        fetchUserPlaylists(userId)
                    }
                }
        }
    }

    private fun observePlaylistRefreshToken() {
        viewModelScope.launch {
            context.dataStore.data
                .map { it[libraryPlaylistRefreshTokenKey] }
                .distinctUntilChanged()
                .collectLatest {
                    val userId = _userInfo.value?.account?.profile?.userId ?: return@collectLatest
                    fetchUserPlaylists(userId)
                }
        }
    }

    private fun observePlaylistCollectionEvents() {
        viewModelScope.launch {
            PlaylistCollectionSyncBus.events.collectLatest { event ->
                applyPlaylistCollectionEvent(event)
            }
        }
    }

    private fun observeFavoriteSongEvents() {
        viewModelScope.launch {
            FavoriteSongSyncBus.events.collectLatest { event ->
                applyFavoriteSongEvent(event)
            }
        }
    }

    private fun observeFavoriteSongIds() {
        viewModelScope.launch {
            context.favoriteSongIdsDatastore.data
                .map { it.songIdsList.size }
                .distinctUntilChanged()
                .collectLatest { count ->
                    favoriteSongCount = count
                    _userPlaylists.value = mergeFavoritePlaylistState(baseUserPlaylists)
                }
        }
    }
}
