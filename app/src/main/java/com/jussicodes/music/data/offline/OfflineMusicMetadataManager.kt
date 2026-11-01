package com.jussicodes.music.data.offline

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.jussicodes.music.data.download.DownloadStore
import com.jussicodes.music.data.external.ExternalPlaylistStore
import com.rcmiku.ncmapi.model.LyricContent
import com.rcmiku.ncmapi.model.LyricResponse
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * 离线音乐元数据管理器：
 * 负责在断网环境下提供歌曲的离线歌词（内嵌/缓存）与离线封面（内嵌/缓存），
 * 彻底保障已下载歌曲与外部导入歌曲在完全离线时的播放体验。
 */
object OfflineMusicMetadataManager {

    private const val TAG = "OfflineMetaManager"

    private fun getLyricCacheDir(context: Context): File =
        File(context.filesDir, "download_lyrics").apply { mkdirs() }

    private fun getCoverCacheDir(context: Context): File =
        File(context.filesDir, "download_covers").apply { mkdirs() }

    /**
     * 保存下载完成的歌词文本到本地独立文件
     */
    fun saveDownloadedLyric(context: Context, songId: Long, lyricText: String) {
        if (lyricText.isBlank()) return
        runCatching {
            val file = File(getLyricCacheDir(context), "$songId.lrc")
            file.writeText(lyricText, Charsets.UTF_8)
        }
    }

    /**
     * 保存下载完成的封面二进制到本地独立图片文件
     */
    fun saveDownloadedCover(context: Context, songId: Long, coverBytes: ByteArray) {
        if (coverBytes.isEmpty()) return
        runCatching {
            val file = File(getCoverCacheDir(context), "$songId.jpg")
            FileOutputStream(file).use { it.write(coverBytes) }
        }
    }

    /**
     * 获取离线歌词（断网时优先读取）：
     * 1. 外部歌单歌曲：直接取已识别的歌词；
     * 2. 已下载歌曲：读取本地缓存 lrc；若无，则从音频文件（FLAC/MP3）中提取内嵌歌词。
     */
    fun getOfflineLyric(context: Context, songId: Long): LyricResponse? {
        // 1. 外部导入歌曲
        val externalLyric = ExternalPlaylistStore.getLyric(songId)
        if (externalLyric != null) return externalLyric

        // 2. 本地已下载歌词缓存文件
        val lyricFile = File(getLyricCacheDir(context), "$songId.lrc")
        if (lyricFile.exists() && lyricFile.length() > 0) {
            val text = runCatching { lyricFile.readText(Charsets.UTF_8) }.getOrNull()
            if (!text.isNullOrBlank()) {
                return LyricResponse(lrc = LyricContent(lyric = text))
            }
        }

        // 3. 从已下载的物理音频文件中尝试提取内嵌歌词（USLT / VorbisComment）
        val task = DownloadStore.getCompletedTask(songId) ?: return null
        val extractedLyric = extractEmbeddedLyric(context, task.filePath, task.fileUri)
        if (!extractedLyric.isNullOrBlank()) {
            // 提取成功后自动存入缓存，后续秒开
            saveDownloadedLyric(context, songId, extractedLyric)
            return LyricResponse(lrc = LyricContent(lyric = extractedLyric))
        }

        return null
    }

    /**
     * 获取离线可用的封面（断网下优先保证不空白）：
     * 1. 检查本地独立封面缓存；
     * 2. 外部歌曲检查本地提取封面；
     * 3. 已下载歌曲从音频文件提取内嵌封面（APIC / PICTURE）；
     * 4. 兜底返回原在线 URL。
     */
    fun getOfflineCover(context: Context, songId: Long, fallbackUrl: String? = null): Any? {
        // 1. 本地封面缓存文件存在
        val coverFile = File(getCoverCacheDir(context), "$songId.jpg")
        if (coverFile.exists() && coverFile.length() > 0) {
            return coverFile
        }

        // 2. 外部歌单歌曲封面
        val extSong = ExternalPlaylistStore.findSongById(songId)
        if (extSong?.coverUri != null) {
            return extSong.coverUri
        }

        // 3. 尝试从已下载的物理音频文件提取内嵌封面并缓存
        val task = DownloadStore.getCompletedTask(songId)
        if (task != null) {
            val embeddedCover = extractEmbeddedCover(context, task.filePath, task.fileUri)
            if (embeddedCover != null && embeddedCover.isNotEmpty()) {
                saveDownloadedCover(context, songId, embeddedCover)
                if (coverFile.exists()) return coverFile
            }
        }

        return fallbackUrl?.takeIf { it.isNotBlank() }
    }

    /**
     * 从音频文件提取内嵌歌词：
     * 结合 MediaMetadataRetriever 与 ID3/FLAC 字节扫描。
     */
    fun extractEmbeddedLyric(context: Context, filePath: String?, fileUriStr: String?): String? {
        // 方法 A: MediaMetadataRetriever (Android P+ 支持 METADATA_KEY_LYRIC)
        val retrieverLyric = runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                if (!filePath.isNullOrBlank() && File(filePath).exists()) {
                    retriever.setDataSource(filePath)
                } else if (!fileUriStr.isNullOrBlank()) {
                    retriever.setDataSource(context, Uri.parse(fileUriStr))
                } else {
                    return@runCatching null
                }
                retriever.extractMetadata(1000)?.trim()
            } finally {
                runCatching { retriever.release() }
            }
        }.getOrNull()

        if (!retrieverLyric.isNullOrBlank()) {
            return retrieverLyric
        }

        // 方法 B: 若文件有物理绝对路径，尝试读取 ID3v2 USLT 帧或 FLAC 块
        if (!filePath.isNullOrBlank()) {
            val file = File(filePath)
            if (file.exists() && file.length() > 128) {
                val direct = readId3OrFlacLyrics(file)
                if (!direct.isNullOrBlank()) {
                    return direct
                }
            }
        }

        return null
    }

    /**
     * 从音频文件提取内嵌封面图片字节。
     */
    fun extractEmbeddedCover(context: Context, filePath: String?, fileUriStr: String?): ByteArray? {
        return runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                if (!filePath.isNullOrBlank() && File(filePath).exists()) {
                    retriever.setDataSource(filePath)
                } else if (!fileUriStr.isNullOrBlank()) {
                    retriever.setDataSource(context, Uri.parse(fileUriStr))
                } else {
                    return@runCatching null
                }
                retriever.embeddedPicture
            } finally {
                runCatching { retriever.release() }
            }
        }.getOrNull()
    }

    /**
     * 轻量解析 MP3 ID3v2 中的 USLT (歌词) 帧或 FLAC VorbisComment
     */
    private fun readId3OrFlacLyrics(file: File): String? {
        return runCatching {
            FileInputStream(file).use { fis ->
                val header = ByteArray(10)
                if (fis.read(header) < 10) return@use null

                // 检查 FLAC "fLaC"
                if (header[0] == 'f'.code.toByte() && header[1] == 'L'.code.toByte() &&
                    header[2] == 'a'.code.toByte() && header[3] == 'C'.code.toByte()) {
                    return@use readFlacVorbisLyrics(file)
                }

                // 检查 ID3v2 "ID3"
                if (header[0] == 'I'.code.toByte() && header[1] == 'D'.code.toByte() && header[2] == '3'.code.toByte()) {
                    val tagSize = (header[6].toInt() and 0x7F shl 21) or
                            (header[7].toInt() and 0x7F shl 14) or
                            (header[8].toInt() and 0x7F shl 7) or
                            (header[9].toInt() and 0x7F)
                    val buffer = ByteArray(minOf(tagSize, 256 * 1024))
                    val readBytes = fis.read(buffer)
                    if (readBytes > 0) {
                        return@use findUsltInId3Buffer(buffer, readBytes)
                    }
                }
                null
            }
        }.getOrNull()
    }

    private fun findUsltInId3Buffer(buffer: ByteArray, length: Int): String? {
        var i = 0
        while (i < length - 10) {
            if (buffer[i] == 'U'.code.toByte() &&
                buffer[i + 1] == 'S'.code.toByte() &&
                buffer[i + 2] == 'L'.code.toByte() &&
                buffer[i + 3] == 'T'.code.toByte()) {
                val frameSize = (buffer[i + 4].toInt() and 0xFF shl 24) or
                        (buffer[i + 5].toInt() and 0xFF shl 16) or
                        (buffer[i + 6].toInt() and 0xFF shl 8) or
                        (buffer[i + 7].toInt() and 0xFF)
                if (frameSize in 1..65536 && i + 10 + frameSize <= length) {
                    val frameData = buffer.copyOfRange(i + 10, i + 10 + frameSize)
                    if (frameData.size > 4) {
                        val encoding = frameData[0].toInt()
                        // 跳过 encoding(1) + language(3)
                        val textBytes = frameData.copyOfRange(4, frameData.size)
                        val charset = when (encoding) {
                            1 -> Charsets.UTF_16
                            2 -> Charsets.UTF_16BE
                            3 -> Charsets.UTF_8
                            else -> Charsets.ISO_8859_1
                        }
                        val rawStr = String(textBytes, charset)
                        // USLT 格式一般为: description + \0 + lyrics
                        val lyric = rawStr.substringAfter('\u0000', rawStr).trim()
                        if (lyric.isNotBlank()) return lyric
                    }
                }
            }
            i++
        }
        return null
    }

    private fun readFlacVorbisLyrics(file: File): String? {
        return runCatching {
            FileInputStream(file).use { fis ->
                fis.skip(4) // skip "fLaC"
                while (true) {
                    val blockHeader = ByteArray(4)
                    if (fis.read(blockHeader) < 4) break
                    val isLast = (blockHeader[0].toInt() and 0x80) != 0
                    val blockType = blockHeader[0].toInt() and 0x7F
                    val blockSize = (blockHeader[1].toInt() and 0xFF shl 16) or
                            (blockHeader[2].toInt() and 0xFF shl 8) or
                            (blockHeader[3].toInt() and 0xFF)

                    if (blockType == 4) { // VORBIS_COMMENT
                        val commentData = ByteArray(minOf(blockSize, 512 * 1024))
                        val read = fis.read(commentData)
                        if (read > 0) {
                            val commentStr = String(commentData, Charsets.UTF_8)
                            val marker = listOf("LYRICS=", "UNSYNCEDLYRICS=", "LYRIC=")
                            for (m in marker) {
                                val idx = commentStr.indexOf(m, ignoreCase = true)
                                if (idx >= 0) {
                                    val start = idx + m.length
                                    val lyricPart = commentStr.substring(start)
                                    // 截断到下一个 null 或不可见控制字符前
                                    val cleanLyric = lyricPart.takeWhile { it != '\u0000' }.trim()
                                    if (cleanLyric.isNotBlank()) return@use cleanLyric
                                }
                            }
                        }
                        break
                    } else {
                        fis.skip(blockSize.toLong())
                    }
                    if (isLast) break
                }
                null
            }
        }.getOrNull()
    }
}
