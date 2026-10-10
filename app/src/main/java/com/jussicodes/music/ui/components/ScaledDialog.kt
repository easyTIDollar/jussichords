package com.jussicodes.music.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * 进程级「当前界面缩放」持有者。
 *
 * [androidx.compose.ui.window.Dialog] 与 [androidx.compose.material3.AlertDialog]
 * 各自开独立窗口/独立 composition，**不会**继承 MainActivity 里
 * [CompositionLocalProvider] 提供的缩放 [Density]，因此弹窗内容默认不随
 * "界面缩放" 变化。为了让弹窗跟随缩放，用这个进程级变量把缩放值跨窗口带出去。
 *
 * 非 Composable 可读：写入/读取都直接走这个 object 的普通字段，
 * 弹窗打开时读一次即可（打开期间缩放值一般不变；变了重开弹窗再读）。
 */
object ScaledDialogDensity {
    var scale: Float = 1f
}

/**
 * 在弹窗（window.Dialog / material3.AlertDialog）内部包裹一层，
 * 让弹窗内的所有 dp / sp 跟随当前界面缩放。
 *
 * 用法（放在 Dialog/AlertDialog 的 content 最外层）：
 * ```
 * Dialog(onDismissRequest = onDismiss) {
 *     ScaledDialogContent {
 *         Card(...) { ... }
 *     }
 * }
 * ```
 * 弹窗窗口内 [LocalDensity.current] 是系统默认密度（dp 跟屏幕 dpi、sp 跟系统字体），
 * 在此基础上再乘进程级 [ScaledDialogDensity.scale]，等效 MainActivity 里的缩放。
 */
@Composable
fun ScaledDialogContent(content: @Composable () -> Unit) {
    val baseDensity = LocalDensity.current
    // 读一次进程级缩放；baseDensity 变化（换机/分屏）时也重算。
    val scaled = remember(baseDensity) {
        val s = ScaledDialogDensity.scale
        if (s != 1f) {
            Density(density = baseDensity.density * s, fontScale = baseDensity.fontScale * s)
        } else {
            baseDensity
        }
    }
    CompositionLocalProvider(LocalDensity provides scaled) {
        content()
    }
}
