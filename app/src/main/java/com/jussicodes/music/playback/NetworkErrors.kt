package com.jussicodes.music.playback

import androidx.media3.common.PlaybackException

/**
 * 弱网可自愈的播放错误：网络/IO 类，以及我们解析播放地址失败时自己抛的 REMOTE_ERROR。
 *
 * 这些错误交给 [PlaybackService] 里的恢复逻辑处理（同曲退避重试 + 网络恢复后续播），
 * 不要在 UI 侧直接跳下一首，否则两条路径会互相打架。
 */
internal fun isRetryableNetworkError(error: PlaybackException): Boolean =
    when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
        PlaybackException.ERROR_CODE_TIMEOUT,
        PlaybackException.ERROR_CODE_REMOTE_ERROR -> true

        else -> false
    }