package com.rcmiku.ncmapi.api

import com.rcmiku.ncmapi.utils.json
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 网易云扫码登录：直接走官方 weapi 二维码接口，不依赖网页。
 *
 * 流程：
 *  1. POST /weapi/login/qrcode/unikey        -> {"code":200,"unikey":"..."}
 *  2. 二维码内容 https://music.163.com/login?codekey=<unikey>，用网易云 App 扫
 *  3. POST /weapi/login/qrcode/client/login  -> code 800 过期 / 801 等待扫码 /
 *     802 已扫码待确认 / 803 成功（803 的响应头 Set-Cookie 里才有 MUSIC_U）
 *
 * 这条通道与 CookieProvider 的登录 cookie 完全隔离：扫码过程中还没有登录态，
 * 只用自己维护的匿名 cookie（首页 Set-Cookie 的 NMTID 等）保证握手。
 */
data class QrLoginStatus(
    val code: Int,
    val message: String,
    val cookies: Map<String, String> = emptyMap(),
)

object QrLoginApi {

    private const val BASE = "https://music.163.com"
    // 用普通桌面 Chrome UA：移动端 WebView UA 容易被风控降级。
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val cookieStore = mutableMapOf<String, String>()
    private var warmedUp = false

    private fun cookieHeader(): String =
        cookieStore.entries.joinToString("; ") { "${it.key}=${it.value}" }

    private fun absorbSetCookies(values: List<String>) {
        values.forEach { raw ->
            val pair = raw.substringBefore(";")
            val idx = pair.indexOf('=')
            if (idx > 0) {
                val key = pair.substring(0, idx).trim()
                val value = pair.substring(idx + 1).trim()
                if (key.isNotEmpty()) cookieStore[key] = value
            }
        }
    }

    private fun parseSetCookies(values: List<String>): Map<String, String> = buildMap {
        values.forEach { raw ->
            val pair = raw.substringBefore(";")
            val idx = pair.indexOf('=')
            if (idx > 0) {
                val key = pair.substring(0, idx).trim()
                val value = pair.substring(idx + 1).trim()
                if (key.isNotEmpty()) put(key, value)
            }
        }
    }

    /** 首次请求前先打一次首页，拿到匿名 cookie，否则 weapi 可能直接回空 body。 */
    private fun warmUp() {
        if (warmedUp) return
        warmedUp = true
        runCatching {
            val request = Request.Builder()
                .url("$BASE/")
                .header("User-Agent", UA)
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                absorbSetCookies(response.headers.values("Set-Cookie"))
            }
        }
    }

    private fun postWeapi(path: String, jsonBody: String): Pair<String, List<String>> {
        warmUp()
        val fields = NeteaseCrypto.weapi(jsonBody)
        val form = FormBody.Builder()
            .add("params", fields.getValue("params"))
            .add("encSecKey", fields.getValue("encSecKey"))
            .build()
        val request = Request.Builder()
            .url("$BASE/weapi/$path?csrf_token=")
            .header("User-Agent", UA)
            .header("Referer", "$BASE/")
            .header("Cookie", cookieHeader())
            .post(form)
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val setCookies = response.headers.values("Set-Cookie")
            absorbSetCookies(setCookies)
            return body to setCookies
        }
    }

    /** 取二维码 unikey；失败返回 Result.failure，不抛给调用方。 */
    suspend fun qrLoginUnikey(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val (body, _) = postWeapi(
                "login/qrcode/unikey",
                """{"type":1,"csrf_token":""}""",
            )
            val unikey = json.parseToJsonElement(body)
                .jsonObject["unikey"]
                ?.jsonPrimitive
                ?.content
                ?.takeIf { it.isNotBlank() }
            unikey ?: error("unikey 获取失败（${body.take(120)}）")
        }
    }

    /** 轮询扫码状态；803 时把该次响应的 Set-Cookie 一并返回。 */
    suspend fun qrLoginCheck(key: String): Result<QrLoginStatus> = withContext(Dispatchers.IO) {
        runCatching {
            val (body, setCookies) = postWeapi(
                "login/qrcode/client/login",
                """{"key":"$key","type":1,"csrf_token":""}""",
            )
            val obj = json.parseToJsonElement(body).jsonObject
            val code = obj["code"]?.jsonPrimitive?.content?.toIntOrNull() ?: -1
            val message = obj["message"]?.jsonPrimitive?.content.orEmpty()
            QrLoginStatus(
                code = code,
                message = message,
                cookies = if (code == 803) parseSetCookies(setCookies) else emptyMap(),
            )
        }
    }
}
