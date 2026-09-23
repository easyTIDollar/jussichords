package com.jussicodes.music.extensions

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.jussicodes.music.constants.currentPlayMediaIdKey
import com.jussicodes.music.utils.SongListUtil
import com.jussicodes.music.utils.SongSourceMeta
import com.jussicodes.music.utils.dataStore
import com.jussicodes.music.utils.get
import com.rcmiku.ncmapi.model.CloudSong
import com.rcmiku.ncmapi.model.Radio
import com.rcmiku.ncmapi.model.Song
import kotlinx.coroutines.flow.MutableStateFlow

internal val Player.currentMediaItems: List<MediaItem>
    get() {
        return List(mediaItemCount, ::getMediaItemAt)
    }

internal val cacheSongs: MutableStateFlow<List<Song>?> = MutableStateFlow(null)
private var cacheSourceId: Long = 0L
private var cacheSourceName: String = "list"
private var cacheSourceType: String? = null
private var cacheSourceNavId: Long = 0L

fun Player.init(context: Context) {
    val currentPlayMediaId = context.dataStore[currentPlayMediaIdKey]
    val savedList = SongListUtil.loadSongList()
    // Restore the last queue's source metadata together with the list, so
    // the top-of-player source label is not blank after an app restart.
    // Written in lockstep with song_list.json by setPlaylist.
    val source = SongListUtil.loadSongSource()
    savedList?.let { songs ->
        if (currentMediaItems.isEmpty()) {
            setMediaItems(
                songs.toMediaItemList(
                    sourceId = source?.sourceId ?: 0L,
                    sourceName = source?.sourceName ?: "list",
                    sourceType = source?.sourceType,
                    navId = source?.navId ?: 0L
                )
            )
            val index =
                currentMediaItems.indexOfFirst { it.mediaId == currentPlayMediaId.toString() }
            if (index != -1)
                seekToDefaultPosition(index)
            prepare()
        }
    }
}

fun Player.setPlaylist(
    songs: List<Song>,
    sourceId: Long = 0L,
    sourceName: String = "list",
    sourceType: String? = null,
    navId: Long = 0L
) {
    if (cacheSongs.value != songs || cacheSourceId != sourceId || cacheSourceName != sourceName || cacheSourceType != sourceType || cacheSourceNavId != navId) {
        cacheSongs.value = songs
        cacheSourceId = sourceId
        cacheSourceName = sourceName
        cacheSourceType = sourceType
        cacheSourceNavId = navId
        setMediaItems(songs.toMediaItemList(sourceId = sourceId, sourceName = sourceName, sourceType = sourceType, navId = navId))
        SongListUtil.saveSongList(songs)
        SongListUtil.saveSongSource(
            SongSourceMeta(
                sourceId = sourceId,
                sourceName = sourceName,
                sourceType = sourceType,
                navId = navId
            )
        )
    }
}

fun Player.setCloudSongPlaylist(
    uid: Long,
    cloudSongs: List<CloudSong>,
    sourceName: String = "云盘音乐",
    sourceType: String? = null,
    navId: Long = 0L
) {
    cacheSongs.value = null
    cacheSourceId = 0L
    cacheSourceName = sourceName
    cacheSourceType = sourceType
    cacheSourceNavId = navId
    setMediaItems(cloudSongs.toCloudSongMediaItemList(uid = uid, sourceName = sourceName, sourceType = sourceType, navId = navId))
}

fun Player.setRadioPlaylist(
    radio: List<Radio>,
    sourceName: String = "电台",
    sourceType: String? = null,
    navId: Long = 0L
) {
    cacheSongs.value = null
    cacheSourceId = 0L
    cacheSourceName = sourceName
    cacheSourceType = sourceType
    cacheSourceNavId = navId
    setMediaItems(radio.toRadioMediaItemList(sourceName = sourceName, sourceType = sourceType, navId = navId))
}

fun Player.addSong(song: Song) {
    if (nextMediaItemIndex != C.INDEX_UNSET) {
        val songIndex = currentMediaItems.indexOfFirst { it.mediaId == song.id.toString() }
        if (songIndex != -1) {
            playMediaAt(songIndex)
        } else {
            addMediaItem(nextMediaItemIndex, song.toMediaItem())
            playMediaAt(nextMediaItemIndex)
            SongListUtil.saveSong(song, nextMediaItemIndex)
        }
    } else {
        setMediaItem(song.toMediaItem())
        playMediaAt()
        SongListUtil.saveSong(song)
    }
}

fun Player.addToPlaylist(song: Song) {
    if (currentMediaItems.isNotEmpty()) {
        val songIndex = currentMediaItems.indexOfFirst { it.mediaId == song.id.toString() }
        if (songIndex == -1) {
            addMediaItem(song.toMediaItem())
            SongListUtil.saveSong(song)
        }
    } else {
        setMediaItem(song.toMediaItem())
        SongListUtil.saveSong(song)
    }
}

fun Player.insertToPlaylist(song: Song) {
    if (nextMediaItemIndex != C.INDEX_UNSET) {
        val songIndex = currentMediaItems.indexOfFirst { it.mediaId == song.id.toString() }
        if (songIndex != -1) {
            moveMediaItem(songIndex, nextMediaItemIndex)
        } else {
            addMediaItem(nextMediaItemIndex, song.toMediaItem())
            SongListUtil.saveSong(song, nextMediaItemIndex)
        }
    } else {
        setMediaItem(song.toMediaItem())
        SongListUtil.saveSong(song)
    }
}

fun Player.removeSong(mediaId: String) {
    if (currentMediaItems.isNotEmpty()) {
        val songIndex = currentMediaItems.indexOfFirst { it.mediaId == mediaId }
        if (songIndex != -1) {
            removeMediaItem(songIndex)
            SongListUtil.removeSong(mediaId.toLong())
        }
    }
}

fun Player.playMediaAt(index: Int? = null) {
    if (index != -1)
        index?.let { seekToDefaultPosition(it) }
    prepare()
    play()
}

fun Player.playMediaAtId(id: Long? = null) {
    val index = currentMediaItems.indexOfFirst { it.mediaId == id.toString() }
    if (index != -1)
        seekToDefaultPosition(index)
    prepare()
    play()
}
