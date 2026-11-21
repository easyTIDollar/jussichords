package com.jussicodes.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.jussicodes.music.utils.CoverImageSize
import com.jussicodes.music.utils.coverMemoryKey

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
        ArtworkBackdropStyle.Compact -> if (isLightSurface) 0.50f else 0.34f
        ArtworkBackdropStyle.FullScreen -> if (isLightSurface) 0.26f else 0.34f
    }
    val glassOverlayAlpha = when (style) {
        ArtworkBackdropStyle.Compact -> if (isLightSurface) 0.58f else 0.78f
        ArtworkBackdropStyle.FullScreen -> if (isLightSurface) 0.78f else 0.72f
    }
    val colorTintAlpha = when (style) {
        ArtworkBackdropStyle.Compact -> if (isLightSurface) 0.08f else 0.06f
        ArtworkBackdropStyle.FullScreen -> if (isLightSurface) 0.05f else 0.08f
    }
    val imageScale = if (style == ArtworkBackdropStyle.FullScreen) 1.45f else 1.18f
    val blurRadius: Dp = if (style == ArtworkBackdropStyle.FullScreen) 64.dp else 28.dp
    val primary = MaterialTheme.colorScheme.primary
    // surface 罩层与 primary 色偏在同一个 Box 里连续两次 SrcOver 绘制，
    // 按结合律预混成一层，省掉一次全屏填充；数值与原两次叠加逐像素一致。
    val mergedOverlayAlpha = colorTintAlpha + glassOverlayAlpha * (1f - colorTintAlpha)
    val mergedOverlayColor = if (mergedOverlayAlpha <= 0.0001f) {
        Color.Transparent
    } else {
        lerp(surface, primary, colorTintAlpha / mergedOverlayAlpha).copy(alpha = mergedOverlayAlpha)
    }
    val context = LocalContext.current
    val artworkRequest = remember(context, artwork) {
        ImageRequest.Builder(context)
            .data(artwork)
            .memoryCacheKey(coverMemoryKey(artwork, CoverImageSize.DETAIL))
            .build()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(surface)
            .clipToBounds()
    ) {
        AsyncImage(
            model = artworkRequest,
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
                .background(mergedOverlayColor)
        )
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
