package com.jussicodes.music.utils

import com.rcmiku.ncmapi.model.Song
import com.rcmiku.ncmapi.utils.json
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class SongSourceMeta(
    val sourceId: Long = 0L,
    val sourceName: String = "list",
    val sourceType: String? = null,
    val navId: Long = 0L
)

object SongListUtil {

    private const val SONG_LIST = "song_list.json"
    private const val SOURCE_META = "song_list_source.json"
    private var file: File? = null
    private var songList: List<Song> = emptyList()
    // The in-memory list is the source of truth for playback resumption;
    // persisting it to disk runs on the IO dispatcher so tapping a song in
    // a large playlist never blocks the UI thread with a full-list JSON
    // encode + file write.
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun init(file: File) {
        if (!file.exists()) file.mkdir()
        this.file = file
    }

    fun saveSong(song: Song) {
        val updatedSongList = songList.toMutableList()
        updatedSongList.add(song)
        saveSongList(updatedSongList)
    }

    fun removeSong(songId: Long) {
        val updatedSongList = songList.toMutableList()
        updatedSongList.removeIf { it.id == songId }
        songList = updatedSongList
        saveSongList(updatedSongList)
    }

    fun saveSong(song: Song, index: Int) {
        val updatedSongList = songList.toMutableList()
        updatedSongList.add(index, song)
        saveSongList(updatedSongList)
    }


    fun saveSongList(songList: List<Song>) {
        this.songList = songList
        ioScope.launch {
            val target = file ?: return@launch
            File(target, SONG_LIST).writeText(json.encodeToString<List<Song>>(songList))
        }
    }

    fun loadSongList(): List<Song>? {
        val songListJson = File(file, SONG_LIST)
        return if (songListJson.exists()) {
            songList = json.decodeFromString<List<Song>>(songListJson.readText())
            return songList
        } else {
            null
        }
    }

    // The last queue's source metadata (label shown above the player),
    // persisted so it survives an app restart like the song list itself.
    fun saveSongSource(meta: SongSourceMeta) {
        ioScope.launch {
            file?.let { File(it, SOURCE_META).writeText(json.encodeToString(meta)) }
        }
    }

    fun loadSongSource(): SongSourceMeta? {
        val target = file ?: return null
        val sourceJson = File(target, SOURCE_META)
        if (!sourceJson.exists()) return null
        return runCatching { json.decodeFromString<SongSourceMeta>(sourceJson.readText()) }.getOrNull()
    }
}
