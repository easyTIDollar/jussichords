package com.jussicodes.music.lyric.model

/**
 * 单个字符/单词的耗时元数据
 * @param text 字符/单词文本
 * @param startOffsetMs 相对于整行歌词起始时间的偏移毫秒数
 * @param durationMs 该字/词的持续发音毫秒数
 */
data class WordInfo(
    val text: String,
    val startOffsetMs: Long,
    val durationMs: Long
)

/**
 * 歌词行领域模型
 * @param timeMs 整行开始时间戳（毫秒）
 * @param durationMs 整行持续时长（毫秒）
 * @param text 整行歌词正文
 * @param translation 翻译歌词
 * @param roma 罗马音歌词
 * @param words 逐字信息列表（普通逐行歌词时为空）
 */
data class LyricLine(
    val timeMs: Long,
    val durationMs: Long = 0,
    val text: String,
    val translation: String? = null,
    val roma: String? = null,
    val words: List<WordInfo> = emptyList()
)
