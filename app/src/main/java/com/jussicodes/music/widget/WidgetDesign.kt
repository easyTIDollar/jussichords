package com.jussicodes.music.widget

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.ContentScale
import androidx.glance.unit.ColorProvider
import com.jussicodes.music.R
import com.jussicodes.music.ui.theme.artworkColorScheme

/**
 * 桌面组件的视觉基线。
 *
 * 和 App 内播放页同源：卡片底色由当前封面主色（[WidgetStateStore.seedFor]）轻微染色，
 * 强调色就是封面主色本身——播放页取色、组件取色走的是同一套 [com.jussicodes.music.ui.theme.extractArtworkSeed]，
 * 所以桌面上和 App 里的“这首歌的颜色”是一致的。
 */
object WidgetTokens {
    /** 卡片圆角。和 App 里的播放页封面卡同一档（24~28dp），取中。 */
    val CardRadius = 26.dp

    /** 封面小图圆角（宽条布局左侧缩略图）。 */
    val CoverRadius = 18.dp

    /** 取不到封面主色时的兜底：App 在 API<31 的默认主题种子色。 */
    val FallbackSeed = Color(0xFF6650A4)
}

/**
 * 组件配色。
 *
 * [card] 只用「封面主色 × 中性底」混出来的实色：Glance 的 RemoteViews 跨进程绘制，
 * 半透明叠层在部分 launcher 上会和壁纸糊成一片，宁可自己算好不透明色。
 */
data class WidgetPalette(
    val isDark: Boolean,
    val card: Color,
    val onCard: Color,
    val secondary: Color,
    val accent: Color,
    val onAccent: Color,
    val track: Color,
) {
    companion object {
        fun of(context: Context, seedArgb: Int): WidgetPalette {
            val dark = (context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

            // 首选 App 写过来的最终色板：卡片底=surfaceContainer、强调色=primary、
            // 文字=onSurface/onSurfaceVariant，和播放页取的是同一套 MaterialTheme。
            WidgetPaletteStore.load(context, dark)?.let { return it }

            // 回落（App 从未组合过主题，例如刚装完就加组件）：用同一个封面种子色
            // 跑 App 的 createLight/DarkScheme，公式一致，只是少了壁纸/主页背景来源。
            val seed = if (seedArgb != 0) Color(seedArgb) else WidgetTokens.FallbackSeed
            val scheme = artworkColorScheme(seed, dark)
            return WidgetPalette(
                isDark = dark,
                card = scheme.surfaceContainer,
                onCard = scheme.onSurface,
                secondary = scheme.onSurfaceVariant,
                accent = scheme.primary,
                onAccent = scheme.onPrimary,
                track = scheme.onSurface.copy(alpha = if (dark) 0.16f else 0.12f),
            )
        }
    }
}

/**
 * 统一圆角底板。
 *
 * API 31+ 用 Glance 的 cornerRadius 裁切；API 26~30 上这一层不生效，
 * 由 drawable 自带的圆角保证外观（tint 上去的是同一个颜色）。
 */
fun roundedCard(
    color: Color,
    radius: Dp = WidgetTokens.CardRadius,
    drawable: Int = R.drawable.widget_card_round,
): GlanceModifier {
    var modifier = GlanceModifier.background(
        imageProvider = ImageProvider(drawable),
        contentScale = ContentScale.FillBounds,
        colorFilter = ColorFilter.tint(ColorProvider(color)),
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        modifier = modifier.cornerRadius(radius)
    }
    return modifier
}

fun Color.blend(other: Color, ratio: Float): Color =
    Color(ColorUtils.blendARGB(toArgb(), other.toArgb(), ratio.coerceIn(0f, 1f)))

