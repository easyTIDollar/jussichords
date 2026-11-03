package com.jussicodes.music.lyric.client

import com.jussicodes.music.lyric.model.LyricLine
import com.jussicodes.music.lyric.parser.TtmlLyricParser
import com.rcmiku.ncmapi.model.LyricContent
import com.rcmiku.ncmapi.model.LyricResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * AMLL 逐字歌词客户端
 * 并发请求 AMLL 社区歌词库镜像，支持超时降级与内存 LRU 缓存。
 */
object AmllLyricsClient {

    private const val MAX_CACHE_ENTRIES = 32

    private val client = OkHttpClient.Builder()
        .callTimeout(5, TimeUnit.SECONDS)
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val cache = LinkedHashMap<Long, LyricResponse?>()

    suspend fun fetch(musicId: Long): LyricResponse? = withContext(Dispatchers.IO) {
        if (musicId <= 0) return@withContext null
        synchronized(cache) {
            if (cache.containsKey(musicId)) return@withContext cache[musicId]
        }

        val mirrors = listOf(
            "https://amlldb.bikonoo.com/ncm-lyrics/$musicId.ttml",
            "https://amll-ttml-db.stevexmh.net/ncm/$musicId",
            "https://raw.githubusercontent.com/amll-dev/amll-ttml-db/refs/heads/main/ncm-lyrics/$musicId.ttml"
        )

        val xml = downloadFirst(mirrors)
        val response = if (!xml.isNullOrBlank()) {
            val lines = TtmlLyricParser.parse(xml)
            if (lines.any { it.words.isNotEmpty() }) {
                toLyricResponse(lines)
            } else null
        } else null

        synchronized(cache) {
            cache[musicId] = response
            while (cache.size > MAX_CACHE_ENTRIES) {
                cache.remove(cache.keys.first())
            }
        }
        response
    }

    private suspend fun downloadFirst(urls: List<String>): String? = coroutineScope {
        val channel = Channel<String?>(urls.size)
        val jobs = urls.mapIndexed { index, url ->
            launch(Dispatchers.IO) {
                if (index > 0) delay(index * 200L) // 镜像轻微交错并发
                val text = try {
                    val req = Request.Builder().url(url).build()
                    client.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) resp.body?.string()?.takeIf { it.isNotBlank() } else null
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
                channel.send(text)
            }
        }

        var found: String? = null
        try {
            repeat(urls.size) {
                val res = channel.receive()
                if (res != null) {
                    found = res
                    return@coroutineScope found
                }
            }
        } finally {
            jobs.forEach { it.cancel() }
            channel.close()
        }
        found
    }

    private fun toLyricResponse(lines: List<LyricLine>): LyricResponse {
        val yrcSb = StringBuilder()
        val lrcSb = StringBuilder()
        val transSb = StringBuilder()
        var hasTrans = false

        lines.forEach { line ->
            // YRC 逐字
            if (line.words.isNotEmpty()) {
                yrcSb.append("[${line.timeMs},${line.durationMs}]")
                line.words.forEach { word ->
                    val absWordTime = line.timeMs + word.startOffsetMs
                    yrcSb.append("($absWordTime,${word.durationMs},0)${word.text}")
                }
                yrcSb.append("\n")
            }

            // LRC 逐行
            val min = line.timeMs / 60000
            val sec = (line.timeMs % 60000) / 1000
            val ms = line.timeMs % 1000
            lrcSb.append(String.format(Locale.US, "[%02d:%02d.%03d]%s\n", min, sec, ms, line.text))

            // 翻译
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
