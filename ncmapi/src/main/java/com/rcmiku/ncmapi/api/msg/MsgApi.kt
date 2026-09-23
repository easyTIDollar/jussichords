package com.rcmiku.ncmapi.api.msg

import com.rcmiku.ncmapi.api.apiGet
import com.rcmiku.ncmapi.model.*

/**
 * 消息中心（/msg 系列端点，需登录）。
 * 评论接口的 uid 必须为登录账号自身（传他人 uid 返回 code 400）；
 * 私信历史的 uid 为对话方（默认 9003 云音乐小秘书）。
 * 分页实测：评论/私信用 before（上一页末条 time），通知用 lasttime（上一页返回的 lastTime，缺省 -1），@我用 offset。
 */
object MsgApi {
    const val SECRETARY_UID = 9003L

    suspend fun comments(uid: Long, limit: Int = 30, before: Long? = null): Result<MsgCommentsResponse> =
        apiGet("/msg/comments", buildMap {
            put("uid", uid)
            put("limit", limit)
            before?.let { put("before", it) }
        })

    suspend fun forwards(limit: Int = 30, offset: Int = 0): Result<MsgForwardsResponse> =
        apiGet("/msg/forwards", mapOf("limit" to limit, "offset" to offset))

    suspend fun notices(limit: Int = 30, lasttime: Long = -1): Result<MsgNoticesResponse> =
        apiGet("/msg/notices", buildMap {
            put("limit", limit)
            if (lasttime >= 0) put("lasttime", lasttime)
        })

    suspend fun privateHistory(
        uid: Long = SECRETARY_UID,
        limit: Int = 30,
        before: Long? = null
    ): Result<MsgPrivateHistoryResponse> =
        apiGet("/msg/private/history", buildMap {
            put("uid", uid)
            put("limit", limit)
            before?.let { put("before", it) }
        })

    /** 发送歌曲私信（/send/song，需登录）；msg 为附言，可留空。 */
    suspend fun sendSong(userIds: String, songId: Long, msg: String = ""): Result<MsgSendResponse> =
        apiGet("/send/song", buildMap {
            put("user_ids", userIds)
            put("id", songId)
            if (msg.isNotBlank()) put("msg", msg)
        })

    /** 发送专辑私信（/send/album，需登录）。 */
    suspend fun sendAlbum(userIds: String, albumId: Long, msg: String = ""): Result<MsgSendResponse> =
        apiGet("/send/album", buildMap {
            put("user_ids", userIds)
            put("id", albumId)
            if (msg.isNotBlank()) put("msg", msg)
        })

    /** 发送歌单私信（/send/playlist，需登录）；不能发重复歌单。 */
    suspend fun sendPlaylist(userIds: String, playlistId: Long, msg: String = ""): Result<MsgSendResponse> =
        apiGet("/send/playlist", buildMap {
            put("user_ids", userIds)
            put("playlist", playlistId)
            if (msg.isNotBlank()) put("msg", msg)
        })
}
