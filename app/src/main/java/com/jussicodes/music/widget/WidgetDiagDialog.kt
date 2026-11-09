package com.jussicodes.music.widget

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jussicodes.music.ui.components.ResponsiveAlertDialog

/**
 * 小组件诊断弹窗。
 *
 * 用户机器上没有 adb：「组件不刷新 / 按钮没反应」这类问题如果只能看 logcat，
 * 现场就永远拿不到证据。这里把 [WidgetDebug.dump] 的当前帧、实例 id、环形事件日志
 * 一次性摆出来，点「复制」就能整段发给开发者。
 */
@Composable
fun WidgetDiagDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val dump = remember { WidgetDebug.dump(context) }
    ResponsiveAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("小组件诊断") },
        text = {
            Text(
                text = dump,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                copy(context, dump)
                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                onDismiss()
            }) { Text("复制") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

private fun copy(context: Context, text: String) {
    runCatching {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        manager?.setPrimaryClip(ClipData.newPlainText("widget_diag", text))
    }
}
