package com.jussicodes.music.lyric.parser

import com.jussicodes.music.lyric.model.LyricLine
import com.jussicodes.music.lyric.model.WordInfo

/**
 * AMLL TTML 格式解析器
 * 移植自 Melodia 开源实现，负责将 Apple Music 规范的 TTML XML 解析为 LyricLine 列表。
 */
object TtmlLyricParser {
    const val MAX_LENGTH = 2 * 1024 * 1024
    private const val TTML = "http://www.w3.org/ns/ttml"
    private const val MAX_TIME_MS = 604_800_000L

    private data class LocalizedTranslation(val text: String, val priority: Int)
    private data class TimedWord(val text: String, val begin: Long, val end: Long)

    private val doctypeOrEntity = Regex("<!\\s*(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE)

    fun parse(xml: String): List<LyricLine> {
        if (xml.isBlank() || xml.length > MAX_LENGTH || doctypeOrEntity.containsMatchIn(xml)) return emptyList()
        return try {
            val root = XmlReader.parse(xml) ?: return emptyList()
            if (root.localName != "tt" || root.namespaceUri != TTML) return emptyList()
            val translations = parseExternalTranslations(root)
            val romanizations = parseExternalAnnotations(root, "transliteration")
            val lines = mutableListOf<LyricLine>()
            fun visit(node: XmlElement, depth: Int) {
                require(depth <= 64) { "TTML nesting is too deep" }
                if (node.localName == "p" && (node.namespaceUri == TTML || node.namespaceUri.isNullOrEmpty())) {
                    parseLine(node, translations, romanizations)?.let(lines::add)
                } else node.childElements().forEach { visit(it, depth + 1) }
            }
            root.childElements().filter { it.localName == "body" }.forEach { visit(it, 0) }
            lines.sortedBy { it.timeMs }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseLine(
        p: XmlElement,
        translations: Map<String, LocalizedTranslation>,
        romanizations: Map<String, String>
    ): LyricLine? {
        val start = time(p.attribute("begin"))
        val end = time(p.attribute("end"))
        val key = p.attribute("key")
        return parseTrack(
            element = p,
            start = start,
            end = end,
            translation = listOfNotNull(p.directTranslation(), translations[key])
                .minByOrNull { it.priority }?.text,
            romanization = p.directAnnotation("x-roman") ?: romanizations[key]
        )
    }

    private fun parseTrack(
        element: XmlElement,
        start: Long?,
        end: Long?,
        translation: String?,
        romanization: String?
    ): LyricLine? {
        if (start != null && end != null && end <= start) return null
        val parts = mutableListOf<TimedWord>()
        val text = StringBuilder()
        var validWords = true
        var previousStart = 0L
        fun walk(node: XmlNode, depth: Int) {
            require(depth <= 64) { "TTML nesting is too deep" }
            when (node) {
                is XmlText -> {
                    val value = node.value
                    if (!(value.isBlank() && (value.contains('\n') || value.contains('\r')))) text.append(value)
                }

                is XmlElement -> {
                    if (node.role() in setOf("x-translation", "x-roman", "x-bg")) return
                    if (node.localName == "br") {
                        text.append(' ')
                        validWords = false
                        return
                    }
                    val timed = node.localName == "span" &&
                        (node.hasAttribute("begin") || node.hasAttribute("end"))
                    if (timed) {
                        val before = text.length
                        node.children.forEach { walk(it, depth + 1) }
                        val wordText = text.substring(before)
                        val begin = time(node.attribute("begin"))
                        val finish = time(node.attribute("end"))
                        if (begin == null || finish == null || finish < begin) validWords = false
                        else if (wordText.isNotBlank()) {
                            if (begin < previousStart) validWords = false
                            parts += TimedWord(wordText, begin, finish)
                            previousStart = begin
                        }
                        return
                    }
                    node.children.forEach { walk(it, depth + 1) }
                }
            }
        }
        element.children.forEach { walk(it, 0) }
        val content = text.toString().trim()
        if (content.isEmpty()) return null
        val timedParts = parts.filter { it.end > it.begin }
        val resolvedStart = listOfNotNull(start, timedParts.minOfOrNull { it.begin })
            .minOrNull() ?: return null
        val resolvedEnd = listOfNotNull(end, timedParts.maxOfOrNull { it.end })
            .maxOrNull() ?: return null
        if (resolvedEnd <= resolvedStart) return null
        val words = if (validWords && timedParts.isNotEmpty() &&
            parts.all { it.begin >= resolvedStart && it.end <= resolvedEnd } &&
            parts.joinToString("") { it.text }.filterNot(Char::isWhitespace) == content.filterNot(Char::isWhitespace)) {
            attachInterstitialSpaces(content, parts.map {
                WordInfo(it.text, it.begin - resolvedStart, it.end - it.begin)
            })
        } else emptyList()
        return LyricLine(
            timeMs = resolvedStart,
            durationMs = resolvedEnd - resolvedStart,
            text = content,
            translation = translation?.trim()?.takeIf { it.isNotEmpty() },
            roma = romanization?.trim()?.takeIf { it.isNotEmpty() },
            words = words
        )
    }

    private fun attachInterstitialSpaces(content: String, parts: List<WordInfo>): List<WordInfo> {
        var cursor = 0
        return parts.mapIndexed { index, word ->
            val token = word.text.trim()
            val at = content.indexOf(token, cursor).coerceAtLeast(cursor)
            val next = if (index == parts.lastIndex) content.length else
                content.indexOf(parts[index + 1].text.trim(), at + token.length)
                    .takeIf { it >= 0 } ?: (at + token.length)
            cursor = next
            word.copy(text = content.substring(at, next))
        }
    }

    private fun parseExternalTranslations(root: XmlElement): Map<String, LocalizedTranslation> {
        val result = mutableMapOf<String, LocalizedTranslation>()
        for (container in root.findDescendants("translation")) {
            container.childElements().filter { it.localName == "text" }.forEach { text ->
                val key = text.attribute("for") ?: return@forEach
                val value = text.textContent.trim().takeIf(String::isNotEmpty) ?: return@forEach
                val language = text.attribute("lang") ?: container.attribute("lang")
                val candidate = LocalizedTranslation(value, translationPriority(language))
                if (candidate.priority < (result[key]?.priority ?: Int.MAX_VALUE)) result[key] = candidate
            }
        }
        return result
    }

    private fun parseExternalAnnotations(root: XmlElement, containerName: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (container in root.findDescendants(containerName)) {
            container.childElements().filter { it.localName == "text" }.forEach { text ->
                val key = text.attribute("for")
                val value = text.textContent.trim()
                if (key != null && value.isNotEmpty()) result[key] = value
            }
        }
        return result
    }

    private fun XmlElement.directAnnotation(role: String): String? = childElements()
        .firstOrNull { it.role() == role }?.textContent?.trim()?.takeIf { it.isNotEmpty() }

    private fun XmlElement.directTranslation(): LocalizedTranslation? = childElements()
        .filter { it.role() == "x-translation" }
        .mapNotNull { span ->
            span.textContent.trim().takeIf(String::isNotEmpty)?.let {
                LocalizedTranslation(it, translationPriority(span.attribute("lang")))
            }
        }
        .minByOrNull { it.priority }

    private fun translationPriority(language: String?): Int {
        val tag = language?.lowercase()?.replace('_', '-') ?: return 3
        return when {
            tag.startsWith("zh-hans") || tag == "zh-cn" || tag == "zh-sg" || tag == "zh-my" -> 0
            tag == "zh" -> 1
            tag.startsWith("zh-") -> 2
            else -> 4
        }
    }

    private fun XmlElement.role(): String? = attribute("role")

    internal fun time(value: String?): Long? {
        val v = value?.trim().orEmpty()
        if (v.isEmpty()) return null
        val millis = when {
            v.matches(Regex("\\d+(\\.\\d+)?ms")) -> decimalToMillis(v.dropLast(2), fromSeconds = false)
            v.matches(Regex("\\d+(\\.\\d+)?s")) -> decimalToMillis(v.dropLast(1), fromSeconds = true)
            v.matches(Regex("\\d+(\\.\\d+)?")) -> decimalToMillis(v, fromSeconds = true)
            v.matches(Regex("(?:\\d+:)?\\d{1,2}:\\d{2}(\\.\\d+)?")) -> clockToMillis(v)
            else -> null
        } ?: return null
        return millis.takeIf { it in 0..MAX_TIME_MS }
    }

    private fun decimalToMillis(text: String, fromSeconds: Boolean): Long? {
        val dot = text.indexOf('.')
        val intText = if (dot >= 0) text.substring(0, dot) else text
        val fracText = if (dot >= 0) text.substring(dot + 1) else ""
        if (intText.isEmpty() || intText.length > 12) return null
        if (intText.any { !it.isDigit() } || fracText.any { !it.isDigit() }) return null
        val whole = intText.toLongOrNull() ?: return null
        if (!fromSeconds) return whole
        val fraction = fracText.take(3).padEnd(3, '0').toLongOrNull() ?: 0L
        return whole * 1000 + fraction
    }

    private fun clockToMillis(value: String): Long? {
        val fields = value.split(':')
        if (fields.size !in 1..3) return null
        if (fields.any { it.isEmpty() || it.length > 12 }) return null
        for (i in 0 until fields.lastIndex) if (fields[i].any { !it.isDigit() }) return null

        val last = fields[fields.lastIndex]
        val dot = last.indexOf('.')
        val lastWholeText = if (dot >= 0) last.substring(0, dot) else last
        val fracText = if (dot >= 0) last.substring(dot + 1) else ""
        if (lastWholeText.isEmpty() || fracText.any { !it.isDigit() }) return null
        val lastWhole = lastWholeText.toLongOrNull() ?: return null
        if (lastWhole >= 60) return null
        if (fields.size == 3 && (fields[1].toLongOrNull() ?: return null) >= 60) return null

        var totalSeconds = lastWhole
        var multiplier = 60L
        for (i in fields.lastIndex - 1 downTo 0) {
            val field = fields[i].toLongOrNull() ?: return null
            totalSeconds += field * multiplier
            multiplier *= 60
        }
        val fraction = fracText.take(3).padEnd(3, '0').toLongOrNull() ?: 0L
        return totalSeconds * 1000 + fraction
    }
}
