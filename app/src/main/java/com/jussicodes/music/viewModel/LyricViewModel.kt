package com.jussicodes.music.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jussicodes.music.lyric.client.AmllLyricsClient
import com.rcmiku.ncmapi.api.player.PlayerApi
import com.rcmiku.ncmapi.model.LyricResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
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
            val localLyric = runCatching {
                com.jussicodes.music.data.offline.OfflineMusicMetadataManager.getOfflineLyric(
                    com.jussicodes.music.JetMeloApp.context,
                    musicId
                )
            }.getOrNull()

            if (localLyric != null) {
                if (currentMusicId == musicId) {
                    lyricCache[musicId] = localLyric
                    _lyric.value = localLyric
                }
                return@launch
            }

            // 并发请求网易云官方与 AMLL 歌词库
            val ncmDeferred = async { PlayerApi.songLyric(musicId).getOrNull() }
            val amllDeferred = async { AmllLyricsClient.fetch(musicId) }

            val ncmLyric = ncmDeferred.await()
            if (currentMusicId == musicId && ncmLyric != null) {
                _lyric.value = ncmLyric
            }

            val amllLyric = amllDeferred.await()
            if (currentMusicId == musicId) {
                val merged = mergeLyricResponse(ncmLyric, amllLyric)
                if (merged != null) {
                    lyricCache[musicId] = merged
                    _lyric.value = merged
                } else if (ncmLyric != null) {
                    lyricCache[musicId] = ncmLyric
                }
            }
        }
    }

    private fun mergeLyricResponse(ncm: LyricResponse?, amll: LyricResponse?): LyricResponse? {
        if (amll != null && !amll.yrc?.lyric.isNullOrBlank()) {
            return LyricResponse(
                lrc = if (!amll.lrc?.lyric.isNullOrBlank()) amll.lrc else ncm?.lrc,
                tlyric = if (!amll.tlyric?.lyric.isNullOrBlank()) amll.tlyric else ncm?.tlyric,
                romalrc = ncm?.romalrc,
                klyric = ncm?.klyric,
                yrc = amll.yrc
            )
        }
        return ncm
    }
}
