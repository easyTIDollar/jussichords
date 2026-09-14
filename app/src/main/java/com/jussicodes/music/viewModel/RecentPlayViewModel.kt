package com.jussicodes.music.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rcmiku.ncmapi.api.recent.RecentListenApi
import com.rcmiku.ncmapi.model.RecentAlbumResource
import com.rcmiku.ncmapi.model.RecentPlaylistResource
import com.rcmiku.ncmapi.model.RecentSongResource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 最近播放页。打开即并行拉取歌曲/歌单/专辑三块数据，顶部 tab 切换时无需再请求。
 */
@HiltViewModel
class RecentPlayViewModel @Inject constructor() : ViewModel() {

    private val _songs = MutableStateFlow<List<RecentSongResource>?>(null)
    private val _playlists = MutableStateFlow<List<RecentPlaylistResource>?>(null)
    private val _albums = MutableStateFlow<List<RecentAlbumResource>?>(null)

    val songs: StateFlow<List<RecentSongResource>?> = _songs.asStateFlow()
    val playlists: StateFlow<List<RecentPlaylistResource>?> = _playlists.asStateFlow()
    val albums: StateFlow<List<RecentAlbumResource>?> = _albums.asStateFlow()

    init {
        viewModelScope.launch {
            RecentListenApi.recentSongs()
                .onSuccess { _songs.value = it.data.list }
                .onFailure { _songs.value = emptyList() }
        }
        viewModelScope.launch {
            RecentListenApi.recentPlaylists()
                .onSuccess { _playlists.value = it.data.list }
                .onFailure { _playlists.value = emptyList() }
        }
        viewModelScope.launch {
            RecentListenApi.recentAlbums()
                .onSuccess { _albums.value = it.data.list }
                .onFailure { _albums.value = emptyList() }
        }
    }
}
