package com.jussicodes.music.viewModel

import android.content.Context
import android.widget.Toast
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jussicodes.music.constants.libraryPlaylistRefreshTokenKey
import com.jussicodes.music.data.favoriteSongIdsDatastore
import com.jussicodes.music.data.offline.PlaylistSnapshotStore
import com.jussicodes.music.utils.FavoriteSongSyncBus
import com.jussicodes.music.utils.PlaylistCollectionSyncBus
import com.jussicodes.music.utils.PlaylistCoverSyncBus
import com.jussicodes.music.utils.dataStore
import com.rcmiku.ncmapi.api.playlist.PlaylistApi
import com.rcmiku.ncmapi.model.Playlist
import com.rcmiku.ncmapi.model.PlaylistDetailResponse
import com.rcmiku.ncmapi.model.PlaylistInfoResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val LIKED_PLAYLIST_SPECIAL_TYPE = 5

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
        // 优先恢复本地持久化快照：秒开展示，断网/离线状态下列表完全可用
        playlistId?.let { id ->
            PlaylistSnapshotStore.getSnapshot(context, id)?.let { cached ->
                _playlistDetail.value = cached
            }
            PlaylistSnapshotStore.getInfoSnapshot(context, id)?.let { cachedInfo ->
                _playlistInfo.value = cachedInfo
            }
        }

        viewModelScope.launch {
            if (noCache)
                fetchWithObserver()
            else
                playlistId?.let {
                    limit?.let {
                        val result = PlaylistApi.playlistDetail(
                            id = playlistId,
                            limit = limit,
                        ).getOrNull()
                        if (result != null) {
                            _playlistDetail.value = result
                            PlaylistSnapshotStore.saveSnapshot(context, playlistId, result)
                        }
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
                playlistId?.let { id ->
                    val remote = PlaylistApi.playlistV6Detail(id = id).getOrNull()
                    if (remote != null) {
                        val merged = FavoriteSongSyncBus.mergeInto(remote, favoriteSongIds)
                        _playlistDetail.value = merged
                        PlaylistSnapshotStore.saveSnapshot(context, id, merged)
                    } else {
                        // 离线/网络失败：利用本地快照合并当前已喜欢状态
                        _playlistDetail.value?.let { current ->
                            _playlistDetail.value = FavoriteSongSyncBus.mergeInto(current, favoriteSongIds)
                        }
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
                PlaylistApi.playlistInfo(currentPlaylistId).getOrNull()?.let { info ->
                    val finalInfo = PlaylistCollectionSyncBus.overrideFor(currentPlaylistId)?.let { subscribed ->
                        info.copy(subscribed = subscribed)
                    } ?: info
                    _playlistInfo.value = finalInfo
                    PlaylistSnapshotStore.saveInfoSnapshot(context, currentPlaylistId, finalInfo)
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
                if (playlist?.specialType == LIKED_PLAYLIST_SPECIAL_TYPE) {
                    Toast.makeText(context, "喜欢歌曲列表不能收藏或取消收藏", Toast.LENGTH_SHORT).show()
                    return@launch
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

                PlaylistApi.playlistSub(id = it, isSub = shouldSubscribe)
                    .onSuccess {
                        Toast.makeText(
                            context,
                            if (shouldSubscribe) "已收藏歌单" else "已取消收藏",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .onFailure {
                    _playlistInfo.value = previousPlaylistInfo
                    playlist?.let { item ->
                        PlaylistCollectionSyncBus.setCollected(item, !shouldSubscribe)
                    }
                    context.dataStore.edit { prefs ->
                        prefs[libraryPlaylistRefreshTokenKey] = System.currentTimeMillis()
                    }
                    Toast.makeText(context, "歌单收藏同步失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun updatePlaylistInfo(name: String, description: String, onResult: (Boolean) -> Unit = {}) {
        val currentPlaylistId = playlistId ?: return onResult(false)
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            onResult(false)
            return
        }
        viewModelScope.launch {
            val previousDetail = _playlistDetail.value
            _playlistDetail.value = previousDetail?.copy(
                playlist = previousDetail.playlist.copy(
                    name = trimmedName,
                    description = description
                )
            )
            val nameSuccess = if (previousDetail?.playlist?.name != trimmedName) {
                PlaylistApi.updatePlaylistName(currentPlaylistId, trimmedName).isSuccess
            } else {
                true
            }
            val descriptionSuccess = if (previousDetail?.playlist?.description != description) {
                PlaylistApi.updatePlaylistDescription(currentPlaylistId, description).isSuccess
            } else {
                true
            }
            val success = nameSuccess && descriptionSuccess
            if (success) {
                refreshLibraryPlaylists()
            } else {
                _playlistDetail.value = previousDetail
            }
            onResult(success)
        }
    }

    fun uploadPlaylistCover(file: File, onResult: (Boolean) -> Unit = {}) {
        val currentPlaylistId = playlistId ?: return onResult(false)
        viewModelScope.launch {
            val success = PlaylistApi.updatePlaylistCover(currentPlaylistId, file).isSuccess
            if (success) {
                PlaylistCoverSyncBus.markUpdated(currentPlaylistId)
                PlaylistApi.clearPlaylistCaches(currentPlaylistId)
                refreshPlaylistDetail()
                refreshLibraryPlaylists()
            }
            onResult(success)
        }
    }

    fun reorderSongs(songs: List<com.rcmiku.ncmapi.model.Song>, onResult: (Boolean) -> Unit = {}) {
        val currentPlaylistId = playlistId ?: return onResult(false)
        viewModelScope.launch {
            val previousDetail = _playlistDetail.value
            _playlistDetail.value = previousDetail?.copy(
                playlist = previousDetail.playlist.copy(
                    tracks = songs,
                    songs = songs
                )
            )
            val success = PlaylistApi.updateSongOrder(
                playlistId = currentPlaylistId,
                ids = songs.map { it.id }
            ).isSuccess
            if (success) {
                refreshLibraryPlaylists()
            } else {
                _playlistDetail.value = previousDetail
            }
            onResult(success)
        }
    }

    fun removeSongsFromPlaylist(songIds: List<Long>, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val playlist = _playlistDetail.value?.playlist
            val isLikedSpecial = playlist?.specialType == LIKED_PLAYLIST_SPECIAL_TYPE
            val success = if (isLikedSpecial) {
                var allSuccess = true
                for (id in songIds) {
                    val res = com.rcmiku.ncmapi.api.account.AccountApi.songLike(like = false, songId = id)
                    if (!res.isSuccess) allSuccess = false
                }
                context.favoriteSongIdsDatastore.updateData { pref ->
                    pref.toBuilder()
                        .clearSongIds()
                        .addAllSongIds(pref.songIdsList.filter { it !in songIds })
                        .build()
                }
                allSuccess
            } else {
                val pid = playlist?.id ?: return@launch
                val res = com.rcmiku.ncmapi.api.account.AccountApi.playlistManipulate(
                    pid,
                    songIds,
                    com.rcmiku.ncmapi.api.account.PlayManipulateType.DEL
                )
                res.isSuccess
            }
            if (success) {
                playlist?.id?.let { PlaylistApi.clearPlaylistCaches(it) }
                refreshPlaylistDetail()
                refreshLibraryPlaylists()
            }
            onResult(success)
        }
    }

    private fun refreshPlaylistDetail() {
        viewModelScope.launch {
            playlistId?.let { id ->
                PlaylistApi.playlistV6Detail(id).getOrNull()?.let { detail ->
                    _playlistDetail.value = detail
                    PlaylistSnapshotStore.saveSnapshot(context, id, detail)
                }
            }
        }
    }

    /** 重新拉取歌单详情（用于「从歌单移除」成功后的即时刷新）。 */
    fun refresh() {
        refreshPlaylistDetail()
    }

    private suspend fun refreshLibraryPlaylists() {
        context.dataStore.edit { prefs ->
            prefs[libraryPlaylistRefreshTokenKey] = System.currentTimeMillis()
        }
    }

}
