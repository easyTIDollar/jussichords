package com.jussicodes.music.utils

import com.rcmiku.ncmapi.model.PlaylistDetailResponse
import com.rcmiku.ncmapi.model.Playlist
import com.rcmiku.ncmapi.model.Song
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update

object FavoriteSongSyncBus {
    data class Event(val song: Song, val liked: Boolean)

    private val _localSongs = MutableStateFlow<Map<Long, Song>>(emptyMap())
    val localSongs: StateFlow<Map<Long, Song>> = _localSongs.asStateFlow()
    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 64)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    fun setLiked(song: Song, liked: Boolean) {
        _localSongs.update { songs ->
            if (liked) {
                songs + (song.id to song)
            } else {
                songs - song.id
            }
        }
        _events.tryEmit(Event(song, liked))
    }

    fun mergeInto(
        response: PlaylistDetailResponse,
        likedSongIds: Collection<Long>
    ): PlaylistDetailResponse {
        val likedIds = likedSongIds.toSet()
        val serverTracks = response.playlist.getAllTracks().filter { serverSong ->
            likedIds.isEmpty() || serverSong.id in likedIds
        }
        val localTracks = _localSongs.value.values.toList().asReversed().filter { localSong ->
            (likedIds.isEmpty() || localSong.id in likedIds) &&
            serverTracks.none { it.id == localSong.id }
        }
        val mergedTracks = localTracks + serverTracks
        val mergedTrackCount = likedIds.takeIf { it.isNotEmpty() }?.size
            ?: maxOf(response.playlist.trackCount, mergedTracks.size)
        val coverUrl = mergedTracks.firstOrNull()?.al?.picUrl?.takeIf { it.isNotBlank() }
        return response.copy(
            playlist = response.playlist.copy(
                coverImgUrl = coverUrl ?: response.playlist.coverImgUrl,
                tracks = mergedTracks,
                trackCount = mergedTrackCount
            )
        )
    }

    fun mergeIntoFavoritePlaylist(playlist: Playlist, likedSongCount: Int): Playlist {
        val localSongs = _localSongs.value.values.toList()
        val coverUrl = localSongs.lastOrNull()?.al?.picUrl?.takeIf { it.isNotBlank() }
        return playlist.copy(
            coverImgUrl = coverUrl ?: playlist.coverImgUrl,
            trackCount = likedSongCount.coerceAtLeast(0)
        )
    }
}
