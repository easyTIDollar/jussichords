package com.rcmiku.ncmapi.ncbl

import com.rcmiku.ncmapi.model.CompletedPlaybackSession
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * NCBL 客户端日志事件（移植自 MeiloX NcblPayload）：
 * - start：_plv，time=0
 * - end：_pld，time=实际收听秒 + end 原因
 * 记录行格式：{epoch秒}\u0001{action}\u0001{JSON字段}
 */
data class NcblLogEvent(
    val action: String,
    val songId: Long,
    val timeSeconds: Long,
    val startLogTimeSeconds: Long,
    val logTimeMs: Long,
    val record: ByteArray
) {
    override fun equals(other: Any?) = other is NcblLogEvent &&
        action == other.action && songId == other.songId &&
        timeSeconds == other.timeSeconds && logTimeMs == other.logTimeMs &&
        record.contentEquals(other.record)

    override fun hashCode(): Int = record.contentHashCode()
}

object NcblPayload {
    fun start(session: NcblSessionContext, eventTimeMs: Long): NcblLogEvent = event(
        session = session,
        action = "_plv",
        playedSeconds = 0L,
        eventTimeMs = eventTimeMs,
        endReason = null,
    )

    fun end(
        session: NcblSessionContext,
        playedDurationMs: Long,
        eventTimeMs: Long,
        endReason: String
    ): NcblLogEvent = event(
        session = session,
        action = "_pld",
        playedSeconds = playedDurationMs.coerceAtLeast(0L) / 1_000L,
        eventTimeMs = eventTimeMs,
        endReason = endReason,
    )

    private fun event(
        session: NcblSessionContext,
        action: String,
        playedSeconds: Long,
        eventTimeMs: Long,
        endReason: String?,
    ): NcblLogEvent {
        val fields = buildMap {
            put("id", session.songId.toString())
            put("type", "song")
            put("source", session.source)
            put("sourceId", session.sourceId.toString())
            put("startlogtime", session.startLogTimeSeconds)
            put("logtime", eventTimeMs)
            put("time", playedSeconds)
            put("realtime", playedSeconds)
            put("_sessid", session.sessionId)
            put("_eventcode", action)
            put("_log_thoroughfare", "ua")
            put("abtest", "")
            put("pid", session.pid)
            if (session.songName.isNotBlank()) put("songName", session.songName)
            if (session.songArtist.isNotBlank()) put("artistName", session.songArtist)
            if (session.songDurationMs > 0L) put("resource_time", session.songDurationMs / 1_000L)
            if (action == "_pld") endReason?.let { put("end", it) }
        }
        val recordTimeSeconds = eventTimeMs / 1_000L
        val record = "$recordTimeSeconds\u0001$action\u0001${toJson(fields)}".toByteArray(Charsets.UTF_8)
        return NcblLogEvent(
            action = action,
            songId = session.songId,
            timeSeconds = playedSeconds,
            startLogTimeSeconds = session.startLogTimeSeconds,
            logTimeMs = eventTimeMs,
            record = record,
        )
    }

    /** 最小 JSON 序列化（kotlinx.serialization，避免引入 Gson 依赖）。 */
    private fun toJson(fields: Map<String, Any>): String {
        return buildJsonObject {
            fields.forEach { (k, v) ->
                when (v) {
                    is String -> put(k, v)
                    is Long -> put(k, v)
                    is Int -> put(k, v)
                    else -> put(k, v.toString())
                }
            }
        }.toString()
    }
}

/** 一次 NCBL 会话的不可变快照（凭证不进 toString）。 */
class NcblSessionContext(
    val musicU: String,
    val deviceId: String,
    val songId: Long,
    val songName: String,
    val songArtist: String,
    val source: String,
    val sourceId: Long,
    val startedAtMs: Long,
    val songDurationMs: Long,
    val sessionId: String,
    val pid: Int,
    val ursAppId: String,
    val appVersion: String,
    val versionCode: String,
    val channel: String,
    val os: String,
    val osVersion: String,
    val model: String,
    val brand: String,
    val processName: String,
    val buildType: String,
    val buildId: String
) {
    val startLogTimeSeconds: Long = startedAtMs / 1_000L
    val buildVersion: Long = startedAtMs.coerceAtLeast(0L) / 1_000L

    override fun toString(): String = "NcblSessionContext"
}

/** 便捷重载：从完成会话 + 应用上下文构建 NCBL 会话。 */
fun buildNcblSession(
    session: CompletedPlaybackSession,
    credentials: NcblCredentials,
    device: NcblDeviceInfo,
    profile: NcblClientProfile,
    sessionId: String
): NcblSessionContext = NcblSessionContext(
    musicU = credentials.musicU,
    deviceId = device.deviceId,
    songId = session.songId,
    songName = session.songName,
    songArtist = session.songArtist,
    source = session.source,
    sourceId = session.sourceId,
    startedAtMs = session.startedAtMs,
    songDurationMs = session.songDurationMs,
    sessionId = sessionId,
    pid = device.pid,
    ursAppId = profile.ursAppId,
    appVersion = profile.appVersion,
    versionCode = profile.versionCode,
    channel = profile.channel,
    os = profile.os,
    osVersion = device.osVersion,
    model = device.model,
    brand = device.brand,
    processName = device.processName,
    buildType = device.buildType,
    buildId = device.buildId
)

/** 移动端 Android 兼容 profile（对齐 MeiloX NcblClientProfile.Android）。 */
data class NcblClientProfile(
    val os: String,
    val appVersion: String,
    val versionCode: String,
    val channel: String,
    val ursAppId: String,
) {
    companion object {
        val Android = NcblClientProfile(
            os = "android",
            appVersion = "8.20.20.231215173437",
            versionCode = "6006066",
            channel = "xiaomi",
            ursAppId = "F2219AE9D7828A7D73E2006D000C61031D196A37DB497E3885B8298504867886B6F0E44087D61EFC06BE92279CD6EEC6",
        )
    }
}

/** 设备指纹（NCBL 元数据需要）。 */
data class NcblDeviceInfo(
    val deviceId: String,
    val osVersion: String,
    val model: String,
    val brand: String,
    val processName: String,
    val buildType: String,
    val pid: Int,
    val buildId: String,
)

/** 凭证。toString 不暴露 MUSIC_U。 */
class NcblCredentials(
    val musicU: String,
    val deviceId: String,
) {
    override fun toString(): String = "NcblCredentials"
}
