package com.jussicodes.music.data

import com.rcmiku.ncmapi.api.apiGet
import com.rcmiku.ncmapi.model.Song
import com.rcmiku.ncmapi.utils.json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** 一起听（listen together）轻量模型 —— NCM 房间同步用到的最少字段。 */
data class LTMember(val userId: Long, val nickname: String, val avatarUrl: String?)

data class LTRoom(
    val id: String,
    val creatorId: Long,
    val members: List<LTMember>,
    val createTime: Long?
)

data class LTStatus(val inRoom: Boolean, val room: LTRoom?)

/** 一次 /sync/playlist/get 快照：远端队列 + 最后一条播放命令。 */
data class LTSnapshot(
    val playMode: String?,
    val songIds: List<Long>,
    val commandType: String?,
    val targetSongId: Long?,
    val formerSongId: Long?,
    val progressMs: Long,
    val isPlaying: Boolean?,
    val clientSeq: Long,
    val serverSeq: Long
)

// ---------------------------------------------------------------------------
// JsonElement 走查工具（NCM 数值字段可能是 number 或 string，统一用 content.toLongOrNull()）。
// 风格对齐 ArtistScreenViewModel 的 apiGet<JsonElement> + 手动 walk。
// ---------------------------------------------------------------------------

private fun JsonElement?.str(): String? = when (this) {
    is JsonPrimitive -> content
    else -> null
}

private fun JsonElement?.ltBool(): Boolean? = when (val s = this?.str()) {
    null -> null
    "true", "1", "yes" -> true
    "false", "0", "no" -> false
    else -> null
}

private fun JsonElement?.ltLong(): Long? = when {
    this == null || this is JsonNull -> null
    this is JsonPrimitive -> content.toLongOrNull()
    this is JsonObject -> this["id"]?.ltLong() ?: this["value"]?.ltLong()
    else -> null
}

/** 解析 NCM 房间信息（roomInfo / data 子对象），缺失返回 null。 */
private fun objectToRoom(o: JsonObject?): LTRoom? {
    if (o == null) return null
    val id = o["roomId"]?.str().orEmpty()
    if (id.isEmpty()) return null
    val creatorId = o["creatorId"]?.ltLong() ?: 0L
    val members = (o["roomUsers"] as? JsonArray).orEmpty().mapNotNull { el ->
        val u = el as? JsonObject ?: return@mapNotNull null
        val uid = u["userId"]?.ltLong() ?: return@mapNotNull null
        LTMember(uid, u["nickname"]?.str().orEmpty(), u["avatarUrl"]?.str())
    }
    return LTRoom(id, creatorId, members, o["roomCreateTime"]?.ltLong())
}

/** 解析 /sync/playlist/get 的 data 为 [LTSnapshot]（快照空时给出保守缺省值）。 */
fun snapshotFromData(data: JsonElement?): LTSnapshot {
    val o = data as? JsonObject ?: return LTSnapshot(null, emptyList(), null, null, null, 0L, null, 0, 0)
    val playlist = o["playlist"] as? JsonObject
    val playMode = playlist?.get("playMode")?.str()
    val randomMode = playMode?.uppercase()?.let { "RANDOM" in it || "SHUFFLE" in it } == true
    val listCol = (playlist?.get(if (randomMode) "randomList" else "displayList") as? JsonObject)
    val songIds = (listCol?.get("result") as? JsonArray).orEmpty()
        .mapNotNull { it.ltLong()?.takeIf { v -> v > 0 } }
        .distinct()
    val cmd = o["playCommand"] as? JsonObject
    val commandType = cmd?.get("commandType")?.str()?.uppercase()
    val playStatus = cmd?.get("playStatus")?.str()?.uppercase()
    val targetSongId = cmd?.get("targetSongId")?.ltLong()
    val isPlaying: Boolean? = when (playStatus) {
        "PLAY", "PLAYING" -> true
        "PAUSE", "PAUSED" -> false
        else -> commandType?.let { it in setOf("PLAY", "GOTO", "NEXT", "PREV") }
    }
    return LTSnapshot(
        playMode = playMode,
        songIds = songIds,
        commandType = commandType,
        targetSongId = targetSongId,
        formerSongId = cmd?.get("formerSongId")?.ltLong(),
        progressMs = cmd?.get("progress")?.ltLong() ?: 0L,
        isPlaying = isPlaying,
        clientSeq = cmd?.get("clientSeq")?.ltLong() ?: 0L,
        serverSeq = cmd?.get("serverSeq")?.ltLong() ?: 0L
    )
}

/** 一起听端点封装（全部走 ncmapi 直连官方 weapi/eapi，路由在 ApiClient.resolveRoute）。 */
object ListenTogetherApi {

    suspend fun status(): Result<LTStatus> =
        apiGet<JsonElement>("/listen/together/status", emptyMap()).map { el ->
            val data = (el as? JsonObject)?.get("data") as? JsonObject
            LTStatus(
                inRoom = data?.get("inRoom")?.ltBool() ?: false,
                room = objectToRoom(data?.get("roomInfo") as? JsonObject)
            )
        }

    suspend fun create(): Result<LTRoom> =
        apiGet<JsonElement>("/listen/together/room/create", mapOf("refer" to "songplay_more")).map { el ->
            val info = ((el as? JsonObject)?.get("data") as? JsonObject)?.get("roomInfo") as? JsonObject
                ?: throw IllegalStateException("NetEase did not return a listen-together room")
            objectToRoom(info) ?: throw IllegalStateException("Invalid listen-together room info")
        }

    suspend fun checkRoom(roomId: String): Result<Pair<Boolean, String?>> =
        apiGet<JsonElement>("/listen/together/room/check", mapOf("roomId" to roomId)).map { el ->
            val data = (el as? JsonObject)?.get("data") as? JsonObject
            (data?.get("joinable")?.ltBool() ?: false) to (data?.get("status")?.str())
        }

    suspend fun accept(roomId: String, inviterId: String): Result<LTRoom> =
        apiGet<JsonElement>(
            "/listen/together/invitation/accept",
            mapOf("roomId" to roomId, "inviterId" to inviterId, "refer" to "inbox_invite")
        ).map { el ->
            val info = ((el as? JsonObject)?.get("data") as? JsonObject)?.get("roomInfo") as? JsonObject
                ?: throw IllegalStateException("NetEase did not return the accepted listen-together room")
            objectToRoom(info) ?: throw IllegalStateException("Invalid accepted listen-together room")
        }

    /** 上报播放命令。commandInfo 为 NCM 需要的 JSON 字符串。 */
    suspend fun reportCommand(roomId: String, commandInfo: String): Result<Unit> =
        apiGet<JsonElement>(
            "/listen/together/play/command",
            mapOf("roomId" to roomId, "commandInfo" to commandInfo)
        ).map { }

    suspend fun snapshot(roomId: String): Result<LTSnapshot> =
        apiGet<JsonElement>("/listen/together/sync/playlist", mapOf("roomId" to roomId)).map { el ->
            snapshotFromData((el as? JsonObject)?.get("data"))
        }

    /** 上报队列（REPLACE）。 */
    suspend fun reportPlaylist(roomId: String, userId: Long, version: Long, songIds: List<Long>): Result<Unit> =
        apiGet<JsonElement>(
            "/listen/together/sync/list",
            mapOf(
                "roomId" to roomId,
                "playlistParam" to buildString {
                    append("{\"commandType\":\"REPLACE\",\"version\":[")
                    append("{\"userId\":").append(userId).append(",\"version\":").append(version).append("]")
                    append(",\"anchorSongId\":\"\",\"anchorPosition\":-1")
                    append(",\"randomList\":[").append(songIds.joinToString(",")).append("]")
                    append(",\"displayList\":[").append(songIds.joinToString(",")).append("]}")
                }
            )
        ).map { }

    /** 心跳，返回 NCM 建议的下次间隔（秒）。缺失回落 30。 */
    suspend fun heartbeat(roomId: String, songId: Long, isPlaying: Boolean, progressMs: Long): Int {
        val result = apiGet<JsonElement>(
            "/listen/together/heartbeat",
            mapOf(
                "roomId" to roomId,
                "songId" to songId.toString(),
                "playStatus" to if (isPlaying) "PLAY" else "PAUSE",
                "progress" to progressMs.coerceAtLeast(0).toString()
            )
        )
        val span = ((result.getOrNull() as? JsonObject)?.get("data") as? JsonObject)
            ?.get("timeSpan")?.ltLong()
        return (span ?: 30L).coerceIn(5, 60).toInt()
    }

    suspend fun end(roomId: String): Result<Unit> =
        apiGet<JsonElement>("/listen/together/end", mapOf("roomId" to roomId)).map { }

    /** 按 id 批量取 [Song]（远端队列重建用，NCM /v3/song/detail；c 为 [{id:...}] 字符串）。 */
    suspend fun songsByIds(ids: List<Long>): Result<List<Song>> {
        if (ids.isEmpty()) return Result.success(emptyList())
        val c = "[" + ids.joinToString(",") { """{"id":$it}""" } + "]"
        return apiGet<JsonElement>("/song/detail/batch", mapOf("c" to c)).map { el ->
            val arr = ((el as? JsonObject)?.get("songs") as? JsonArray).orEmpty()
            arr.mapNotNull { item ->
                runCatching { json.decodeFromString<Song>(item.toString()) }.getOrNull()
            }
        }
    }
}

/** 一起听官方 H5 分享/邀请链接（MeiloX 同款：st.music.163.com）。 */
fun buildListenTogetherInviteUrl(roomId: String, inviterId: Long, songId: Long): String =
    "https://st.music.163.com/listen-together/share?songId=$songId&roomId=$roomId&inviterId=$inviterId"

/** 从分享的文本里解析 roomId + inviterId（官方链接或用户粘的整段文字都认）。 */
fun parseListenTogetherInvitation(text: String): Pair<String, String>? {
    val normalized = text.trim().replace("&amp;", "&")
    if (normalized.isEmpty()) return null
    val candidate = normalized
        .split(Regex("\\s+"))
        .firstOrNull { it.contains("roomId=", ignoreCase = true) } ?: normalized
    val values = Regex("(?:[?&#]|^)([A-Za-z][A-Za-z0-9]*)=([^&#\\s]+)")
        .findAll(candidate)
        .associate { m ->
            m.groupValues[1].lowercase() to runCatching {
                java.net.URLDecoder.decode(m.groupValues[2], Charsets.UTF_8.name())
            }.getOrNull().orEmpty().trim()
        }
    val roomId = values["roomid"]?.takeIf { it.isNotBlank() } ?: return null
    val inviterId = (values["inviterid"] ?: values["inviteruid"])?.takeIf { it.isNotBlank() } ?: return null
    return roomId to inviterId
}
