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

    /**
     * 把 picker 选中的 Uri 拷贝成本地文件；失败返回 null。
     * 每次生成时间戳唯一文件名：Coil 以文件路径为缓存 key，
     * 若写死 home_bg.jpg，换图后同路径命中旧位图内存缓存，需重启应用才生效。
     * 不清理旧文件——编辑模式取消时需要旧图还在；清理由 commit/prune/deleteFile 负责。
     */
    suspend fun select(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, DIR_NAME)
            if (!dir.exists()) dir.mkdirs()
            val target = File(dir, "home_bg_${System.currentTimeMillis()}.jpg")
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
            File(context.filesDir, DIR_NAME).listFiles()?.forEach { it.delete() }
        } catch (_: Exception) {
        }
    }

    /** 只删指定文件（编辑取消时丢弃未提交的临时图，不动已提交的其他文件）。 */
    fun deleteFile(context: Context, path: String) {
        try {
            val f = File(path)
            // 防御：只允许删 home_bg 目录内的文件
            if (f.absolutePath.startsWith(File(context.filesDir, DIR_NAME).absolutePath)) {
                f.delete()
            }
        } catch (_: Exception) {
        }
    }

    /** 清理 home_bg 目录内除 [keepPath] 外的所有文件（保存后清掉旧的临时图）。 */
    fun prune(context: Context, keepPath: String) {
        try {
            val dir = File(context.filesDir, DIR_NAME)
            dir.listFiles()?.forEach { f ->
                if (f.absolutePath != keepPath) f.delete()
            }
        } catch (_: Exception) {
        }
    }
}
