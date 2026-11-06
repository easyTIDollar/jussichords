package com.jussicodes.music.playback

/**
 * 播放直链的进程内缓存。
 *
 * 为什么需要：`ResolvingDataSource` 每 open 一次数据流就会重新跑一遍
 * `updateMediaItemUri`，而 seek 必然触发新的 open。原实现没有任何复用，
 * 所以每拖一次进度条都要重新走一次完整解析链路（官方 `/song/url/v1` 往返；
 * VIP 歌还可能走 LX 脚本沙盒或多源直连轮询），表现为「点进度条跳转要等半天」。
 *
 * 约定：
 * - key 是「原始 MediaItem uri + 音质 + 音源代号」，与 [ResolveGuard] 的
 *   songId key 分开：音质/音源切换后自然失效，不会把旧档位的链子放给新档位；
 * - TTL 只保留保守的一段窗口：第三方 CDN 直链普遍带时效签名，过期后交给
 *   `onPlayerError` 的坏源换源链处理（拉黑 → 重新解析），不会无限复用死链；
 * - 命中缓存前必须再问一次 [ResolveGuard.skipUrls]：坏源被拉黑后重放时，
 *   缓存里那条链不能再被交出去，否则「换源重试」等于原地打转；
 * - 纯内存、有上限，进程重启即清空。
 */
object PlayUrlCache {

    /** 单条直链最多复用多久；CDN 签名普遍比这更久，保守取值。 */
    private const val TTL_MS = 30 * 60_000L

    /** 缓存条目上限：够覆盖当前队列附近 + 拖过进度条的几首，跳转不再重走解析。 */
    private const val MAX_ENTRIES = 64

    private class Entry(val url: String, var at: Long)

    private val entries = object : LinkedHashMap<String, Entry>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean =
            size > MAX_ENTRIES
    }

    private fun keyOf(uri: String, quality: String, sourceTag: String): String =
        "$uri|$quality|$sourceTag"

    /** 取一条仍有效的直链；过期即丢弃。 */
    @Synchronized
    fun get(uri: String, quality: String, sourceTag: String): String? {
        val key = keyOf(uri, quality, sourceTag)
        val entry = entries[key] ?: return null
        if (System.currentTimeMillis() - entry.at > TTL_MS) {
            entries.remove(key)
            return null
        }
        return entry.url
    }

    @Synchronized
    fun put(uri: String, quality: String, sourceTag: String, url: String) {
        if (url.isBlank()) return
        entries[keyOf(uri, quality, sourceTag)] = Entry(url, System.currentTimeMillis())
    }

    /** 某首歌的直链被判定为坏源时，连同缓存一起清掉。 */
    @Synchronized
    fun invalidate(uri: String, quality: String, sourceTag: String) {
        entries.remove(keyOf(uri, quality, sourceTag))
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }
}
