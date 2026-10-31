package com.jussicodes.music.data.download

import kotlinx.serialization.Serializable

@Serializable
enum class DownloadStatus {
    PENDING,     // 等待排队
    RESOLVING,   // 正在解析直链
    DOWNLOADING, // 下载中
    PAUSED,      // 已暂停
    COMPLETED,   // 已完成
    FAILED       // 失败
}

@Serializable
data class DownloadTask(
    val songId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val artistId: Long = 0L,
    val albumId: Long = 0L,
    val coverUrl: String,
    val durationMs: Long = 0L,
    val quality: String = "lossless", // standard, exhigh, lossless, hires
    val status: DownloadStatus = DownloadStatus.PENDING,
    val progress: Float = 0f,         // 0.0 ~ 1.0
    val speedText: String = "",       // 如 "1.2 MB/s"
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val filePath: String? = null,     // 物理绝对路径（若有）
    val fileUri: String? = null,      // Content Uri 或 File Uri 字符串
    val mimeType: String = "audio/mpeg",
    val actualQualityText: String? = null, // 真实下载到的格式与码率，如 "FLAC 无损"、"MP3 320K"
    val isAlbumFolder: Boolean = false,    // 仅整张专辑下载时分专辑文件夹
    val groupFolder: String? = null,       // 强制统一的文件夹名，避免先行单曲专辑名不同被拆成多个文件夹
    val customGroup: String? = null,       // 用户自定义分组名称
    val sourceName: String = "",           // 来源名称（歌单名 / 专辑名）
    val sourceType: String = "",           // 来源类型：album, playlist, single
    val errorMsg: String? = null,
    val createTime: Long = System.currentTimeMillis(),
    val finishTime: Long? = null
)
