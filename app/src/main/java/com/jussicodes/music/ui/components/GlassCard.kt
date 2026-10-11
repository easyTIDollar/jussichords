package com.jussicodes.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jussicodes.music.constants.glassCardsKey
import com.jussicodes.music.utils.rememberPreference

/** 玻璃卡的四个颜色分量；纯数据，便于单元测试。 */
data class GlassCardColors(
    val fill: Color,
    val fillAlpha: Float,
    val borderAlpha: Float,
    val sheen: Color,
    val sheenAlpha: Float,
) {
    companion object {
        fun glassCardColors(isLightSurface: Boolean): GlassCardColors =
            if (isLightSurface) {
                GlassCardColors(
                    fill = Color(0xFFFFFFFF),
                    fillAlpha = 0.55f,
                    borderAlpha = 0.60f,
                    sheen = Color(0xFFFFFFFF),
                    sheenAlpha = 0.55f,
                )
            } else {
                GlassCardColors(
                    fill = Color(0xFF1C1C1F),
                    fillAlpha = 0.42f,
                    borderAlpha = 0.50f,
                    sheen = Color(0xFFFFFFFF),
                    sheenAlpha = 0.14f,
                )
            }
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.extraLarge
    val enabled by rememberPreference(glassCardsKey, false)

    if (!enabled) {
        Box(
            modifier = modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
        ) { content() }
        return
    }

    val isLight = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    val c = GlassCardColors.glassCardColors(isLight)
    val outline = MaterialTheme.colorScheme.outlineVariant
    val shadowRadius: Dp = if (isLight) 3.dp else 2.dp

    Box(
        modifier = modifier
            .shadow(shadowRadius, shape, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.18f))
            .clip(shape)
            .background(c.fill.copy(alpha = c.fillAlpha))
            .background(
                Brush.linearGradient(
                    colors = listOf(c.sheen.copy(alpha = c.sheenAlpha), c.sheen.copy(alpha = 0f)),
                    start = Offset.Zero,
                    end = Offset(0f, 360f),
                )
            )
            .border(1.dp, outline.copy(alpha = c.borderAlpha), shape)
    ) { content() }
}
