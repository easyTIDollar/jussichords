package com.jussicodes.music.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 主页背景编辑"放弃"后回设置页的信号。
 *
 * MainScreen 的编辑面板点"放弃"：[request] → 跳回 SettingsScreen；
 * SettingsScreen 组合时一次性 collect 到 true 就自动打开「外观」弹窗（Theme 色源对话框），
 * 并 [consume] 复位，之后手动进设置页不再自动弹。
 * StateFlow 而非路由参数：导航目标（Settings）与"打开哪个弹窗"是两件事，
 * 弹窗状态没法靠 navigate 传递，用 bus 声明"我需要打开外观"最稳。
 */
object SettingsAppearanceBus {
    private val _requested = MutableStateFlow(false)
    val requested: StateFlow<Boolean> = _requested.asStateFlow()

    fun request() {
        _requested.value = true
    }

    fun consume() {
        _requested.value = false
    }
}
