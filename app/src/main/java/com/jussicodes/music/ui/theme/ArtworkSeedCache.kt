package com.jussicodes.music.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * 上一次从封面算出的主题种子色的进程内缓存 + 落盘。
 *
 * 冷启动时 MediaController 还没连上，`playerState` 为 null，主题只能先用默认配色出首帧；
 * controller 连上后 artwork 才有值，取色要先解码封面（IO），颜色算完再整棵树重绘一次 ——
 * 用户看到的就是「界面已经出来了，然后配色跳一下」。把上一次的 (artwork → seed) 存下来，
 * 首帧直接拿它当初始值，取色结果一致时不再触发重绘。
 *
 * 只缓存最近一次，够冷启动用；体积可忽略（SharedPreferences 两个键），[preload] 同步读表。
 */
object ArtworkSeedCache {

    private const val PREFS_NAME = "artwork_seed"
    private const val KEY_ARTWORK = "artwork"
    private const val KEY_COLOR = "color"

    private var prefs: SharedPreferences? = null

    @Volatile
    private var cachedKey: String? = null

    @Volatile
    private var cachedColor: Int = 0

    /** 在 Application.onCreate 里同步调用；只在首帧前读一次极小的 SharedPreferences 表。 */
    fun preload(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = p
        cachedKey = p.getString(KEY_ARTWORK, null)
        cachedColor = p.getInt(KEY_COLOR, 0)
    }

    /** 命中上次缓存的同一张封面时返回种子色，否则 null（回落到默认配色）。 */
    fun get(artwork: Any?): Color? {
        val key = artwork?.toString() ?: return null
        if (key != cachedKey || cachedColor == 0) return null
        return Color(cachedColor)
    }

    fun put(artwork: Any?, color: Color) {
        val key = artwork?.toString() ?: return
        val argb = color.toArgb()
        if (key == cachedKey && argb == cachedColor) return
        cachedKey = key
        cachedColor = argb
        prefs?.edit()?.putString(KEY_ARTWORK, key)?.putInt(KEY_COLOR, argb)?.apply()
    }
}
