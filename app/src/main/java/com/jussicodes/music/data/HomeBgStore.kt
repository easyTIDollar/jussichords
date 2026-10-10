package com.jussicodes.music.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 主页背景图（"我的"/"探索"页底层）：选中的图片拷贝到 filesDir/home_bg/，
 * 路径写进 DataStore（homeBgImageKey），缩放/透明度/模糊度各有独立键。
 */
object HomeBgStore {
    private const val DIR_NAME = "home_bg"
    private const val FILE_NAME = "home_bg.jpg"

    /** 把 picker 选中的 Uri 拷贝成本地文件；失败返回 null。 */
    suspend fun select(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, DIR_NAME)
            if (!dir.exists()) dir.mkdirs()
            val target = File(dir, FILE_NAME)
            val written = context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        total += n
                    }
                    total > 0L
                }
            }
            if (written == true) target.absolutePath else null
        } catch (_: Exception) {
            null
        }
    }

    /** 移除本地文件（DataStore 键由调用方清空）。 */
    fun clear(context: Context) {
        try {
            File(context.filesDir, "$DIR_NAME/$FILE_NAME").delete()
        } catch (_: Exception) {
        }
    }
}
