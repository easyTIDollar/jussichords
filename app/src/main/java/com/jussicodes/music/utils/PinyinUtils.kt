package com.jussicodes.music.utils

/**
 * 拼音与首字母提取工具类：
 * 提供零第三方依赖的中英文首字母提取（支持常用汉字 GBK/GB2312 编码范围解析）。
 * 专用于本地音乐、下载列表的 A-Z 快速字母表索引与排序。
 */
object PinyinUtils {

    private val GB2312_TABLE = arrayOf(
        Triple(0xB0A1, 0xB0C4, "A"), Triple(0xB0C5, 0xB2C0, "B"), Triple(0xB2C1, 0xB4ED, "C"),
        Triple(0xB4EE, 0xB6E9, "D"), Triple(0xB6EA, 0xB7A1, "E"), Triple(0xB7A2, 0xB8C0, "F"),
        Triple(0xB8C1, 0xB9FD, "G"), Triple(0xB9FE, 0xBBF6, "H"), Triple(0xBBF7, 0xBFA5, "J"),
        Triple(0xBFA6, 0xC0AB, "K"), Triple(0xC0AC, 0xC2E7, "L"), Triple(0xC2E8, 0xC4C2, "M"),
        Triple(0xC4C3, 0xC5B5, "N"), Triple(0xC5B6, 0xC5BD, "O"), Triple(0xC5BE, 0xC6D9, "P"),
        Triple(0xC6DA, 0xC8BA, "Q"), Triple(0xC8BB, 0xC8F5, "R"), Triple(0xC8F6, 0xCBF9, "S"),
        Triple(0xCBFA, 0xCDD9, "T"), Triple(0xCDDA, 0xCEF3, "W"), Triple(0xCEF4, 0xD1B8, "X"),
        Triple(0xD1B9, 0xD4D0, "Y"), Triple(0xD4D1, 0xD7F9, "Z"),
    )

    fun getInitial(text: String?): String {
        if (text.isNullOrBlank()) return "#"
        val trimmed = text.trim()
        val firstChar = trimmed.first()
        if (firstChar in 'a'..'z') return firstChar.uppercase()
        if (firstChar in 'A'..'Z') return firstChar.toString()
        if (firstChar in '0'..'9') return "#"
        return runCatching {
            val bytes = firstChar.toString().toByteArray(charset("GBK"))
            if (bytes.size == 2) {
                val b1 = bytes[0].toInt() and 0xFF
                val b2 = bytes[1].toInt() and 0xFF
                val code = (b1 shl 8) or b2
                for ((start, end, letter) in GB2312_TABLE) {
                    if (code in start..end) return@runCatching letter
                }
            }
            "#"
        }.getOrDefault("#")
    }
}
