package com.jussicodes.music.utils

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import com.rcmiku.ncmapi.utils.base64Encode
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.net.URLEncoder
import java.util.Locale
import java.util.UUID

fun getDeviceID(): String {
    val uuid = UUID.randomUUID().toString().replace("-", "").take(16)
    val deviceID = "null 02:00:00:00:00:00 $uuid unknown"
    return urlEncode(base64Encode(deviceID.toByteArray()))
}

fun <T> getItemShape(
    prevItem: T?,
    nextItem: T?,
    corner: Dp,
    subCorner: Dp,
): RoundedCornerShape {
    val shape = when {
        prevItem != null && nextItem != null -> RoundedCornerShape(subCorner)
        prevItem == null && nextItem == null -> RoundedCornerShape(corner)
        prevItem == null -> RoundedCornerShape(
            topStart = corner, topEnd = corner,
            bottomStart = subCorner, bottomEnd = subCorner
        )

        else -> RoundedCornerShape(
            topStart = subCorner, topEnd = subCorner,
            bottomStart = corner, bottomEnd = corner
        )
    }

    return shape
}

fun formatPlayCount(count: Double): String {
    val locale = Locale.getDefault()
    return when {
        count >= 1_0000_0000 -> String.format(locale, "%.1f亿", count / 1_0000_0000.0)
        count >= 1_0000 -> String.format(locale, "%.1f万", count / 1_0000.0)
        else -> count.toInt().toString()
    }
}

fun formatTimestamp(timestamp: Long): String {
    val localDateTime = Instant.fromEpochMilliseconds(timestamp)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    return "${localDateTime.year}-${localDateTime.monthNumber}-${localDateTime.dayOfMonth}"
}


fun urlEncode(data: String): String = URLEncoder.encode(data, Charsets.UTF_8.name())

fun makeTimeString(duration: Long?): String {
    if (duration == null || duration < 0) return ""
    var sec = duration / 1000
    val day = sec / 86400
    sec %= 86400
    val hour = sec / 3600
    sec %= 3600
    val minute = sec / 60
    sec %= 60
    return when {
        day > 0 -> "%d:%02d:%02d:%02d".format(day, hour, minute, sec)
        hour > 0 -> "%d:%02d:%02d".format(hour, minute, sec)
        else -> "%d:%02d".format(minute, sec)
    }
}

data class LrcLine(val time: Long, val text: String)
data class YrcWord(val time: Long, val duration: Long, val text: String)
data class YrcLine(val time: Long, val duration: Long, val text: String, val words: List<YrcWord>)

fun String.parseLrc(): List<LrcLine> {
    if (this.isBlank()) return emptyList()

    // 匹配 [mm:ss], [mm:ss.xx], [mm:ss.xxx], [mm:ss:xx], [mm:ss,xx]
    val timeTagRegex = """\[(\d{1,3}):(\d{2})(?:[.:,](\d{1,3}))?]""".toRegex()
    // 去除行内可能残留的逐字时间戳标签，例如 <00:12.34> 或 <120>
    val inlineTagRegex = """<[^>]+>""".toRegex()

    val result = mutableListOf<LrcLine>()
    var offsetMs = 0L

    for (rawLine in this.lines()) {
        val line = rawLine.trim().removePrefix("\uFEFF")
        if (line.isBlank()) continue

        // 支持 [offset:+/-xxx] 偏移标签
        if (line.startsWith("[offset:", ignoreCase = true)) {
            val offsetVal = line.substringAfter(':').substringBefore(']').trim().toLongOrNull()
            if (offsetVal != null) {
                offsetMs = offsetVal
            }
            continue
        }

        // 过滤头部元数据标签，如 [ti:], [ar:], [al:], [by:], [re:], [ve:] 等
        if (line.matches("""^\[[a-zA-Z]{1,10}:.*]""".toRegex())) {
            continue
        }

        // 过滤头部制作人员信息（作词/作曲/编曲/制作人/混音等），不将其混入歌词正文高亮
        val isCreditLine = line.matches("""^\[\d{1,3}:\d{2}(?:[.:,]\d{1,3})?]\s*(作词|作曲|编曲|制作人|监制|混音|母带|录音|吉他|贝斯|鼓|键盘|弦乐|企划|统筹|出品|发行|OP|SP|Lyricist|Composer|Arranger|Producer)\s*[:：].*""".toRegex(RegexOption.IGNORE_CASE))
        if (isCreditLine) {
            continue
        }

        val tagMatches = timeTagRegex.findAll(line).toList()
        if (tagMatches.isEmpty()) continue

        fun parseMatchTime(match: MatchResult): Long? {
            val (minStr, secStr, msStr) = match.destructured
            val min = minStr.toLongOrNull() ?: return null
            val sec = secStr.toLongOrNull() ?: return null
            val ms = when (msStr.length) {
                0 -> 0L
                1 -> msStr.toLong() * 100L
                2 -> msStr.toLong() * 10L
                else -> msStr.take(3).toLong()
            }
            return (min * 60 * 1000L + sec * 1000L + ms + offsetMs).coerceAtLeast(0L)
        }

        // 检查标签之间是否存在歌词文本（如 [02:29.750]Driver pull over [02:29.750]司机靠边停车）
        var hasTextBetweenTags = false
        for (i in 0 until tagMatches.size - 1) {
            val between = line.substring(tagMatches[i].range.last + 1, tagMatches[i + 1].range.first).trim()
            if (between.isNotEmpty()) {
                hasTextBetweenTags = true
                break
            }
        }

        if (hasTextBetweenTags) {
            // 分段解析：每个时间戳后跟着各自的文字
            for (i in tagMatches.indices) {
                val start = tagMatches[i].range.last + 1
                val end = if (i + 1 < tagMatches.size) tagMatches[i + 1].range.first else line.length
                val segRaw = line.substring(start, end).trim()
                val segClean = inlineTagRegex.replace(segRaw, "").trim()
                val t = parseMatchTime(tagMatches[i]) ?: continue
                result.add(LrcLine(t, segClean))
            }
        } else {
            // 传统格式：多个时间戳聚集在开头或结尾，共用一段文本
            val lastMatch = tagMatches.last()
            val rawText = line.substring(lastMatch.range.last + 1).trim()
            val cleanText = inlineTagRegex.replace(rawText, "").trim()

            for (match in tagMatches) {
                val t = parseMatchTime(match) ?: continue
                result.add(LrcLine(t, cleanText))
            }
        }
    }

    return result.sortedBy { it.time }
}

/**
 * 自动识别并拆分双语 LRC（同时间戳连续双行、或同行双语）为 (原文LRC, 译文LRC)。
 * 若未检测到成对翻译，返回 (原文本, null)。
 */
fun splitBilingualLrc(rawLrc: String): Pair<String, String?> {
    if (rawLrc.isBlank()) return Pair(rawLrc, null)

    val timeTagRegex = """\[(\d{1,3}):(\d{2})(?:[.:,](\d{1,3}))?]""".toRegex()
    val lines = rawLrc.lines()
    data class RawEntry(val timeMs: Long, val tag: String, val text: String)

    val entries = mutableListOf<RawEntry>()
    var offsetMs = 0L

    for (rawLine in lines) {
        val line = rawLine.trim().removePrefix("\uFEFF")
        if (line.isBlank()) continue

        if (line.startsWith("[offset:", ignoreCase = true)) {
            val v = line.substringAfter(':').substringBefore(']').trim().toLongOrNull()
            if (v != null) offsetMs = v
            continue
        }
        if (line.matches("""^\[[a-zA-Z]{1,10}:.*]""".toRegex())) continue

        val tagMatches = timeTagRegex.findAll(line).toList()
        if (tagMatches.isEmpty()) continue

        fun calcTime(m: MatchResult): Long? {
            val (minStr, secStr, msStr) = m.destructured
            val min = minStr.toLongOrNull() ?: return null
            val sec = secStr.toLongOrNull() ?: return null
            val ms = when (msStr.length) {
                0 -> 0L
                1 -> msStr.toLong() * 100L
                2 -> msStr.toLong() * 10L
                else -> msStr.take(3).toLong()
            }
            return (min * 60 * 1000L + sec * 1000L + ms + offsetMs).coerceAtLeast(0L)
        }

        var hasTextBetween = false
        for (i in 0 until tagMatches.size - 1) {
            val between = line.substring(tagMatches[i].range.last + 1, tagMatches[i + 1].range.first).trim()
            if (between.isNotEmpty()) {
                hasTextBetween = true
                break
            }
        }

        if (hasTextBetween) {
            for (i in tagMatches.indices) {
                val start = tagMatches[i].range.last + 1
                val end = if (i + 1 < tagMatches.size) tagMatches[i + 1].range.first else line.length
                val txt = line.substring(start, end).trim()
                val t = calcTime(tagMatches[i]) ?: continue
                entries.add(RawEntry(t, tagMatches[i].value, txt))
            }
        } else {
            val trailing = line.substring(tagMatches.last().range.last + 1).trim()
            for (m in tagMatches) {
                val t = calcTime(m) ?: continue
                entries.add(RawEntry(t, m.value, trailing))
            }
        }
    }

    if (entries.size < 2) return Pair(rawLrc, null)

    // 检测是否存在双语配对（相同时间戳且连续存在内容）
    val origList = mutableListOf<String>()
    val transList = mutableListOf<String>()
    var i = 0
    var pairCount = 0

    while (i < entries.size) {
        val cur = entries[i]
        val nxt = entries.getOrNull(i + 1)
        if (nxt != null && kotlin.math.abs(nxt.timeMs - cur.timeMs) <= 300 && cur.text.isNotEmpty() && nxt.text.isNotEmpty()) {
            origList.add("${cur.tag} ${cur.text}")
            transList.add("${nxt.tag} ${nxt.text}")
            pairCount++
            i += 2
        } else {
            origList.add("${cur.tag} ${cur.text}")
            i++
        }
    }

    return if (pairCount >= 2 || (entries.size <= 4 && pairCount >= 1)) {
        Pair(origList.joinToString("\n"), transList.joinToString("\n"))
    } else {
        Pair(rawLrc, null)
    }
}

fun String.parseYrc(): List<YrcLine> {
    val lineRegex = """\[(\d+),(\d+)](.*)""".toRegex()
    val wordRegex = """\((\d+),(\d+),\d+\)([^()]*)""".toRegex()
    return lines().mapNotNull { line ->
        val match = lineRegex.matchEntire(line) ?: return@mapNotNull null
        val (time, duration, content) = match.destructured
        val words = wordRegex.findAll(content).map { wordMatch ->
            val (wordTime, wordDuration, text) = wordMatch.destructured
            YrcWord(
                time = wordTime.toLong(),
                duration = wordDuration.toLong(),
                text = text
            )
        }.toList()
        val text = words.joinToString("") { it.text }.trim()
        if (text.isEmpty()) null else YrcLine(
            time = time.toLong(),
            duration = duration.toLong(),
            text = text,
            words = words
        )
    }
}
