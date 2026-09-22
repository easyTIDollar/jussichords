package com.jussicodes.music.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/**
 * 私信聊天页用到的 app 本地模型（不走 ncmapi，端点直接 apiGet）。
 *
 * 最近联系（/msg/recentcontact）：按最近私信往来时间排序的联系人快照，
 * 实测约 43 人封顶（count 参数不生效，需登录）。字段名 follow 有误导性，并非全量关注列表。
 * 仅保留客户端展示/过滤用的字段；userType 过滤 0（普通）/ 207（官方未公开枚举，数据驱动）。
 */
@Serializable
data class MsgRecentContact(
    @SerialName("userId") val userId: Long = 0,
    val nickname: String = "",
    @SerialName("avatarUrl") val avatarUrl: String = "",
    @SerialName("userType") val userType: Int = -1,
    @SerialName("vipType") val vipType: Int = 0,
    val mutual: Boolean = false,
    val onlined: Boolean = false
)

@Serializable
data class MsgRecentContactsData(
    val follow: List<MsgRecentContact> = emptyList()
)

/** /msg/recentcontact 响应：data.follow[]。 */
@Serializable
data class MsgRecentContactsResponse(
    val code: Int = 0,
    val data: MsgRecentContactsData? = null
) {
    val follow: List<MsgRecentContact> get() = data?.follow ?: emptyList()
}

/** /send/text 响应：code 301 = 未登录；不能发私信给自己。 */
@Serializable
data class MsgSendTextResponse(
    val code: Int = 0,
    val msg: String = ""
)

// ——————————————— 会话列表（/msg/private）———————————————

/** 会话内 user 子对象：会话元数据。lastMsg 为两层 JSON 字符串（外层 {msgId,msg,type}，内层 msg 又是 JSON 字符串）。 */
@Serializable
data class MsgSessionMeta(
    @SerialName("fromUserId") val fromUserId: Long = 0,
    @SerialName("toUserId") val toUserId: Long = 0,
    @SerialName("msgCount") val msgCount: Int = 0,
    @SerialName("newMsgCount") val newMsgCount: Int = 0,
    @SerialName("lastMsgTime") val lastMsgTime: Long = 0,
    val lastMsg: String = ""
)

/** 会话端资料（fromUser / toUser 共用）；仅保留展示/过滤字段。 */
@Serializable
data class MsgSessionUser(
    @SerialName("userId") val userId: Long = 0,
    val nickname: String = "",
    @SerialName("avatarUrl") val avatarUrl: String = "",
    @SerialName("userType") val userType: Int = -1,
    @SerialName("vipType") val vipType: Int = 0,
    val mutual: Boolean = false
)

/** /msg/private 单条会话。实测 toUser 恒为自己、fromUser 为对方。 */
@Serializable
data class MsgPrivateSession(
    val user: MsgSessionMeta = MsgSessionMeta(),
    @SerialName("fromUser") val fromUser: MsgSessionUser? = null,
    @SerialName("toUser") val toUser: MsgSessionUser? = null
) {
    /** 对方资料：排除自己后取一端；myUid=0（未登录）时取 fromUser。 */
    fun otherUser(myUid: Long): MsgSessionUser? =
        when {
            myUid > 0 && fromUser?.userId == myUid -> toUser
            else -> fromUser ?: toUser
        }
}

/** /msg/private 响应；顶层 newMsgCount = 全账号未读总数。 */
@Serializable
data class MsgPrivateSessionsResponse(
    val code: Int = 0,
    val msgs: List<MsgPrivateSession> = emptyList(),
    val more: Boolean = false,
    @SerialName("newMsgCount") val newMsgCount: Int = 0
)

