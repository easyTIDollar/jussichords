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
    val errorMsg: String? = null,
    val createTime: Long = System.currentTimeMillis(),
    val finishTime: Long? = null
)
