package com.jussicodes.music.utils

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import com.jussicodes.music.constants.libraryPlaylistRefreshTokenKey
import com.jussicodes.music.constants.userIdKye
import com.jussicodes.music.lx.SongJsonCache
import com.rcmiku.ncmapi.api.account.AccountApi
import com.rcmiku.ncmapi.api.player.OfficialPlayState
import com.rcmiku.ncmapi.api.player.PlayerApi
import com.rcmiku.ncmapi.model.Song
import com.rcmiku.ncmapi.utils.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

object FavoriteSongAction {
    const val LOCAL_LIKE_MESSAGE = "已加入本地喜欢 · 该歌曲无版权，不同步网易云"

    private const val TAG = "FavoriteSongAction"
    private const val MIN_SYNC_INTERVAL_MS = 2_000L
    private const val MAX_SYNC_RETRIES = 3
    private const val SYNC_RETRY_DELAY_MS = 2_000L

    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queueMutex = Mutex()
    private val syncMutex = Mutex()
    private val songMutexes = java.util.concurrent.ConcurrentHashMap<Long, kotlinx.coroutines.sync.Mutex>()
    private val pendingStates = LinkedHashMap<Long, Boolean>()
    private val syncFailureCounts = mutableMapOf<Long, Int>()
    private var syncWorkerRunning = false
    private var lastSyncAt = 0L

    suspend fun toggle(
        context: Context,
        songId: Long,
        likedSongIds: List<Long>,
        song: Song? = null
    ): Boolean {
        return setLiked(
            context = context,
            songId = songId,
            like = songId !in likedSongIds,
            song = song
        )
    }

    /** 返回是否走网易云同步队列；无版权歌曲只存本地。 */
    suspend fun setLiked(
        context: Context,
        songId: Long,
        like: Boolean,
        song: Song? = null
    ): Boolean = songMutexes.computeIfAbsent(songId) { Mutex() }.withLock {
        val appContext = context.applicationContext
        GreySongRegistry.load(appContext)
        val isGrey = GreySongRegistry.isGrey(songId) ||
            (like && song?.fee != 1 && song?.fee != 4 && runCatching {
                withTimeoutOrNull(3_000L) {
                    PlayerApi.officialPlayState(songId.toString(), song?.fee ?: 0)
                } == OfficialPlayState.NO_COPYRIGHT
            }.onFailure { if (it is CancellationException) throw it }.getOrDefault(false))
        val matchingSong = song?.takeIf { it.id == songId }
        val cachedSong = if (matchingSong == null) {
            SongJsonCache.get(songId.toString())
                ?.let { runCatching { json.decodeFromString<Song>(it) }.getOrNull() }
                ?.takeIf { it.id == songId }
        } else null
        val synced = FavoriteSongIdsUtil.localStateMutex.withLock {
            val localOnly = GreySongRegistry.isGrey(songId) || isGrey
            val localSong = matchingSong
                ?: if (localOnly) {
                    cachedSong ?: FavoriteSongSyncBus.localSongs.value[songId]
                        ?: Song(id = songId, name = "歌曲 $songId")
                } else null
            updateLocalState(appContext, songId, like, localSong)
            if (localOnly) {
                GreySongRegistry.markGrey(appContext, songId)
                if (like) {
                    GreySongRegistry.saveLiked(appContext, checkNotNull(localSong))
                } else {
                    GreySongRegistry.removeLiked(appContext, songId)
                }
                return@withLock false
            }
            enqueueSync(appContext, songId, like)
            true
        }
        Log.i(TAG, "setLiked songId=$songId like=$like localOnly=${!synced}")
        synced
    }

    private suspend fun updateLocalState(
        context: Context,
        songId: Long,
        like: Boolean,
        song: Song?
    ) {
        if (like) {
            FavoriteSongIdsUtil.addSongId(context, songId)
        } else {
            FavoriteSongIdsUtil.removeSongId(context, songId)
        }
        context.dataStore.edit { prefs ->
            prefs[libraryPlaylistRefreshTokenKey] = System.currentTimeMillis()
        }
        song?.let { FavoriteSongSyncBus.setLiked(it, like) }
    }

    private suspend fun enqueueSync(context: Context, songId: Long, like: Boolean) {
        queueMutex.withLock {
            pendingStates[songId] = like
            if (syncWorkerRunning) return
            syncWorkerRunning = true
        }
        syncScope.launch {
            processQueue(context.applicationContext)
        }
    }

    private suspend fun processQueue(context: Context) {
        try {
            while (true) {
                val next = queueMutex.withLock {
                    val entry = pendingStates.entries.firstOrNull() ?: return@withLock null
                    pendingStates.remove(entry.key)
                    entry.key to entry.value
                } ?: break

                val result = syncMutex.withLock {
                    val elapsed = System.currentTimeMillis() - lastSyncAt
                    if (elapsed < MIN_SYNC_INTERVAL_MS) {
                        delay(MIN_SYNC_INTERVAL_MS - elapsed)
                    }
                    val response = try {
                        AccountApi.songLike(next.second, next.first, resolveUserId(context))
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Throwable) {
                        Result.failure(error)
                    }
                    lastSyncAt = System.currentTimeMillis()
                    response
                }
                val error = result.exceptionOrNull()
                queueMutex.withLock {
                    if (error == null) {
                        syncFailureCounts.remove(next.first)
                    } else {
                        val failures = (syncFailureCounts[next.first] ?: 0) + 1
                        if (failures > MAX_SYNC_RETRIES) {
                            syncFailureCounts.remove(next.first)
                            Log.e(TAG, "收藏同步放弃 songId=${next.first} like=${next.second} failures=$failures", error)
                        } else {
                            syncFailureCounts[next.first] = failures
                            // 请求期间可能已有新意图入队，不能用失败的旧意图覆盖。
                            if (!pendingStates.containsKey(next.first)) {
                                pendingStates[next.first] = next.second
                            }
                        }
                    }
                }
                if (error != null) delay(SYNC_RETRY_DELAY_MS)
            }
        } finally {
            queueMutex.withLock {
                if (pendingStates.isEmpty()) {
                    syncWorkerRunning = false
                } else {
                    syncScope.launch {
                        processQueue(context)
                    }
                }
            }
        }
    }

    private suspend fun resolveUserId(context: Context): Long? {
        context.dataStore.data.first()[userIdKye]?.takeIf { it > 0 }?.let {
            return it
        }
        val userId = AccountApi.accountInfo().getOrNull()?.account?.profile?.userId?.takeIf { it > 0 }
        if (userId != null) {
            context.dataStore.edit { prefs ->
                prefs[userIdKye] = userId
            }
        }
        return userId
    }
}
