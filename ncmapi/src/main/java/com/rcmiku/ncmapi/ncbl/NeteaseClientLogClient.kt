package com.rcmiku.ncmapi.ncbl

import com.rcmiku.ncmapi.model.NcblUploadResult
import com.rcmiku.ncmapi.utils.json
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.concurrent.atomic.AtomicLong

/**
 * NCBL（clientlogsf）上传客户端（移植自 MeiloX NeteaseClientLogClient，Ktor 化）。
 *
 * 端点：https://clientlogsf.music.163.com/api/clientlog/encrypt/upload?multiupload=true
 * 每次上传一个新的 NCBL 信封文件（start/end 各一次，multipart form 的 file 字段）；
 * 响应在 data.successfiles 里回显被接受的文件名，HTTP 2xx + code=200 + 文件名匹配才视为受理。
 */
class NeteaseClientLogClient(
    private val http: HttpClient = HttpClient(OkHttp)
) {
    /** 校验并返回可上报的会话（对齐 MeiloX beginSession）：MUSIC_U / songId / sourceId 齐备。 */
    fun beginSession(session: NcblSessionContext): NcblSessionContext? = session.takeIf {
        it.musicU.isNotBlank() && it.songId > 0L && it.sourceId > 0L
    }

    suspend fun submitStart(
        session: NcblSessionContext,
        eventTimeMs: Long
    ): NcblUploadResult = upload(session, NcblPayload.start(session, eventTimeMs))

    suspend fun submitEnd(
        session: NcblSessionContext,
        playedDurationMs: Long,
        eventTimeMs: Long,
        endReason: String
    ): NcblUploadResult = upload(
        session,
        NcblPayload.end(session, playedDurationMs, eventTimeMs, endReason),
    )

    private suspend fun upload(
        session: NcblSessionContext,
        event: NcblLogEvent
    ): NcblUploadResult = withContext(Dispatchers.IO) {
        val fileName = nextFileName()
        try {
            val meta = buildMeta(session)
            val encoded = NcblCodec.encode(meta.toByteArray(Charsets.UTF_8), event.record)
            val filePart = MultiPartFormDataContent(
                formData {
                    // 对齐 MeiloX：file 字段（form-data; name="file"; filename=<fileName>）。
                    append(
                        "file",
                        encoded,
                        Headers.build {
                            append(
                                HttpHeaders.ContentDisposition,
                                "form-data; name=\"file\"; filename=\"$fileName\""
                            )
                        },
                    )
                },
            )
            val response = http.request(NCBL_UPLOAD_ENDPOINT) {
                method = HttpMethod.Post
                header("X-Music-U", session.musicU)
                header("X-DeviceId", session.deviceId)
                header("X-Os", session.os)
                header("X-Osver", session.osVersion)
                header("X-SDeviceId", session.deviceId)
                header("X-Buildver", session.buildVersion.toString())
                header("User-Agent", buildUserAgent(session))
                header("Cookie", buildCookie(session))
                header("Accept", "application/json")
                setBody(filePart)
            }
            val bodyText = runCatching { response.bodyAsText() }.getOrNull()
            parseNcblUploadResponse(response.status.value, bodyText, fileName)
        } catch (e: Exception) {
            NcblUploadResult(fileAccepted = false)
        }
    }

    /** 元数据 JSON（对齐 MeiloX buildMeta：MUSIC_U + URS_APPID + appver + buildver）。 */
    private fun buildMeta(session: NcblSessionContext): String = buildJsonObject {
        put("MUSIC_U", session.musicU)
        put("URS_APPID", session.ursAppId)
        put("appver", session.appVersion)
        put("buildver", session.buildVersion.toString())
    }.toString()

    private fun buildUserAgent(session: NcblSessionContext): String =
        "NeteaseMusic/${session.appVersion}(${session.versionCode}); Dalvik/2.1.0 " +
            "(Linux; U; Android ${session.osVersion}; ${session.model} Build/${session.buildId})"

    private fun buildCookie(session: NcblSessionContext): String = buildString {
        append("MUSIC_U=").append(session.musicU)
        append("; URS_APPID=").append(session.ursAppId)
        append("; deviceId=").append(session.deviceId)
        append("; sDeviceId=").append(session.deviceId)
        append("; os=").append(session.os)
        append("; osver=").append(session.osVersion)
        append("; appver=").append(session.appVersion)
        append("; versioncode=").append(session.versionCode)
        append("; buildver=").append(session.buildVersion)
        append("; channel=").append(session.channel)
        append("; mobilename=").append(session.model.replace(' ', '+'))
        append("; brand=").append(session.brand.replace(' ', '+'))
        append("; packageType=").append(session.buildType)
    }

    companion object {
        const val NCBL_UPLOAD_ENDPOINT =
            "https://clientlogsf.music.163.com/api/clientlog/encrypt/upload?multiupload=true"

        private val fileSequence = AtomicLong()

        private fun nextFileName(): String {
            val id = fileSequence.getAndIncrement()
            val random = (1L..4_294_967_295L).random()
            return "flush_ua_${(10_000..99_999).random()}_${id}_$random"
        }

        /**
         * 解析上传响应（对齐 MeiloX parseNcblUploadResponse）：
         * HTTP 2xx 且 code=200 且 data.successfiles 含本次文件名才视为 fileAccepted。
         */
        fun parseNcblUploadResponse(
            httpStatus: Int,
            body: String?,
            expectedFileName: String
        ): NcblUploadResult {
            val root = runCatching {
                body?.takeIf { it.isNotBlank() }?.let { json.parseToJsonElement(it).jsonObject }
            }.getOrNull()
            val code = root?.get("code")?.let { runCatching { it.jsonPrimitive.content.toIntOrNull() }.getOrNull() }
            val files = root
                ?.get("data")
                ?.jsonObject
                ?.get("successfiles")
                ?.jsonArray
                ?.map { it.jsonPrimitive.content }
                ?: emptyList()
            val accepted = files.any { it == expectedFileName }
            return NcblUploadResult(
                fileAccepted = httpStatus in 200..299 && code == 200 && accepted,
                httpStatus = httpStatus,
                businessCode = code,
            )
        }
    }
}
