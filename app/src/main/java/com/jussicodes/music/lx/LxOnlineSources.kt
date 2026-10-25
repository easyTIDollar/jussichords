package com.jussicodes.music.lx

import com.rcmiku.ncmapi.utils.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** 在线音源条目（对接 lx.jussichords.kdns.fr 的 /api/public/sources 返回结构）。 */
@kotlinx.serialization.Serializable
data class OnlineSource(
    val name: String = "",
    val slug: String = "",
    val description: String = "",
    val author: String = "",
    val homepage: String = "",
    val versionNo: Int = 0,
    val size: Long = 0,
    val sha: String = "",
    val url: String = "",
    val enabled: Boolean = true
) {
    /** 展示用的体积文本，例如 32 KB。 */
    val sizeText: String
        get() = when {
            size <= 0 -> ""
            size >= 1024 * 1024 -> "%.1f MB".format(size / 1024f / 1024f)
            size >= 1024 -> "${size / 1024} KB"
            else -> "$size B"
        }
}

@kotlinx.serialization.Serializable
private data class OnlineSourcesResponse(
    val ok: Boolean = false,
    val count: Int = 0,
    val sources: List<OnlineSource> = emptyList()
)

/**
 * 在线音源列表客户端：拉列表 + 下载脚本。
 * 出网用独立 OkHttpClient（不带 NCM cookie / 代理 base url），与 LxPluginEngine 的沙盒出网同原则。
 */
object LxOnlineSources {

    const val API_URL = "https://lx.jussichords.kdns.fr/api/public/sources"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /** 拉取线上音源列表；失败返回带原因的 Result。 */
    suspend fun fetch(): Result<List<OnlineSource>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(API_URL).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("服务器返回 ${response.code}")
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) error("服务器返回空内容")
                val parsed = json.decodeFromString<OnlineSourcesResponse>(body)
                parsed.sources.filter { it.enabled && it.url.isNotBlank() }
            }
        }
    }

    /**
     * 下载脚本正文；API 给了 sha256 就顺带校验（不通过按下载失败处理）。
     * 返回 null 表示失败。
     */
    suspend fun download(source: OnlineSource): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(source.url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val bytes = response.body?.bytes() ?: return@use null
                if (bytes.isEmpty()) return@use null
                if (source.sha.isNotBlank()) {
                    val actual = MessageDigest.getInstance("SHA-256").digest(bytes)
                        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
                    if (!actual.equals(source.sha, ignoreCase = true)) return@use null
                }
                bytes.toString(Charsets.UTF_8)
            }
        }.getOrNull()
    }
}
