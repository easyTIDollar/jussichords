package com.jussicodes.music.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jussicodes.music.constants.pinnedAlbumIdsKey
import com.jussicodes.music.constants.pinnedAlbumsAccountKey
import com.jussicodes.music.constants.pinnedAlbumsCacheKey
import com.jussicodes.music.utils.dataStore
import com.rcmiku.ncmapi.api.album.AlbumApi
import com.rcmiku.ncmapi.model.Album
import com.rcmiku.ncmapi.model.Song
import com.rcmiku.ncmapi.utils.CookieProvider
import com.rcmiku.ncmapi.utils.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * 主页"置顶专辑墙"的唯一数据源（按账号作用域，cookie 变化自动失效旧缓存）。
 *
 *  1. 磁盘缓存（DataStore）：[loadCache] 在 App 启动时把上次的内容灌进共享
 *     [albums]，专辑墙首帧就有封面；
 *  2. 网络同步（[sync]）：并发拉取每个置顶专辑的详情（封面 + 曲目），成功才
 *     回写缓存并更新 dedup 标记——全失败时不动标记，下次 [sync] 会重试
 *     （修复旧实现"失败即锁死、ids 不变就永不重试"的 bug）；
 *  3. 本地写操作（[togglePin] / [reorder] / [applyPinned]）：内存 + 落盘即时生效。
 *
 * 曲目缓存在 [songCache]（内存，进程内有效）：专辑墙双击封面循环播放 /
 * 编辑菜单"播放整张"直接命中，不重复请求 /album。
 */
object PinnedAlbumStore {

    private const val GUEST_KEY = "guest"

    /** 同一账号去重窗口：窗口内对相同 ids 的 [sync] 直接跳过（force 除外）。 */
    private const val REFRESH_DEDUPE_MS = 15_000L

    private val mutex = Mutex()

    @Volatile
    private var store: DataStore<Preferences>? = null

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    /** 上一次成功同步的账号 + ids + 时间，用于去重；失败不更新。 */
    @Volatile
    private var lastSyncedAccount: String = ""

    @Volatile
    private var lastSyncedIds: List<Long> = emptyList()

    @Volatile
    private var lastSyncedAt: Long = 0L

    /** albumId -> 曲目，进程内有效。 */
    private val songCache = ConcurrentHashMap<Long, List<Song>>()

    /** 必须在 Application.onCreate 里调用一次。 */
    fun init(context: Context) {
        if (store == null) store = context.applicationContext.dataStore
    }

    /** 已缓存的专辑曲目；未缓存返回 null（调用方自行拉取后 [cacheAlbumSongs]）。 */
    fun getAlbumSongs(albumId: Long): List<Song>? = songCache[albumId]

    fun cacheAlbumSongs(albumId: Long, songs: List<Song>) {
        songCache[albumId] = songs
    }

    /** 读磁盘缓存灌 [albums]（启动时调用）；账号不匹配时清掉旧内容。 */
    suspend fun loadCache() {
        val s = store ?: return
        val prefs = s.data.first()
        val storedAccount = prefs[pinnedAlbumsAccountKey].orEmpty()
        val acc = accountKey()
        if (storedAccount.isNotEmpty() && acc != GUEST_KEY && storedAccount != acc) {
            _albums.value = emptyList()
            return
        }
        val ids = readIds(prefs)
        val raw = prefs[pinnedAlbumsCacheKey].orEmpty()
        val cached = if (raw.isNotBlank()) {
            runCatching { json.decodeFromString<List<Album>>(raw) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        val ordered = if (ids.isEmpty()) cached else ids.mapNotNull { id ->
            cached.firstOrNull { it.id == id }
        }
        if (ordered.isNotEmpty()) {
            _albums.value = ordered
        }
    }

    /**
     * 并发拉取专辑详情（封面 + 曲目）并回写缓存。[extraIds] 用于把尚未入库的新
     * 专辑一并拉下来（[applyPinned] 走这个）。返回是否成功：全失败时不动 dedup
     * 标记，下次调用会重试。
     */
    suspend fun sync(force: Boolean = false, extraIds: List<Long> = emptyList()): Boolean {
        return mutex.withLock {
            loadCache()
            val acc = accountKey()
            if (acc == GUEST_KEY) return@withLock false
            val ids = (_albums.value.map { it.id } + extraIds).distinct()
            if (ids.isEmpty()) {
                _albums.value = emptyList()
                persist(emptyList(), emptyList())
                return@withLock true
            }
            val now = System.currentTimeMillis()
            if (
                !force && acc == lastSyncedAccount &&
                ids == lastSyncedIds && now - lastSyncedAt < REFRESH_DEDUPE_MS
            ) {
                return@withLock true
            }
            val results = kotlinx.coroutines.coroutineScope {
                ids.map { id ->
                    async(Dispatchers.IO) {
                        AlbumApi.albumDetail(id).getOrNull()
                    }
                }.awaitAll()
            }
            val found = results.filterNotNull()
            if (found.isEmpty()) return@withLock false
            for (r in found) {
                songCache[r.album.id] = r.songs
            }
            val ordered = ids.mapNotNull { albumId -> found.firstOrNull { it.album.id == albumId }?.album }
            _albums.value = ordered
            persist(ids, ordered)
            lastSyncedAccount = acc
            lastSyncedIds = ids
            lastSyncedAt = now
            true
        }
    }

    /** 置顶 / 取消置顶单个专辑（内存 + 落盘；新增时顺带拉取详情 + 曲目）。 */
    suspend fun togglePin(album: Album) {
        val current = _albums.value
        val added = album.id !in current.map { it.id }
        _albums.value = if (added) {
            listOf(album) + current
        } else {
            current.filterNot { it.id == album.id }.also { songCache.remove(album.id) }
        }
        persist(_albums.value.map { it.id }, _albums.value)
        if (added) sync(force = true)
    }

    /** 编辑模式拖拽排序（内存 + 落盘，不拉网络）。 */
    suspend fun reorder(orderedIds: List<Long>) {
        val current = _albums.value
        val next = orderedIds.mapNotNull { id -> current.firstOrNull { it.id == id } }
        _albums.value = next
        persist(orderedIds, next)
    }

    /** 退出登录：清内存态（磁盘缓存保留，重新登录后 [sync] 会刷新）。 */
    fun resetOnLogout() {
        _albums.value = emptyList()
        songCache.clear()
        lastSyncedAccount = ""
        lastSyncedIds = emptyList()
        lastSyncedAt = 0L
    }

    /**
     * 多选弹窗"应用"：[finalIds] 是最终置顶集合（保留已有顺序、新增追加、
     * 未勾选的移除）。已有专辑保留本地数据，新增的交给 [sync] 拉详情。
     */
    suspend fun applyPinned(finalIds: List<Long>) {
        val current = _albums.value
        val kept = current.filter { it.id in finalIds }
        _albums.value = kept
        persist(finalIds, kept)
        val newIds = finalIds - kept.map { it.id }.toSet()
        if (newIds.isNotEmpty()) sync(force = true, extraIds = newIds)
    }

    private suspend fun persist(ids: List<Long>, albums: List<Album>) {
        val s = store ?: return
        s.edit { prefs ->
            prefs[pinnedAlbumIdsKey] = ids.joinToString(",")
            prefs[pinnedAlbumsCacheKey] = json.encodeToString(albums)
            prefs[pinnedAlbumsAccountKey] = accountKey()
        }
    }

    private fun readIds(prefs: Preferences): List<Long> =
        prefs[pinnedAlbumIdsKey].orEmpty().split(",").mapNotNull { it.toLongOrNull() }

    /** 按当前 cookie 算账号指纹（sha256 前 16 hex）；未登录为 "guest"。 */
    private fun accountKey(): String {
        val cookie = CookieProvider.cookie
        if (cookie.isEmpty()) return GUEST_KEY
        val digest = MessageDigest.getInstance("SHA-256").digest(cookie.toByteArray())
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }
}
