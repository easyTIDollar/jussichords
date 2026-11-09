package com.jussicodes.music.playback

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * 播放直链缓存（内存 + 磁盘两层）。
 *
 * 为什么需要：`ResolvingDataSource` 每 open 一次数据流就会重新跑一遍
 * `updateMediaItemUri`，而 seek 必然触发新的 open。原实现没有任何复用，
 * 所以每拖一次进度条都要重新走一次完整解析链路（官方 `/song/url/v1` 往返；
 * VIP 歌还可能走 LX 脚本沙盒或多源直连轮询），表现为「点进度条跳转要等半天」。
 *
 * 磁盘层解决的是另一件事：**进程被回收后（桌面组件冷启动点播放 / 蓝牙 / 系统媒体面板）
 * 内存缓存是空的**，于是每次冷启动起播都要白等一次完整解析往返。把最近几条直链
 * 落到 SharedPreferences，冷启动第一首可以直接开流，解析延迟归零。
 *
 * 约定：
 * - key 是「原始 MediaItem uri + 音质 + 音源代号」，与 [ResolveGuard] 的
 *   songId key 分开：音质/音源切换后自然失效，不会把旧档位的链子放给新档位；
 * - TTL 只保留保守的一段窗口：第三方 CDN 直链普遍带时效签名，过期后交给
 *   `onPlayerError` 的坏源换源链处理（拉黑 → 重新解析），不会无限复用死链；
 *   磁盘层比内存层更保守（进程外存活时间长，过期风险更高）；
 * - 命中缓存前必须再问一次 [ResolveGuard.skipUrls]：坏源被拉黑后重放时，
 *   缓存里那条链不能再被交出去，否则「换源重试」等于原地打转；
 * - 内存层有上限 LRU，磁盘层只留最近 [DISK_MAX_ENTRIES] 条，进程重启不清空。
 */
object PlayUrlCache {

    /** 单条直链在内存里最多复用多久；CDN 签名普遍比这更久，保守取值。 */
    private const val TTL_MS = 30 * 60_000L

    /**
     * 磁盘层的复用窗口比内存更短：直链在进程存活期间通常仍有效，
     * 但落盘后可能跨几个小时才被读到，过期链会先 403 再触发换源重试。
     * 15 分钟覆盖「杀进程 → 立刻从桌面组件续播」这个真实场景。
     */
    private const val DISK_TTL_MS = 15 * 60_000L

    /** 缓存条目上限：够覆盖当前队列附近 + 拖过进度条的几首，跳转不再重走解析。 */
    private const val MAX_ENTRIES = 64

    /** 磁盘层只留最近播放的几条，够冷启动第一首命中即可。 */
    private const val DISK_MAX_ENTRIES = 16

    private const val PREFS_NAME = "play_url_cache"
    private const val KEY_ENTRIES = "entries"

    private class Entry(val url: String, var at: Long)

    private val entries = object : LinkedHashMap<String, Entry>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean =
            size > MAX_ENTRIES
    }

    /** 磁盘层（内存镜像），首次访问时从 prefs 解一次。 */
    private var disk: LinkedHashMap<String, Entry>? = null

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var prefs: SharedPreferences? = null

    /**
     * 在 Application.onCreate 里同步调用。这里只存 context，不碰 SharedPreferences ——
     * 首次 getSharedPreferences 会同步读 XML，放在解析线程（LoadControl）上做更安全。
     */
    fun attach(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    private fun prefsOrNull(): SharedPreferences? {
        prefs?.let { return it }
        val ctx = appContext ?: return null
        return runCatching {
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }.getOrNull()?.also { prefs = it }
    }

    private fun keyOf(uri: String, quality: String, sourceTag: String): String =
        "$uri|$quality|$sourceTag"

    private fun diskMap(): LinkedHashMap<String, Entry> {
        disk?.let { return it }
        val loaded = LinkedHashMap<String, Entry>()
        runCatching {
            val raw = prefsOrNull()?.getString(KEY_ENTRIES, null) ?: return@runCatching
            val json = JSONObject(raw)
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val obj = json.optJSONObject(key) ?: continue
                val url = obj.optString("u").takeIf { it.isNotEmpty() } ?: continue
                loaded[key] = Entry(url, obj.optLong("t"))
            }
        }
        disk = loaded
        return loaded
    }

    private fun persist() {
        val p = prefsOrNull() ?: return
        val map = disk ?: return
        runCatching {
            val json = JSONObject()
            for ((key, entry) in map) {
                json.put(key, JSONObject().put("u", entry.url).put("t", entry.at))
            }
            p.edit().putString(KEY_ENTRIES, json.toString()).apply()
        }
    }

    private fun putDisk(key: String, url: String, at: Long) {
        val map = diskMap()
        map.remove(key)
        map[key] = Entry(url, at)
        // 只留最近 N 条：LinkedHashMap 是插入序，删最旧的表头。
        while (map.size > DISK_MAX_ENTRIES) {
            val eldest = map.keys.firstOrNull() ?: break
            map.remove(eldest)
        }
        persist()
    }

    /** 取一条仍有效的直链；内存未命中再查磁盘层。 */
    @Synchronized
    fun get(uri: String, quality: String, sourceTag: String): String? {
        val key = keyOf(uri, quality, sourceTag)
        val now = System.currentTimeMillis()
        entries[key]?.let { entry ->
            if (now - entry.at > TTL_MS) {
                entries.remove(key)
            } else {
                return entry.url
            }
        }
        val map = diskMap()
        val fromDisk = map[key] ?: return null
        if (now - fromDisk.at > DISK_TTL_MS) {
            map.remove(key)
            persist()
            return null
        }
        // 回填内存层：后续 seek / 重放不再碰磁盘。
        entries[key] = Entry(fromDisk.url, fromDisk.at)
        return fromDisk.url
    }

    @Synchronized
    fun put(uri: String, quality: String, sourceTag: String, url: String) {
        if (url.isBlank()) return
        val key = keyOf(uri, quality, sourceTag)
        val now = System.currentTimeMillis()
        entries[key] = Entry(url, now)
        putDisk(key, url, now)
    }

    /** 某首歌的直链被判定为坏源时，连同缓存一起清掉（内存 + 磁盘）。 */
    @Synchronized
    fun invalidate(uri: String, quality: String, sourceTag: String) {
        val key = keyOf(uri, quality, sourceTag)
        entries.remove(key)
        if (diskMap().remove(key) != null) persist()
    }

    @Synchronized
    fun clear() {
        entries.clear()
        diskMap().clear()
        persist()
    }
}
