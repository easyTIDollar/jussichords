package com.jussicodes.music.utils

import android.content.Context
import android.util.Log
import androidx.datastore.core.CorruptionException
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.rcmiku.ncmapi.model.Song
import com.rcmiku.ncmapi.utils.json
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

object GreySongRegistry {
    private const val TAG = "GreySongRegistry"
    private val greySongIds = stringSetPreferencesKey("greySongIdsV2")
    private val greyAlbumIdsKey = stringSetPreferencesKey("greyAlbumIdsV2")
    private val greyLikedSongs = stringPreferencesKey("greyLikedSongs")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val idsLock = Any()
    private val likedMutex = Mutex()
    private var pendingIdsPublishJob: Job? = null
    private var idsPublishGeneration = 0L

    @Volatile private var knownIds: Set<Long> = emptySet()
    private val _ids = MutableStateFlow<Set<Long>>(emptySet())
    val ids: StateFlow<Set<Long>> = _ids.asStateFlow()
    private val _greyAlbumIds = MutableStateFlow<Set<Long>>(emptySet())
    val greyAlbumIds: StateFlow<Set<Long>> = _greyAlbumIds.asStateFlow()
    @Volatile private var localLikes: Map<String, Song> = emptyMap()
    private var loaded = false
    private var lastLoadFailureAt: Long? = null

    fun markGrey(context: Context, songId: Long) {
        synchronized(idsLock) {
            if (songId in knownIds) return
            knownIds = knownIds + songId
            scheduleIdsPublishLocked()
            Log.i(TAG, "标记无版权 songId=$songId")
        }
        val appContext = context.applicationContext
        scope.launch {
            try {
                appContext.dataStore.edit { prefs ->
                    val songIdString = songId.toString()
                    val shouldBeMarked = synchronized(idsLock) { songId in knownIds }
                    prefs[greySongIds] = if (shouldBeMarked) {
                        prefs[greySongIds].orEmpty() + songIdString
                    } else {
                        prefs[greySongIds].orEmpty() - songIdString
                    }
                }
            } catch (error: IOException) {
                Log.w(TAG, "保存无版权歌曲标记失败：$songId", error)
            }
        }
    }

    fun isGrey(songId: Long): Boolean = songId in knownIds

    private fun scheduleIdsPublishLocked() {
        val generation = ++idsPublishGeneration
        pendingIdsPublishJob?.cancel()
        pendingIdsPublishJob = scope.launch {
            delay(200)
            synchronized(idsLock) {
                if (generation == idsPublishGeneration) {
                    _ids.value = knownIds
                    pendingIdsPublishJob = null
                }
            }
        }
    }

    suspend fun unmarkGrey(context: Context, songId: Long) {
        synchronized(idsLock) {
            knownIds = knownIds - songId
            idsPublishGeneration++
            pendingIdsPublishJob?.cancel()
            pendingIdsPublishJob = null
            _ids.value = knownIds
        }
        val appContext = context.applicationContext
        scope.launch {
            try {
                appContext.dataStore.edit { prefs ->
                    val songIdString = songId.toString()
                    val shouldBeMarked = synchronized(idsLock) { songId in knownIds }
                    prefs[greySongIds] = if (shouldBeMarked) {
                        prefs[greySongIds].orEmpty() + songIdString
                    } else {
                        prefs[greySongIds].orEmpty() - songIdString
                    }
                }
            } catch (error: CorruptionException) {
                Log.w(TAG, "清除无版权歌曲标记失败：$songId", error)
            } catch (error: IOException) {
                Log.w(TAG, "清除无版权歌曲标记失败：$songId", error)
            }
        }
    }

    fun markGreyAlbum(context: Context, albumId: Long) {
        synchronized(idsLock) {
            if (albumId in _greyAlbumIds.value) return
            _greyAlbumIds.value = _greyAlbumIds.value + albumId
        }
        val appContext = context.applicationContext
        scope.launch {
            try {
                appContext.dataStore.edit { prefs ->
                    val albumIdString = albumId.toString()
                    val shouldBeMarked = synchronized(idsLock) { albumId in _greyAlbumIds.value }
                    prefs[greyAlbumIdsKey] = if (shouldBeMarked) {
                        prefs[greyAlbumIdsKey].orEmpty() + albumIdString
                    } else {
                        prefs[greyAlbumIdsKey].orEmpty() - albumIdString
                    }
                }
            } catch (error: IOException) {
                Log.w(TAG, "保存无版权专辑标记失败：$albumId", error)
            }
        }
    }

    suspend fun unmarkGreyAlbum(context: Context, albumId: Long) {
        synchronized(idsLock) {
            _greyAlbumIds.value = _greyAlbumIds.value - albumId
        }
        val appContext = context.applicationContext
        scope.launch {
            try {
                appContext.dataStore.edit { prefs ->
                    val albumIdString = albumId.toString()
                    val shouldBeMarked = synchronized(idsLock) { albumId in _greyAlbumIds.value }
                    prefs[greyAlbumIdsKey] = if (shouldBeMarked) {
                        prefs[greyAlbumIdsKey].orEmpty() + albumIdString
                    } else {
                        prefs[greyAlbumIdsKey].orEmpty() - albumIdString
                    }
                }
            } catch (error: CorruptionException) {
                Log.w(TAG, "清除无版权专辑标记失败：$albumId", error)
            } catch (error: IOException) {
                Log.w(TAG, "清除无版权专辑标记失败：$albumId", error)
            }
        }
    }

    fun isGreyAlbum(albumId: Long): Boolean = albumId in _greyAlbumIds.value

    suspend fun load(context: Context) {
        likedMutex.withLock { loadLocked(context) }
    }

    private suspend fun loadLocked(context: Context) {
        if (loaded) return
        val now = System.currentTimeMillis()
        if (lastLoadFailureAt?.let { now - it < 5_000 } == true) return
        val prefs = try {
            context.dataStore.data.first()
        } catch (error: CorruptionException) {
            lastLoadFailureAt = System.currentTimeMillis()
            Log.w(TAG, "读取无版权数据失败，保留内存状态", error)
            return
        } catch (error: IOException) {
            lastLoadFailureAt = System.currentTimeMillis()
            Log.w(TAG, "读取无版权数据失败，保留内存状态", error)
            return
        }
        lastLoadFailureAt = null
        val ids = prefs[greySongIds].orEmpty().mapNotNull { it.toLongOrNull() }
        val albumIds = prefs[greyAlbumIdsKey].orEmpty().mapNotNull { it.toLongOrNull() }
        synchronized(idsLock) {
            knownIds = knownIds + ids
            _ids.value = knownIds
            _greyAlbumIds.value = _greyAlbumIds.value + albumIds
        }
        localLikes = prefs[greyLikedSongs]?.let { saved ->
            try {
                json.decodeFromString<Map<String, Song>>(saved)
            } catch (error: SerializationException) {
                Log.w(TAG, "读取本地喜欢失败", error)
                emptyMap()
            }
        }.orEmpty()
        loaded = true
        Log.i(TAG, "载入无版权 ${knownIds.size} 首、本地喜欢 ${localLikes.size} 首")
    }

    suspend fun saveLiked(context: Context, song: Song) {
        val id = song.id.toString()
        updateLiked(context, greyId = id) { it + (id to song) }
    }

    suspend fun removeLiked(context: Context, songId: Long) {
        updateLiked(context) { it - songId.toString() }
    }

    private suspend fun updateLiked(
        context: Context,
        greyId: String? = null,
        update: (Map<String, Song>) -> Map<String, Song>
    ) {
        likedMutex.withLock {
            loadLocked(context)
            val updated = update(localLikes)
            if (updated == localLikes) return
            localLikes = updated
            try {
                context.dataStore.edit { prefs ->
                    if (greyId != null) {
                        prefs[greySongIds] = prefs[greySongIds].orEmpty() + greyId
                    }
                    prefs[greyLikedSongs] = json.encodeToString(updated)
                }
            } catch (error: CorruptionException) {
                Log.w(TAG, "保存本地喜欢失败，已保留内存状态", error)
            } catch (error: IOException) {
                Log.w(TAG, "保存本地喜欢失败，已保留内存状态", error)
            }
        }
    }

    fun likedSongs(): List<Song> = localLikes.values.toList()

    fun greyIds(): Set<Long> = knownIds.toSet()
}
