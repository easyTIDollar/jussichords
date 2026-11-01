package com.jussicodes.music.data.external

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jussicodes.music.utils.dataStore
import com.rcmiku.ncmapi.model.LyricContent
import com.rcmiku.ncmapi.model.LyricResponse
import com.rcmiku.ncmapi.utils.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import java.util.UUID

object ExternalPlaylistStore {

    private val EXTERNAL_PLAYLISTS_KEY = stringPreferencesKey("external_playlists_v1")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _playlists = MutableStateFlow<List<ExternalPlaylist>>(emptyList())
    val playlists: StateFlow<List<ExternalPlaylist>> = _playlists.asStateFlow()

    @Volatile
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        val appContext = context.applicationContext
        scope.launch {
            mutex.withLock {
                if (initialized) return@withLock
                runCatching {
                    val raw = appContext.dataStore.data.first()[EXTERNAL_PLAYLISTS_KEY].orEmpty()
                    if (raw.isNotBlank()) {
                        val parsed = json.decodeFromString<List<ExternalPlaylist>>(raw)
                        _playlists.value = parsed
                    }
                }
                initialized = true
            }
        }
    }

    /**
     * 供播放器 ResolvingDataSource 使用：若属于外部歌单歌曲，返回对应的 content:// 或 file:// Uri。
     */
    fun getPlayableUri(context: Context, songId: Long): Uri? {
        val song = findSongById(songId) ?: return null
        return runCatching { Uri.parse(song.fileUri) }.getOrNull()
    }

    /**
     * 供播放器歌词页与桌面歌词使用：若外部歌曲提取到了歌词，直接返回 LyricResponse。
     */
    fun getLyric(songId: Long): LyricResponse? {
        val song = findSongById(songId) ?: return null
        val lrc = song.lyricText?.takeIf { it.isNotBlank() } ?: return null
        return LyricResponse(lrc = LyricContent(lyric = lrc))
    }

    private fun findSongById(songId: Long): ExternalSong? {
        for (pl in _playlists.value) {
            val s = pl.songs.firstOrNull { it.id == songId }
            if (s != null) return s
        }
        return null
    }

    /**
     * 导入外部文件夹为新歌单，自动扫描音频、歌词、封面。
     */
    suspend fun importFolder(
        context: Context,
        treeUri: Uri,
        folderName: String
    ): ExternalPlaylist = mutex.withLock {
        val appContext = context.applicationContext
        val scannedSongs = ExternalAudioScanner.scanFolder(appContext, treeUri, folderName)

        val playlistId = UUID.randomUUID().toString()
        val newPlaylist = ExternalPlaylist(
            id = playlistId,
            name = folderName,
            treeUri = treeUri.toString(),
            folderPathName = folderName,
            createTime = System.currentTimeMillis(),
            songs = scannedSongs
        )

        val current = _playlists.value.toMutableList()
        // 若已导入过相同 treeUri，替换旧的；否则添加到头部
        val existingIndex = current.indexOfFirst { it.treeUri == treeUri.toString() }
        if (existingIndex >= 0) {
            current[existingIndex] = newPlaylist.copy(name = current[existingIndex].name)
        } else {
            current.add(0, newPlaylist)
        }

        _playlists.value = current
        save(appContext, current)
        newPlaylist
    }

    /**
     * 重新扫描指定歌单文件夹，自动识别增删的音频、歌词与封面。
     * 返回 Pair(新增歌曲数, 移除歌曲数)。
     */
    suspend fun refreshPlaylist(
        context: Context,
        playlistId: String
    ): Pair<Int, Int> = mutex.withLock {
        val appContext = context.applicationContext
        val current = _playlists.value.toMutableList()
        val index = current.indexOfFirst { it.id == playlistId }
        if (index < 0) return@withLock Pair(0, 0)

        val oldPlaylist = current[index]
        val treeUri = runCatching { Uri.parse(oldPlaylist.treeUri) }.getOrNull()
            ?: return@withLock Pair(0, 0)

        val newScannedSongs = ExternalAudioScanner.scanFolder(
            appContext,
            treeUri,
            oldPlaylist.folderPathName
        )

        val oldSongUris = oldPlaylist.songs.map { it.fileUri }.toSet()
        val newSongUris = newScannedSongs.map { it.fileUri }.toSet()

        val addedCount = newSongUris.count { it !in oldSongUris }
        val removedCount = oldSongUris.count { it !in newSongUris }

        val updatedPlaylist = oldPlaylist.copy(songs = newScannedSongs)
        current[index] = updatedPlaylist
        _playlists.value = current
        save(appContext, current)

        Pair(addedCount, removedCount)
    }

    /**
     * 重命名外部歌单。
     */
    suspend fun renamePlaylist(context: Context, playlistId: String, newName: String) = mutex.withLock {
        val appContext = context.applicationContext
        val current = _playlists.value.toMutableList()
        val index = current.indexOfFirst { it.id == playlistId }
        if (index >= 0) {
            current[index] = current[index].copy(name = newName)
            _playlists.value = current
            save(appContext, current)
        }
    }

    /**
     * 解绑/移除外部歌单（不删除手机物理文件）。
     */
    suspend fun deletePlaylist(context: Context, playlistId: String) = mutex.withLock {
        val appContext = context.applicationContext
        val current = _playlists.value.filterNot { it.id == playlistId }
        _playlists.value = current
        save(appContext, current)
    }

    /**
     * 从歌单中移除指定歌曲。
     */
    suspend fun removeSong(context: Context, playlistId: String, songId: Long) = mutex.withLock {
        val appContext = context.applicationContext
        val current = _playlists.value.toMutableList()
        val index = current.indexOfFirst { it.id == playlistId }
        if (index >= 0) {
            val pl = current[index]
            val updatedSongs = pl.songs.filterNot { it.id == songId }
            current[index] = pl.copy(songs = updatedSongs)
            _playlists.value = current
            save(appContext, current)
        }
    }

    private suspend fun save(context: Context, list: List<ExternalPlaylist>) {
        runCatching {
            val raw = json.encodeToString(list)
            context.dataStore.edit { prefs ->
                prefs[EXTERNAL_PLAYLISTS_KEY] = raw
            }
        }
    }
}
