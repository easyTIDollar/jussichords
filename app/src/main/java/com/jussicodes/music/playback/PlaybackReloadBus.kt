package com.jussicodes.music.playback

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 「换源后重放当前歌曲」的进程级信号。
 *
 * 换源不只改一个解析分支：ExoPlayer 的 `ResolvingDataSource` 会把解析出的直链
 * 连同 `cacheKey`（只含 url + 音质）一起缓存，音源变了但 cacheKey 不变的话，
 * 重新 prepare 仍会命中旧的直链。所以这里额外让 cacheKey 带上音源代号
 * （见 PlaybackService.setCacheKeyFactory），再由本信号触发重新 prepare。
 */
object PlaybackReloadBus {

    private val _requests = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val requests: SharedFlow<Unit> = _requests.asSharedFlow()

    /** 当前音源代号，参与 cacheKey 计算（由 app 侧在切换时写入）。 */
    @Volatile
    var sourceTag: String = "init"

    fun requestReload() {
        _requests.tryEmit(Unit)
    }
}