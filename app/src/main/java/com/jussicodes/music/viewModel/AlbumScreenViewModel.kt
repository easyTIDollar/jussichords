package com.jussicodes.music.viewModel

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jussicodes.music.utils.GreySongRegistry
import com.rcmiku.ncmapi.utils.CookieProvider
import com.rcmiku.ncmapi.api.album.AlbumApi
import com.rcmiku.ncmapi.api.player.OfficialPlayState
import com.rcmiku.ncmapi.api.player.PlayerApi
import com.rcmiku.ncmapi.model.AlbumDetailResponse
import com.rcmiku.ncmapi.model.AlbumInfoResponse
import com.rcmiku.ncmapi.model.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
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
                _albumDetail.value?.getOrNull()?.let { detail ->
                    probeGreySongs(albumId, detail.songs)
                }
                fetchAlbumInfo()
            }
        }
    }

    private fun probeGreySongs(albumId: Long, songs: List<Song>) {
        if (songs.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!CookieProvider.isLoggedIn()) return@launch
                GreySongRegistry.load(context)
                val semaphore = Semaphore(2)
                val probeSpacing = Mutex()
                // 只检查前 20 首，不补位；已标记歌曲不发请求。
                coroutineScope {
                    songs.take(20).distinctBy { it.id }.map { song ->
                        launch {
                            semaphore.withPermit {
                                ensureActive()
                                if (GreySongRegistry.isGrey(song.id)) return@withPermit
                                try {
                                    // 两个并发请求共用发起间隔，减少连续请求触发限流。
                                    probeSpacing.withLock { delay(150) }
                                    val confirmedNoCopyright = withTimeoutOrNull(4_000) {
                                        val firstState = PlayerApi.officialPlayState(song.id.toString(), song.fee)
                                        if (firstState != OfficialPlayState.NO_COPYRIGHT) {
                                            false
                                        } else {
                                            delay(500)
                                            ensureActive()
                                            PlayerApi.officialPlayState(song.id.toString(), song.fee) ==
                                                OfficialPlayState.NO_COPYRIGHT
                                        }
                                    }
                                    ensureActive()
                                    if (confirmedNoCopyright == true) {
                                        GreySongRegistry.markGrey(context, song.id)
                                    }
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (_: Exception) {
                                    // 单首失败静默跳过，继续检查其余歌曲。
                                }
                            }
                        }
                    }.joinAll()
                }
                ensureActive()
                // 分母是专辑已加载的全部歌曲，不是前 20 首样本。
                val greyIds = GreySongRegistry.greyIds()
                if (songs.count { it.id in greyIds } * 2 >= songs.size) {
                    GreySongRegistry.markGreyAlbum(context, albumId)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // 本地标记读取失败时结束探测，不影响专辑展示。
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
