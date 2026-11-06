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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections
import java.io.ByteArrayOutputStream

/**
 * 封面目标像素按组件实际绘制尺寸给。
 *
 * Glance 每一帧都把位图塞进 RemoteViews 走 Binder：512×512 ARGB_8888 正好 1,048,576 字节，
 * 加上 Bundle/RemoteViews 头就超过 Binder 单次事务上限（TransactionTooLargeException），
 * updateAppWidget 直接失败——表现为组件永远停在旧帧或「加载中」。桌面组件最大也就 2×2 格，
 * 按 dp 实际像素出图足够清晰，体积还小一个数量级。
 */
enum class CoverStyle(val px: Int) {
    /** 经典 4×2：66dp 封面，3x 屏约 198px。 */
    ROUNDED(224),

    /** 方形 2×2：整格铺满封面，3x 屏约 330px。 */
    LARGE(320),

    /** 胶囊 4×1：40dp 圆形封面，3x 屏约 120px。 */
    CIRCLE(160),
}

/**
 * 封面加载：Coil（复用 App 已有的 ImageLoader / 磁盘缓存）→ 方形裁切 → 缩到组件实际尺寸
 * → JPEG 压缩再解码（顺便得到软件位图，RemoteViews 才能跨进程传）
 * → 按组件样式画圆角 / 圆形。结果进内存 LruCache。
 *
 * 关键约束：**合成（provideGlance）里绝不发起网络请求**。
 * 合成是 Glance 的一帧事务，下一次 update 会取消它；只要在里面 await 一次网络，
 * 弱网或图源超时时组件就一直「没合成完」，观感就是状态彻底不同步。
 * 所以合成只读 [cached]，没命中交给 [prefetch] 在后台补，补完再推一帧。
 */
object WidgetCoverLoader {
    private const val JPEG_QUALITY = 82
    private const val CACHE_BYTES = 12 * 1024 * 1024

    /** 同一张图失败后 60s 内不再重试，避免每次刷新都打一遍网络。 */
    private const val FAIL_TTL_MS = 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val pending: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
    private val failedAt: MutableMap<String, Long> = Collections.synchronizedMap(mutableMapOf())

    private fun key(snapshot: WidgetSnapshot, style: CoverStyle, cornerRatio: Float): String =
        "${snapshot.songId}|${snapshot.coverUrl.hashCode()}|$style|$cornerRatio"

    /** 只查内存缓存：命中同步返回。合成路径唯一允许的取图方式。 */
    fun cached(snapshot: WidgetSnapshot, style: CoverStyle, cornerRatio: Float): Bitmap? {
        if (snapshot.coverUrl.isBlank()) return null
        return cache.get(key(snapshot, style, cornerRatio))
    }

    /** 后台补图，成功后回调（用来重绘一帧）。重复调用安全。 */
    fun prefetch(
        context: Context,
        snapshot: WidgetSnapshot,
        style: CoverStyle,
        cornerRatio: Float,
        onReady: suspend () -> Unit,
    ) {
        if (snapshot.coverUrl.isBlank()) return
        val app = context.applicationContext
        val k = key(snapshot, style, cornerRatio)
        if (cache.get(k) != null) return
        val now = System.currentTimeMillis()
        failedAt[k]?.let { if (now - it < FAIL_TTL_MS) return }
        if (!pending.add(k)) return
        scope.launch {
            val bitmap = runCatching { decode(app, snapshot.coverUrl, style, cornerRatio) }.getOrNull()
            pending.remove(k)
            if (bitmap == null) {
                failedAt[k] = System.currentTimeMillis()
                return@launch
            }
            cache.put(k, bitmap)
            failedAt.remove(k)
            runCatching { onReady() }
        }
    }

    private suspend fun decode(
        context: Context,
        url: String,
        style: CoverStyle,
        cornerRatio: Float,
    ): Bitmap? = withContext(Dispatchers.IO) {
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(style.px)
            .build()
        val raw = (SingletonImageLoader.get(context).execute(request) as? SuccessResult)
            ?.image?.toBitmap() ?: return@withContext null
        // Coil 3 不再提供 allowHardware，硬件位图无法跨进程塞进 RemoteViews，这里统一转软件位图。
        val loaded = if (raw.config == Bitmap.Config.HARDWARE) {
            raw.copy(Bitmap.Config.ARGB_8888, false) ?: return@withContext null
        } else {
            raw
        }
        val square = squareCrop(loaded, style.px)

        val bytes = ByteArrayOutputStream().use { out ->
            square.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
        val compressed = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: square
        val software = if (compressed.isMutable) compressed else compressed.copy(Bitmap.Config.ARGB_8888, true)

        when (style) {
            CoverStyle.ROUNDED, CoverStyle.LARGE -> round(software, software.width * cornerRatio)
            CoverStyle.CIRCLE -> circle(software)
        }
    }

    private fun squareCrop(src: Bitmap, maxPx: Int): Bitmap {
        val side = minOf(src.width, src.height)
        if (side <= 0) return src
        val x = (src.width - side) / 2
        val y = (src.height - side) / 2
        val cropped = Bitmap.createBitmap(src, x, y, side, side)
        if (side <= maxPx) return cropped
        val scaled = Bitmap.createScaledBitmap(cropped, maxPx, maxPx, true)
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
