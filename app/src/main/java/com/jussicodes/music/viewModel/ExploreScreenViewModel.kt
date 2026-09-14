package com.jussicodes.music.viewModel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jussicodes.music.data.ExplorePreloader
import com.rcmiku.ncmapi.model.DailySongsResponse
import com.rcmiku.ncmapi.model.RecommendPlaylistResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Explore screen state is backed by [ExplorePreloader], which preloads the
 * same content at app startup so the explore page opens instantly.
 * [refresh] re-runs the preloader to fetch fresh data on demand.
 */
@HiltViewModel
class ExploreScreenViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    val recommendSongs: StateFlow<Result<DailySongsResponse>?>
        get() = ExplorePreloader.recommendSongs

    val recommendPlaylist: StateFlow<Result<RecommendPlaylistResponse>?>
        get() = ExplorePreloader.recommendPlaylist

    init {
        // Make sure the shared preloader actually ran for this process
        // (covers e.g. the process being created without the Application
        // scope observing the cookie flow yet).
        viewModelScope.launch {
            ExplorePreloader.warmUp(context)
        }
    }

    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            ExplorePreloader.warmUp(context, force = true)
            _refreshing.value = false
        }
    }
}
