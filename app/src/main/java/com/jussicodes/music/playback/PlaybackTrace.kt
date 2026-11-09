package com.jussicodes.music.playback

import android.content.Context
import android.os.Process
import android.os.SystemClock
import com.jussicodes.music.widget.WidgetDebug

/**
 * 冷启动起播链路计时。
 *
 * 用户手机没有电脑、拿不到 logcat，「点了要等多久才出声」只能靠应用内诊断看。
 * 组件点击时记 t0，链路各段（控制器连接 / 服务就绪 / 队列恢复 / 直链解析 / READY）
 * 都以「相对点击时刻的毫秒数」写进小组件诊断的环形日志 —— 用户点一次、贴一次日志，
 * 就能分清是进程/服务启动慢、队列恢复慢、解析慢，还是首块下载慢。
 *
 * 只在点击后 [WINDOW_MS] 窗口内记录，避免把 60 行环形缓冲刷爆。
 */
object PlaybackTrace {

    private const val WINDOW_MS = 20_000L

    @Volatile
    private var t0 = 0L

    private val seen = java.util.Collections.synchronizedSet(HashSet<String>())

    /** 组件点了播放/暂停：重置一次计时窗口。 */
    fun click(context: Context) {
        t0 = SystemClock.elapsedRealtime()
        seen.clear()
        event(context, "click")
    }

    fun mark(context: Context, label: String) {
        val base = t0
        if (base == 0L) {
            // 没有点击上下文（App 内点播）：只记进程年龄，方便和组件点击链路对照。
            event(
                context,
                "$label proc+${SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()}ms"
            )
            return
        }
        val rel = SystemClock.elapsedRealtime() - base
        if (rel > WINDOW_MS) return
        event(context, "$label +${rel}ms")
    }

    /** 同一标签每轮点击只记一次（解析/状态事件会重复到达）。 */
    fun markOnce(context: Context, key: String, label: String) {
        if (!seen.add(key)) return
        mark(context, label)
    }

    private val eventLock = Any()

    private fun event(context: Context, msg: String) = synchronized(eventLock) {
        // WidgetDebug.event 是读-改-写 SharedPreferences，解析线程与播放器线程会同时打点，
        // 串行化避免互相覆盖丢行（丢的往往正是关键的那一段）。
        WidgetDebug.event(context, "trace $msg")
    }
}
