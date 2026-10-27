package com.rcmiku.ncmapi.api.player

import android.util.Log
import com.rcmiku.ncmapi.api.API_BASE_URL
import com.rcmiku.ncmapi.api.AudioSourceSettings
import com.rcmiku.ncmapi.api.AudioSources
import com.rcmiku.ncmapi.api.LocalSourceFetcher
import com.rcmiku.ncmapi.api.LocalSources
import com.rcmiku.ncmapi.api.LxSourceBridge
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

    /**
     * 解析可播放直链。
     *
     * @param skipUrls 上次交给播放器但被判定「不是这首歌的完整音频」的直链（播放器侧时长校验失败后写入，
     *   见 app 的 `ResolveGuard`）。客户端音源命中的 url 落在这个集合里就当作未命中，继续试下一个插件 /
     *   下一个直连源，这样「换源重试」才真的会换到别的东西，而不是把同一根坏链再刷一遍。
     */
    suspend fun songPlayUrlV1(
        songId: String,
        songLevel: SongLevel = SongLevel.STANDARD,
        source: String? = null,
        skipUrls: Set<String> = emptySet()
    ): Result<SongUrlResponse> {
        val parsed = parseSongId(songId)
        val realId = parsed.first
        val fee = parsed.second

        // 只有 fee==1（VIP/黑胶）需要第三方解灰。fee=0（免费）、fee=8（普通）等其它值
        // 官方直连即可播放，完全不碰 API 服务器。
        // 若开启了 AudioSourceSettings.RESOLVE_ALL，则所有歌曲均走第三方音源解析，未命中再兜底官方。
        if (fee != 1 && !AudioSourceSettings.RESOLVE_ALL) {
            return apiGet<SongUrlResponse>("/song/url/v1", songUrlParams(realId, songLevel, unblock = false))
        }

        // VIP（fee==1）或全量解析模式：三选一音源（LX 脚本 / 内置直连 / 代理解灰），互斥不串联。
        // 客户端侧（LX / 内置直连）开启时不再走 API 解灰链；只有代理模式才用服务器解灰。
        val selected = AudioSourceSettings.SELECTED
        val clientSideSelected = selected == AudioSources.LX || selected == AudioSources.DIRECT
        if (clientSideSelected) {
            // 试听链（musicrep-ts）不能当命中：脚本降级到官方源时会给 30 秒片段，
            // 直接返回会让播放器「解析成功」然后播一半就断。
            val clientUrl = when (selected) {
                AudioSources.LX -> LxSourceBridge.resolver?.invoke(realId, songLevel.value, skipUrls)
                else -> tryDirectSourceUrl(realId, songLevel, skipUrls)
            }?.takeIf { it.isNotBlank() && !it.contains("musicrep-ts", ignoreCase = true) }
                ?.takeUnless { PlayUrlSkip.blocks(skipUrls, it) }
            if (!clientUrl.isNullOrBlank()) {
                Log.d("PlayerApi", "Client source ($selected) resolved songId=$realId")
                return Result.success(SongUrlResponse(data = listOf(
                    SongUrl(id = realId.toLong(), url = clientUrl, br = 320000)
                )))
            }
            Log.w("PlayerApi", "Client source ($selected) missed songId=$realId skipped=${skipUrls.size}, fall back to official url")
        } else {
            val unblockResult = tryUnblockUrl(realId, source, songLevel)
            // 上次被判定为错源的 url 不再用（否则重试等于原地打转），交给官方兜底。
            val blocked = PlayUrlSkip.blocks(
                skipUrls,
                unblockResult.getOrNull()?.data?.firstOrNull()?.url
            )
            if (unblockResult.hasPlayableUrl() && !blocked) return unblockResult
        }

        // 最后兜底：官方直连。客户端音源开启时 VIP 歌在此回 30 秒试听占位（账号有会员则完整 url）；
        // 代理音源模式下解灰失败时也是走这条官方直连。
        return apiGet<SongUrlResponse>("/song/url/v1", songUrlParams(realId, songLevel, unblock = false))
    }

    private fun songUrlParams(songId: String, songLevel: SongLevel, unblock: Boolean): Map<String, Any> =
        mapOf("id" to songId, "level" to songLevel.value, "unblock" to unblock)

    /**
     * 直连音源：用户指定单源就只试它；「自动选择」按 [LocalSources.DIRECT_KEYS] 顺序轮询
     * 六条，命中即返回（照 Melodia 六源）。
     */
    private fun tryDirectSourceUrl(
        songId: String,
        songLevel: SongLevel = SongLevel.STANDARD,
        skipUrls: Set<String> = emptySet()
    ): String? {
        val chosen = LocalSources.fromKey(DirectSourceSettings.SOURCE)?.value
            ?: LocalSources.AUTO
        val chain = if (chosen == LocalSources.AUTO) LocalSources.DIRECT_KEYS else listOf(chosen)
        for (m in chain) {
            val url = LocalSourceFetcher.fetch(m, songId, songLevel)
            if (url == null) continue
            if (PlayUrlSkip.blocks(skipUrls, url)) {
                Log.d("PlayerApi", "Local source '$m' skipped (rejected by duration check) songId=$songId")
                continue
            }
            Log.d("PlayerApi", "Local source '$m' hit for songId=$songId")
            return url
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

    private suspend fun tryUnblockUrl(
        songId: String,
        sourceOverride: String?,
        songLevel: SongLevel = SongLevel.STANDARD
    ): Result<SongUrlResponse> {
        // Per-song override wins; otherwise fall back to the global setting.
        // "AUTO" (or null) means "let the server pick".
        val primary = sourceOverride?.takeIf { it.isNotBlank() && it != "AUTO" }
            ?: UNBLOCK_SOURCE.takeIf { it != "AUTO" }

        if (primary == null) {
            val matchResult = requestUnblockUrl("$API_BASE_URL/song/url/match", songId, source = null, songLevel = songLevel)
            if (matchResult.isSuccess) return matchResult
            return requestUnblockUrl("$API_BASE_URL/match", songId, source = null, songLevel = songLevel)
        }

        // Explicit source: try it first, then the remaining known sources in
        // picker order until one yields a playable URL (auto-fallback).
        val chain = listOfNotNull(primary) +
            KNOWN_UNBLOCK_SOURCES.filter { it != primary }
        var last = Result.failure<SongUrlResponse>(Exception("No unblock URL found for any source"))
        for (src in chain) {
            val matchResult = requestUnblockUrl("$API_BASE_URL/song/url/match", songId, source = src, songLevel = songLevel)
            if (matchResult.isSuccess) {
                Log.d("PlayerApi", "Unblock fallback settled on source=$src for songId=$songId")
                return matchResult
            }
            val legacyResult = requestUnblockUrl("$API_BASE_URL/match", songId, source = src, songLevel = songLevel)
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

    /** 直连源选择（「音源设置」里选中「直连音源」时的子设置）。 */
    object DirectSourceSettings {
        var SOURCE: String = LocalSources.AUTO
    }

    private suspend fun requestUnblockUrl(
        url: String,
        songId: String,
        source: String?,
        songLevel: SongLevel = SongLevel.STANDARD
    ): Result<SongUrlResponse> {
        return try {
            val response = apiClient.request(url) {
                method = HttpMethod.Get
                parameter("id", songId)
                if (!source.isNullOrEmpty()) {
                    parameter("source", source)
                }
                parameter("level", songLevel.value)
                val br = when (songLevel) {
                    SongLevel.STANDARD, SongLevel.HIGHER -> 128000
                    SongLevel.EXHIGH -> 320000
                    else -> 999000
                }
                parameter("br", br)
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
