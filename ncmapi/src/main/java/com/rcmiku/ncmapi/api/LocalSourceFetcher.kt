package com.rcmiku.ncmapi.api

import com.rcmiku.ncmapi.utils.json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 本地直连音源抓取器（照抄 Melodia 的 NativeSourceFetcher / SourceHttpClient 六源实现）：
 * 客户端直接 GET 各第三方公开接口拿 NCM 歌曲音频直链，不经代理、不经 NCM 加密通道。
 *
 * 响应两种形态（与 Melodia 一致）：
 *  - 30x 重定向 → 取 Location 直链（byfuns / qijieya / msls）
 *  - 200 JSON → 取 body 里的 url 字段（ddyr / gdmusic / oi）
 * 单个源 5s 超时、失败静默返回 null，由 PlayerApi 轮询下一个源。
 */
object LocalSourceFetcher {

    private const val UA = "Melodia/1.0"

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    fun fetch(moduleKey: String, songId: String): String? {
        val url = when (moduleKey) {
            "byfuns" -> "https://api.byfuns.top/1/?id=$songId&level=lossless"
            "ddyr" -> "https://yy.zddyr.top/lx/api/?source=netease&songmid=$songId&quality=hires"
            "gdmusic" -> "https://music-api.gdstudio.xyz/api.php?types=url&source=netease&id=$songId&br=999"
            "oi" -> "https://oiapi.net/api/Music_163?id=$songId"
            "qijieya" -> "https://api.qijieya.cn/meting/?type=url&id=$songId"
            "msls" -> "https://api.msls1441.com/?type=url&id=$songId"
            else -> return null
        }
        val raw = httpGet(url) ?: return null
        // 形态一（byfuns / qijieya / msls）：30x 的 Location 或 200 的 body 本身即直链
        val direct = raw.trim()
        if (directForm(moduleKey) && isValidAudioUrl(direct)) return direct
        // 形态二（ddyr / gdmusic / oi）：200 JSON，从 body 取 url
        val fromBody = urlFromBody(moduleKey, raw)
        return if (fromBody != null && isValidAudioUrl(fromBody)) fromBody else null
    }

    // Melodia 原实现里 body 可能是直链也可能是 JSON 的三个源
    private fun directForm(moduleKey: String): Boolean =
        moduleKey in setOf("byfuns", "qijieya", "msls")

    private fun urlFromBody(moduleKey: String, body: String): String? = runCatching {
        val root = json.parseToJsonElement(body).jsonObject
        when (moduleKey) {
            // oi：{ "data": [ { "url": ... } ] }
            "oi" -> root["data"]?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("url")?.jsonPrimitive?.contentOrNull?.trim()
            // ddyr / gdmusic / qijieya / msls / byfuns：{ "url": ... }
            else -> root["url"]?.jsonPrimitive?.contentOrNull?.trim()
        }
    }.getOrNull()

    private fun httpGet(url: String): String? {
        return try {
            val response = client.newCall(
                Request.Builder()
                    .url(url)
                    .header("User-Agent", UA)
                    .get()
                    .build()
            ).execute()
            val location = response.header("Location")?.trim()
            if (response.code in 300..399 && !location.isNullOrBlank()) {
                location
            } else if (response.isSuccessful) {
                response.body?.string()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun isValidAudioUrl(url: String): Boolean =
        url.startsWith("http") && !url.contains("<html", ignoreCase = true)
}
