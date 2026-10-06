package com.jussicodes.music.utils

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 一条待展示的「菜单操作完成」反馈。
 *
 * [undoLabel] 非空时，根界面的 Snackbar 附带「撤销」按钮；点击后执行 [onUndo]
 * （例如重新加回歌单、恢复/取消喜欢）。[onUndo] 是挂起函数，由接收端在自己的
 * 协程里调用。
 */
data class MenuSnackbarEvent(
    val message: String,
    val undoLabel: String? = null,
    val onUndo: (suspend () -> Unit)? = null,
)

/**
 * 全局「菜单操作反馈」通道。歌曲等底部菜单执行完一个动作后，先把菜单关闭，
 * 再 [show] 一条事件；根界面（MainScreen 的 SnackbarHost）统一消费并弹 Snackbar。
 *
 * 用 [MutableSharedFlow]（带缓冲）解耦：发送方在自身 scope 内同步 emit，即使
 * 菜单随后关闭/销毁，事件也已入缓冲，不会被取消。
 */
object MenuSnackbarBus {
    private val _events = MutableSharedFlow<MenuSnackbarEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<MenuSnackbarEvent> = _events.asSharedFlow()

    suspend fun show(event: MenuSnackbarEvent) {
        _events.emit(event)
    }

    suspend fun show(message: String, undoLabel: String? = null, onUndo: (suspend () -> Unit)? = null) {
        // 有撤销动作但没给标签时，默认显示「撤销」按钮
        val label = undoLabel ?: onUndo?.let { "撤销" }
        _events.emit(MenuSnackbarEvent(message, label, onUndo))
    }
}
