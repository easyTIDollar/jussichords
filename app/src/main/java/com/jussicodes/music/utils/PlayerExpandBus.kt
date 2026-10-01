package com.jussicodes.music.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 请求把常驻迷你播放条展开成全屏播放界面的跨屏幕信号（单调递增令牌）。
 *
 * 典型触发：从私信「一起听」卡片进房成功后，[com.jussicodes.music.playback.ListenTogetherSession]
 * 会 [expand] 一次，播放界面（PlayerTransform）随之展开，用户直接落在播放页而非停在聊天页。
 * 0 表示尚未请求过；每次 expand 递增。
 */
object PlayerExpandBus {
    private val _token = MutableStateFlow(0L)
    val token: StateFlow<Long> = _token.asStateFlow()

    fun expand() {
        _token.update { it + 1 }
    }
}
