package com.jussicodes.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

enum class ArtworkBackdropStyle {
    Compact,
    FullScreen
}

@Composable
fun ArtworkGlassBackdrop(
    artwork: Any?,
    modifier: Modifier = Modifier,
    style: ArtworkBackdropStyle = ArtworkBackdropStyle.Compact,
) {
    val surface = MaterialTheme.colorScheme.surfaceContainer
    val isLightSurface = surface.luminance() > 0.5f
    val artworkBackdropAlpha = when (style) {
        ArtworkBackdropStyle.Compact -> if (isLightSurface) 0.58f else 0.50f
        ArtworkBackdropStyle.FullScreen -> if (isLightSurface) 0.26f else 0.34f
    }
    val glassOverlayAlpha = when (style) {
        ArtworkBackdropStyle.Compact -> if (isLightSurface) 0.68f else 0.85f
        ArtworkBackdropStyle.FullScreen -> if (isLightSurface) 0.78f else 0.72f
    }
    val colorTintAlpha = when (style) {
        ArtworkBackdropStyle.Compact -> if (isLightSurface) 0.10f else 0.10f
        ArtworkBackdropStyle.FullScreen -> if (isLightSurface) 0.05f else 0.08f
    }
    val imageScale = if (style == ArtworkBackdropStyle.FullScreen) 1.45f else 1.30f
    val blurRadius: Dp = if (style == ArtworkBackdropStyle.FullScreen) 64.dp else 36.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        AsyncImage(
            model = artwork,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = imageScale
                    scaleY = imageScale
                    alpha = artworkBackdropAlpha
                }
                .blur(blurRadius)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(surface.copy(alpha = glassOverlayAlpha))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = colorTintAlpha))
        )
        if (style == ArtworkBackdropStyle.Compact) {
            // 亚克力高光：顶部一道白色渐变，玻璃质感
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.00f to Color.White.copy(alpha = if (isLightSurface) 0.10f else 0.14f),
                                0.35f to Color.White.copy(alpha = 0f),
                            )
                        )
                    )
            )
        }
        if (style == ArtworkBackdropStyle.FullScreen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.00f to surface.copy(alpha = if (isLightSurface) 0.10f else 0.18f),
                                0.42f to surface.copy(alpha = if (isLightSurface) 0.24f else 0.28f),
                                1.00f to surface.copy(alpha = if (isLightSurface) 0.52f else 0.46f),
                            )
                        )
                    )
            )
        }
    }
}
