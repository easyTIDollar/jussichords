package com.jussicodes.music.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rcmiku.ncmapi.api.player.PlayerApi
import com.rcmiku.ncmapi.model.LyricResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LyricViewModel @Inject constructor() : ViewModel() {

    companion object {
        private val lyricCache = mutableMapOf<Long, LyricResponse?>()
    }

    private var currentMusicId: Long? = null

    private val _lyric = MutableStateFlow<LyricResponse?>(null)
    val lyric: StateFlow<LyricResponse?> = _lyric.asStateFlow()

    fun fetchLyric(musicId: Long) {
        if (currentMusicId == musicId && _lyric.value != null) return

        currentMusicId = musicId
        if (lyricCache.containsKey(musicId)) {
            _lyric.value = lyricCache[musicId]
            return
        }

        viewModelScope.launch {
            val response = PlayerApi.songLyric(musicId).getOrNull()
            if (currentMusicId == musicId) {
                lyricCache[musicId] = response
                _lyric.value = response
            }
        }
    }
}
