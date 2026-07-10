package com.jussicodes.music.utils

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.jussicodes.music.constants.libraryPlaylistRefreshTokenKey
import com.jussicodes.music.constants.userIdKye
import com.rcmiku.ncmapi.api.account.AccountApi
import com.rcmiku.ncmapi.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object FavoriteSongAction {
    private const val MIN_SYNC_INTERVAL_MS = 2_000L

    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queueMutex = Mutex()
    private val syncMutex = Mutex()
    private val pendingStates = LinkedHashMap<Long, Boolean>()
    private var syncWorkerRunning = false
    private var lastSyncAt = 0L

    suspend fun toggle(
        context: Context,
        songId: Long,
        likedSongIds: List<Long>,
        song: Song? = null
    ) {
        setLiked(
            context = context,
            songId = songId,
            like = songId !in likedSongIds,
            song = song
        )
    }

    suspend fun setLiked(
        context: Context,
        songId: Long,
        like: Boolean,
        song: Song? = null
    ) {
        val appContext = context.applicationContext
        updateLocalState(appContext, songId, like, song)
        enqueueSync(appContext, songId, like)
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

                syncMutex.withLock {
                    val elapsed = System.currentTimeMillis() - lastSyncAt
                    if (elapsed < MIN_SYNC_INTERVAL_MS) {
                        delay(MIN_SYNC_INTERVAL_MS - elapsed)
                    }
                    AccountApi.songLike(next.second, next.first, resolveUserId(context))
                    lastSyncAt = System.currentTimeMillis()
                }
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
