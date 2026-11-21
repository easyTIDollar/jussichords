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
import com.jussicodes.music.data.HomeUserInfoStore
import com.jussicodes.music.utils.FavoriteSongSyncBus
import com.jussicodes.music.utils.AvatarUploadLimiter
import com.jussicodes.music.utils.PlaylistCollectionSyncBus
import com.jussicodes.music.utils.PlaylistCoverSyncBus
import com.jussicodes.music.utils.dataStore
import com.rcmiku.ncmapi.api.account.AccountApi
import com.rcmiku.ncmapi.api.account.UserPlaylistType
import com.rcmiku.ncmapi.api.apiGet
import com.rcmiku.ncmapi.api.artist.ArtistApi
import com.rcmiku.ncmapi.api.playlist.PlaylistApi
import com.rcmiku.ncmapi.model.ApiCodeResponse
import com.rcmiku.ncmapi.model.FavoriteSongResponse
import com.rcmiku.ncmapi.model.Playlist
import com.rcmiku.ncmapi.model.UserInfoBatch
import com.rcmiku.ncmapi.model.UserDetailResponse
import com.rcmiku.ncmapi.utils.CookieProvider
import com.rcmiku.ncmapi.utils.json
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import javax.inject.Inject

private const val USER_INFO_REFRESH_INTERVAL_MS = 5 * 60 * 1000L
private const val PLAYLIST_REFRESH_INTERVAL_MS = 15_000L
private const val LIKED_PLAYLIST_SPECIAL_TYPE = 5
private const val LIKED_PLAYLIST_NAME_FRAGMENT = "\u559c\u6b22"

@HiltViewModel
class LibraryScreenViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {
    data class HomeState(
        val userInfo: UserInfoBatch? = null,
        val favoriteSong: FavoriteSongResponse? = null,
        val userPlaylists: List<Playlist> = emptyList()
    )

    private val _homeState = MutableStateFlow(HomeState())
    val homeState: StateFlow<HomeState> = _homeState.asStateFlow()
    private val _initialDataPublished = MutableStateFlow(false)
    val initialDataPublished: StateFlow<Boolean> = _initialDataPublished.asStateFlow()
    private var cacheLoadGeneration = 0
    private var baseUserPlaylists: List<Playlist> = emptyList()

    private var userInfoJob: Job? = null
    private var lastUserInfoRefreshAt = 0L
    private var lastUserInfoCookieHash = 0
    private var lastUserDetailUid = 0L
    private var lastPlaylistFetchUid = 0L
    private var lastPlaylistFetchAt = 0L
    private var lastFavoriteSongFetchUid = 0L
    private var lastFavoriteSongFetchAt = 0L
    private val _userDetail = MutableStateFlow<UserDetailResponse?>(null)
    val userDetail: StateFlow<UserDetailResponse?> = _userDetail.asStateFlow()
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
        if (!CookieProvider.isLoggedIn()) return
        val now = System.currentTimeMillis()
        val cookieHash = cookie?.hashCode() ?: 0
        if (!force && userInfoJob?.isActive == true && lastUserInfoCookieHash == cookieHash) return
        val shouldUseCurrentInfo = !force &&
            _homeState.value.userInfo != null &&
            lastUserInfoCookieHash == cookieHash &&
            now - lastUserInfoRefreshAt < USER_INFO_REFRESH_INTERVAL_MS
        if (shouldUseCurrentInfo) return

        lastUserInfoRefreshAt = now
        lastUserInfoCookieHash = cookieHash
        userInfoJob = viewModelScope.launch {
            refreshUserInfo()
        }
    }

    private suspend fun refreshUserInfo() {
        if (!CookieProvider.isLoggedIn()) return
        val cookieHash = CookieProvider.cookie.hashCode()
        val generation = cacheLoadGeneration
        val info = withContext(Dispatchers.IO) {
            AccountApi.accountInfo().getOrNull()
        }
        if (!CookieProvider.isLoggedIn() || CookieProvider.cookie.hashCode() != cookieHash ||
            cacheLoadGeneration != generation
        ) return
        info?.let {
            _homeState.update { state -> state.copy(userInfo = it) }
            withContext(Dispatchers.IO) {
                context.dataStore.edit { prefs ->
                    if (!CookieProvider.isLoggedIn() || CookieProvider.cookie.hashCode() != cookieHash ||
                        cacheLoadGeneration != generation
                    ) return@edit
                    prefs[libraryUserInfoCacheKey] = json.encodeToString(it)
                    prefs[userIdKye] = it.account.profile.userId
                }
            }
            if (!CookieProvider.isLoggedIn() || CookieProvider.cookie.hashCode() != cookieHash ||
                cacheLoadGeneration != generation
            ) return
            HomeUserInfoStore.publishUserInfo(it)
            fetchUserDetail(it.account.profile.userId)
        }
    }

    /** 关注/粉丝数：/user/account 不带，走 /user/detail；失败保持上值，UI 侧无数据时不显示数字。 */
    private fun fetchUserDetail(userId: Long) {
        if (!CookieProvider.isLoggedIn() || userId <= 0L || userId == lastUserDetailUid) return
        lastUserDetailUid = userId
        viewModelScope.launch {
            val detail = withContext(Dispatchers.IO) {
                ArtistApi.userDetail(userId).getOrNull()
            }
            _userDetail.value = detail
            if (detail != null) HomeUserInfoStore.publishUserDetail(detail)
        }
    }

    private fun fetchFavoriteSong(userId: Long) {
        viewModelScope.launch {
            if (!CookieProvider.isLoggedIn() || userId <= 0L) return@launch
            val now = System.currentTimeMillis()
            if (userId == lastFavoriteSongFetchUid && now - lastFavoriteSongFetchAt < PLAYLIST_REFRESH_INTERVAL_MS) {
                return@launch
            }
            val favoriteSong = withContext(Dispatchers.IO) {
                AccountApi.favoriteSong(userId).getOrNull()
            }
            favoriteSong?.let {
                lastFavoriteSongFetchUid = userId
                lastFavoriteSongFetchAt = System.currentTimeMillis()
                _homeState.update { state -> state.copy(favoriteSong = it) }
                withContext(Dispatchers.IO) {
                    context.dataStore.edit { prefs ->
                        prefs[libraryFavoriteSongCacheKey] = json.encodeToString(it)
                    }
                }
            }
        }
    }

    private fun fetchUserPlaylists(userId: Long) {
        viewModelScope.launch {
            if (!CookieProvider.isLoggedIn() || userId <= 0L) return@launch
            val now = System.currentTimeMillis()
            if (userId == lastPlaylistFetchUid && now - lastPlaylistFetchAt < PLAYLIST_REFRESH_INTERVAL_MS) {
                return@launch
            }
            val playlists = withContext(Dispatchers.IO) {
                AccountApi.userPlaylists(userId)
                    .getOrNull()
                    ?.data
                    ?.playlist
                    ?.let(PlaylistCollectionSyncBus::mergeInto)
            }
            if (playlists != null) {
                val merged = preparePlaylistState(playlists, userId, Dispatchers.IO)
                lastPlaylistFetchUid = userId
                lastPlaylistFetchAt = System.currentTimeMillis()
                baseUserPlaylists = playlists
                _homeState.update { state -> state.copy(userPlaylists = merged) }
                withContext(Dispatchers.IO) {
                    context.dataStore.edit { prefs ->
                        prefs[libraryUserPlaylistsCacheKey] = json.encodeToString(playlists)
                    }
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
            _homeState.update { state -> state.copy(userPlaylists = mergeFavoritePlaylistState(baseUserPlaylists)) }
            val success = PlaylistApi.deletePlaylist(playlist.id).isSuccess
            if (success) {
                saveUserPlaylistsCache()
                invalidateUserPlaylistCache()
                refreshUserPlaylists()
            } else {
                baseUserPlaylists = previousPlaylists
                _homeState.update { state -> state.copy(userPlaylists = mergeFavoritePlaylistState(baseUserPlaylists)) }
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
            _homeState.update { state -> state.copy(userPlaylists = mergeFavoritePlaylistState(baseUserPlaylists)) }
            val success = PlaylistApi.playlistSub(id = playlist.id, isSub = false).isSuccess
            if (success) {
                PlaylistCollectionSyncBus.setCollected(playlist, false)
                saveUserPlaylistsCache()
                invalidateUserPlaylistCache()
                refreshUserPlaylists()
            } else {
                baseUserPlaylists = previousPlaylists
                _homeState.update { state -> state.copy(userPlaylists = mergeFavoritePlaylistState(baseUserPlaylists)) }
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
            _homeState.update { state -> state.copy(userPlaylists = mergeFavoritePlaylistState(baseUserPlaylists)) }

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
                _homeState.update { state -> state.copy(userPlaylists = mergeFavoritePlaylistState(baseUserPlaylists)) }
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
                item.id !in orderedIds && item.creator?.userId == _homeState.value.userInfo?.account?.profile?.userId && !item.subscribed
            }
            val others = baseUserPlaylists.filterNot { item ->
                item.id in orderedIds || item in remainingCreated
            }
            baseUserPlaylists = others + orderedIds.mapNotNull(orderedById::get) + remainingCreated
            _homeState.update { state -> state.copy(userPlaylists = mergeFavoritePlaylistState(baseUserPlaylists)) }
            val success = PlaylistApi.updatePlaylistOrder(orderedIds).isSuccess
            if (success) {
                saveUserPlaylistsCache()
                invalidateUserPlaylistCache()
            } else {
                baseUserPlaylists = previousPlaylists
                _homeState.update { state -> state.copy(userPlaylists = mergeFavoritePlaylistState(baseUserPlaylists)) }
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
        _homeState.update { state -> state.copy(userPlaylists = mergeFavoritePlaylistState(baseUserPlaylists)) }
        onResult(true)
    }

    private fun invalidateUserPlaylistCache() {
        AccountApi.invalidateUserPlaylistCache(_homeState.value.userInfo?.account?.profile?.userId)
    }

    private fun refreshUserPlaylists() {
        val userId = _homeState.value.userInfo?.account?.profile?.userId ?: return
        lastPlaylistFetchAt = 0L
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

    /**
     * 修改我的页昵称/签名。直连 NCM /user/update 路由（eapi + 合成 cookie）。
     * 成功即乐观更新本地 userInfo + 签名，并强制刷新一次保证同步；失败回滚并给出 NCM 原文。
     * @return 通过 onResult(success, errorMessage) 回传，errorMessage 为 NCM message（如 250 受理失败文案）。
     */
    fun updateProfile(
        nickname: String,
        signature: String,
        onResult: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        val trimmed = nickname.trim()
        if (trimmed.isEmpty()) {
            onResult(false, "昵称不能为空")
            return
        }
        val previous = _homeState.value.userInfo
        // 只发起请求；成功刷新（refreshUserInfo 会重拉并更新首页状态），失败回滚到提交前快照。
        viewModelScope.launch {
            val resp: Result<ApiCodeResponse> =
                apiGet<ApiCodeResponse>(
                    "/user/update",
                    mapOf(
                        "nickname" to trimmed,
                        "signature" to signature
                    )
                )
            val body = resp.getOrNull()
            val ok = resp.isSuccess && body?.code == 200
            if (ok) {
                refreshUserInfo()
                onResult(true, null)
            } else {
                if (previous != null) _homeState.update { it.copy(userInfo = previous) }
                val msg = body?.message ?: body?.msg
                    ?: resp.exceptionOrNull()?.message ?: "更新失败"
                onResult(false, msg)
            }
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
        _homeState.update { state -> state.copy(userPlaylists = mergeFavoritePlaylistState(baseUserPlaylists)) }
        viewModelScope.launch {
            context.dataStore.edit { prefs ->
                prefs[libraryUserPlaylistsCacheKey] = json.encodeToString(baseUserPlaylists)
            }
        }
    }

    private fun applyFavoriteSongEvent(event: FavoriteSongSyncBus.Event) {
        _homeState.update { state -> state.copy(userPlaylists = mergeFavoritePlaylistState(baseUserPlaylists)) }
    }

    private fun isFavoritePlaylist(playlist: Playlist, userId: Long): Boolean {
        // 必须是"自己的"我喜欢的歌：收藏别人(specialType=5)的歌单时 creator 是对方，
        // 不能当成本地喜欢的歌去覆盖数量/叠加本地喜欢列表。
        val isOwner = playlist.creator?.userId == userId
        return isOwner && (playlist.specialType == LIKED_PLAYLIST_SPECIAL_TYPE ||
            playlist.name.contains(LIKED_PLAYLIST_NAME_FRAGMENT))
    }

    private fun mergeFavoritePlaylistState(
        playlists: List<Playlist>,
        userId: Long = _homeState.value.userInfo?.account?.profile?.userId ?: 0L,
        songCount: Int = favoriteSongCount
    ): List<Playlist> {
        return playlists.map { playlist ->
            if (isFavoritePlaylist(playlist, userId)) {
                FavoriteSongSyncBus.mergeIntoFavoritePlaylist(playlist, songCount)
            } else {
                playlist
            }
        }
    }

    private suspend fun preparePlaylistState(
        playlists: List<Playlist>,
        userId: Long,
        dispatcher: CoroutineDispatcher
    ): List<Playlist> {
        // 后台转换期间喜欢数量可能已回流，按最新数量重算后再发布。
        while (true) {
            val count = favoriteSongCount
            val merged = withContext(dispatcher) {
                mergeFavoritePlaylistState(playlists, userId, count)
            }
            if (count == favoriteSongCount) return merged
        }
    }

    fun clear() {
        userInfoJob?.cancel()
        cacheLoadGeneration++
        _homeState.value = HomeState()
        _userDetail.value = null
        lastUserDetailUid = 0L
        baseUserPlaylists = emptyList()
        lastUserInfoRefreshAt = 0L
        lastUserInfoCookieHash = 0
        viewModelScope.launch {
            context.dataStore.edit { prefs ->
                prefs[userIdKye] = 0L
            }
        }
    }

    private suspend fun loadCachedLibrary() {
        val generation = cacheLoadGeneration
        val initialState = _homeState.value
        val libraryCache = withContext(Dispatchers.Default) {
            HomeUserInfoStore.awaitLibraryCache(context.dataStore.data.first())
        }
        val cached = HomeState(
            userInfo = HomeUserInfoStore.userInfo.value,
            favoriteSong = libraryCache.favoriteSong,
            userPlaylists = libraryCache.userPlaylists
        )
        val merged = preparePlaylistState(
            cached.userPlaylists,
            cached.userInfo?.account?.profile?.userId ?: 0L,
            Dispatchers.Default
        )
        // 避免后台解码晚到，覆盖退出登录或已经发布的网络结果。
        if (generation == cacheLoadGeneration && _homeState.value == initialState) {
            baseUserPlaylists = cached.userPlaylists
            _homeState.value = cached.copy(userPlaylists = merged)
        }
        // 网络先返回或已退出登录时，当前状态也已经可供首屏展示。
        _initialDataPublished.value = true
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeUserIdChanges() {
        viewModelScope.launch {
            homeState
                .mapLatest { it.userInfo?.account?.profile?.userId }
                .distinctUntilChanged()
                .collectLatest { userId ->
                    if (userId == null) {
                        baseUserPlaylists = emptyList()
                        _homeState.update { it.copy(favoriteSong = null, userPlaylists = emptyList()) }
                    } else {
                        // 若本地已有缓存铺底，延迟 1800ms 错峰刷新，让出冷启动黄金渲染期；无缓存则立即可见
                        val staggerDelay = if (baseUserPlaylists.isNotEmpty()) 1800L else 0L
                        delay(staggerDelay)
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
                // 首次值仅建立基线；启动刷新统一由用户 uid 入口调度。
                .drop(1)
                .collectLatest {
                    val userId = _homeState.value.userInfo?.account?.profile?.userId ?: return@collectLatest
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
                    _homeState.update { state -> state.copy(userPlaylists = mergeFavoritePlaylistState(baseUserPlaylists)) }
                }
        }
    }
}
