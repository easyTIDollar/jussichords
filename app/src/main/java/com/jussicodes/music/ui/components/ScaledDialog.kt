package com.jussicodes.music.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * 进程级「当前界面缩放」持有者（[androidx.compose.runtime.mutableFloatStateOf] 可观察）。
 *
 * [androidx.compose.ui.window.Dialog] 与 [androidx.compose.material3.AlertDialog]
 * 各自开独立窗口/独立 composition，**不会**继承 MainActivity 里
 * [CompositionLocalProvider] 提供的缩放 [Density]，因此弹窗内容默认不随
 * "界面缩放" 变化。为了让弹窗跟随缩放，用这个进程级变量把缩放值跨窗口带出去。
 *
 * [scale] 是可观察 state：写它（如外观弹窗里拖动界面缩放滑块）时，
 * 读它的弹窗窗口会实时重组，缩放实时生效、无需关弹窗重开。
 * 主 app 窗口的实时缩放由 MainActivity 自己的 liveUiScale 驱动，两边同源。
 */
object ScaledDialogDensity {
    var scale by mutableFloatStateOf(1f)
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
    // 直接读可观察 state：弹窗内拖动「界面缩放」滑块时 ScaledDialogDensity.scale
    // 变化，本组合自动重组，弹窗内的 dp/sp 实时跟随缩放（无需关弹窗重开）。
    val scale = ScaledDialogDensity.scale
    val scaled = remember(baseDensity, scale) {
        if (scale != 1f) {
            Density(density = baseDensity.density * scale, fontScale = baseDensity.fontScale * scale)
        } else {
            baseDensity
        }
    }
    CompositionLocalProvider(LocalDensity provides scaled) {
        content()
    }
}
