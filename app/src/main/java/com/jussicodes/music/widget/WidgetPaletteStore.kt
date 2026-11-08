package com.jussicodes.music.widget

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 组件配色镜像。
 *
 * App 的主题色板（壁纸动态色 / 封面取色 / 主页背景图 / 默认种子）由 [JetMeloTheme] 算完后
 * 写进这张小表，组件合成时同步读取——两边用的是**同一个 ColorScheme**，
 * 不再各算一套 blend 比例，所以卡片底、按钮色、文字色和 App 内完全一致。
 *
 * 浅色/深色两套都存：系统深浅色切换时 App 不一定在前台，
 * 组件按当前 uiMode 直接取对应的一套，不需要等 App 重组。
 */
object WidgetPaletteStore {
    private const val PREFS = "jussi_widget_palette"
    private const val K_SIG = "sig"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 最近一次发布过的签名：App 每秒会重组多次，先用内存挡掉，不必每次都读盘。 */
    @Volatile
    private var lastSignature: String? = null

    /** App 主题变化时写入；颜色没变直接返回，不重复写盘、不重复重绘。 */
    fun publish(context: Context, light: ColorScheme, dark: ColorScheme) {
        val app = context.applicationContext
        val sig = signature(light) + "#" + signature(dark)
        if (sig == lastSignature) return
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(K_SIG, null) == sig) {
            lastSignature = sig
            return
        }
        lastSignature = sig
        prefs.edit()
            .putString(K_SIG, sig)
            .also { e -> e.putScheme("light", light); e.putScheme("dark", dark) }
            .apply()
        // 配色变了要立刻重绘，否则要等下一次播放事件才变色。
        scope.launch { runCatching { WidgetUpdater.redraw(app) } }
    }

    /** 组件合成路径：只读同步小表，拿不到（App 还没组合过主题）返回 null 走种子色回落。 */
    fun load(context: Context, dark: Boolean): WidgetPalette? {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(K_SIG, null) == null) return null
        val tag = if (dark) "dark" else "light"
        val card = prefs.getInt("${tag}_card", 0)
        if (card == 0) return null
        return WidgetPalette(
            isDark = dark,
            card = Color(card),
            onCard = Color(prefs.getInt("${tag}_on_card", 0)),
            secondary = Color(prefs.getInt("${tag}_secondary", 0)),
            accent = Color(prefs.getInt("${tag}_accent", 0)),
            onAccent = Color(prefs.getInt("${tag}_on_accent", 0)),
            track = Color(prefs.getInt("${tag}_track", 0)),
        )
    }

    private fun signature(scheme: ColorScheme): String = listOf(
        scheme.surfaceContainer, scheme.onSurface, scheme.onSurfaceVariant,
        scheme.primary, scheme.onPrimary,
    ).joinToString(",") { it.toArgb().toUInt().toString(16) }

    private fun android.content.SharedPreferences.Editor.putScheme(
        tag: String,
        scheme: ColorScheme,
    ) {
        putInt("${tag}_card", scheme.surfaceContainer.toArgb())
        putInt("${tag}_on_card", scheme.onSurface.toArgb())
        putInt("${tag}_secondary", scheme.onSurfaceVariant.toArgb())
        putInt("${tag}_accent", scheme.primary.toArgb())
        putInt("${tag}_on_accent", scheme.onPrimary.toArgb())
        // 进度条轨道：和 App 一样用文字色低透明度，深色/浅色各一档。
        val trackAlpha = if (scheme.surface.luminance() > 0.5f) 0.12f else 0.16f
        putInt("${tag}_track", scheme.onSurface.copy(alpha = trackAlpha).toArgb())
    }
}
