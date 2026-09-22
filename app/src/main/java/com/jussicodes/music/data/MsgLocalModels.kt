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
