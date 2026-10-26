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
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
        PlaybackException.ERROR_CODE_TIMEOUT,
        PlaybackException.ERROR_CODE_REMOTE_ERROR -> true

        else -> false
    }

/**
 * 「这条音源本身是坏的」类错误：和网络抖动无关，重试同一条直链永远不会好。
 *
 * 第三方音源常见的坏形态是 404 / 返回 HTML 反爬页 / 返回空 body / 内容不是音频，
 * 对应下面的解析与 IO 错误码。这类错误应当**换源重试**（拉黑这条链再解析下一个），
 * 而不是同曲退避重试，也不能当成「网络异常」提示用户。
 */
internal fun isBadSourceError(error: PlaybackException): Boolean =
    when (error.errorCode) {
        // 403/404 常见于 CDN token 过期或反爬页；换一条新解析出来的直链往往就好了，
        // 所以归「坏源」而不是「弱网」——退避重试同一条 url 永远失败。
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
        PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
        PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> true

        else -> false
    }
