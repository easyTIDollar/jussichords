package com.jussicodes.music.utils

import com.rcmiku.ncmapi.model.PlaylistDetailResponse
import com.rcmiku.ncmapi.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object FavoriteSongSyncBus {
    private val _localSongs = MutableStateFlow<Map<Long, Song>>(emptyMap())
    val localSongs: StateFlow<Map<Long, Song>> = _localSongs.asStateFlow()

    fun setLiked(song: Song, liked: Boolean) {
        _localSongs.update { songs ->
            if (liked) {
                songs + (song.id to song)
            } else {
                songs - song.id
            }
        }
    }

    fun mergeInto(response: PlaylistDetailResponse): PlaylistDetailResponse {
        val serverTracks = response.playlist.getAllTracks()
        val localTracks = _localSongs.value.values.filter { localSong ->
            serverTracks.none { it.id == localSong.id }
        }
        val mergedTracks = localTracks + serverTracks
        return response.copy(
            playlist = response.playlist.copy(
                tracks = mergedTracks,
                trackCount = maxOf(response.playlist.trackCount, mergedTracks.size)
            )
        )
    }
}