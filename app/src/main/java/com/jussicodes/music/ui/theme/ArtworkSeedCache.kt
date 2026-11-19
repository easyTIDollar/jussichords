package com.jussicodes.music.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 上一次封面种子色的内存缓存；异步预读与持久化都在 IO，未命中时使用主题回退色。 */
object ArtworkSeedCache {

    private const val PREFS_NAME = "artwork_seed"
    private const val KEY_ARTWORK = "artwork"
    private const val KEY_COLOR = "color"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ioLock = Mutex()
    private val loading = AtomicBoolean(false)
    private val cached = AtomicReference<Pair<String, Int>?>(null)

    @Volatile
    private var appContext: Context? = null

    /** Application 只登记上下文并发起异步预读，首帧不等待磁盘。 */
    fun preload(context: Context) {
        appContext = context.applicationContext
        if (!loading.compareAndSet(false, true)) return
        scope.launch {
            ioLock.withLock {
                val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val key = p.getString(KEY_ARTWORK, null)
                val color = p.getInt(KEY_COLOR, 0)
                // 异步预读不能覆盖已经计算出的新封面颜色。
                if (key != null && color != 0) cached.compareAndSet(null, key to color)
            }
        }
    }

    /** 命中上次缓存的同一张封面时返回种子色，否则 null（回落到默认配色）。 */
    fun get(artwork: Any?): Color? {
        val key = artwork?.toString() ?: return null
        val entry = cached.get() ?: return null
        if (key != entry.first || entry.second == 0) return null
        return Color(entry.second)
    }

    fun put(artwork: Any?, color: Color) {
        val key = artwork?.toString() ?: return
        val argb = color.toArgb()
        val entry = key to argb
        if (cached.getAndSet(entry) == entry) return
        val app = appContext ?: return
        scope.launch {
            ioLock.withLock {
                val latest = cached.get() ?: return@withLock
                app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                    .putString(KEY_ARTWORK, latest.first)
                    .putInt(KEY_COLOR, latest.second)
                    .apply()
            }
        }
    }
}
