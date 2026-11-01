package com.jussicodes.music.data.external

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.charset.Charset

object ExternalAudioScanner {

    private const val TAG = "ExternalAudioScanner"

    private val AUDIO_EXTENSIONS = setOf(
        "mp3", "flac", "wav", "m4a", "aac", "ogg", "opus", "wma", "ape"
    )

    private val IMAGE_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "webp"
    )

    /**
     * 扫描指定 SAF 文件夹，自动识别音频文件、同名/通用歌词、内嵌/外挂封面。
     */
    suspend fun scanFolder(
        context: Context,
        treeUri: Uri,
        folderName: String
    ): List<ExternalSong> = withContext(Dispatchers.IO) {
        val rootDir = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
        val allFiles = runCatching { rootDir.listFiles() }.getOrNull() ?: return@withContext emptyList()

        // 1. 分类文件
        val audioDocs = mutableListOf<DocumentFile>()
        val lrcMap = mutableMapOf<String, DocumentFile>() // lowerBaseName -> doc
        val imageMap = mutableMapOf<String, DocumentFile>() // lowerBaseName -> doc
        var genericCoverDoc: DocumentFile? = null

        val genericCoverNames = setOf("cover", "folder", "album", "front", "artwork")

        for (doc in allFiles) {
            val name = doc.name ?: continue
            if (doc.isDirectory) continue
            val ext = name.substringAfterLast('.', "").lowercase()
            val baseName = name.substringBeforeLast('.').lowercase()

            when {
                ext in AUDIO_EXTENSIONS -> audioDocs.add(doc)
                ext == "lrc" -> lrcMap[baseName] = doc
                ext in IMAGE_EXTENSIONS -> {
                    imageMap[baseName] = doc
                    if (baseName in genericCoverNames && genericCoverDoc == null) {
                        genericCoverDoc = doc
                    }
                }
            }
        }

        if (audioDocs.isEmpty()) {
            return@withContext emptyList()
        }

        // 封面缓存目录
        val coverDir = File(context.filesDir, "external_covers").apply { mkdirs() }

        val resultSongs = mutableListOf<ExternalSong>()

        // 2. 逐一提取音频元数据
        for (doc in audioDocs) {
            val fileName = doc.name ?: continue
            val fileUri = doc.uri
            val baseName = fileName.substringBeforeLast('.')
            val lowerBaseName = baseName.lowercase()
            val ext = fileName.substringAfterLast('.', "").lowercase()

            val songId = generateStableId(treeUri.toString(), fileName)
            val fileSize = doc.length()
            val lastModified = doc.lastModified()
            val mimeType = doc.type ?: "audio/$ext"

            var parsedTitle: String? = null
            var parsedArtist: String? = null
            var parsedAlbum: String? = null
            var durationMs = 0L
            var coverUri: String? = null

            // 使用 MediaMetadataRetriever 提取内嵌元数据
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, fileUri)
                parsedTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim()
                parsedArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.trim()
                parsedAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.trim()
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L

                // 提取内嵌封面图片
                val embeddedPic = retriever.embeddedPicture
                if (embeddedPic != null && embeddedPic.isNotEmpty()) {
                    val coverFile = File(coverDir, "${kotlin.math.abs(songId)}.jpg")
                    runCatching {
                        FileOutputStream(coverFile).use { it.write(embeddedPic) }
                        coverUri = Uri.fromFile(coverFile).toString()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to retrieve metadata for $fileName: ${e.message}")
            } finally {
                runCatching { retriever.release() }
            }

            // 若无内嵌封面，尝试从同目录下查找同名封面或通用封面
            if (coverUri == null) {
                val matchedImg = imageMap[lowerBaseName] ?: genericCoverDoc
                if (matchedImg != null) {
                    val coverFile = File(coverDir, "${kotlin.math.abs(songId)}_ext.jpg")
                    runCatching {
                        context.contentResolver.openInputStream(matchedImg.uri)?.use { input ->
                            FileOutputStream(coverFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        coverUri = Uri.fromFile(coverFile).toString()
                    }
                }
            }

            // 识别歌词：优先找同目录下同名 .lrc
            var lyricText: String? = null
            val lrcDoc = lrcMap[lowerBaseName]
            if (lrcDoc != null) {
                lyricText = readLrcSafely(context, lrcDoc.uri)
            }

            // 歌名与歌手保底解析（智能从文件名提取）
            val (fallbackArtist, fallbackTitle) = splitArtistAndTitle(baseName)
            val finalTitle = parsedTitle?.takeIf { it.isNotBlank() } ?: fallbackTitle
            val finalArtist = parsedArtist?.takeIf { it.isNotBlank() } ?: (fallbackArtist ?: "未知歌手")
            val finalAlbum = parsedAlbum?.takeIf { it.isNotBlank() } ?: folderName

            resultSongs.add(
                ExternalSong(
                    id = songId,
                    title = finalTitle,
                    artist = finalArtist,
                    album = finalAlbum,
                    durationMs = durationMs,
                    fileUri = fileUri.toString(),
                    fileName = fileName,
                    fileSize = fileSize,
                    coverUri = coverUri,
                    lyricText = lyricText,
                    mimeType = mimeType,
                    lastModified = lastModified
                )
            )
        }

        // 默认按歌名排序
        resultSongs.sortedBy { it.title.lowercase() }
    }

    /**
     * 生成稳定且唯一的负数 ID（绝不与网易云正数 ID 冲突）。
     */
    fun generateStableId(treeUriStr: String, fileName: String): Long {
        val composite = "$treeUriStr/$fileName"
        var hash = 1125899906842624L
        for (ch in composite) {
            hash = hash * 31L + ch.code
        }
        val safePositive = (hash and 0x7FFFFFFFFFFFFFFFL) % 8_000_000_000L
        return -1_000_000_000L - safePositive
    }

    /**
     * 智能从文件名拆分歌手与歌名。
     * 支持："歌手 - 歌名"、"歌手-歌名"、"序号. 歌手 - 歌名" 等格式。
     */
    private fun splitArtistAndTitle(baseName: String): Pair<String?, String> {
        // 清除开头的序号，如 "01. "、"1 - "
        val cleaned = baseName.replaceFirst(Regex("""^\d+[\s._-]+"""), "").trim()
        val parts = cleaned.split(Regex("""\s*[-–—]\s*"""), limit = 2)
        return if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
            parts[0].trim() to parts[1].trim()
        } else {
            null to cleaned.ifBlank { baseName }
        }
    }

    /**
     * 安全读取 LRC 歌词文件，支持 UTF-8 与 GBK/GB2312 编码自动探测。
     */
    private fun readLrcSafely(context: Context, uri: Uri): String? {
        return runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            if (bytes.isEmpty()) return null

            // 优先尝试 UTF-8 解码
            val utf8Str = String(bytes, Charsets.UTF_8)
            // 若包含过多乱码字符 \uFFFD，回退到 GBK
            if (utf8Str.count { it == '\uFFFD' } > 2) {
                runCatching { String(bytes, Charset.forName("GBK")) }.getOrNull() ?: utf8Str
            } else {
                utf8Str
            }
        }.getOrNull()
    }
}
