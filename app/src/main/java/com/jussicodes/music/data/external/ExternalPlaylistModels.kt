package com.jussicodes.music.data.external

import com.rcmiku.ncmapi.model.Artist
import com.rcmiku.ncmapi.model.Song
import com.rcmiku.ncmapi.model.SongAlbum
import kotlinx.serialization.Serializable

/**
 * 外部导入单曲数据模型。
 * [id] 采用稳定的负数 ID，绝不与网易云官方正数 ID 冲突。
 */
@Serializable
data class ExternalSong(
    val id: Long,                          // 稳定负数唯一 ID
    val title: String,                     // 歌曲名称
    val artist: String = "未知歌手",       // 歌手
    val album: String = "",                // 专辑名称
    val durationMs: Long = 0L,             // 时长 (ms)
    val fileUri: String,                   // 音频文件 SAF content:// 或 file:// Uri
    val fileName: String,                  // 原始文件名（如 七里香.mp3）
    val fileSize: Long = 0L,               // 文件大小（字节）
    val coverUri: String? = null,          // 本地解析并缓存的封面 Uri (file://...)
    val lyricText: String? = null,         // 自动识别/提取的 LRC 歌词文本
    val mimeType: String = "audio/mpeg",   // 音频 MIME 类型
    val lastModified: Long = 0L            // 文件最后修改时间戳
) {
    /**
     * 将外部单曲转换为播放器内核标准的 [Song] 模型。
     */
    fun toSong(playlistName: String = "外部歌单"): Song {
        return Song(
            id = id,
            name = title,
            ar = listOf(Artist(id = 0L, name = artist)),
            al = SongAlbum(
                id = 0L,
                name = album.ifBlank { playlistName },
                picUrl = coverUri.orEmpty()
            ),
            dt = durationMs
        )
    }
}

/**
 * 外部歌单数据模型。
 * 对应用户通过 SAF 导入的一个手机本地音乐文件夹。
 */
@Serializable
data class ExternalPlaylist(
    val id: String,                        // 唯一标识（UUID 或 treeUri hash）
    val name: String,                      // 歌单显示名称（支持用户重命名）
    val treeUri: String,                   // 绑定的 SAF 文件夹 Tree Uri
    val folderPathName: String,            // 文件夹原始名称/显示路径
    val createTime: Long = System.currentTimeMillis(),
    val songs: List<ExternalSong> = emptyList()
) {
    val totalBytes: Long
        get() = songs.sumOf { it.fileSize }

    val totalDurationMs: Long
        get() = songs.sumOf { it.durationMs }

    val coverUrl: String
        get() = songs.firstOrNull { !it.coverUri.isNullOrBlank() }?.coverUri.orEmpty()
}
