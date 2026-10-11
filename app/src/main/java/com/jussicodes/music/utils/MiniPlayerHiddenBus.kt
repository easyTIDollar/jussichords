package com.jussicodes.music.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 常驻迷你播放条是否隐藏的跨屏幕信号。
 *
 * 贴底输入框的页面（如私信聊天页）不希望小播放条盖住输入框，
 * 进入时 [setHidden] true、退出（onDispose）时恢复 false。
 * 采用 StateFlow 而非路由字符串匹配：类型安全参数路由的 destination.route
 * 未必等于 [KClass.qualifiedName]（会拼参数占位符后缀），拿类名比较不可靠，
 * 让页面自身声明"我在时需要隐藏小播放条"才是稳的。
 */
object MiniPlayerHiddenBus {
    private val _hidden = MutableStateFlow(false)
    val hidden: StateFlow<Boolean> = _hidden.asStateFlow()

    fun setHidden(hidden: Boolean) {
        _hidden.value = hidden
    }
}
