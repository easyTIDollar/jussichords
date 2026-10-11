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

    /** 选取结果：成功给本地路径；失败给可读原因（toast 直接展示，用来定位是哪类失败）。 */
    sealed class HomeBgSelectResult {
        data class Success(val path: String) : HomeBgSelectResult()
        data class Failure(val reason: String) : HomeBgSelectResult()
    }

    /**
     * 把 picker 选中的 Uri 拷贝成本地文件；失败返回 [HomeBgSelectResult.Failure]（带原因）。
     * 每次生成时间戳唯一文件名：Coil 以文件路径为缓存 key，
     * 若写死 home_bg.jpg，换图后同路径命中旧位图内存缓存，需重启应用才生效。
     * 不清理旧文件——编辑模式取消时需要旧图还在；清理由 commit/prune/deleteFile 负责。
     */
    suspend fun select(context: Context, uri: Uri): HomeBgSelectResult = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, DIR_NAME)
        try {
            if (!dir.exists()) dir.mkdirs()
        } catch (e: Exception) {
            return@withContext HomeBgSelectResult.Failure("无法创建目录 ${dir.absolutePath}：${e.message}")
        }
        val target = File(dir, "home_bg_${System.currentTimeMillis()}.jpg")
        var lastError = "未知"
        // 部分内容 provider（DocsUI / 第三方选图库 / 大图解码）首开流偶发打不开或读到 0 字节，
        // 重试 3 次（间隔 300ms）再放弃；每一类的失败原因都记进 [lastError] 供 UI 展示。
        for (attempt in 1..3) {
            try {
                val stream = context.contentResolver.openInputStream(uri)
                if (stream == null) {
                    lastError = "图片流打不开（openInputStream 返回 null，第 $attempt 次）"
                } else {
                    val ok = stream.use { input ->
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
                    if (ok) {
                        android.util.Log.i("JussicordsBg", "select ok path=${target.absolutePath} attempt=$attempt")
                        return@withContext HomeBgSelectResult.Success(target.absolutePath)
                    } else {
                        lastError = "图片流为空（读到 0 字节，第 $attempt 次）"
                    }
                }
            } catch (ex: Exception) {
                lastError = "读取/写入异常 ${ex.javaClass.simpleName}：${ex.message}"
                android.util.Log.w("JussicordsBg", "select exception (attempt=$attempt)", ex)
            }
            if (attempt < 3) kotlinx.coroutines.delay(300L)
        }
        HomeBgSelectResult.Failure(lastError)
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
