package com.jussicodes.music.utils

import com.rcmiku.ncmapi.model.SearchArtist
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update

object ArtistCollectionSyncBus {
    data class Event(
        val artist: SearchArtist,
        val collected: Boolean
    )

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()
    private val localStates = MutableStateFlow<Map<Long, Boolean>>(emptyMap())
    private val localArtists = MutableStateFlow<Map<Long, SearchArtist>>(emptyMap())

    fun setCollected(artist: SearchArtist, collected: Boolean) {
        localStates.update { states -> states + (artist.id to collected) }
        localArtists.update { artists ->
            if (collected) artists + (artist.id to artist) else artists - artist.id
        }
        _events.tryEmit(Event(artist, collected))
    }

    fun overrideFor(artistId: Long): Boolean? = localStates.value[artistId]

    fun mergeInto(artists: List<SearchArtist>): List<SearchArtist> {
        val states = localStates.value
        val localCollected = localArtists.value
        val filtered = artists.filterNot { states[it.id] == false }
        val missingLocal = localCollected.values.filter { local ->
            filtered.none { it.id == local.id }
        }
        return filtered + missingLocal
    }
}
