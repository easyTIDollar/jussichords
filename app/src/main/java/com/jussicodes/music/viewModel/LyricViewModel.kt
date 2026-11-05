package com.jussicodes.music.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jussicodes.music.lyric.client.AmllLyricsClient
import com.jussicodes.music.lyric.client.QqLyricsClient
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
        private val lyricCache = mutableMapOf<String, LyricResponse?>()
    }

    private var currentMusicId: Long? = null

    private val _lyric = MutableStateFlow<LyricResponse?>(null)
    val lyric: StateFlow<LyricResponse?> = _lyric.asStateFlow()

    fun fetchLyric(
        musicId: Long,
        title: String? = null,
        artist: String? = null,
        durationMs: Long = 0L,
        source: String = "auto",
        forceRefresh: Boolean = false
    ) {
        val cacheKey = "${musicId}_$source"
        if (!forceRefresh && currentMusicId == musicId && _lyric.value != null) return

        currentMusicId = musicId
        if (!forceRefresh && lyricCache.containsKey(cacheKey)) {
            _lyric.value = lyricCache[cacheKey]
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
                    lyricCache[cacheKey] = localLyric
                    _lyric.value = localLyric
                }
                return@launch
            }

            when (source) {
                "ncm" -> {
                    val ncmLyric = PlayerApi.songLyric(musicId).getOrNull()
                    if (currentMusicId == musicId && ncmLyric != null) {
                        lyricCache[cacheKey] = ncmLyric
                        _lyric.value = ncmLyric
                    }
                }
                "amll" -> {
                    val ncmDeferred = async { PlayerApi.songLyric(musicId).getOrNull() }
                    val amllDeferred = async { AmllLyricsClient.fetch(musicId) }
                    val ncmLyric = ncmDeferred.await()
                    val amllLyric = amllDeferred.await()
                    val result = if (amllLyric != null && !amllLyric.yrc?.lyric.isNullOrBlank()) {
                        mergeLyricResponse(ncmLyric, amllLyric)
                    } else {
                        ncmLyric
                    }
                    if (currentMusicId == musicId && result != null) {
                        lyricCache[cacheKey] = result
                        _lyric.value = result
                    }
                }
                "qq" -> {
                    val ncmDeferred = async { PlayerApi.songLyric(musicId).getOrNull() }
                    val qqDeferred = async {
                        if (!title.isNullOrBlank()) QqLyricsClient.fetch(title, artist, durationMs) else null
                    }
                    val ncmLyric = ncmDeferred.await()
                    val qqLyric = qqDeferred.await()
                    val result = if (qqLyric != null && (!qqLyric.yrc?.lyric.isNullOrBlank() || ncmLyric == null)) {
                        mergeLyricResponse(ncmLyric, qqLyric)
                    } else {
                        ncmLyric
                    }
                    if (currentMusicId == musicId && result != null) {
                        lyricCache[cacheKey] = result
                        _lyric.value = result
                    }
                }
                else -> {
                    // 并发请求网易云官方与 AMLL 歌词库
                    val ncmDeferred = async { PlayerApi.songLyric(musicId).getOrNull() }
                    val amllDeferred = async { AmllLyricsClient.fetch(musicId) }

                    val ncmLyric = ncmDeferred.await()
                    if (currentMusicId == musicId && ncmLyric != null) {
                        _lyric.value = ncmLyric
                    }

                    val amllLyric = amllDeferred.await()
                    if (currentMusicId == musicId && amllLyric != null && !amllLyric.yrc?.lyric.isNullOrBlank()) {
                        val mergedAmll = mergeLyricResponse(ncmLyric, amllLyric)
                        lyricCache[cacheKey] = mergedAmll
                        _lyric.value = mergedAmll
                        return@launch
                    }

                    // 若 AMLL 与 网易官方均无逐字歌词，且有歌曲元数据，尝试 QQ 音乐歌词源
                    val hasNcmYrc = !ncmLyric?.yrc?.lyric.isNullOrBlank()
                    if (!hasNcmYrc && !title.isNullOrBlank()) {
                        val qqLyric = QqLyricsClient.fetch(title, artist, durationMs)
                        if (currentMusicId == musicId && qqLyric != null && (!qqLyric.yrc?.lyric.isNullOrBlank() || ncmLyric == null)) {
                            val mergedQq = mergeLyricResponse(ncmLyric, qqLyric)
                            lyricCache[cacheKey] = mergedQq
                            _lyric.value = mergedQq
                            return@launch
                        }
                    }

                    // 最终落位网易歌词（若无逐字则至少有普通 LRC）
                    if (currentMusicId == musicId && ncmLyric != null) {
                        lyricCache[cacheKey] = ncmLyric
                        _lyric.value = ncmLyric
                    }
                }
            }
        }
    }

    fun switchLyricSource(
        source: String,
        musicId: Long,
        title: String? = null,
        artist: String? = null,
        durationMs: Long = 0L
    ) {
        currentMusicId = null
        fetchLyric(
            musicId = musicId,
            title = title,
            artist = artist,
            durationMs = durationMs,
            source = source,
            forceRefresh = true
        )
    }

    private fun mergeLyricResponse(base: LyricResponse?, overlay: LyricResponse?): LyricResponse? {
        if (overlay == null) return base
        if (base == null) return overlay

        return LyricResponse(
            lrc = if (!overlay.lrc?.lyric.isNullOrBlank()) overlay.lrc else base.lrc,
            tlyric = if (!base.tlyric?.lyric.isNullOrBlank()) base.tlyric else overlay.tlyric,
            romalrc = base.romalrc,
            klyric = base.klyric,
            yrc = if (!overlay.yrc?.lyric.isNullOrBlank()) overlay.yrc else base.yrc
        )
    }
}
