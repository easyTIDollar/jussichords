package com.jussicodes.music.data.external

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.jussicodes.music.data.offline.OfflineMusicMetadataManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset

object ExternalAudioScanner {

    private const val TAG = "ExternalAudioScanner"

    private val AUDIO_EXTENSIONS = setOf(
        "mp3", "flac", "wav", "m4a", "aac", "ogg", "opus", "wma", "ape", "dsf", "dff"
    )

    private val IMAGE_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "webp"
    )

    private val GENERIC_COVER_NAMES = setOf(
        "cover", "folder", "album", "front", "artwork"
    )

    /**
     * 递归/平铺扫描指定 SAF 文件夹，识别全部音频文件、同名/通用歌词、内嵌/外挂封面。
     */
    suspend fun scanFolder(
        context: Context,
        treeUri: Uri,
        folderName: String,
        onProgress: ((current: Int, total: Int, name: String) -> Unit)? = null
    ): List<ExternalSong> = withContext(Dispatchers.IO) {
        val rootDir = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()

        val audioDocs = mutableListOf<DocumentFile>()
        val lrcMap = mutableMapOf<String, DocumentFile>() // lowerBaseName -> doc
        val imageMap = mutableMapOf<String, DocumentFile>() // lowerBaseName -> doc
        var genericCoverDoc: DocumentFile? = null

        // 递归遍历文件，深度最多 3 层
        fun collectFiles(dir: DocumentFile, depth: Int) {
            if (depth > 3) return
            val list = runCatching { dir.listFiles() }.getOrNull() ?: return
            for (doc in list) {
                if (doc.isDirectory) {
                    collectFiles(doc, depth + 1)
                } else {
                    val name = doc.name ?: continue
                    val ext = name.substringAfterLast('.', "").lowercase()
                    val baseName = name.substringBeforeLast('.').lowercase()
                    val strippedBase = cleanFileNameForMatch(baseName)

                    when {
                        ext in AUDIO_EXTENSIONS -> audioDocs.add(doc)
                        ext == "lrc" -> {
                            lrcMap[baseName] = doc
                            lrcMap[strippedBase] = doc
                        }
                        ext in IMAGE_EXTENSIONS -> {
                            imageMap[baseName] = doc
                            imageMap[strippedBase] = doc
                            if (baseName in GENERIC_COVER_NAMES && genericCoverDoc == null) {
                                genericCoverDoc = doc
                            }
                        }
                    }
                }
            }
        }

        collectFiles(rootDir, 0)

        if (audioDocs.isEmpty()) {
            return@withContext emptyList()
        }

        val coverDir = File(context.filesDir, "external_covers").apply {
            mkdirs()
            runCatching { File(this, ".nomedia").createNewFile() }
        }
        val resultSongs = mutableListOf<ExternalSong>()
        val totalCount = audioDocs.size

        for ((idx, doc) in audioDocs.withIndex()) {
            val fileName = doc.name ?: continue
            onProgress?.invoke(idx + 1, totalCount, fileName)
            val fileUri = doc.uri
            val baseName = fileName.substringBeforeLast('.')
            val lowerBaseName = baseName.lowercase()
            val strippedBase = cleanFileNameForMatch(lowerBaseName)
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
            var embeddedLyric: String? = null

            // 提取内嵌元数据、内嵌封面与内嵌歌词
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

                // 提取内嵌歌词 (1000 为常用歌词元数据键值)
                embeddedLyric = runCatching {
                    retriever.extractMetadata(1000)?.trim()
                }.getOrNull()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to retrieve metadata for $fileName: ${e.message}")
            } finally {
                runCatching { retriever.release() }
            }

            // 若无内嵌封面，尝试从同目录下查找同名封面或通用封面 (cover/folder/album)
            if (coverUri == null) {
                val matchedImg = imageMap[lowerBaseName] ?: imageMap[strippedBase] ?: genericCoverDoc
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

            // 识别歌词：优先找同目录同名或模糊匹配的 .lrc，次选内嵌歌词
            var lyricText: String? = null
            val lrcDoc = lrcMap[lowerBaseName] ?: lrcMap[strippedBase]
            if (lrcDoc != null) {
                lyricText = readLrcSafely(context, lrcDoc.uri)
            }
            if (lyricText.isNullOrBlank()) {
                lyricText = embeddedLyric
            }

            // 智能从文件名拆分歌手与歌名保底
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

    private fun cleanFileNameForMatch(name: String): String {
        return name.replaceFirst(Regex("""^\d+[\s._-]+"""), "").trim().lowercase()
    }

    /**
     * 智能从文件名拆分歌手与歌名。
     * 支持："歌手 - 歌名"、"序号. 歌手 - 歌名" 等格式。
     */
    private fun splitArtistAndTitle(baseName: String): Pair<String?, String> {
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

            // 检查 UTF-8 BOM
            val offset = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) 3 else 0
            val utf8Str = String(bytes, offset, bytes.size - offset, Charsets.UTF_8)

            // 若包含过多乱码字符 \uFFFD，回退到 GBK / GB18030
            if (utf8Str.count { it == '\uFFFD' } > 1) {
                runCatching {
                    String(bytes, offset, bytes.size - offset, Charset.forName("GB18030"))
                }.getOrNull() ?: runCatching {
                    String(bytes, offset, bytes.size - offset, Charset.forName("GBK"))
                }.getOrNull() ?: utf8Str
            } else {
                utf8Str
            }
        }.getOrNull()
    }
}
