package com.jussicodes.music.viewModel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rcmiku.ncmapi.api.account.AccountApi
import com.rcmiku.ncmapi.api.artist.ArtistApi
import com.rcmiku.ncmapi.model.Playlist
import com.rcmiku.ncmapi.model.UserDetailResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserScreenViewModel @Inject constructor(savedStateHandle: SavedStateHandle) : ViewModel() {
    private val userId = savedStateHandle.get<Long>("userId") ?: 0L

    private val _userDetail = MutableStateFlow<UserDetailResponse?>(null)
    val userDetail: StateFlow<UserDetailResponse?> = _userDetail.asStateFlow()

    private val _userPlaylists = MutableStateFlow<List<Playlist>>(emptyList())
    val userPlaylists: StateFlow<List<Playlist>> = _userPlaylists.asStateFlow()

    init {
        viewModelScope.launch {
            if (userId > 0) {
                _userDetail.value = ArtistApi.userDetail(userId).getOrNull()
                _userPlaylists.value = AccountApi.userPlaylist(
                    userId = userId,
                    userPlaylistType = com.rcmiku.ncmapi.api.account.UserPlaylistType.CREATE
                ).getOrNull()?.data?.playlist.orEmpty()
            }
        }
    }
}
