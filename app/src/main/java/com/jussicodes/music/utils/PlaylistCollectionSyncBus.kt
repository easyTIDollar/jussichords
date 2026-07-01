package com.jussicodes.music.utils

import com.rcmiku.ncmapi.model.Playlist
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update

object PlaylistCollectionSyncBus {
    data class Event(
        val playlist: Playlist,
        val collected: Boolean
    )

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()
    private val localStates = MutableStateFlow<Map<Long, Boolean>>(emptyMap())
    private val localPlaylists = MutableStateFlow<Map<Long, Playlist>>(emptyMap())

    fun setCollected(playlist: Playlist, collected: Boolean) {
        val syncedPlaylist = playlist.copy(subscribed = collected)
        localStates.update { states -> states + (playlist.id to collected) }
        localPlaylists.update { playlists ->
            if (collected) playlists + (playlist.id to syncedPlaylist) else playlists - playlist.id
        }
        _events.tryEmit(Event(syncedPlaylist, collected))
    }

    fun overrideFor(playlistId: Long): Boolean? = localStates.value[playlistId]

    fun mergeInto(playlists: List<Playlist>): List<Playlist> {
        val states = localStates.value
        val localCollected = localPlaylists.value
        val filtered = playlists.filterNot { states[it.id] == false }
        val missingLocal = localCollected.values.filter { local ->
            filtered.none { it.id == local.id }
        }
        return filtered + missingLocal
    }
}
