package com.jussicodes.music.lx

import java.util.concurrent.ConcurrentHashMap

/**
 * 队列里那份 Song JSON 的快取（songId → 列表接口返回的原始 JSON）。
 *
 * 为什么需要：LX 脚本匹配只要 name / singer / albumName / interval 四个字段，而点歌时
 * MediaItem 的 extras 里已经存了整份 Song。解析前再打一次 `/song/detail/batch`
 * 等于白付一个网络往返（每首 100~300ms），更糟的是它连续失败两次，整条 LX 就直接
 * 放弃 → 回落官方 30 秒试听。
 *
 * 实现约束：这里只存字符串引用、不做任何解析，构建队列时（[com.jussicodes.music.extensions.toMediaItem]）
 * 顺手放进来，零额外 CPU；真正解码发生在解析线程上，字段不齐（电台 / 云盘 / 外部歌单）
 * 就回退网络请求，行为与以前一致。
 */
object SongJsonCache {

    /** 队列规模上限：正常歌单远小于它，超了说明长时间累积，整表换掉即可。 */
    private const val MAX_ENTRIES = 3_000

    private val map = ConcurrentHashMap<String, String>()

    fun put(songId: String, songJson: String) {
        if (songId.isBlank() || songJson.isBlank()) return
        if (map.size >= MAX_ENTRIES && !map.containsKey(songId)) map.clear()
        map[songId] = songJson
    }

    fun get(songId: String): String? = map[songId]
}
