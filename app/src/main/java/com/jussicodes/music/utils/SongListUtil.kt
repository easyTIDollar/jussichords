package com.jussicodes.music.utils

import com.rcmiku.ncmapi.model.Song
import com.rcmiku.ncmapi.utils.json
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object SongListUtil {

    private const val SONG_LIST = "song_list.json"
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
            val songListJson = json.encodeToString<List<Song>>(songList)
            File(target, SONG_LIST).writeText(songListJson)
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
}
