package com.rcmiku.ncmapi.api.player

import com.rcmiku.ncmapi.api.PlayUrlTrace
import com.rcmiku.ncmapi.utils.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.abs

object QqSourceSettings {
    @Volatile
    var ENABLED = true
}

internal data class QqResolution(val url: String, val br: Int, val entryHost: String = "")

internal object QqMusicSource {
    const val BUDGET_MS = 15_000L
    private const val CALL_TIMEOUT_MS = 8_000L
    private const val PROBE_TIMEOUT_MS = 2_000L
    private const val DIRECT_AUDIO_PROBE_TIMEOUT_MS = 4_000L

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .writeTimeout(6, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    data class Match(
        val songmid: String,
        val name: String,
        val singer: String,
        val sizeTry: Long,
        val size320: Long,
        val sizeFlac: Long,
        val intervalSec: Int,
        val songType: Int?,
        val payPlay: Int?
    )

    private enum class TxQuality(val label: String, val prefix: String, val extension: String, val vkeys: Int, val br: Int) {
        MP3_128("128k", "M500", "mp3", 8, 128),
        MP3_320("320k", "M800", "mp3", 9, 320),
        FLAC("flac", "F000", "flac", 10, 0)
    }

    suspend fun search(
        keyword: String,
        expectedName: String,
        expectedArtist: String,
        expectedDurationMs: Long
    ): Match? = withContext(Dispatchers.IO) {
        runCatching {
            val searchDeadline = now() + CALL_TIMEOUT_MS
            val name = normalize(expectedName)
            val artists = artistNames(expectedArtist)
            if (name.isEmpty() || artists.isEmpty()) return@runCatching null

            val url = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp".toHttpUrl().newBuilder()
                .addQueryParameter("w", keyword)
                .addQueryParameter("format", "json")
                .addQueryParameter("n", "5")
                .addQueryParameter("p", "1")
                .addQueryParameter("cr", "1")
                .addQueryParameter("new_json", "1")
                .build()
            val matches = readJson(request(url.toString()).build(), searchDeadline)
                ?.obj("data")?.obj("song")?.array("list").orEmpty()
                .mapNotNull { (it as? JsonObject)?.toMatch() }
                .filter { match ->
                    (normalize(match.name) == name || normalize(match.name).let { qq ->
                        qq.contains(name) || name.contains(qq)
                    }) && artistNames(match.singer).any { qq ->
                        artists.any { expected -> qq.contains(expected) || expected.contains(qq) }
                    } && (expectedDurationMs <= 0 || (match.intervalSec > 0 &&
                        abs(expectedDurationMs - match.intervalSec * 1000L) <= 8_000L))
                }

            if (now() >= searchDeadline) return@runCatching null
            // 先保留原版，再比时长和付费状态，减少 live、remix 与翻唱错配。
            matches.sortedWith(compareBy<Match>(
                { if (it.songType == 0) 0 else 1 },
                { if (expectedDurationMs <= 0) 0L else abs(expectedDurationMs - it.intervalSec * 1000L) },
                { if (it.payPlay == 1) 0 else 1 }
            )).firstOrNull()
        }.onFailure { if (it is CancellationException) throw it }.getOrNull()
    }

    suspend fun resolve(
        songId: String,
        name: String,
        artist: String,
        durationMs: Long,
        level: SongLevel,
        skipUrls: Set<String>
    ): QqResolution? {
        val startedAt = now()
        val resolveDeadline = startedAt + BUDGET_MS
        val qualities = when (level) {
            SongLevel.STANDARD, SongLevel.HIGHER -> listOf(TxQuality.MP3_320, TxQuality.MP3_128)
            SongLevel.EXHIGH -> listOf(TxQuality.MP3_320)
            else -> listOf(TxQuality.FLAC)
        }
        val match = search("$name $artist", name, artist, durationMs)
        trace(songId, "搜索", match?.let { "匹配 ${it.name} / ${it.singer}" } ?: "未找到匹配歌曲",
            startedAt, qualities.first())
        if (match == null) return null

        return withContext(Dispatchers.IO) {
            Resolution(songId, match, skipUrls, resolveDeadline).fetch(qualities)
        }
    }

    private class Resolution(
        private val songId: String,
        private val match: Match,
        private val skipUrls: Set<String>,
        private val deadline: Long
    ) {
        suspend fun fetch(qualities: List<TxQuality>): QqResolution? {
            fetchDirectAudio()?.let { return it }
            for (quality in qualities) {
                if (now() >= deadline) return null
                val resolution = trySource("官方取流", quality) { fetchOfficial(match.songmid, quality) }
                    ?: fetchThirdParty(match.songmid, quality)
                if (resolution != null) return resolution
            }
            return null
        }

        private suspend fun fetchDirectAudio(): QqResolution? {
            val step = "直给音频 · 128k"
            val quality = TxQuality.MP3_128
            val startedAt = now()
            return try {
                val entryUrl = "http://175.27.166.236/kgqq1/qq.php".toHttpUrl()
                trySource(step, quality, requireLength = true) {
                    entryUrl.newBuilder()
                        .addQueryParameter("type", "mp3")
                        .addQueryParameter("id", match.songmid)
                        .build().toString()
                }?.copy(entryHost = entryUrl.host)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                trace(songId, step, "请求失败", startedAt, quality)
                null
            }
        }

        private suspend fun fetchOfficial(mid: String, quality: TxQuality): String? {
            val filename = "${quality.prefix}$mid.${quality.extension}"
            val guid = UUID.randomUUID().toString().replace("-", "")
            val body = """
                {"req_0":{"module":"vkey.GetVkeyServer","method":"CgiGetVkey","param":{
                  "filename":[${JsonPrimitive(filename)}],"guid":"$guid","songmid":[${JsonPrimitive(mid)}],
                  "songtype":[0],"uin":"0","loginflag":0,"platform":"20"}},
                  "loginUin":"0","comm":{"uin":"0","format":"json","ct":24,"cv":0}}
            """.trimIndent().toRequestBody("application/json".toMediaType())
            val data = readJson(request("https://u.y.qq.com/cgi-bin/musicu.fcg").post(body).build(), deadline)
                ?.obj("req_0")?.obj("data") ?: return null
            val purl = (data.array("midurlinfo")?.firstOrNull() as? JsonObject)?.text("purl") ?: return null
            val sip = data.array("sip").orEmpty()
                .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                .filter { it.toHttpUrlOrNull() != null }
                .randomOrNull() ?: return null
            return "${sip.trimEnd('/')}/${purl.trimStart('/')}"
        }

        private suspend fun fetchThirdParty(mid: String, quality: TxQuality): QqResolution? =
            trySource("vkeys", quality) {
                val url = "https://aapi.vkeys.cn/music/tencent/song/link".toHttpUrl().newBuilder()
                    .addQueryParameter("mid", mid)
                    .addQueryParameter("quality", quality.vkeys.toString())
                    .build()
                linkFromJson(readJson(request(url.toString()).build(), deadline))
            } ?: trySource("gdstudio", quality) {
                val url = "https://music-aapi.gdstudio.xyz/aapi.php".toHttpUrl().newBuilder()
                    .addQueryParameter("types", "url")
                    .addQueryParameter("source", "qq")
                    .addQueryParameter("id", mid)
                    .build()
                linkFromJson(readJson(request(url.toString()).build(), deadline))
            }

        private suspend fun trySource(
            step: String,
            quality: TxQuality,
            requireLength: Boolean = false,
            fetch: suspend () -> String?
        ): QqResolution? {
            val startedAt = now()
            if (startedAt >= deadline) return null
            val url = fetch()?.takeIf { it.toHttpUrlOrNull() != null }
            trace(songId, step, url?.let { "返回 ${PlayUrlTrace.hostOf(it)}" } ?: "未返回直链",
                startedAt, quality)
            if (url == null || now() >= deadline) return null

            val probeStartedAt = now()
            if (PlayUrlSkip.blocks(skipUrls, url)) {
                trace(songId, "$step · 试听探测", "已拉黑的坏链，跳过", probeStartedAt, quality)
                return null
            }

            val probe = probeLength(url, requireLength)
            val length = probe?.length ?: 0L
            val finalUrl = if (requireLength) probe?.finalUrl ?: url else url
            if (finalUrl != url && PlayUrlSkip.blocks(skipUrls, finalUrl)) {
                trace(songId, "$step · 试听探测", "已拉黑的坏链，跳过", probeStartedAt, quality)
                return null
            }
            val trial = length > 0 && match.sizeTry > 0 && length <= match.sizeTry * 1.2
            val missingLength = requireLength && length == 0L
            val detail = when {
                now() >= deadline -> "预算耗尽"
                trial -> "试听片段，跳过"
                missingLength -> "探测未取得总长度，跳过"
                length == 0L -> "总长度未知，接受直链"
                else -> "通过，音频共 $length 字节"
            }
            trace(songId, "$step · 试听探测", detail, probeStartedAt, quality)
            // 返回入口地址，播放器跟随重定向时可以取得新的 CDN 令牌。
            return url.takeIf { !trial && !missingLength && now() < deadline }
                ?.let { QqResolution(it, quality.br) }
        }

        private data class LengthProbe(val length: Long, val finalUrl: String)

        private suspend fun probeLength(url: String, requireLength: Boolean): LengthProbe? {
            val probeTimeout = if (requireLength) DIRECT_AUDIO_PROBE_TIMEOUT_MS else PROBE_TIMEOUT_MS
            val probeDeadline = minOf(deadline, now() + probeTimeout)
            return execute(request(url).header("Range", "bytes=0-0").build(), probeDeadline) { response ->
                if (requireLength && response.code != 200 && response.code != 206) return@execute null
                val length = response.header("Content-Range")
                    ?.takeIf { it.startsWith("bytes ", ignoreCase = true) }
                    ?.substringAfterLast('/', "")?.trim()?.toLongOrNull()?.takeIf { it > 0L }
                    ?: return@execute null
                LengthProbe(length, response.request.url.toString())
            }
        }
    }

    private fun request(url: String): Request.Builder = Request.Builder().url(url)
        .header("User-Agent", "Mozilla/5.0")
        .header("Referer", "https://y.qq.com/")

    private suspend fun <T> execute(request: Request, deadline: Long, read: (Response) -> T?): T? {
        val remaining = (deadline - now()).coerceAtMost(CALL_TIMEOUT_MS)
        if (remaining <= 0) return null
        return withTimeoutOrNull(remaining) {
            suspendCancellableCoroutine<T?> { continuation ->
                val call = client.newCall(request)
                call.timeout().timeout(remaining, TimeUnit.MILLISECONDS)
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resumeWith(Result.success(null))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val result = runCatching {
                            response.use { if (it.isSuccessful) read(it) else null }
                        }.recoverCatching {
                            if (it is CancellationException) throw it
                            null
                        }
                        continuation.resumeWith(result)
                    }
                })
            }
        }
    }

    private suspend fun readJson(request: Request, deadline: Long): JsonObject? =
        execute(request, deadline) { response ->
            response.body?.string()?.let { json.parseToJsonElement(it) as? JsonObject }
        }

    private fun linkFromJson(root: JsonObject?): String? = root?.obj("data")?.text("url") ?: root?.text("url")

    private fun JsonObject.toMatch(): Match? {
        val mid = text("mid") ?: return null
        val name = text("name") ?: return null
        val singer = array("singer").orEmpty().mapNotNull { (it as? JsonObject)?.text("name") }
            .joinToString(" / ")
        val file = obj("file")
        return Match(mid, name, singer, file?.long("size_try") ?: 0L, file?.long("size_320") ?: 0L,
            file?.long("size_flac") ?: 0L, (this["interval"] as? JsonPrimitive)?.intOrNull ?: 0,
            (this["songtype"] as? JsonPrimitive)?.intOrNull,
            obj("pay")?.let { (it["pay_play"] as? JsonPrimitive)?.intOrNull })
    }

    private fun JsonObject.obj(key: String) = this[key] as? JsonObject
    private fun JsonObject.array(key: String) = this[key] as? JsonArray
    private fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
        ?.trim()?.takeIf { it.isNotEmpty() }
    private fun JsonObject.long(key: String) = (this[key] as? JsonPrimitive)?.longOrNull

    private fun normalize(value: String) = value.filterNot { it.isWhitespace() }.lowercase()
    private fun artistNames(value: String) = value.split('/', '&', ',').map(::normalize).filter { it.isNotEmpty() }
    private fun now() = System.currentTimeMillis()

    private fun trace(songId: String, step: String, detail: String, startedAt: Long, quality: TxQuality) {
        PlayUrlTrace.emit(songId, "QQ 音源 · $step", "$detail · ${now() - startedAt}ms", listOf("QQ", quality.label))
    }
}
