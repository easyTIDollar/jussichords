package com.jussicodes.music.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jussicodes.music.utils.FavoriteSongIdsUtil
import com.jussicodes.music.utils.dataStore
import com.rcmiku.ncmapi.api.account.AccountApi
import com.rcmiku.ncmapi.api.recommend.RecommendApi
import com.rcmiku.ncmapi.model.DailySongsResponse
import com.rcmiku.ncmapi.model.RecommendPlaylistResponse
import com.rcmiku.ncmapi.utils.CookieProvider
import com.rcmiku.ncmapi.utils.json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest

/**
 * Startup preloader for the explore page.
 *
 * Content is served in two tiers:
 *  1. Disk cache (per-account, [DataStore]) — [loadCache] fills the shared
 *     flows as soon as the app boots, so the explore tab renders instantly
 *     even before any network round-trip completes.
 *  2. Live API fetch ([warmUp]) — runs when the cookie is applied, on app
 *     startup, or on manual refresh. On success it updates the flows and
 *     persists the cache; on failure the cached content is kept so the page
 *     never goes blank.
 */
object ExplorePreloader {

    private const val GUEST_KEY = "guest"

    /** Window in which a duplicate [warmUp] for the same account is skipped. */
    private const val REFRESH_DEDUPE_MS = 10_000L

    private val mutex = Mutex()

    private val _recommendSongs = MutableStateFlow<Result<DailySongsResponse>?>(null)
    val recommendSongs: StateFlow<Result<DailySongsResponse>?> = _recommendSongs.asStateFlow()

    private val _recommendPlaylist = MutableStateFlow<Result<RecommendPlaylistResponse>?>(null)
    val recommendPlaylist: StateFlow<Result<RecommendPlaylistResponse>?> =
        _recommendPlaylist.asStateFlow()

    @Volatile
    private var store: DataStore<Preferences>? = null

    /** Dedup bookkeeping so concurrent warmUps don't re-fetch. */
    @Volatile
    private var lastFetchedKey: String? = null

    @Volatile
    private var lastFetchedAt: Long = 0L

    private val keyUser = stringPreferencesKey("explore_cache_user")
    private val keySongs = stringPreferencesKey("explore_cache_songs")
    private val keyPlaylist = stringPreferencesKey("explore_cache_playlist")

    /** Must be called once with the application context before use. */
    fun init(context: Context) {
        if (store == null) store = context.applicationContext.dataStore
    }

    /**
     * Fills the shared flows from the per-account disk cache so the explore
     * page has content before any network call resolves. While the cookie is
     * not applied yet ("guest", i.e. right after a cold boot) the cached
     * content of the last known account is kept — a brief flash of stale
     * data is far better than a blank page. When the *applied* account does
     * not match the cache, the cache is invalidated and the flows are
     * cleared. Fresh network results already in the flows are never
     * overwritten by stale cache.
     */
    suspend fun loadCache() {
        val s = store ?: return
        val prefs = s.data.first()
        val cachedUser = prefs[keyUser] ?: return
        val currentKey = userKey()
        if (currentKey != GUEST_KEY && cachedUser != currentKey) {
            _recommendSongs.value = null
            _recommendPlaylist.value = null
            return
        }
        if (_recommendSongs.value == null) {
            prefs[keySongs]?.let { raw ->
                runCatching { json.decodeFromString<DailySongsResponse>(raw) }
                    .getOrNull()
                    ?.let { _recommendSongs.value = Result.success(it) }
            }
        }
        if (_recommendPlaylist.value == null) {
            prefs[keyPlaylist]?.let { raw ->
                runCatching { json.decodeFromString<RecommendPlaylistResponse>(raw) }
                    .getOrNull()
                    ?.let { _recommendPlaylist.value = Result.success(it) }
            }
        }
    }

    /**
     * Preloads all explore page content. Safe to call more than once (app
     * start, cookie change, manual refresh): [mutex] serializes concurrent
     * calls and a fresh fetch for the same account within [REFRESH_DEDUPE_MS]
     * is skipped unless [force] is set.
     *
     * While the cookie is not applied yet (guest) no network fetch happens:
     * a guest fetch would return un-personalized data that could overwrite
     * the real account's cache.
     */
    suspend fun warmUp(context: Context, force: Boolean = false) {
        mutex.withLock {
            loadCache()
            val key = userKey()
            if (key == GUEST_KEY) return@withLock
            if (
                !force && key == lastFetchedKey &&
                System.currentTimeMillis() - lastFetchedAt < REFRESH_DEDUPE_MS
            ) {
                return@withLock
            }
            AccountApi.favoriteSongIds().getOrNull()?.ids?.let { songIds ->
                FavoriteSongIdsUtil.updateSongIds(context, songIds)
            }
            val songs = RecommendApi.recommendSongs()
            val playlist = RecommendApi.recommendPlaylist()
            if (songs.isSuccess) {
                _recommendSongs.value = songs
                persist(key, keySongs, json.encodeToString(songs.getOrThrow()))
            }
            if (playlist.isSuccess) {
                _recommendPlaylist.value = playlist
                persist(key, keyPlaylist, json.encodeToString(playlist.getOrThrow()))
            }
            // Dedup only counts a successful fetch — a failed request is
            // retried on the next warmUp even inside the window.
            if (songs.isSuccess || playlist.isSuccess) {
                lastFetchedKey = key
                lastFetchedAt = System.currentTimeMillis()
            }
        }
    }

    private suspend fun persist(userKey: String, prefKey: Preferences.Key<String>, value: String) {
        store?.edit { prefs ->
            prefs[prefKey] = value
            prefs[keyUser] = userKey
        }
    }

    /**
     * Stable per-account identifier (sha256 of the active cookie, first 16
     * hex chars). "guest" when no cookie is applied yet. The cache is stored
     * under this key so content never bleeds across accounts.
     */
    private fun userKey(): String {
        val cookie = CookieProvider.cookie
        if (cookie.isEmpty()) return GUEST_KEY
        val digest = MessageDigest.getInstance("SHA-256").digest(cookie.toByteArray())
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }
}
