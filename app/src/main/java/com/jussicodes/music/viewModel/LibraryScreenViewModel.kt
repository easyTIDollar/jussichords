package com.jussicodes.music.viewModel

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jussicodes.music.constants.libraryFavoriteSongCacheKey
import com.jussicodes.music.constants.libraryPlaylistRefreshTokenKey
import com.jussicodes.music.constants.libraryUserInfoCacheKey
import com.jussicodes.music.constants.libraryUserPlaylistsCacheKey
import com.jussicodes.music.constants.userIdKye
import com.jussicodes.music.data.favoriteSongIdsDatastore
import com.jussicodes.music.utils.FavoriteSongSyncBus
import com.jussicodes.music.utils.AvatarUploadLimiter
import com.jussicodes.music.utils.PlaylistCollectionSyncBus
import com.jussicodes.music.utils.PlaylistCoverSyncBus
import com.jussicodes.music.utils.dataStore
import com.rcmiku.ncmapi.api.account.AccountApi
import com.rcmiku.ncmapi.api.account.UserPlaylistType
import com.rcmiku.ncmapi.api.playlist.PlaylistApi
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

    fun createPlaylist(name: String, onResult: (Boolean) -> Unit = {}) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            onResult(false)
            return
        }
        viewModelScope.launch {
            val success = PlaylistApi.createPlaylist(trimmedName).isSuccess
            if (success) {
                invalidateUserPlaylistCache()
                refreshUserPlaylists()
            }
            onResult(success)
        }
    }

    fun deletePlaylist(playlist: Playlist, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val previousPlaylists = baseUserPlaylists
            baseUserPlaylists = baseUserPlaylists.filterNot { it.id == playlist.id }
            _userPlaylists.value = mergeFavoritePlaylistState(baseUserPlaylists)
            val success = PlaylistApi.deletePlaylist(playlist.id).isSuccess
            if (success) {
                saveUserPlaylistsCache()
                invalidateUserPlaylistCache()
                refreshUserPlaylists()
            } else {
                baseUserPlaylists = previousPlaylists
                _userPlaylists.value = mergeFavoritePlaylistState(baseUserPlaylists)
            }
            onResult(success)
        }
    }

    fun uncollectPlaylist(playlist: Playlist, onResult: (Boolean) -> Unit = {}) {
        if (playlist.specialType == LIKED_PLAYLIST_SPECIAL_TYPE) {
            onResult(false)
            return
        }
        viewModelScope.launch {
            val previousPlaylists = baseUserPlaylists
            baseUserPlaylists = baseUserPlaylists.filterNot { it.id == playlist.id }
            _userPlaylists.value = mergeFavoritePlaylistState(baseUserPlaylists)
            val success = PlaylistApi.playlistSub(id = playlist.id, isSub = false).isSuccess
            if (success) {
                PlaylistCollectionSyncBus.setCollected(playlist, false)
                saveUserPlaylistsCache()
                invalidateUserPlaylistCache()
                refreshUserPlaylists()
            } else {
                baseUserPlaylists = previousPlaylists
                _userPlaylists.value = mergeFavoritePlaylistState(baseUserPlaylists)
            }
            onResult(success)
        }
    }

    fun updatePlaylistInfo(
        playlist: Playlist,
        name: String,
        description: String,
        onResult: (Boolean) -> Unit = {}
    ) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            onResult(false)
            return
        }
        viewModelScope.launch {
            val previousPlaylists = baseUserPlaylists
            val updatedPlaylist = playlist.copy(name = trimmedName, description = description)
            baseUserPlaylists = baseUserPlaylists.map { item ->
                if (item.id == playlist.id) updatedPlaylist else item
            }
            _userPlaylists.value = mergeFavoritePlaylistState(baseUserPlaylists)

            val nameSuccess = if (playlist.name != trimmedName) {
                PlaylistApi.updatePlaylistName(playlist.id, trimmedName).isSuccess
            } else {
                true
            }
            val descriptionSuccess = if (playlist.description != description) {
                PlaylistApi.updatePlaylistDescription(playlist.id, description).isSuccess
            } else {
                true
            }
            val success = nameSuccess && descriptionSuccess
            if (success) {
                saveUserPlaylistsCache()
                invalidateUserPlaylistCache()
                refreshUserPlaylists()
            } else {
                baseUserPlaylists = previousPlaylists
                _userPlaylists.value = mergeFavoritePlaylistState(baseUserPlaylists)
            }
            onResult(success)
        }
    }

    fun uploadPlaylistCover(playlist: Playlist, file: File, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val success = PlaylistApi.updatePlaylistCover(playlist.id, file).isSuccess
            if (success) {
                PlaylistCoverSyncBus.markUpdated(playlist.id)
                invalidateUserPlaylistCache()
                refreshUserPlaylists()
            }
            onResult(success)
        }
    }

    fun reorderCreatedPlaylists(orderedCreatedPlaylists: List<Playlist>, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val previousPlaylists = baseUserPlaylists
            val orderedIds = orderedCreatedPlaylists.map { it.id }
            val orderedById = orderedCreatedPlaylists.associateBy { it.id }
            val remainingCreated = baseUserPlaylists.filter { item ->
                item.id !in orderedIds && item.creator?.userId == _userInfo.value?.account?.profile?.userId && !item.subscribed
            }
            val others = baseUserPlaylists.filterNot { item ->
                item.id in orderedIds || item in remainingCreated
            }
            baseUserPlaylists = others + orderedIds.mapNotNull(orderedById::get) + remainingCreated
            _userPlaylists.value = mergeFavoritePlaylistState(baseUserPlaylists)
            val success = PlaylistApi.updatePlaylistOrder(orderedIds).isSuccess
            if (success) {
                saveUserPlaylistsCache()
                invalidateUserPlaylistCache()
            } else {
                baseUserPlaylists = previousPlaylists
                _userPlaylists.value = mergeFavoritePlaylistState(baseUserPlaylists)
            }
            onResult(success)
        }
    }

    fun reorderCollectedPlaylists(orderedCollectedPlaylists: List<Playlist>, onResult: (Boolean) -> Unit = {}) {
        // NCM 没有重排"收藏歌单"的接口，这里只做本地内存重排，
        // 不写缓存、不调 NCM API：重启 App 后恢复 NCM 原始顺序。
        val orderedIds = orderedCollectedPlaylists.map { it.id }
        val orderedById = orderedCollectedPlaylists.associateBy { it.id }
        val others = baseUserPlaylists.filterNot { it.id in orderedIds }
        baseUserPlaylists = others + orderedIds.mapNotNull(orderedById::get)
        _userPlaylists.value = mergeFavoritePlaylistState(baseUserPlaylists)
        onResult(true)
    }

    private fun invalidateUserPlaylistCache() {
        AccountApi.invalidateUserPlaylistCache(_userInfo.value?.account?.profile?.userId)
    }

    private fun refreshUserPlaylists() {
        val userId = _userInfo.value?.account?.profile?.userId ?: return
        fetchUserPlaylists(userId)
    }

    private suspend fun saveUserPlaylistsCache() {
        context.dataStore.edit { prefs ->
            prefs[libraryUserPlaylistsCacheKey] = json.encodeToString(baseUserPlaylists)
        }
    }

    fun uploadAvatar(file: File, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        if (_isAvatarUploading.value) return
        if (AvatarUploadLimiter.remaining(context) <= 0) {
            onResult(false, "本周头像上传次数已用完（每周最多 5 次）")
            return
        }
        viewModelScope.launch {
            _isAvatarUploading.value = true
            val result = AccountApi.uploadAvatar(file)
            val response = result.getOrNull()
            val success = response?.isSuccess == true
            if (success) {
                AvatarUploadLimiter.recordSuccess(context)
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

    private suspend fun loadCachedLibrary() {
        val prefs = context.dataStore.data.first()
        _userInfo.value = decodeCache(prefs[libraryUserInfoCacheKey])
        _favoriteSong.value = decodeCache(prefs[libraryFavoriteSongCacheKey])
        baseUserPlaylists = decodeCache<List<Playlist>>(prefs[libraryUserPlaylistsCacheKey]).orEmpty()
        _userPlaylists.value = mergeFavoritePlaylistState(baseUserPlaylists)
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
