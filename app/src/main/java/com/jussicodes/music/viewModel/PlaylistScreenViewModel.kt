package com.jussicodes.music.viewModel

import android.content.Context
import android.widget.Toast
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jussicodes.music.constants.libraryPlaylistRefreshTokenKey
import com.jussicodes.music.data.favoriteSongIdsDatastore
import com.jussicodes.music.utils.FavoriteSongSyncBus
import com.jussicodes.music.utils.PlaylistCollectionSyncBus
import com.jussicodes.music.utils.dataStore
import com.rcmiku.ncmapi.api.playlist.PlaylistApi
import com.rcmiku.ncmapi.model.Playlist
import com.rcmiku.ncmapi.model.PlaylistDetailResponse
import com.rcmiku.ncmapi.model.PlaylistInfoResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistScreenViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context
) :
    ViewModel() {
    private val playlistId = savedStateHandle.get<Long>("playlistId")
    private val limit = savedStateHandle.get<Int>("limit")
    private val noCache = savedStateHandle.get<Boolean>("noCache") ?: false
    private var favoriteSongIds: List<Long> = emptyList()
    private val _playlistDetail =
        MutableStateFlow<PlaylistDetailResponse?>(null)
    val playlistDetail: StateFlow<PlaylistDetailResponse?> =
        _playlistDetail.asStateFlow()

    private val _playlistInfo =
        MutableStateFlow<PlaylistInfoResponse?>(null)
    val playlistInfo: StateFlow<PlaylistInfoResponse?> =
        _playlistInfo.asStateFlow()

    init {
        viewModelScope.launch {
            if (noCache)
                fetchWithObserver()
            else
                playlistId?.let {
                    limit?.let {
                        _playlistDetail.value = PlaylistApi.playlistDetail(
                            id = playlistId,
                            limit = limit,
                        ).getOrNull()
                    }
                    fetchPlaylistInfo()
                }
        }
        if (noCache) {
            observeLocalFavoriteSongs()
        }
    }

    private fun fetchWithObserver() {
        viewModelScope.launch {
            context.favoriteSongIdsDatastore.data.distinctUntilChanged().collectLatest { favoriteSongs ->
                favoriteSongIds = favoriteSongs.songIdsList
                playlistId?.let {
                    _playlistDetail.value = PlaylistApi.playlistV6Detail(
                        id = playlistId,
                    ).getOrNull()?.let { response ->
                        FavoriteSongSyncBus.mergeInto(response, favoriteSongIds)
                    }
                }
            }
        }
    }

    private fun observeLocalFavoriteSongs() {
        viewModelScope.launch {
            FavoriteSongSyncBus.localSongs.collectLatest {
                _playlistDetail.value = _playlistDetail.value?.let { response ->
                    FavoriteSongSyncBus.mergeInto(response, favoriteSongIds)
                }
            }
        }
    }

    private fun fetchPlaylistInfo() {
        viewModelScope.launch {
            playlistId?.let { currentPlaylistId ->
                _playlistInfo.value = PlaylistApi.playlistInfo(currentPlaylistId).getOrNull()?.let { info ->
                    PlaylistCollectionSyncBus.overrideFor(currentPlaylistId)?.let { subscribed ->
                        info.copy(subscribed = subscribed)
                    } ?: info
                }
            }
        }
    }

    fun playlistSub(shouldSubscribe: Boolean) {
        viewModelScope.launch {
            playlistId?.let {
                val previousPlaylistInfo = _playlistInfo.value
                val playlist = previousPlaylistInfo?.playlist?.takeIf { playlist -> playlist.id != 0L }
                    ?: _playlistDetail.value?.playlist?.let { detail ->
                        Playlist(
                            id = detail.id,
                            name = detail.name,
                            coverImgUrl = detail.coverImgUrl,
                            playCount = detail.playCount,
                            trackCount = detail.trackCount,
                            creator = detail.creator,
                            description = detail.description,
                            subscribed = shouldSubscribe
                        )
                    }
                _playlistInfo.value = previousPlaylistInfo?.copy(
                    subscribed = shouldSubscribe
                ) ?: PlaylistInfoResponse(subscribed = shouldSubscribe)
                playlist?.let { item ->
                    PlaylistCollectionSyncBus.setCollected(item, shouldSubscribe)
                }

                context.dataStore.edit { prefs ->
                    prefs[libraryPlaylistRefreshTokenKey] = System.currentTimeMillis()
                }

                PlaylistApi.playlistSub(id = it, isSub = shouldSubscribe).onFailure {
                    Toast.makeText(context, "歌单收藏同步失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

}
