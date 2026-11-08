package com.jussicodes.music.lyric

import android.graphics.Color

/** 桌面歌词字号默认值（sp）。 */
const val DesktopLyricDefaultFontSizeSp = 18f

/** 桌面歌词字号可调范围（sp）。 */
const val DesktopLyricMinFontSizeSp = 12f
const val DesktopLyricMaxFontSizeSp = 32f

/** 描边粗细倍数：0=不描边，1=默认，2=最粗。 */
const val DesktopLyricDefaultStrokeScale = 1f
const val DesktopLyricMaxStrokeScale = 2f

/** 未拖动过的默认位置：水平居中，垂直偏上。 */
const val DesktopLyricDefaultXFraction = 0.5f
const val DesktopLyricDefaultYFraction = 0.1f

/**
 * 桌面歌词外观设置。
 *
 * 所有可视尺寸都由 [fontSizeSp] 等比派生，避免字号改大后描边/副行比例失真；
 * 描边色按主色亮度自动取黑或白，避免用户在浅色底上选白字配白描边。
 */
data class DesktopLyricStyle(
    val fontSizeSp: Float = DesktopLyricDefaultFontSizeSp,
    val textColor: Int = Color.WHITE,
    val strokeColor: Int = Color.BLACK,
    val strokeScale: Float = 1f,
    val locked: Boolean = false,
    val translationEnabled: Boolean = false,
) {
    /** 副行字号：主行的 0.66 倍。 */
    val secondaryFontSizeSp: Float
        get() = fontSizeSp * 0.66f

    /** 副行颜色：主色 80% 不透明度。 */
    val secondaryTextColor: Int
        get() = Color.argb(204, Color.red(textColor), Color.green(textColor), Color.blue(textColor))

    /**
     * Stroke width as a fraction of text size. 0.17 is far too thick for
     * Chinese glyphs: counters (口/日/目) close up and adjacent characters
     * bleed into each other, which reads as "糊成一团". Typical workable
     * values are 0.05~0.08. Settings preview uses the same ratio so the
     * preview stays WYSIWYG.
     */
    companion object {
        const val StrokeRatio = 0.08f
    }

    /** 主行描边宽度（px），随字号、屏幕密度与描边倍数缩放；倍数为 0 时不描边。 */
    fun primaryStrokeWidth(density: Float): Float = fontSizeSp * density * StrokeRatio * strokeScale.coerceAtLeast(0f)

    /** 副行描边宽度（px）：与设置页预览同为 0.08 比例，否则所见非实际。 */
    fun secondaryStrokeWidth(density: Float): Float = fontSizeSp * density * StrokeRatio * strokeScale.coerceAtLeast(0f)

    /** 副行描边色：沿用描边色，透明度略降。 */
    val secondaryStrokeColor: Int
        get() = Color.argb(
            (Color.alpha(strokeColor) * 0.82f).toInt().coerceIn(0, 255),
            Color.red(strokeColor),
            Color.green(strokeColor),
            Color.blue(strokeColor)
        )
}
