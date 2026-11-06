package com.jussicodes.music.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.LruCache
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

enum class CoverStyle { ROUNDED, CIRCLE }

/**
 * 封面加载：Coil（复用 App 已有的 ImageLoader / 磁盘缓存）→ 方形裁切 → 缩到 ≤512px
 * → JPEG 压缩再解码（顺便得到软件位图，RemoteViews 才能跨进程传）
 * → 按组件样式画圆角 / 圆形 / 黑胶。结果进内存 LruCache，同一首歌的多次组件刷新不重复解码。
 */
object WidgetCoverLoader {
    private const val MAX_PX = 512
    private const val JPEG_QUALITY = 86
    private const val CACHE_BYTES = 12 * 1024 * 1024

    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    suspend fun load(
        context: Context,
        snapshot: WidgetSnapshot,
        style: CoverStyle,
        cornerRatio: Float,
    ): Bitmap? {
        val url = snapshot.coverUrl
        if (url.isBlank()) return null
        val key = "${snapshot.songId}|${url.hashCode()}|$style|$cornerRatio"
        cache.get(key)?.let { return it }
        val bitmap = withContext(Dispatchers.IO) {
            runCatching { decode(context, url, style, cornerRatio) }.getOrNull()
        } ?: return null
        cache.put(key, bitmap)
        return bitmap
    }

    private suspend fun decode(
        context: Context,
        url: String,
        style: CoverStyle,
        cornerRatio: Float,
    ): Bitmap? {
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(MAX_PX)
            .build()
        val raw = (SingletonImageLoader.get(context).execute(request) as? SuccessResult)
            ?.image?.toBitmap() ?: return null
        // Coil 3 不再提供 allowHardware，硬件位图无法跨进程塞进 RemoteViews，这里统一转软件位图。
        val loaded = if (raw.config == Bitmap.Config.HARDWARE) {
            raw.copy(Bitmap.Config.ARGB_8888, false) ?: return null
        } else {
            raw
        }
        val square = squareCrop(loaded)

        val bytes = ByteArrayOutputStream().use { out ->
            square.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
        val compressed = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: square
        val software = if (compressed.isMutable) compressed else compressed.copy(Bitmap.Config.ARGB_8888, true)

        return when (style) {
            CoverStyle.ROUNDED -> round(software, software.width * cornerRatio)
            CoverStyle.CIRCLE -> circle(software)
        }
    }

    private fun squareCrop(src: Bitmap): Bitmap {
        val side = minOf(src.width, src.height)
        if (side <= 0) return src
        val x = (src.width - side) / 2
        val y = (src.height - side) / 2
        val cropped = Bitmap.createBitmap(src, x, y, side, side)
        if (side <= MAX_PX) return cropped
        val scaled = Bitmap.createScaledBitmap(cropped, MAX_PX, MAX_PX, true)
        if (scaled !== cropped && cropped !== src) cropped.recycle()
        return scaled
    }

    private fun round(src: Bitmap, radiusPx: Float): Bitmap {
        val size = src.width
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        val r = radiusPx.coerceIn(0f, size / 2f)
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), r, r, paint)
        return out
    }

    private fun circle(src: Bitmap): Bitmap {
        val size = src.width
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        return out
    }
}
