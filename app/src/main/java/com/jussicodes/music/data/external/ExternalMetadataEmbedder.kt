package com.jussicodes.music.data.external

import android.content.Context
import android.net.Uri
import com.jussicodes.music.download.AudioTagWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 外部音频元数据内嵌执行器：
 * 将用户导入的外部文件夹中独立存在的歌词（.lrc）与封面图片，
 * 永久批量写入/内嵌到音频文件（MP3 / FLAC）内部。
 */
object ExternalMetadataEmbedder {

    private val SUPPORTED_EMBED_EXTENSIONS = setOf("mp3", "flac")

    /**
     * 判断单曲是否支持内嵌
     */
    fun isSupported(song: ExternalSong): Boolean {
        val ext = song.fileName.substringAfterLast('.', "").lowercase()
        return ext in SUPPORTED_EMBED_EXTENSIONS
    }

    /**
     * 判断歌曲是否拥有可内嵌的元数据（歌词或封面）
     */
    fun hasEmbeddableMetadata(song: ExternalSong): Boolean {
        return isSupported(song) && (!song.lyricText.isNullOrBlank() || !song.coverUri.isNullOrBlank())
    }

    /**
     * 内嵌单曲元数据
     */
    suspend fun embedSong(
        context: Context,
        song: ExternalSong,
        embedLyric: Boolean = true,
        embedCover: Boolean = true
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isSupported(song)) return@withContext false

        val fileUri = runCatching { Uri.parse(song.fileUri) }.getOrNull() ?: return@withContext false

        val lyricsToEmbed = if (embedLyric) song.lyricText?.takeIf { it.isNotBlank() } else null

        var coverBytes: ByteArray? = null
        if (embedCover && !song.coverUri.isNullOrBlank()) {
            val coverUri = runCatching { Uri.parse(song.coverUri) }.getOrNull()
            if (coverUri != null) {
                coverBytes = runCatching {
                    context.contentResolver.openInputStream(coverUri)?.use { it.readBytes() }
                }.getOrNull()
            }
        }

        AudioTagWriter.embedMetadataToUri(
            context = context,
            fileUri = fileUri,
            title = song.title,
            artist = song.artist,
            album = song.album,
            lyrics = lyricsToEmbed,
            coverBytes = coverBytes
        )
    }

    /**
     * 批量内嵌外部歌单内全部歌曲
     * @param onProgress (当前第几首, 总共可内嵌首数, 当前歌名)
     * @return Pair(成功数量, 失败/跳过数量)
     */
    suspend fun embedPlaylist(
        context: Context,
        playlist: ExternalPlaylist,
        embedLyric: Boolean = true,
        embedCover: Boolean = true,
        onProgress: (Int, Int, String) -> Unit = { _, _, _ -> }
    ): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val targets = playlist.songs.filter { isSupported(it) }
        var successCount = 0
        var failCount = 0

        targets.forEachIndexed { index, song ->
            onProgress(index + 1, targets.size, song.title)
            val ok = embedSong(context, song, embedLyric, embedCover)
            if (ok) {
                successCount++
            } else {
                failCount++
            }
        }

        // 内嵌完成后刷新一次歌单状态
        if (successCount > 0) {
            ExternalPlaylistStore.refreshPlaylist(context, playlist.id)
        }

        Pair(successCount, failCount)
    }
}
