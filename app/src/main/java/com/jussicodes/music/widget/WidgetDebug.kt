package com.jussicodes.music.widget

import android.content.ComponentName
import android.content.Context
import android.util.Log
import android.appwidget.AppWidgetManager

/**
 * 小组件的求救通道。
 *
 * Glance 的合成一旦抛异常，session 会被直接关掉、组件停在上一帧的 RemoteViews 上，
 * 表面上就是「状态永远不同步」，而且 Logcat 之外没有任何痕迹。
 * 这里把最后一次异常记下来，由 [BaseMusicWidget] 在合成失败后画成一张可见的错误卡，
 * 这样用户截一张图就能定位到真实原因，不用连 adb。
 */
object WidgetDebug {
    private const val TAG = "JussiWidget"
    private const val PREFS = "jussi_widget_debug"
    private const val K_ERROR = "last_error"
    private const val K_AT = "last_error_at"

    /** 只在这个时间窗内展示错误卡，历史异常不该长期污染组件。 */
    private const val SHOW_WINDOW_MS = 10 * 60 * 1000L

    fun record(context: Context, throwable: Throwable) {
        val text = buildString {
            append(throwable.javaClass.simpleName)
            throwable.message?.take(220)?.let { append(": ").append(it) }
        }
        Log.e(TAG, "widget composition failed: $text", throwable)
        runCatching {
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(K_ERROR, text)
                .putLong(K_AT, System.currentTimeMillis())
                .commit()
        }
    }

    /** 近期的合成异常；没有就返回 null。 */
    fun recentError(context: Context): String? = runCatching {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val at = prefs.getLong(K_AT, 0L)
        if (at == 0L || System.currentTimeMillis() - at > SHOW_WINDOW_MS) return null
        prefs.getString(K_ERROR, null)?.takeIf { it.isNotBlank() }
    }.getOrNull()

    fun clear(context: Context) {
        runCatching {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().clear().commit()
        }
    }

    fun log(message: String) {
        Log.d(TAG, message)
    }

    fun error(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
    }

    // ---- 应用内诊断日志：没有 adb 也能把组件链路的状态导出来 ----

    private const val K_LOG = "log"
    private const val MAX_LINES = 60

    private fun time(): String {
        val now = System.currentTimeMillis()
        return java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date(now))
    }

    /** 记一条事件到环形缓冲，同时照常打 logcat。 */
    fun event(context: Context, message: String) {
        Log.d(TAG, message)
        runCatching {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val old = prefs.getString(K_LOG, "").orEmpty()
            val lines = (old.split('\n').filter { it.isNotBlank() } + "${time()} $message")
                .takeLast(MAX_LINES)
            prefs.edit().putString(K_LOG, lines.joinToString("\n")).apply()
        }
    }

    fun clearLog(context: Context) {
        runCatching {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(K_LOG).apply()
        }
    }

    /** 当前环境 + 环形日志，给诊断弹窗直接展示。 */
    fun dump(context: Context): String = buildString {
        val app = context.applicationContext
        appendLine("包名: ${app.packageName}")
        appendLine("版本: ${runCatching { com.jussicodes.music.BuildConfig.VERSION_NAME }.getOrDefault("?")} (${runCatching { com.jussicodes.music.BuildConfig.BUILD_TYPE }.getOrDefault("?")})")
        val manager = runCatching { AppWidgetManager.getInstance(app) }.getOrNull()
        if (manager == null) {
            appendLine("AppWidgetManager: 取不到")
        } else {
            listOf(
                "经典" to ClassicWidgetReceiver::class.java,
                "方形" to SquareWidgetReceiver::class.java,
                "胶囊" to CapsuleWidgetReceiver::class.java,
            ).forEach { (label, receiver) ->
                val ids = runCatching {
                    manager.getAppWidgetIds(ComponentName(app, receiver)).joinToString(",")
                }.getOrElse { "err:${it.javaClass.simpleName}" }
                appendLine("$label: [$ids]")
            }
        }
        val snapshot = runCatching { WidgetUpdater.current(app) }.getOrNull()
        appendLine(
            "帧: song=${snapshot?.songId ?: "-"} play=${snapshot?.isPlaying ?: "-"} " +
                "load=${snapshot?.isLoading ?: "-"} title=${snapshot?.title.orEmpty().take(18)}"
        )
        appendLine("最近异常: ${recentError(app) ?: "无"}")
        appendLine("--- 日志 ---")
        val lines = runCatching {
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(K_LOG, "").orEmpty()
        }.getOrDefault("")
        appendLine(lines.ifBlank { "（空）" })
    }.trim()

}
