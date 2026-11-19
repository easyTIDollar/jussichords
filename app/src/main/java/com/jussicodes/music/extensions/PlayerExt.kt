package com.jussicodes.music.extensions

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.jussicodes.music.constants.currentPlayMediaIdKey
import com.jussicodes.music.utils.SongListUtil
import com.jussicodes.music.utils.SongSourceMeta
import com.jussicodes.music.utils.dataStore
import com.rcmiku.ncmapi.model.CloudSong
import com.rcmiku.ncmapi.model.Radio
import com.rcmiku.ncmapi.model.Song
import com.rcmiku.ncmapi.utils.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal val Player.currentMediaItems: List<MediaItem>
    get() {
        return List(mediaItemCount, ::getMediaItemAt)
    }

internal val cacheSongs: MutableStateFlow<List<Song>?> = MutableStateFlow(null)
private var cacheSourceId: Long = 0L
private var cacheSourceName: String = "list"
private var cacheSourceType: String? = null
private var cacheSourceNavId: Long = 0L

private val playerInitScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

internal const val RESTORED_QUEUE_EXTRA = "com.jussicodes.music.RESTORED_QUEUE"

fun Player.init(context: Context) {
    playerInitScope.launch {
        val currentPlayMediaId = runCatching {
            context.dataStore.data.first()[currentPlayMediaIdKey]
        }.getOrNull()
        val savedList = SongListUtil.loadSongList()
        // Restore the last queue's source metadata together with the list, so
        // the top-of-player source label is not blank after an app restart.
        // Written in lockstep with song_list.json by setPlaylist.
        val source = SongListUtil.loadSongSource()
        savedList?.let { songs ->
            val mediaItems = songs.toMediaItemList(
                sourceId = source?.sourceId ?: 0L,
                sourceName = source?.sourceName ?: "list",
                sourceType = source?.sourceType,
                navId = source?.navId ?: 0L
            ).toMutableList()
            // 标记随队列命令送达服务，避免控制器异步转发时过早解除恢复保护。
            mediaItems.firstOrNull()?.let { first ->
                val extras = Bundle(first.mediaMetadata.extras ?: Bundle()).apply {
                    putBoolean(RESTORED_QUEUE_EXTRA, true)
                }
                mediaItems[0] = first.buildUpon()
                    .setMediaMetadata(first.mediaMetadata.buildUpon().setExtras(extras).build())
                    .build()
            }
            withContext(Dispatchers.Main) {
                if (currentMediaItems.isEmpty()) {
                    setMediaItems(mediaItems)
                    val index =
                        currentMediaItems.indexOfFirst { it.mediaId == currentPlayMediaId.toString() }
                    if (index != -1)
                        seekToDefaultPosition(index)
                    prepare()
                }
            }
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

/**
 * 保留当前曲目、只替换其后所有未播项（私人 FM 切模式用）：正在播放的这首不打断，
 * 把「接下来听什么」换成新一批；队列同步回写磁盘，避免重启后恢复成旧列表。
 */
fun Player.replaceUpcoming(
    songs: List<Song>,
    sourceName: String = "list",
    sourceType: String? = null,
    navId: Long = 0L
) {
    val curIndex = currentMediaItemIndex
    if (curIndex == C.INDEX_UNSET || mediaItemCount == 0) {
        setPlaylist(songs, sourceName = sourceName, sourceType = sourceType, navId = navId)
        return
    }
    // 只排除正在播的这首（免得新批次把它又排到后面重复播放）。
    // 不做全量去重：MediaController 的队列命令是异步下发的，这里读到的 mediaItemCount
    // 还是旧值，拿去和队列比对会把该追加的歌误判成「已在队列」。
    val currentId = currentMediaItem?.mediaId
    val fresh = songs.filter { it.id.toString() != currentId }
    if (mediaItemCount > curIndex + 1) {
        // remove 先于 add 落地，范围按当前（旧）队列算是对的
        removeMediaItems(curIndex + 1, mediaItemCount)
    }
    if (fresh.isNotEmpty()) {
        addMediaItems(
            fresh.toMediaItemList(sourceName = sourceName, sourceType = sourceType, navId = navId)
        )
    }
    // 队列快照要等命令落地后再读，否则写回磁盘的是替换前的旧队列
    Handler(Looper.getMainLooper()).post {
        syncQueueSnapshot(sourceName, sourceType, navId)
    }
}

/** 把播放器队列回写成磁盘队列（[replaceUpcoming] 走 addMediaItems，不经过 [setPlaylist]）。 */
private fun Player.syncQueueSnapshot(sourceName: String, sourceType: String?, navId: Long) {
    val queued = (0 until mediaItemCount).mapNotNull { index ->
        getMediaItemAt(index).mediaMetadata.extras?.getString("song")
            ?.let { runCatching { json.decodeFromString<Song>(it) }.getOrNull() }
    }
    if (queued.isEmpty()) return
    cacheSongs.value = queued
    cacheSourceName = sourceName
    cacheSourceType = sourceType
    cacheSourceNavId = navId
    SongListUtil.saveSongList(queued)
    SongListUtil.saveSongSource(
        SongSourceMeta(
            sourceId = cacheSourceId,
            sourceName = sourceName,
            sourceType = sourceType,
            navId = navId
        )
    )
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
