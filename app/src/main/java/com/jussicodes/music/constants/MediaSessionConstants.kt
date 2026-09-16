package com.jussicodes.music.constants

import android.os.Bundle
import androidx.media3.session.SessionCommand

object MediaSessionConstants {
    const val ACTION_TOGGLE_LIKE = "TOGGLE_LIKE"
    const val ACTION_TOGGLE_SHUFFLE = "TOGGLE_SHUFFLE"
    const val ACTION_TOGGLE_DESKTOP_LYRIC = "TOGGLE_DESKTOP_LYRIC"
    const val EXTRA_SOURCE_ID = "source_id"
    const val EXTRA_SOURCE_NAME = "source_name"
    const val EXTRA_SOURCE_TYPE = "source_type"
    const val EXTRA_NAV_ID = "nav_id"

    // 来源类型：点击播放页顶部来源标签时，按类型 + EXTRA_NAV_ID 路由回来源页。
    const val SOURCE_TYPE_PLAYLIST = "playlist"
    const val SOURCE_TYPE_ALBUM = "album"
    const val SOURCE_TYPE_ARTIST = "artist"
    const val SOURCE_TYPE_ROAM = "roam"
    const val SOURCE_TYPE_CLOUD = "cloud"
    const val SOURCE_TYPE_RADIO = "radio"
    const val SOURCE_TYPE_EXPLORE = "explore"
    const val SOURCE_TYPE_RECENT = "recent"
    const val SOURCE_TYPE_RECORD = "record"
    const val EXTRA_DURATION_MS = "duration_ms"
    val CommandToggleLike = SessionCommand(ACTION_TOGGLE_LIKE, Bundle.EMPTY)
    val CommandToggleShuffle = SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY)
    val CommandToggleDesktopLyric = SessionCommand(ACTION_TOGGLE_DESKTOP_LYRIC, Bundle.EMPTY)

    /**
     * 把写进 MediaItem extras 的 sourceName 映射成播放页顶部可读的来源文案。
     * 新队列写的是人类可读名（歌单/专辑/歌手/云盘等），直接透传；
     * 旧队列写的还是内部 key，做一次映射兜底，避免顶部显示 "list"/"album"。
     */
    const val SOURCE_PERSONAL_FM = "personal_fm"
    const val SOURCE_CLOUD = "cloud"
    const val SOURCE_RADIO = "radio"

    fun sourceLabel(sourceName: String?): String {
        val base = when (sourceName) {
            null, "", "list" -> "播放列表"
            "album" -> "专辑"
            SOURCE_CLOUD -> "云盘音乐"
            SOURCE_RADIO -> "电台"
            SOURCE_PERSONAL_FM -> "私人 FM"
            else -> sourceName
        }
        // NCM 每日更新歌单（如「私人雷达」）的 name 是带营销前缀的动态文案，
        // 形如「今天从《…》听起|私人雷达」，前缀每天变。标签只保留真名：
        // 取最后一个 '|' 之后的非空部分；不含 '|' 的名称原样显示。
        val sep = base.lastIndexOf('|')
        return if (sep in 0 until base.length - 1) {
            base.substring(sep + 1).ifBlank { base }
        } else {
            base
        }
    }
}
