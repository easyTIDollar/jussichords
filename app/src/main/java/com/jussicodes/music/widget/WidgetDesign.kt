package com.jussicodes.music.widget

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.ui.graphics.Color
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
 * 小组件统一视觉：卡片圆角 28dp，强调色取应用图标同款红 #C12727。
 *
 * 动态取色兜底：API 31+ 读系统 accent1_500 作种子，把卡片底色轻微染上系统色；
 * minSdk 26 的机器没有 Material You，回落到应用自带主题种子（Theme.kt 的 DefaultThemeSeed 紫）。
 */
object WidgetTokens {
    val Accent = Color(0xFFC12727)
    val CardRadius = 28.dp
    val CoverRadiusSmall = 16.dp

    /** compose Theme.kt 里 API<31 的默认种子色，保持小组件与应用主题同源。 */
    internal val FallbackSeed = Color(0xFF6650A4)
}

data class WidgetPalette(
    val isDark: Boolean,
    val card: Color,
    val chip: Color,
    val onCard: Color,
    val secondary: Color,
    val accent: Color,
    val placeholderTop: Color,
    val placeholderBottom: Color,
) {
    companion object {
        fun of(context: Context): WidgetPalette {
            val dark = (context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val seed = systemSeed(context)

            val base = if (dark) Color(0xFF15141A) else Color(0xFFFDF8FB)
            val card = base.blend(seed, if (dark) 0.10f else 0.06f)
            val chip = if (dark) Color(0xFF232228).blend(seed, 0.10f)
            else Color(0xFFF0EAEF).blend(seed, 0.08f)

            return WidgetPalette(
                isDark = dark,
                card = card,
                chip = chip,
                onCard = if (dark) Color(0xFFF3EFF4) else Color(0xFF1B1B1F),
                secondary = if (dark) Color(0xFFA9A2AE) else Color(0xFF6C6670),
                accent = WidgetTokens.Accent,
                placeholderTop = card.blend(WidgetTokens.Accent, if (dark) 0.34f else 0.22f),
                placeholderBottom = card.blend(WidgetTokens.Accent, if (dark) 0.12f else 0.08f),
            )
        }

        private fun systemSeed(context: Context): Color {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching {
                    return Color(context.getColor(android.R.color.system_accent1_500))
                }
            }
            return WidgetTokens.FallbackSeed
        }
    }
}

/** 白/黑通用圆角底板；tint 上色，API<31 由 drawable 自带圆角，API 31+ 再叠 cornerRadius 裁子视图。 */
fun roundedCard(color: Color, radius: Dp = WidgetTokens.CardRadius, drawable: Int = R.drawable.widget_card_round): GlanceModifier {
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

/** 每款组件向系统声明的可选尺寸（SizeMode.Responsive 的候选集）。 */
object WidgetSizes {
    val Classic = setOf(DpSize(250.dp, 110.dp), DpSize(300.dp, 140.dp), DpSize(340.dp, 160.dp))
    val Square = setOf(DpSize(140.dp, 140.dp), DpSize(180.dp, 180.dp), DpSize(230.dp, 230.dp))
    val Capsule = setOf(DpSize(250.dp, 60.dp), DpSize(300.dp, 68.dp), DpSize(350.dp, 76.dp))
}
