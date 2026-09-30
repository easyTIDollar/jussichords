package com.rcmiku.ncmapi.model

import kotlinx.serialization.Serializable

/**
 * 播放历史上报（eapi feedback/weblog）双事件模型，字段对齐 MeiloX
 * PlaybackHistoryReporter + MeloXRepository.playbackHistoryBaseFields。
 *
 * startplay：开始时刻一条；play：结束时带 time（实际收听秒）+ end 原因。
 * 两条日志以 JSON 数组字符串放进 /scrobble/weblog 的 logs 参数。
 */

/** 一次播放会话的完成记录（由 app 侧 PlaybackHistorySession 计时产出，NCBL + weblog 共用）。 */
data class CompletedPlaybackSession(
    val mediaId: String,
    val songId: Long,
    /** 来源资源 id（歌单/专辑/歌手 id）；<=0 时由上报层回落到 songId。 */
    val sourceId: Long,
    /** 来源类型，取值 list/album/artist/track；不可靠时回落 track。 */
    val source: String,
    val startedAtMs: Long,
    val playedDurationMs: Long,
    val endedAtMs: Long,
    val endReason: String,
    /** 歌曲元数据（NCBL 上报需要；可为空字符串 / 0）。 */
    val songName: String = "",
    val songArtist: String = "",
    val songDurationMs: Long = 0L
)

/** 播放历史上报结果（对齐 MeiloX PlaybackLogResponse，仅保留业务判定需要的字段）。 */
@Serializable
data class PlaybackWeblogResponse(
    val code: Int? = null,
    val message: String? = null,
    val msg: String? = null
) {
    /** code 在 200..299 视为业务受理。 */
    val businessAccepted: Boolean
        get() = code != null && code in 200..299
}

/** NCBL 客户端日志（clientlogsf）上传结果（对齐 MeiloX NcblUploadResult）。 */
data class NcblUploadResult(
    val fileAccepted: Boolean,
    val httpStatus: Int? = null,
    val businessCode: Int? = null
)
