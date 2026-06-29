package com.jussicodes.music.viewModel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.jussicodes.music.paging.ArtistAlbumPagingSource
import com.rcmiku.ncmapi.api.artist.ArtistApi
import com.rcmiku.ncmapi.model.ArtistHeadInfoResponse
import com.rcmiku.ncmapi.model.ArtistSongsResponse
import com.rcmiku.ncmapi.model.ArtistTopSong
import com.rcmiku.ncmapi.model.SearchArtist
import com.rcmiku.ncmapi.model.SimiArtistResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ArtistScreenViewModel @Inject constructor(savedStateHandle: SavedStateHandle) : ViewModel() {
    private val artistId = savedStateHandle.get<Long>("artistId")

    private val _artistHeadInfo = MutableStateFlow<ArtistHeadInfoResponse?>(null)
    val artistHeadInfo: StateFlow<ArtistHeadInfoResponse?> = _artistHeadInfo.asStateFlow()

    private val _artistTopSong = MutableStateFlow<ArtistTopSong?>(null)
    val artistTopSong: StateFlow<ArtistTopSong?> = _artistTopSong.asStateFlow()

    private val _artistAllSongs = MutableStateFlow<ArtistSongsResponse?>(null)
    val artistAllSongs: StateFlow<ArtistSongsResponse?> = _artistAllSongs.asStateFlow()

    private val _simiArtists = MutableStateFlow<List<SearchArtist>>(emptyList())
    val simiArtists: StateFlow<List<SearchArtist>> = _simiArtists.asStateFlow()

    init {
        viewModelScope.launch {
            artistId?.let {
                _artistHeadInfo.value = ArtistApi.artistHeadInfo(it).getOrNull()
                _artistTopSong.value = ArtistApi.artistTopSong(it).getOrNull()
                _artistAllSongs.value = ArtistApi.artistSongs(it).getOrNull()
                _simiArtists.value = ArtistApi.simiArtist(it).getOrNull()?.artists.orEmpty()
            }
        }
    }

    val artistAlbumList = artistId?.let { id ->
        Pager(
            config = PagingConfig(
                pageSize = 100,
                prefetchDistance = 50,
                enablePlaceholders = false
            ),
            pagingSourceFactory = { ArtistAlbumPagingSource(id) }
        ).flow.cachedIn(viewModelScope)
    } ?: flowOf()
}
