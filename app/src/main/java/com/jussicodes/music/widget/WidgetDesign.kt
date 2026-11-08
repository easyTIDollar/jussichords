package com.jussicodes.music.widget

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
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
            val seed = if (seedArgb != 0) Color(seedArgb) else WidgetTokens.FallbackSeed

            return if (dark) {
                val accent = seed.blend(Color.White, 0.32f)
                WidgetPalette(
                    isDark = true,
                    card = Color(0xFF15131A).blend(seed, 0.26f),
                    onCard = Color(0xFFF4F1F7),
                    secondary = Color(0xFFB0A9B8),
                    accent = accent,
                    onAccent = if (accent.luminance() > 0.55f) Color(0xFF191521) else Color.White,
                    track = Color.White.copy(alpha = 0.16f),
                )
            } else {
                val accent = seed.blend(Color.Black, 0.18f)
                WidgetPalette(
                    isDark = false,
                    card = Color(0xFFFCF9FC).blend(seed, 0.14f),
                    onCard = Color(0xFF17141C),
                    secondary = Color(0xFF6A636F),
                    accent = accent,
                    onAccent = if (accent.luminance() > 0.55f) Color(0xFF17141C) else Color.White,
                    track = Color.Black.copy(alpha = 0.12f),
                )
            }
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

/**
 * 组件的候选尺寸。
 *
 * 只声明三档，各自对应一种布局：宽条 4×2 / 方形 2×2 / 窄条 4×1。
 * 桌面拉伸时 Glance 会吸附到最近的候选尺寸，布局分支就不会落到中间态。
 */
object WidgetSizes {
    val Card = setOf(DpSize(250.dp, 120.dp), DpSize(300.dp, 140.dp), DpSize(340.dp, 160.dp))
    val Square = setOf(DpSize(150.dp, 150.dp), DpSize(180.dp, 180.dp), DpSize(230.dp, 230.dp))
    val Strip = setOf(DpSize(250.dp, 64.dp), DpSize(300.dp, 72.dp), DpSize(350.dp, 80.dp))

    val All: Set<DpSize> = Card + Square + Strip
}
