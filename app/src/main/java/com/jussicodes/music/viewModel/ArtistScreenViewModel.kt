package com.jussicodes.music.viewModel

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.jussicodes.music.constants.userIdKye
import com.jussicodes.music.data.favoriteSongIdsDatastore
import com.jussicodes.music.paging.ArtistAlbumPagingSource
import com.jussicodes.music.paging.ArtistSongsPagingSource
import com.jussicodes.music.utils.ArtistCollectionSyncBus
import com.jussicodes.music.utils.FavoriteSongSyncBus
import com.jussicodes.music.utils.dataStore
import com.rcmiku.ncmapi.api.apiGet
import com.rcmiku.ncmapi.api.artist.ArtistApi
import com.rcmiku.ncmapi.model.ArtistHeadInfoResponse
import com.rcmiku.ncmapi.model.ArtistTopSong
import com.rcmiku.ncmapi.model.Song
import com.rcmiku.ncmapi.model.ArtistUser
import com.rcmiku.ncmapi.model.SearchArtist
import com.rcmiku.ncmapi.model.SimiArtistResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject

@HiltViewModel
class ArtistScreenViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val artistId = savedStateHandle.get<Long>("artistId")

    private val _artistHeadInfo = MutableStateFlow<ArtistHeadInfoResponse?>(null)
    val artistHeadInfo: StateFlow<ArtistHeadInfoResponse?> = _artistHeadInfo.asStateFlow()

    private val _artistTopSong = MutableStateFlow<ArtistTopSong?>(null)
    val artistTopSong: StateFlow<ArtistTopSong?> = _artistTopSong.asStateFlow()

    private val _songMode = MutableStateFlow("top")
    val songMode: StateFlow<String> = _songMode.asStateFlow()

    private val _sortOrder = MutableStateFlow("hot")
    val sortOrder: StateFlow<String> = _sortOrder.asStateFlow()

    private val _allSongsForQueue = MutableStateFlow<List<Song>?>(null)
    val allSongsForQueue: StateFlow<List<Song>?> = _allSongsForQueue.asStateFlow()

    val artistAllSongs: Flow<PagingData<Song>> = _sortOrder.flatMapLatest { order ->
        artistId?.let { id ->
            Pager(
                config = PagingConfig(
                    pageSize = 100,
                    prefetchDistance = 50,
                    enablePlaceholders = false
                ),
                pagingSourceFactory = { ArtistSongsPagingSource(id, order) }
            ).flow.cachedIn(viewModelScope)
        } ?: flowOf()
    }

    private val _simiArtists = MutableStateFlow<List<SearchArtist>>(emptyList())
    val simiArtists: StateFlow<List<SearchArtist>> = _simiArtists.asStateFlow()

    private val _isArtistSubscribed = MutableStateFlow(false)
    val isArtistSubscribed: StateFlow<Boolean> = _isArtistSubscribed.asStateFlow()

    private val _isArtistSubUpdating = MutableStateFlow(false)
    val isArtistSubUpdating: StateFlow<Boolean> = _isArtistSubUpdating.asStateFlow()

    private val _likedSongIds = MutableStateFlow<Set<Long>>(emptySet())
    val likedSongIds: StateFlow<Set<Long>> = _likedSongIds.asStateFlow()

    init {
        viewModelScope.launch {
            artistId?.let {
                val headInfo = ArtistApi.artistHeadInfo(it).getOrNull()
                _artistHeadInfo.value = headInfo
                _isArtistSubscribed.value = ArtistCollectionSyncBus.overrideFor(it)
                    ?: ArtistApi.artistFollowCount(it).getOrNull()?.data?.followed
                    ?: (headInfo?.data?.user?.followed == true)
                val topSong = ArtistApi.artistTopSong(it).getOrNull()
                _artistTopSong.value = topSong
                refreshLikedSongs(topSong?.songs?.map { song -> song.id }.orEmpty())
                _simiArtists.value = ArtistApi.simiArtist(it).getOrNull()?.artists.orEmpty()
            }
        }
        viewModelScope.launch {
            context.favoriteSongIdsDatastore.data.collect { ids ->
                val list = ids.songIdsList.orEmpty()
                if (list.isNotEmpty()) {
                    _likedSongIds.update { current -> current + list }
                }
            }
        }
        viewModelScope.launch {
            FavoriteSongSyncBus.events.collect { event ->
                _likedSongIds.update { current ->
                    if (event.liked) current + event.song.id else current - event.song.id
                }
            }
        }
    }

    /**
     * 登录态下用 /song/like/check 批量确认一批歌曲 id 的喜爱状态，结果并入 [likedSongIds]。
     * 响应结构未确认，用容忍型 JsonElement 提取。
     */
    private fun refreshLikedSongs(songIds: List<Long>) {
        val ids = songIds.distinct()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val loggedIn = context.dataStore.data.first()[userIdKye]?.takeIf { it > 0 } != null
            if (!loggedIn) return@launch
            val idsParam = ids.take(500).joinToString(",")
            val result = apiGet<JsonElement>(
                "/song/like/check",
                mapOf("ids" to "[$idsParam]")
            )
            val liked = result.getOrNull()?.let { extractLikedIds(it) } ?: return@launch
            if (liked.isNotEmpty()) {
                _likedSongIds.update { current -> current + liked }
            }
        }
    }

    private fun extractLikedIds(root: JsonElement, depth: Int = 0): Set<Long> {
        if (depth > 4) return emptySet()
        val out = LinkedHashSet<Long>()
        when (root) {
            is JsonArray -> root.forEach { out += extractLikedIds(it, depth + 1) }
            is JsonObject -> {
                (root["id"] as? JsonPrimitive)?.content?.toLongOrNull()?.let { out += it }
                (root["songId"] as? JsonPrimitive)?.content?.toLongOrNull()?.let { out += it }
                val container =
                    root["ids"] ?: root["list"] ?: root["result"] ?: root["data"] ?: root["songs"]
                when (container) {
                    is JsonArray -> container.forEach { element ->
                        if (element is JsonPrimitive) {
                            element.content.toLongOrNull()?.let { out += it }
                        } else if (element is JsonObject) {
                            out += extractLikedIds(element, depth + 1)
                        }
                    }
                    is JsonObject -> out += extractLikedIds(container, depth + 1)
                    else -> {}
                }
            }
            else -> {}
        }
        return out
    }

    fun setSongMode(mode: String) {
        if (mode == _songMode.value) return
        _songMode.value = mode
        if (mode == "all") {
            loadAllSongsForQueue()
        }
    }

    fun setSortOrder(order: String) {
        if (order == _sortOrder.value) return
        _sortOrder.value = order
        _allSongsForQueue.value = null
        loadAllSongsForQueue()
    }

    private fun loadAllSongsForQueue() {
        val id = artistId ?: return
        val order = _sortOrder.value
        viewModelScope.launch {
            _allSongsForQueue.value = null
            val songs = mutableListOf<Song>()
            var offset = 0
            val page = 100
            while (true) {
                val resp = ArtistApi.artistSongs(id, order = order, limit = page, offset = offset).getOrNull()
                    ?: break
                songs += resp.songs
                if (!resp.more || resp.songs.isEmpty()) break
                offset += page
            }
            _allSongsForQueue.value = songs
            refreshLikedSongs(songs.map { it.id })
        }
    }

    fun toggleArtistSub() {
        val id = artistId ?: return
        if (_isArtistSubUpdating.value) return
        val nextSubscribed = !_isArtistSubscribed.value
        viewModelScope.launch {
            _isArtistSubUpdating.value = true
            _isArtistSubscribed.value = nextSubscribed

            val artist = _artistHeadInfo.value?.data?.artist?.let { artist ->
                if (artist.id == 0L) artist.copy(id = id) else artist
            }
            artist?.let { ArtistCollectionSyncBus.setCollected(it, nextSubscribed) }
            _artistHeadInfo.value = _artistHeadInfo.value?.let { headInfo ->
                headInfo.copy(
                    data = headInfo.data.copy(
                        user = (headInfo.data.user ?: ArtistUser()).copy(followed = nextSubscribed)
                    )
                )
            }

            ArtistApi.artistSub(id, nextSubscribed)
                .onFailure {
                    _isArtistSubscribed.value = !nextSubscribed
                    artist?.let { ArtistCollectionSyncBus.setCollected(it, !nextSubscribed) }
                    _artistHeadInfo.value = _artistHeadInfo.value?.let { headInfo ->
                        headInfo.copy(
                            data = headInfo.data.copy(
                                user = (headInfo.data.user ?: ArtistUser()).copy(
                                    followed = !nextSubscribed
                                )
                            )
                        )
                    }
                    Toast.makeText(
                        context,
                        it.message ?: "歌手收藏失败，请稍后重试",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            _isArtistSubUpdating.value = false
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
