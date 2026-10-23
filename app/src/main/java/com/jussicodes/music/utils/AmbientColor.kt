package com.jussicodes.music.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max
import kotlin.math.min

/**
 * 抓取网络图片并估算它的「主色调」，用作页面环境光晕的底色。
 *
 * 做法：HttpURLConnection 取字节 → BitmapFactory 解码（带 inSampleSize 降采样，
 * 不占内存）→ 采样像素求加权平均（饱和度越高权重越大，避免灰色背景把色调冲淡）。
 *
 * 任何一步失败都返回 null，调用方回退到主题主色即可 —— 这是纯装饰效果，
 * 绝不能因为取不到色就把页面搞崩。
 */
suspend fun extractAmbientColor(url: String?): Color? {
    if (url.isNullOrBlank()) return null
    return withContext(Dispatchers.IO) {
        try {
            val bytes = downloadBytes(url) ?: return@withContext null
            val bitmap = decodeSampled(bytes) ?: return@withContext null
            val color = averageColor(bitmap)
            bitmap.recycle()
            color
        } catch (_: Throwable) {
            null
        }
    }
}

private fun downloadBytes(url: String): ByteArray? {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 8000
        readTimeout = 8000
        requestMethod = "GET"
        instanceFollowRedirects = true
    }
    return try {
        if (connection.responseCode !in 200..299) return null
        connection.inputStream.use { it.readBytes() }
    } finally {
        connection.disconnect()
    }
}

/** 解码并降采样到 ~64px 宽，够算平均色了。 */
private fun decodeSampled(bytes: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    val maxSize = max(bounds.outWidth, bounds.outHeight)
    while (maxSize / sample > 64) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}

/** 采样像素求加权平均色：饱和度越高权重越大。 */
private fun averageColor(bitmap: Bitmap): Color? {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= 0 || height <= 0) return null
    var rSum = 0.0
    var gSum = 0.0
    var bSum = 0.0
    var weightSum = 0.0
    val step = max(1, min(width, height) / 16)
    var y = 0
    while (y < height) {
        var x = 0
        while (x < width) {
            val pixel = bitmap.getPixel(x, y)
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val maxC = max(r, max(g, b))
            val minC = min(r, min(g, b))
            // 饱和度（0..1）与亮度权重：太暗或太灰的像素不参与
            val saturation = if (maxC == 0) 0f else (maxC - minC).toFloat() / maxC
            val brightness = maxC / 255f
            if (brightness > 0.08f && brightness < 0.97f) {
                val weight = (0.15 + saturation) * brightness
                rSum += r * weight
                gSum += g * weight
                bSum += b * weight
                weightSum += weight
            }
            x += step
        }
        y += step
    }
    if (weightSum <= 0.0) return null
    return Color(
        red = (rSum / weightSum / 255.0).toFloat(),
        green = (gSum / weightSum / 255.0).toFloat(),
        blue = (bSum / weightSum / 255.0).toFloat(),
        alpha = 1f
    )
}
