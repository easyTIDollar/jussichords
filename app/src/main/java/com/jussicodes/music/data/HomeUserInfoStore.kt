package com.jussicodes.music.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.jussicodes.music.constants.libraryUserDetailCacheKey
import com.jussicodes.music.constants.libraryUserInfoCacheKey
import com.jussicodes.music.constants.libraryFavoriteSongCacheKey
import com.jussicodes.music.constants.libraryUserPlaylistsCacheKey
import com.jussicodes.music.utils.dataStore
import com.rcmiku.ncmapi.model.UserInfoBatch
import com.rcmiku.ncmapi.model.UserDetailResponse
import com.rcmiku.ncmapi.model.FavoriteSongResponse
import com.rcmiku.ncmapi.model.Playlist
import com.rcmiku.ncmapi.utils.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * 主页信息卡（头像/昵称/关注粉丝）数据源（进程级，PinnedAlbumStore/HomeTransparentCardStore 同款模式）。
 * LibraryScreen 随 tab 切换重组时，VM 的 collectAsState 首帧可能出 null（userInfo 门控整卡、
 * userDetail 无缓存恒 null）→ 信息卡整张抖/闪一下。
 * 值提到进程级 StateFlow：Application 启动从 DataStore 缓存灌入（首帧即真值，进程生命周期稳定，
 * 不随 composition dispose）；VM 网络刷新成功后回写本 store（内存 + 落盘），信息卡即时跟上。
 */
object HomeUserInfoStore {

    @Volatile
    private var store: DataStore<Preferences>? = null

    private val _userInfo = MutableStateFlow<UserInfoBatch?>(null)
    val userInfo: StateFlow<UserInfoBatch?> = _userInfo.asStateFlow()

    private val _userDetail = MutableStateFlow<UserDetailResponse?>(null)
    val userDetail: StateFlow<UserDetailResponse?> = _userDetail.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    data class LibraryCache(
        val favoriteSong: FavoriteSongResponse?,
        val userPlaylists: List<Playlist>,
        val favoriteSongRaw: String?,
        val userPlaylistsRaw: String?
    )

    private val libraryCache = CompletableDeferred<LibraryCache>()

    /** 启动时仅登记引用，缓存由 Application 的聚合恢复任务统一灌入。 */
    fun init(context: Context) {
        store = context.applicationContext.dataStore
    }

    /** 后台统一解码首页必要缓存，发布后再允许恢复非首页数据。 */
    fun applySnapshot(prefs: Preferences) {
        val userInfo = decode<UserInfoBatch>(prefs[libraryUserInfoCacheKey])
        val userDetail = decode<UserDetailResponse>(prefs[libraryUserDetailCacheKey])
        val cached = decodeLibraryCache(prefs)
        // 网络结果可能先到，不让旧缓存覆盖已发布的新值。
        _userInfo.compareAndSet(null, userInfo)
        _userDetail.compareAndSet(null, userDetail)
        libraryCache.complete(cached)
    }

    suspend fun awaitLibraryCache(prefs: Preferences): LibraryCache {
        val cached = libraryCache.await()
        // 同一进程重新创建页面时，喜欢列表或歌单缓存可能已被刷新。
        return if (cached.favoriteSongRaw == prefs[libraryFavoriteSongCacheKey] &&
            cached.userPlaylistsRaw == prefs[libraryUserPlaylistsCacheKey]
        ) cached else decodeLibraryCache(prefs)
    }

    private fun decodeLibraryCache(prefs: Preferences): LibraryCache = LibraryCache(
        favoriteSong = decode(prefs[libraryFavoriteSongCacheKey]),
        userPlaylists = decode<List<Playlist>>(prefs[libraryUserPlaylistsCacheKey]).orEmpty(),
        favoriteSongRaw = prefs[libraryFavoriteSongCacheKey],
        userPlaylistsRaw = prefs[libraryUserPlaylistsCacheKey]
    )

    /** 网络刷新成功后回写（内存 + 落盘）。 */
    fun publishUserInfo(batch: UserInfoBatch) {
        _userInfo.value = batch
        val s = store ?: return
        scope.launch { s.edit { it[libraryUserInfoCacheKey] = json.encodeToString(batch) } }
    }

    /**
     * 网络刷新成功后回写（内存 + 落盘）。只存"拉到的值"；没拉过则保持 null
     * （UI 侧降级 label-only，不 invent 0）。
     */
    fun publishUserDetail(detail: UserDetailResponse) {
        _userDetail.value = detail
        val s = store ?: return
        scope.launch { s.edit { it[libraryUserDetailCacheKey] = json.encodeToString(detail) } }
    }

    private inline fun <reified T> decode(raw: String?): T? =
        raw?.takeIf { it.isNotBlank() }?.let { runCatching { json.decodeFromString<T>(it) }.getOrNull() }
}
