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

        // 提取该行开头的全部时间戳（支持单行多时间戳，如 [01:23.45][02:34.56]歌词）
        val tagMatches = timeTagRegex.findAll(line).toList()
        if (tagMatches.isEmpty()) continue

        // 歌词正文：截取最后一个时间戳之后的部分，并剥离逐字内联时间戳 <...>
        val lastMatch = tagMatches.last()
        val rawText = line.substring(lastMatch.range.last + 1).trim()
        val cleanText = inlineTagRegex.replace(rawText, "").trim()

        for (match in tagMatches) {
            val (minStr, secStr, msStr) = match.destructured
            val min = minStr.toLongOrNull() ?: continue
            val sec = secStr.toLongOrNull() ?: continue
            val ms = when (msStr.length) {
                0 -> 0L
                1 -> msStr.toLong() * 100L
                2 -> msStr.toLong() * 10L
                else -> msStr.take(3).toLong()
            }
            val time = (min * 60 * 1000L + sec * 1000L + ms + offsetMs).coerceAtLeast(0L)
            result.add(LrcLine(time, cleanText))
        }
    }

    return result.sortedBy { it.time }
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
