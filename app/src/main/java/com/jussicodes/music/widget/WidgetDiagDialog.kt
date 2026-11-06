package com.jussicodes.music.widget

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 组件诊断弹窗：把「谁被刷新、刷新成没成功、合成读到什么帧」直接摊在手机上。
 *
 * 组件不刷新这类问题以前只能靠 adb logcat，用户手机没有电脑就没法定位。
 * 这里读取 WidgetDebug 的环形日志，一次复制就能贴出来。
 */
@Composable
fun WidgetDiagDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf(WidgetDebug.dump(context)) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("小组件诊断") },
        text = {
            Text(
                text = text,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                cm?.setPrimaryClip(ClipData.newPlainText("widget-diag", text))
                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
            }) { Text("复制") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    scope.launch {
                        // 绕过「没有实例就不干活」的门禁，直接强制刷新一遍，看 update 是否成功。
                        withContext(Dispatchers.Default) { runCatching { WidgetHub.updateAll(context) } }
                        text = WidgetDebug.dump(context)
                    }
                }) { Text("强制刷新") }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        },
    )
}
