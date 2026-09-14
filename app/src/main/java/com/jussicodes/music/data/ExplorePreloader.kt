package com.jussicodes.music.data

import android.content.Context
import com.jussicodes.music.utils.FavoriteSongIdsUtil
import com.rcmiku.ncmapi.api.account.AccountApi
import com.rcmiku.ncmapi.api.recommend.RecommendApi
import com.rcmiku.ncmapi.model.DailySongsResponse
import com.rcmiku.ncmapi.model.RecommendPlaylistResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Startup preloader for the explore page.
 *
 * As soon as the application boots (and the Netease cookie has been
 * applied) it fires the same API requests the explore screen would make
 * (daily recommended songs, personalized playlists, favorite song ids)
 * and keeps the results in shared StateFlows. [ExploreScreenViewModel]
 * consumes those flows directly, so by the time the user opens the
 * explore tab the content is already there.
 */
object ExplorePreloader {

    private val mutex = Mutex()

    private val _recommendSongs = MutableStateFlow<Result<DailySongsResponse>?>(null)
    val recommendSongs: StateFlow<Result<DailySongsResponse>?> = _recommendSongs.asStateFlow()

    private val _recommendPlaylist = MutableStateFlow<Result<RecommendPlaylistResponse>?>(null)
    val recommendPlaylist: StateFlow<Result<RecommendPlaylistResponse>?> =
        _recommendPlaylist.asStateFlow()

    /**
     * Preloads all explore page content. Safe to call more than once
     * (app start, cookie change, manual refresh); the [mutex] just
     * serializes concurrent calls.
     */
    suspend fun warmUp(context: Context) = mutex.withLock {
        AccountApi.favoriteSongIds().getOrNull()?.ids?.let { songIds ->
            FavoriteSongIdsUtil.updateSongIds(context, songIds)
        }
        _recommendSongs.value = RecommendApi.recommendSongs()
        _recommendPlaylist.value = RecommendApi.recommendPlaylist()
    }
}
