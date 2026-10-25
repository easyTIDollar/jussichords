package com.jussicodes.music.lyric

import android.graphics.Color

/** 桌面歌词字号默认值（sp）。 */
const val DesktopLyricDefaultFontSizeSp = 18f

/** 桌面歌词字号可调范围（sp）。 */
const val DesktopLyricMinFontSizeSp = 12f
const val DesktopLyricMaxFontSizeSp = 32f

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
    val backgroundVisible: Boolean = true,
    val locked: Boolean = false,
    val translationEnabled: Boolean = false,
) {
    /** 副行字号：主行的 0.66 倍。 */
    val secondaryFontSizeSp: Float
        get() = fontSizeSp * 0.66f

    /** 副行颜色：主色 80% 不透明度。 */
    val secondaryTextColor: Int
        get() = Color.argb(204, Color.red(textColor), Color.green(textColor), Color.blue(textColor))

    /** 主行描边宽度（px），随字号与屏幕密度缩放。 */
    fun primaryStrokeWidth(density: Float): Float = fontSizeSp * density * 0.17f

    /** 副行描边宽度（px）。 */
    fun secondaryStrokeWidth(density: Float): Float = fontSizeSp * density * 0.11f

    /** 主行描边色：亮字配黑，暗字配白。 */
    val strokeColor: Int
        get() = if (luminance(textColor) > 0.5f) {
            Color.argb(209, 0, 0, 0)
        } else {
            Color.argb(189, 255, 255, 255)
        }

    val secondaryStrokeColor: Int
        get() = if (luminance(textColor) > 0.5f) {
            Color.argb(189, 0, 0, 0)
        } else {
            Color.argb(160, 255, 255, 255)
        }

    companion object {
        /** 相对亮度（0..1），用于决定描边颜色。 */
        fun luminance(color: Int): Float {
            val r = Color.red(color) / 255f
            val g = Color.green(color) / 255f
            val b = Color.blue(color) / 255f
            return 0.299f * r + 0.587f * g + 0.114f * b
        }
    }
}
