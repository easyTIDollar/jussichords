package com.rcmiku.ncmapi.api.player

import android.util.Log
import com.rcmiku.ncmapi.api.API_BASE_URL
import com.rcmiku.ncmapi.api.LocalSourceFetcher
import com.rcmiku.ncmapi.api.LocalSourceSettings
import com.rcmiku.ncmapi.api.LocalSources
import com.rcmiku.ncmapi.api.UNBLOCK_SOURCE
import com.rcmiku.ncmapi.api.apiClient
import com.rcmiku.ncmapi.api.apiGet
import com.rcmiku.ncmapi.model.LyricResponse
import com.rcmiku.ncmapi.model.SongUrl
import com.rcmiku.ncmapi.model.SongUrlResponse
import com.rcmiku.ncmapi.utils.CookieProvider
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object PlayerApi {

    suspend fun songPlayUrlV1(
        songId: String,
        songLevel: SongLevel = SongLevel.STANDARD,
        source: String? = null
    ): Result<SongUrlResponse> {
        val parsed = parseSongId(songId)
        val realId = parsed.first
        val fee = parsed.second

        // 只有 fee==1（VIP/黑胶）需要第三方解灰。fee=0（免费）、fee=8（普通）等其它值
        // 官方直连即可播放，完全不碰 API 服务器。
        // 用户确认：fee=1 是要解灰的 VIP，fee=8 是直接能听的普通歌。
        if (fee != 1) {
            return apiGet<SongUrlResponse>("/song/url/v1", songUrlParams(realId, songLevel, unblock = false))
        }

        // VIP（fee==1）：先试设置里选的本地直连源（客户端直接 GET 第三方公开接口，
        // 不经代理、不经 NCM 加密通道，照抄 Melodia 六源）。
        val localUrl = tryLocalSourceUrl(realId)
        if (localUrl != null) {
            Log.d("PlayerApi", "Local source resolved songId=$realId url=$localUrl")
            return Result.success(SongUrlResponse(data = listOf(
                SongUrl(id = realId.toLong(), url = localUrl, br = 320000)
            )))
        }

        // 本地源未命中：退回官方接口走设置里选的 API 服务器解灰。
        val unblockResult = tryUnblockUrl(realId, source)
        if (unblockResult.hasPlayableUrl()) return unblockResult

        // 解灰失败（服务器不可用/该源无版权）：退回官方直连。
        // 账号有会员则 NCM 直接回完整 url，否则是 30 秒试听占位。
        return apiGet<SongUrlResponse>("/song/url/v1", songUrlParams(realId, songLevel, unblock = false))
    }

    private fun songUrlParams(songId: String, songLevel: SongLevel, unblock: Boolean): Map<String, Any> =
        mapOf("id" to songId, "level" to songLevel.value, "unblock" to unblock)

    /**
     * 本地直连音源探测（照 Melodia）：OFF 直接跳过；AUTO 按序轮询全部源；
     * 单源则只试它。命中返回直链，否则 null（交回代理解灰链）。
     */
    private fun tryLocalSourceUrl(songId: String): String? {
        val chosen = LocalSourceSettings.SONG_SOURCE
        if (chosen == LocalSources.OFF) return null
        val modules = if (chosen == LocalSources.AUTO) LocalSources.LOCAL_KEYS
            else listOf(chosen.lowercase())
        for (m in modules) {
            val url = LocalSourceFetcher.fetch(m, songId)
            if (url != null) {
                Log.d("PlayerApi", "Local source '$m' hit for songId=$songId")
                return url
            }
        }
        return null
    }


    private fun Result<SongUrlResponse>.hasPlayableUrl(): Boolean =
        getOrNull()?.data?.any { it.isPlayableFullUrl() } == true

    private fun SongUrl.isPlayableFullUrl(): Boolean =
        !url.isNullOrEmpty() &&
            // NCM 占位/外链（未真正解锁成功时返回），实测返回反爬 HTML 而非音频
            !url.contains("outer/url") &&
            // 试听 30 秒占位（time 单位 ms：30040）
            time != 30_040L
            // 注意：freeTrial* 字段 NCM 对可播歌曲也常回非 null 的空对象，不能用作判据

    private fun parseSongId(raw: String): Triple<String, Int, Int> {
        val queryIndex = raw.indexOf('?')
        if (queryIndex == -1) return Triple(raw, 0, 0)
        val id = raw.substring(0, queryIndex)
        val query = raw.substring(queryIndex + 1)
        val params = query.split("&").associate {
            val eq = it.indexOf('=')
            if (eq > 0) it.substring(0, eq) to it.substring(eq + 1) else it to ""
        }
        val fee = params["fee"]?.toIntOrNull() ?: 0
        val pl = params["pl"]?.toIntOrNull() ?: 0
        return Triple(id, fee, pl)
    }

    private suspend fun tryUnblockUrl(songId: String, sourceOverride: String?): Result<SongUrlResponse> {
        // Per-song override wins; otherwise fall back to the global setting.
        // "AUTO" (or null) means "let the server pick".
        val primary = sourceOverride?.takeIf { it.isNotBlank() && it != "AUTO" }
            ?: UNBLOCK_SOURCE.takeIf { it != "AUTO" }

        if (primary == null) {
            val matchResult = requestUnblockUrl("$API_BASE_URL/song/url/match", songId, source = null)
            if (matchResult.isSuccess) return matchResult
            return requestUnblockUrl("$API_BASE_URL/match", songId, source = null)
        }

        // Explicit source: try it first, then the remaining known sources in
        // picker order until one yields a playable URL (auto-fallback).
        val chain = listOfNotNull(primary) +
            KNOWN_UNBLOCK_SOURCES.filter { it != primary }
        var last = Result.failure<SongUrlResponse>(Exception("No unblock URL found for any source"))
        for (src in chain) {
            val matchResult = requestUnblockUrl("$API_BASE_URL/song/url/match", songId, source = src)
            if (matchResult.isSuccess) {
                Log.d("PlayerApi", "Unblock fallback settled on source=$src for songId=$songId")
                return matchResult
            }
            val legacyResult = requestUnblockUrl("$API_BASE_URL/match", songId, source = src)
            if (legacyResult.isSuccess) {
                Log.d("PlayerApi", "Unblock fallback settled on source=$src (legacy) for songId=$songId")
                return legacyResult
            }
            last = legacyResult
        }
        return last
    }

    /**
     * Fallback chain for unblock sources, mirroring the order of the app's
     * source picker (shared in ncmapi's UnblockSources so UI and fallback
     * never drift).
     */
    private val KNOWN_UNBLOCK_SOURCES: List<String> =
        com.rcmiku.ncmapi.api.UnblockSources.FALLBACK_VALUES

    private suspend fun requestUnblockUrl(url: String, songId: String, source: String?): Result<SongUrlResponse> {
        return try {
            val response = apiClient.request(url) {
                method = HttpMethod.Get
                parameter("id", songId)
                if (!source.isNullOrEmpty()) {
                    parameter("source", source)
                }
                CookieProvider.cookie.takeIf { it.isNotEmpty() }?.let {
                    header("Cookie", it)
                }
            }
            if (response.status.isSuccess()) {
                val body = response.bodyAsText()
                val unblockData = parseUnblockResponse(body)
                if (unblockData != null) {
                    Log.d("PlayerApi", "Unblock resolved songId=$songId source=$source url=${unblockData.url}")
                    Result.success(SongUrlResponse(data = listOf(
                        SongUrl(id = songId.toLong(), url = unblockData.url, br = unblockData.br)
                    )))
                } else {
                    Log.w("PlayerApi", "No unblock URL found for songId=$songId source=$source body=$body")
                    Result.failure(Exception("No unblock URL found: $body"))
                }
            } else {
                val body = response.bodyAsText()
                Log.w("PlayerApi", "Unblock service returned ${response.status} for songId=$songId body=$body")
                Result.failure(Exception("Unblock service returned ${response.status}: $body"))
            }
        } catch (e: Exception) {
            Log.w("PlayerApi", "Unblock request failed for songId=$songId source=$UNBLOCK_SOURCE url=$url", e)
            Result.failure(e)
        }
    }

    private data class UnblockData(val url: String, val br: Int)

    private fun parseUnblockResponse(body: String): UnblockData? {
        return try {
            val json = kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
                isLenient = true
            }
            val jsonObj = json.parseToJsonElement(body).jsonObject
            parseUnblockData(jsonObj)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseUnblockData(jsonObj: JsonObject): UnblockData? {
        val candidates = listOfNotNull(
            jsonObj["data"],
            jsonObj["result"],
            jsonObj["url"],
        )
        return candidates.firstNotNullOfOrNull(::parseUnblockElement)
    }

    private fun parseUnblockElement(element: JsonElement): UnblockData? =
        when (element) {
            is JsonPrimitive -> {
                val url = element.contentOrNull
                if (!url.isNullOrEmpty()) UnblockData(url, 320000) else null
            }
            is JsonArray -> element.firstNotNullOfOrNull(::parseUnblockElement)
            is JsonObject -> {
                if (element.isTrialUrl()) {
                    null
                } else {
                    val url = element["url"]?.jsonPrimitive?.contentOrNull
                        ?: element["data"]?.let(::parseUnblockElement)?.url
                    val br = element["br"]?.jsonPrimitive?.intOrNull ?: 320000
                    if (!url.isNullOrEmpty()) UnblockData(url, br) else null
                }
            }
            else -> null
        }

    private fun JsonObject.isTrialUrl(): Boolean =
        containsKey("freeTrialInfo") ||
            containsKey("freeTrialPrivilege") ||
            containsKey("freeTimeTrialPrivilege") ||
            this["time"]?.jsonPrimitive?.contentOrNull == "30040"

    suspend fun songLyric(musicId: Long): Result<LyricResponse> =
        apiGet("/lyric/new", mapOf("id" to musicId))
}
