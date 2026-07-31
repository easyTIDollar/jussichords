package com.jussicodes.music.utils

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun saveRemoteImage(context: Context, imageUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
        val extension = imageUrl.substringBefore('?').substringAfterLast('.', "jpg")
            .lowercase().takeIf { it in setOf("jpg", "jpeg", "png", "webp") } ?: "jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Mooic_${System.currentTimeMillis()}.$extension")
            put(MediaStore.Images.Media.MIME_TYPE, when (extension) {
                "png" -> "image/png"
                "webp" -> "image/webp"
                else -> "image/jpeg"
            })
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Mooic")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("无法创建图片文件")
        try {
            URL(imageUrl).openStream().use { input ->
                resolver.openOutputStream(uri)?.use(input::copyTo) ?: error("无法写入图片")
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            Unit
        } catch (throwable: Throwable) {
            resolver.delete(uri, null, null)
            throw throwable
        }
    }
}
