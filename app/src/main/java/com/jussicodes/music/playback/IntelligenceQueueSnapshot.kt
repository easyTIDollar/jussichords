package com.jussicodes.music.playback

import androidx.media3.common.MediaItem

/**
 * 心动模式的原队列快照。
 *
 * 开启心动模式会把整个队列换成「当前歌 + 推荐」，菜单再点一次要能退回去，
 * 所以进入前把当时的队列和播放下标存在这里。只活在内存里：不落盘、不跨进程，
 * 重启后没有快照就退化成「只清来源标记」，不会把队列搞乱。
 */
object IntelligenceQueueSnapshot {
    private var items: List<MediaItem> = emptyList()
    private var index: Int = 0

    fun save(items: List<MediaItem>, index: Int) {
        if (items.isEmpty()) return
        this.items = items
        this.index = index.coerceIn(0, items.lastIndex)
    }

    /** 取出快照并清空，避免下次关闭时还原到过期队列。 */
    fun take(): Pair<List<MediaItem>, Int>? {
        val snapshot = items.takeIf { it.isNotEmpty() } ?: return null
        val snapshotIndex = index
        clear()
        return snapshot to snapshotIndex
    }

    fun clear() {
        items = emptyList()
        index = 0
    }
}
