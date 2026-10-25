package com.jussicodes.music.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import kotlin.math.ceil

/**
 * 桌面歌词设置页的预览背景：用户从相册选的图拷贝到 filesDir/desktop_lyric_preview/，
 * 路径写进 DataStore（desktopLyricPreviewBgKey）；键为空时界面用内置默认图。
 *
 * 只服务一张 132dp 高的预览卡片，所以写入时统一下采样到 [MAX_DIM] 内再压 JPEG，
 * 读取时再按 2 倍下采样，避免把手机原图整张解码进内存。
 */
object DesktopLyricPreviewBgStore {
    private const val DIR_NAME = "desktop_lyric_preview"
    private const val MAX_DIM = 1600
    private const val QUALITY = 85

    private fun dir(context: Context) = File(context.filesDir, DIR_NAME)

    /** 把相册 Uri 存成本地 JPEG，返回新路径；失败返回 null。 */
    suspend fun save(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val targetDir = dir(context)
            if (!targetDir.exists()) targetDir.mkdirs()
            val target = File(targetDir, "preview_${System.currentTimeMillis()}.jpg")
            val raw = context.contentResolver.openInputStream(uri) ?: return@runCatching null
            raw.use { stream ->
                val input = BufferedInputStream(stream)
                input.mark(1024 * 1024)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(input, null, bounds)
                input.reset()
                val longest = maxOf(bounds.outWidth, bounds.outHeight)
                val sample = if (longest > 0) {
                    ceil(longest / MAX_DIM.toFloat()).toInt().coerceAtLeast(1)
                } else {
                    1
                }
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                val bitmap = BitmapFactory.decodeStream(input, null, options)
                    ?: return@runCatching null
                target.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
                }
                bitmap.recycle()
            }
            // 只留最新一张
            targetDir.listFiles()?.forEach { file ->
                if (file.name != target.name) runCatching { file.delete() }
            }
            target.absolutePath
        }.getOrNull()
    }

    /** 读取本地预览背景（下采样 2 倍）；路径失效返回 null，调用方回退默认图。 */
    fun load(path: String): Bitmap? {
        if (path.isBlank()) return null
        return runCatching {
            val file = File(path)
            if (!file.exists()) return null
            BitmapFactory.decodeFile(
                path,
                BitmapFactory.Options().apply { inSampleSize = 2 }
            )
        }.getOrNull()
    }

    /** 删除所有自选预览图（恢复默认时调用）。 */
    fun clear(context: Context) {
        runCatching {
            dir(context).listFiles()?.forEach { it.delete() }
        }
    }
}
