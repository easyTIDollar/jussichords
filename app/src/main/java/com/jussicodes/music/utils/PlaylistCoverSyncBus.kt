package com.jussicodes.music.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object PlaylistCoverSyncBus {
    private val _versions = MutableStateFlow<Map<Long, Long>>(emptyMap())
    val versions = _versions.asStateFlow()

    fun markUpdated(playlistId: Long, version: Long = System.currentTimeMillis()) {
        _versions.update { versions -> versions + (playlistId to version) }
    }
}
