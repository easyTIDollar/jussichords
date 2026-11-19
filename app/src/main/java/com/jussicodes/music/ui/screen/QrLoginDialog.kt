package com.jussicodes.music.ui.screen

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.jussicodes.music.ui.components.ResponsiveAlertDialog
import com.rcmiku.ncmapi.api.QrLoginApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** 二维码边长（像素）。显示 220dp，给 640px 留足缩放余量。 */
private const val QR_PIXELS = 640

private fun buildQrBitmap(content: String): Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, QR_PIXELS, QR_PIXELS)
    val pixels = IntArray(QR_PIXELS * QR_PIXELS)
    for (y in 0 until QR_PIXELS) {
        val row = y * QR_PIXELS
        for (x in 0 until QR_PIXELS) {
            pixels[row + x] =
                if (matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
    }
    Bitmap.createBitmap(pixels, QR_PIXELS, QR_PIXELS, Bitmap.Config.ARGB_8888)
}.getOrNull()

/**
 * 扫码登录：自己调网易云 qrcode 接口出码 + 轮询，成功后把 cookie 交给调用方持久化。
 * 之所以不放在 WebView 里做，是因为网页登录页的第三方快捷登录依赖 QQ 客户端回跳，
 * 登录态落不到 WebView；扫码这条链路能完整拿回 MUSIC_U。
 */
@Composable
fun QrLoginDialog(
    onDismiss: () -> Unit,
    onLoginSuccess: (Map<String, String>) -> Unit,
) {
    var refreshKey by remember { mutableIntStateOf(0) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var statusText by remember { mutableStateOf("正在生成二维码…") }
    var errorText by remember { mutableStateOf<String?>(null) }
    val currentOnSuccess by rememberUpdatedState(onLoginSuccess)

    LaunchedEffect(refreshKey) {
        qrBitmap = null
        errorText = null
        statusText = "正在生成二维码…"

        val unikey = QrLoginApi.qrLoginUnikey().getOrElse { throwable ->
            errorText = "二维码获取失败：${throwable.message ?: "网络异常"}"
            return@LaunchedEffect
        }
        val bitmap = buildQrBitmap("https://music.163.com/login?codekey=$unikey")
        if (bitmap == null) {
            errorText = "二维码渲染失败"
            return@LaunchedEffect
        }
        qrBitmap = bitmap
        statusText = "请使用网易云音乐 App 扫码登录"

        while (isActive) {
            delay(2000)
            val status = QrLoginApi.qrLoginCheck(unikey).getOrNull() ?: continue
            when (status.code) {
                800 -> {
                    qrBitmap = null
                    statusText = "二维码已过期，请点击刷新"
                    return@LaunchedEffect
                }

                801 -> statusText = "请使用网易云音乐 App 扫码登录"
                802 -> statusText = "已扫码，请在手机上确认登录"
                803 -> {
                    statusText = "登录成功"
                    currentOnSuccess(status.cookies)
                    return@LaunchedEffect
                }

                else -> statusText = status.message.ifBlank { "等待扫码…" }
            }
        }
    }

    ResponsiveAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("扫码登录") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val bitmap = qrBitmap
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "网易云登录二维码",
                        modifier = Modifier.size(220.dp),
                    )
                } else {
                    Box(
                        modifier = Modifier.size(220.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (errorText != null) {
                            Text(
                                text = errorText.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                            )
                        } else {
                            CircularProgressIndicator()
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
        dismissButton = {
            TextButton(onClick = { refreshKey += 1 }) { Text("刷新二维码") }
        },
    )
}
