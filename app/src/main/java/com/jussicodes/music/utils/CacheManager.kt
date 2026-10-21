package com.jussicodes.music.utils

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import coil3.SingletonImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 软件缓存统计与清理。
 *
 * 覆盖三块可回收缓存：
 * 1. **音频缓存**（media3 SimpleCache，cacheDir/audio）：ExoPlayer 下载/播放的音频分片。
 * 2. **图片缓存**（Coil disk cache，cacheDir/coil）：封面/头像等网络图片落盘。
 * 3. **杂项缓存**（cacheDir 下其余目录，如 updates/ 的更新包、ncm/、library 截图临时文件）。
 *
 * 音频/图片缓存内部各自有 LRU 淘汰（media3 512MB、Coil 512MB），到达后自动淘汰最旧的，
 * 不对用户暴露"上限"概念；用户可见的上限只有一个——「自动清理」阈值（见 SettingsScreen）。
 *
 * 内存缓存（Coil memoryCache、ExoPlayer 解码缓冲）不统计：它们随进程释放，不属于磁盘占用。
 */
object CacheManager {

    data class Bucket(
        val name: String,
        val bytes: Long,
    )

    data class Snapshot(
        val audio: Bucket,
        val image: Bucket,
        val other: Bucket,
    ) {
        val totalBytes: Long get() = audio.bytes + image.bytes + other.bytes
    }

    /** 目录名 → 桶归属。未列出的 cacheDir 子目录归入 other。 */
    private const val AUDIO_DIR = "audio"
    private const val IMAGE_DIR = "coil"

    /** 只统计文件字节数，不遍历子目录（性能：cacheDir 可能有上万个小分片文件）。 */
    private fun dirSize(dir: File?): Long {
        val files = runCatching { dir?.listFiles() }.getOrNull() ?: return 0L
        var total = 0L
        for (f in files) {
            if (f.isFile) total += f.length()
            // 音频/图片缓存目录内含一层子目录，递归一层即可覆盖，避免全量递归开销。
            else if (f.isDirectory) {
                runCatching { f.listFiles() }.getOrNull()?.forEach { c ->
                    if (c.isFile) total += c.length()
                    else if (c.isDirectory) {
                        runCatching { c.listFiles() }.getOrNull()?.forEach { g ->
                            if (g.isFile) total += g.length()
                        }
                    }
                }
            }
        }
        return total
    }

    suspend fun measure(context: Context): Snapshot = withContext(Dispatchers.IO) {
        val cacheDir = context.cacheDir
        val audioBytes = runCatching { AudioCache.measureBytes() }.getOrElse { dirSize(File(cacheDir, AUDIO_DIR)) }
        val imageBytes = runCatching {
            SingletonImageLoader.get(context).diskCache?.size
        }.getOrNull() ?: dirSize(File(cacheDir, IMAGE_DIR))

        // other：cacheDir 直接子文件 + 非 audio/coil 的其它目录（updates/ncm/library…）
        var otherBytes = 0L
        runCatching { cacheDir.listFiles() }.getOrNull()?.forEach { f ->
            when {
                f.isFile -> otherBytes += f.length()
                f.isDirectory && f.name != AUDIO_DIR && f.name != IMAGE_DIR ->
                    otherBytes += dirSize(f)
            }
        }

        Snapshot(
            audio = Bucket("音频缓存", audioBytes),
            image = Bucket("图片缓存", imageBytes),
            other = Bucket("其它缓存", otherBytes),
        )
    }

    /**
     * 清理全部可回收缓存。调用方应保证此时没有正在写入缓存（播放中清理音频缓存
     * 只影响命中率，不影响播放——ExoPlayer 会回源；图片缓存清空后重新下载）。
     */
    suspend fun clear(context: Context): Boolean = withContext(Dispatchers.IO) {
        val cacheDir = context.cacheDir
        var ok = true

        // 1. 音频缓存：先走 SimpleCache 自身的索引删除（数据库里的 CachedContentIndex 一并清掉），
        //    索引不删的话下次启动会看到一堆指向已删文件的"幽灵"条目。
        ok = runCatching { AudioCache.clearCached() }.getOrElse { false } && ok
        ok = runCatching { File(cacheDir, AUDIO_DIR).deleteRecursively() }.getOrElse { false } && ok

        // 2. 图片缓存：Coil disk cache 的 clear()；同时删目录兜底（内存缓存随后由 UI 侧清理）。
        //    clear() 返回 Unit，包一层显式 Boolean 才能参与 && 累计。
        ok = runCatching {
            SingletonImageLoader.get(context).diskCache?.clear()
            true
        }.getOrElse { false } && ok
        ok = runCatching { File(cacheDir, IMAGE_DIR).deleteRecursively() }.getOrElse { false } && ok

        // 3. 其它缓存：整目录删掉（updates 的安装包、ncm 临时文件、截图临时文件）。
        ok = runCatching {
            cacheDir.listFiles()?.forEach { f ->
                when {
                    f.isFile -> f.delete()
                    f.isDirectory && f.name != AUDIO_DIR && f.name != IMAGE_DIR -> f.deleteRecursively()
                }
            }
            true
        }.getOrElse { false } && ok

        // 4. 图片内存缓存：不落盘但吃 RAM，顺手清掉。
        runCatching { SingletonImageLoader.get(context).memoryCache?.clear() }

        ok
    }
}

/**
 * 音频缓存（media3 SimpleCache）的字节统计与清理入口。
 *
 * 播放过程中 [get] 已由 PlaybackService 初始化，这里只在 **已初始化** 时读
 * `getCacheSpace()`；未初始化（用户没播放过）时直接读目录大小，避免为看个缓存
 * 数字就启动一个 SimpleCache 实例（会占数据库句柄）。
 */
@UnstableApi
object AudioCache {
    private const val MAX_CACHE_SIZE_BYTES = 512L * 1024 * 1024
    private val lock = Any()

    @Volatile
    private var cache: SimpleCache? = null

    fun get(context: Context): SimpleCache {
        cache?.let { return it }

        return synchronized(lock) {
            cache ?: SimpleCache(
                context.cacheDir.resolve("audio"),
                LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE_BYTES),
                StandaloneDatabaseProvider(context)
            ).also { cache = it }
        }
    }

    /** 已初始化则读索引统计的字节数，否则按目录大小兜底。 */
    fun measureBytes(): Long {
        val c = cache ?: return 0L
        return runCatching { c.cacheSpace }.getOrElse { 0L }
    }

    /** 已初始化则清空索引+文件，未初始化时无需清理（目录本来就没数据）。 */
    fun clearCached(): Boolean {
        val c = cache ?: return true
        return runCatching {
            for (key in c.keys) c.removeResource(key)
            true
        }.getOrElse { false }
    }
}
