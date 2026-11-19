package com.jussicodes.music.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jussicodes.music.animation.SharedArtworkShape

private const val SHIMMER_DURATION_MILLIS = 1200

@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp
) {
    SkeletonShape(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        progress = rememberSkeletonProgress(),
        colors = rememberSkeletonColors()
    )
}

@Composable
fun SkeletonLine(
    widthFraction: Float,
    height: Dp = 16.dp,
    modifier: Modifier = Modifier
) {
    SkeletonShape(
        modifier = modifier
            .fillMaxWidth(widthFraction.coerceIn(0.01f, 1f))
            .height(height),
        shape = RoundedCornerShape(height / 2),
        progress = rememberSkeletonProgress(),
        colors = rememberSkeletonColors()
    )
}

@Composable
fun SkeletonCircle(
    size: Dp,
    modifier: Modifier = Modifier
) {
    SkeletonShape(
        modifier = modifier.size(size),
        shape = CircleShape,
        progress = rememberSkeletonProgress(),
        colors = rememberSkeletonColors()
    )
}

/** 与列表行、评论行共用的骨架布局，可通过尺寸参数贴合对应真实行。 */
@Composable
fun SkeletonListItem(
    hasArtwork: Boolean,
    lines: Int,
    modifier: Modifier = Modifier,
    artworkSize: Dp = 48.dp,
    artworkShape: Shape = SharedArtworkShape,
    circleArtwork: Boolean = false,
    artworkInset: Dp = 6.dp,
    artworkTextSpacing: Dp = 0.dp,
    horizontalPadding: Dp = 6.dp,
    textHorizontalPadding: Dp = 6.dp,
    rowMinHeight: Dp = 64.dp,
    lineSpacing: Dp = 4.dp,
    lineSpacings: List<Dp> = emptyList(),
    lineHeights: List<Dp> = emptyList(),
    lineWidths: List<Float> = emptyList(),
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically
) {
    val progress = rememberSkeletonProgress()
    val colors = rememberSkeletonColors()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = rowMinHeight)
            .padding(horizontal = horizontalPadding),
        verticalAlignment = verticalAlignment
    ) {
        if (hasArtwork) {
            Box(modifier = Modifier.padding(artworkInset).size(artworkSize)) {
                SkeletonShape(
                    modifier = Modifier.fillMaxSize(),
                    shape = if (circleArtwork) CircleShape else artworkShape,
                    progress = progress,
                    colors = colors
                )
            }
            if (artworkTextSpacing > 0.dp) {
                Spacer(Modifier.width(artworkTextSpacing))
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = textHorizontalPadding),
            verticalArrangement = Arrangement.Top
        ) {
            repeat(lines.coerceAtLeast(0)) { index ->
                if (index > 0) {
                    Spacer(Modifier.height(lineSpacings.getOrNull(index - 1) ?: lineSpacing))
                }
                SkeletonShape(
                    modifier = Modifier
                        .fillMaxWidth(
                            (lineWidths.getOrNull(index) ?: if (index == 0) 0.68f else 0.48f)
                                .coerceIn(0.01f, 1f)
                        )
                        .height(lineHeights.getOrNull(index) ?: 16.dp),
                    shape = RoundedCornerShape((lineHeights.getOrNull(index) ?: 16.dp) / 2),
                    progress = progress,
                    colors = colors
                )
            }
        }
    }
}

@Composable
private fun SkeletonShape(
    modifier: Modifier,
    shape: Shape,
    progress: Float,
    colors: Pair<Color, Color>
) {
    Box(
        modifier = modifier
            .clip(shape)
            .drawWithCache {
                val brush = Brush.linearGradient(
                    colors = listOf(colors.first, colors.second, colors.first),
                    start = Offset((progress - 1f) * size.width, 0f),
                    end = Offset(progress * size.width, 0f)
                )
                onDrawBehind { drawRect(brush) }
            }
    )
}

@Composable
private fun rememberSkeletonProgress(): Float {
    val transition = rememberInfiniteTransition()
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = SHIMMER_DURATION_MILLIS,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        )
    )
    return progress
}

@Composable
private fun rememberSkeletonColors(): Pair<Color, Color> {
    val surface = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    val base = remember(surface, onSurface) {
        onSurface.copy(alpha = 0.08f).compositeOver(surface)
    }
    val highlight = remember(surface, onSurface) {
        onSurface.copy(alpha = 0.12f).compositeOver(surface)
    }
    return remember(base, highlight) { base to highlight }
}
