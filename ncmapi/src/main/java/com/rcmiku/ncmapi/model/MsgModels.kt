package com.rcmiku.ncmapi.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonElement

/** 消息中心（/msg 系列端点，需登录）的数据模型。 */

@Serializable
data class MsgUser(
    @SerialName("userId") val userId: Long = 0,
    val nickname: String = "",
    @SerialName("avatarUrl") val avatarUrl: String = ""
)

/** 评论通知（/msg/comments）单项；uid 必须为登录账号自身。 */
@Serializable
data class MsgComment(
    @SerialName("commentId") val commentId: Long = 0,
    val content: String = "",
    @SerialName("richContent") val richContent: String = "",
    val time: Long = 0,
    val user: MsgUser? = null,
    @SerialName("beRepliedUser") val beRepliedUser: MsgUser? = null,
    @SerialName("beRepliedContent") val beRepliedContent: String = "",
    @SerialName("threadId") val threadId: String = "",
    @SerialName("resourceType") val resourceType: Int = 0
)

@Serializable
data class MsgCommentsResponse(
    val code: Int = 0,
    val total: Int = 0,
    val comments: List<MsgComment> = emptyList(),
    val more: Boolean = false
)

/** @我 消息（/msg/forwards）单项；字段结构 NCM 未完全公开，msg 保留原文按需解析。 */
@Serializable
data class MsgForward(
    val time: Long = 0,
    val msg: JsonElement? = null,
    @SerialName("fromUser") val fromUser: MsgUser? = null
)

@Serializable
data class MsgForwardsResponse(
    val code: Int = 0,
    @SerialName("newCount") val newCount: Int = 0,
    @SerialName("lasttime") val lasttime: Long = 0,
    val more: Boolean = false,
    val forwards: List<MsgForward> = emptyList()
)

/** 系统通知（/msg/notices）单项；notice 为内层 JSON 字符串。 */
@Serializable
data class MsgNotice(
    val id: Long = 0,
    val time: Long = 0,
    val notice: String = "",
    @SerialName("userId") val userId: Long = 0
)

@Serializable
data class MsgNoticesResponse(
    val code: Int = 0,
    val notices: List<MsgNotice> = emptyList(),
    val more: Boolean = false,
    @SerialName("lastTime") val lastTime: Long = 0
)

/** 私信（/msg/private/history）单项；msg 为内层 JSON 字符串（可能含 song/playlist 等字段）。 */
@Serializable
data class MsgPrivateMessage(
    val time: Long = 0,
    val msg: String = "",
    @SerialName("fromUser") val fromUser: MsgUser? = null,
    @SerialName("toUser") val toUser: MsgUser? = null
)

@Serializable
data class MsgPrivateHistoryResponse(
    val code: Int = 0,
    val msgs: List<MsgPrivateMessage> = emptyList(),
    val more: Boolean = false
)

/** 通知 notice 字段（内层 JSON 字符串）的常用结构：type 6 = 评论互动。 */
@Serializable
data class MsgNoticeInner(
    val type: Int = 0,
    val comment: MsgNoticeComment? = null,
    val user: MsgUser? = null
)

@Serializable
data class MsgNoticeComment(
    val content: String = "",
    val user: MsgUser? = null,
    @SerialName("resourceType") val resourceType: Int = 0
)

/** 私信 msg 字段（内层 JSON 字符串）的常用结构。 */
@Serializable
data class MsgPrivateInner(
    val msg: String = "",
    val song: MsgPrivateSong? = null,
    val playlist: MsgPrivatePlaylist? = null
)

@Serializable
data class MsgPrivateSong(
    val id: Long = 0,
    val name: String = "",
    val artists: List<MsgPrivateArtist> = emptyList(),
    @SerialName("picUrl") val picUrl: String = ""
)

@Serializable
data class MsgPrivateArtist(
    val name: String = ""
)

@Serializable
data class MsgPrivatePlaylist(
    val id: Long = 0,
    val name: String = "",
    @SerialName("coverImgUrl") val coverImgUrl: String = "",
    @SerialName("picUrl") val picUrl: String = ""
)
