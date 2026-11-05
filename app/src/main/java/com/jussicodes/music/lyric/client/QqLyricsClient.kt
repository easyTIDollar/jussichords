package com.jussicodes.music.lyric.client

import com.jussicodes.music.lyric.model.LyricLine
import com.jussicodes.music.lyric.parser.QrcLyricParser
import com.rcmiku.ncmapi.model.LyricContent
import com.rcmiku.ncmapi.model.LyricResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Base64
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * QQ 音乐歌词源客户端
 * 通过歌名、歌手和时长匹配 QQ 音乐曲目，获取并解密 QRC 逐字歌词。
 */
object QqLyricsClient {

    private const val MAX_CACHE_ENTRIES = 32

    private val client = OkHttpClient.Builder()
        .cookieJar(CookieJar.NO_COOKIES)
        .callTimeout(4, TimeUnit.SECONDS)
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    private data class QqCandidate(
        val songId: Long,
        val songName: String,
        val singer: String,
        val albumName: String,
        val durationMs: Long
    )

    private val cache = LinkedHashMap<String, LyricResponse?>()

    suspend fun fetch(
        title: String?,
        artist: String?,
        durationMs: Long = 0L
    ): LyricResponse? = withContext(Dispatchers.IO) {
        val cleanTitle = title?.trim().orEmpty()
        if (cleanTitle.isBlank()) return@withContext null
        val cleanArtist = artist?.trim().orEmpty()

        val cacheKey = "$cleanTitle|$cleanArtist|$durationMs"
        synchronized(cache) {
            if (cache.containsKey(cacheKey)) return@withContext cache[cacheKey]
        }

        val simplifiedTitle = cleanTitle.replace(Regex("""[\(（][^)）]*[\)）]"""), "").trim()
        val queries = listOf(
            "$cleanTitle $cleanArtist".trim(),
            "$simplifiedTitle $cleanArtist".trim(),
            cleanTitle
        ).distinct()

        var result: LyricResponse? = null

        for (query in queries) {
            try {
                val candidates = search(query)
                val matched = pickCandidate(candidates, cleanTitle, cleanArtist, durationMs) ?: continue
                val lines = fetchLyricLines(matched)
                if (lines.any { it.words.isNotEmpty() } || lines.isNotEmpty()) {
                    result = toLyricResponse(lines)
                    break
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 忽略单个关键词搜索异常，尝试下一个
            }
        }

        synchronized(cache) {
            cache[cacheKey] = result
            while (cache.size > MAX_CACHE_ENTRIES) {
                cache.remove(cache.keys.first())
            }
        }
        result
    }

    private fun pickCandidate(
        candidates: List<QqCandidate>,
        targetTitle: String,
        targetArtist: String,
        targetDurationMs: Long
    ): QqCandidate? {
        if (candidates.isEmpty()) return null
        if (targetDurationMs <= 0) return candidates.firstOrNull()

        // 优先在时长相差 5 秒以内的歌曲中选择
        val filtered = candidates.filter { abs(it.durationMs - targetDurationMs) <= 5000 }
        if (filtered.isEmpty()) return null

        // 进一步按歌手匹配加权
        if (targetArtist.isNotBlank()) {
            val artistMatch = filtered.find {
                it.singer.contains(targetArtist, ignoreCase = true) ||
                    targetArtist.contains(it.singer, ignoreCase = true)
            }
            if (artistMatch != null) return artistMatch
        }

        return filtered.minByOrNull { abs(it.durationMs - targetDurationMs) }
    }

    private fun search(keyword: String): List<QqCandidate> {
        // 先尝试 Lite 接口
        val liteResults = searchLite(keyword)
        if (liteResults.isNotEmpty()) return liteResults

        // 降级使用公开搜索接口
        return searchPublic(keyword)
    }

    private fun searchLite(keyword: String): List<QqCandidate> {
        return try {
            val reqObj = JSONObject().apply {
                put("comm", JSONObject().apply {
                    put("ct", 11)
                    put("cv", "1003006")
                    put("tmeAppID", "qqmusiclight")
                    put("tmeLoginType", 0)
                    put("v", "1003006")
                    put("os_ver", "15")
                    put("phonetype", "24122RKC7C")
                    put("nettype", "NETWORK_WIFI")
                    put("udid", "0")
                })
                put("search", JSONObject().apply {
                    put("module", "music.search.SearchCgiService")
                    put("method", "DoSearchForQQMusicLite")
                    put("param", JSONObject().apply {
                        put("query", keyword)
                        put("search_type", 0)
                        put("num_per_page", 5)
                        put("page_num", 1)
                        put("highlight", 0)
                        put("nqc_flag", 0)
                        put("page_id", 1)
                        put("grp", 1)
                    })
                })
            }

            val request = Request.Builder()
                .url("https://u.y.qq.com/cgi-bin/musicu.fcg")
                .header("Referer", "https://y.qq.com/")
                .header("User-Agent", "Mozilla/5.0")
                .post(reqObj.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val json = JSONObject(resp.body?.string().orEmpty())
                val search = json.optJSONObject("search") ?: return emptyList()
                val body = search.optJSONObject("data")?.optJSONObject("body") ?: return emptyList()
                val items = body.optJSONArray("item_song") ?: return emptyList()

                val list = mutableListOf<QqCandidate>()
                for (i in 0 until items.length()) {
                    val song = items.optJSONObject(i) ?: continue
                    val id = song.optString("id").toLongOrNull() ?: continue
                    val name = song.optString("title").ifBlank { song.optString("name") }
                    val singers = mutableListOf<String>()
                    val singerArr = song.optJSONArray("singer")
                    if (singerArr != null) {
                        for (j in 0 until singerArr.length()) {
                            singerArr.optJSONObject(j)?.optString("name")?.takeIf { it.isNotBlank() }?.let {
                                singers.add(it)
                            }
                        }
                    }
                    val interval = song.optLong("interval", 0L)
                    val album = song.optJSONObject("album")?.optString("name").orEmpty()
                    list.add(QqCandidate(id, name, singers.joinToString("/"), album, interval * 1000))
                }
                list
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun searchPublic(keyword: String): List<QqCandidate> {
        return try {
            val encoded = URLEncoder.encode(keyword, "UTF-8")
            val url = "https://c.y.qq.com/soso/fcgi-bin/search_for_qq_cp?w=$encoded&format=json&p=1&n=5"
            val request = Request.Builder()
                .url(url)
                .header("Referer", "https://y.qq.com/")
                .header("User-Agent", "Mozilla/5.0")
                .build()

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val json = JSONObject(resp.body?.string().orEmpty())
                val data = json.optJSONObject("data") ?: return emptyList()
                val song = data.optJSONObject("song") ?: return emptyList()
                val listArr = song.optJSONArray("list") ?: return emptyList()

                val list = mutableListOf<QqCandidate>()
                for (i in 0 until listArr.length()) {
                    val item = listArr.optJSONObject(i) ?: continue
                    val id = item.optString("songid").toLongOrNull() ?: continue
                    val name = item.optString("songname")
                    val singers = mutableListOf<String>()
                    val singerArr = item.optJSONArray("singer")
                    if (singerArr != null) {
                        for (j in 0 until singerArr.length()) {
                            singerArr.optJSONObject(j)?.optString("name")?.takeIf { it.isNotBlank() }?.let {
                                singers.add(it)
                            }
                        }
                    }
                    val interval = item.optLong("interval", 0L)
                    val album = item.optString("albumname")
                    list.add(QqCandidate(id, name, singers.joinToString("/"), album, interval * 1000))
                }
                list
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchLyricLines(candidate: QqCandidate): List<LyricLine> {
        // 先请求 QRC 逐字
        val qrcLines = requestLyric(candidate, wordSynced = true)
        if (qrcLines.any { it.words.isNotEmpty() }) return qrcLines

        // 降级取普通 LRC
        return requestLyric(candidate, wordSynced = false)
    }

    private fun requestLyric(candidate: QqCandidate, wordSynced: Boolean): List<LyricLine> {
        return try {
            fun b64(value: String): String =
                Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))

            val paramObj = JSONObject().apply {
                put("songID", candidate.songId)
                put("songName", b64(candidate.songName))
                put("singerName", b64(candidate.singer))
                put("albumName", b64(candidate.albumName))
                put("interval", candidate.durationMs / 1000)
                put("crypt", 1)
                put("ct", 19)
                put("cv", 2111)
                put("qrc", if (wordSynced) 1 else 0)
                put("qrc_t", 0)
                put("lrc_t", 0)
                put("trans", 1)
                put("trans_t", 0)
                put("roma", 1)
                put("roma_t", 0)
                put("type", 0)
            }

            val reqObj = JSONObject().apply {
                put("comm", JSONObject().apply {
                    put("ct", 11)
                    put("cv", "1003006")
                    put("tmeAppID", "qqmusiclight")
                    put("tmeLoginType", 0)
                    put("v", "1003006")
                    put("os_ver", "15")
                    put("phonetype", "24122RKC7C")
                    put("nettype", "NETWORK_WIFI")
                    put("udid", "0")
                })
                put("lyric", JSONObject().apply {
                    put("module", "music.musichallSong.PlayLyricInfo")
                    put("method", "GetPlayLyricInfo")
                    put("param", paramObj)
                })
            }

            val request = Request.Builder()
                .url("https://u.y.qq.com/cgi-bin/musicu.fcg")
                .header("Referer", "https://y.qq.com/")
                .header("User-Agent", "Mozilla/5.0")
                .post(reqObj.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val json = JSONObject(resp.body?.string().orEmpty())
                val lyric = json.optJSONObject("lyric") ?: return emptyList()
                val data = lyric.optJSONObject("data") ?: return emptyList()

                val rawLyricHex = data.optString("lyric")
                val rawTransHex = data.optString("trans")
                val rawRomaHex = data.optString("roma")

                val original = QqLyricDecoder.decodeLyric(rawLyricHex)
                val translation = QqLyricDecoder.decodeLyric(rawTransHex)
                val roma = QqLyricDecoder.decodeLyric(rawRomaHex)

                QrcLyricParser.parse(original, translation, roma)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun toLyricResponse(lines: List<LyricLine>): LyricResponse {
        val yrcSb = StringBuilder()
        val lrcSb = StringBuilder()
        val transSb = StringBuilder()
        var hasTrans = false

        lines.forEach { line ->
            if (line.words.isNotEmpty()) {
                yrcSb.append("[${line.timeMs},${line.durationMs}]")
                line.words.forEach { word ->
                    val absWordTime = line.timeMs + word.startOffsetMs
                    yrcSb.append("($absWordTime,${word.durationMs},0)${word.text}")
                }
                yrcSb.append("\n")
            }

            val min = line.timeMs / 60000
            val sec = (line.timeMs % 60000) / 1000
            val ms = line.timeMs % 1000
            lrcSb.append(String.format(Locale.US, "[%02d:%02d.%03d]%s\n", min, sec, ms, line.text))

            if (!line.translation.isNullOrBlank()) {
                hasTrans = true
                transSb.append(String.format(Locale.US, "[%02d:%02d.%03d]%s\n", min, sec, ms, line.translation))
            }
        }

        return LyricResponse(
            lrc = LyricContent(lrcSb.toString().trim()),
            tlyric = if (hasTrans) LyricContent(transSb.toString().trim()) else null,
            yrc = if (yrcSb.isNotEmpty()) LyricContent(yrcSb.toString().trim()) else null
        )
    }
}
