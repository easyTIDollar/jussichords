package com.jussicodes.music.viewModel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jussicodes.music.data.ExplorePreloader
import com.rcmiku.ncmapi.model.DailySongsResponse
import com.rcmiku.ncmapi.model.RecommendPlaylistResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 探索页复用 [ExplorePreloader] 的磁盘缓存，打开页面后按需获取新数据。
 * [refresh] 用于手动刷新。
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
        // 页面首次按需组合时才预取，解析与列表处理不占用主线程。
        viewModelScope.launch(Dispatchers.Default) {
            ExplorePreloader.warmUp(context)
        }
    }

    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            withContext(Dispatchers.Default) {
                ExplorePreloader.warmUp(context, force = true)
            }
            _refreshing.value = false
        }
    }
}
