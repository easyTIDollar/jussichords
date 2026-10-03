package com.jussicodes.music.viewModel

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rcmiku.ncmapi.api.album.AlbumApi
import com.rcmiku.ncmapi.model.AlbumDetailResponse
import com.rcmiku.ncmapi.model.AlbumInfoResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlbumScreenViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context
) :
    ViewModel() {
    private val albumId = savedStateHandle.get<Long>("albumId")

    private val _albumDetail =
        MutableStateFlow<Result<AlbumDetailResponse>?>(null)
    val albumDetail: StateFlow<Result<AlbumDetailResponse>?> =
        _albumDetail.asStateFlow()
    private val _albumInfo =
        MutableStateFlow<AlbumInfoResponse?>(null)
    val albumInfo: StateFlow<AlbumInfoResponse?> =
        _albumInfo.asStateFlow()

    init {
        viewModelScope.launch {
            albumId?.let {
                _albumDetail.value = AlbumApi.albumDetail(albumId)
                fetchAlbumInfo()
            }
        }
    }

    private fun fetchAlbumInfo() {
        viewModelScope.launch {
            albumId?.let {
                _albumInfo.value = AlbumApi.albumInfo(albumId).getOrNull()
            }
        }
    }

    fun albumSub(isSub: Boolean) {
        viewModelScope.launch {
            albumId?.let {
                AlbumApi.albumSub(id = albumId, isSub = isSub)
                    .onSuccess {
                        fetchAlbumInfo()
                        Toast.makeText(
                            context,
                            if (isSub) "已收藏专辑" else "已取消收藏",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .onFailure {
                        Toast.makeText(
                            context,
                            "专辑收藏同步失败",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
        }
    }

}
