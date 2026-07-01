package com.jussicodes.music.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaMetadata
import coil3.compose.AsyncImage
import com.jussicodes.music.LocalPlayerController
import com.jussicodes.music.LocalPlayerState
import com.jussicodes.music.constants.MiniPlayerHeight
import com.jussicodes.music.ui.icons.ChevronDown
import com.jussicodes.music.ui.icons.Pause
import com.jussicodes.music.ui.icons.PlayArrow
import com.jussicodes.music.ui.icons.SkipNext
import kotlin.math.abs

@Composable
fun MiniPlayer(
    mediaMetadata: MediaMetadata,
    position: Long,
    duration: Long,
    modifier: Modifier = Modifier,
    imageModifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val mediaController = LocalPlayerController.current.controller
    val playerState = LocalPlayerState.current
    val density = LocalDensity.current
    val swipeOpenThresholdPx = with(density) { 48.dp.toPx() }
    val swipePreviewLimitPx = with(density) { 96.dp.toPx() }
    val horizontalSwipeThresholdPx = with(density) { 120.dp.toPx() }
    val horizontalPreviewLimitPx = with(density) { 220.dp.toPx() }
    val dragDirectionSlopPx = with(density) { 12.dp.toPx() }
    var playerDragOffsetY by remember { mutableFloatStateOf(0f) }
    var playerDragOffsetX by remember { mutableFloatStateOf(0f) }
    val animatedPlayerDragOffsetY by animateFloatAsState(
        targetValue = playerDragOffsetY,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "MiniPlayerDragOffset"
    )
    val animatedPlayerDragOffsetX by animateFloatAsState(
        targetValue = playerDragOffsetX,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "MiniPlayerHorizontalDragOffset"
    )
    val arrowBreath by rememberInfiniteTransition(label = "MiniPlayerOpenHint").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "MiniPlayerOpenHintBreath"
    )
    val miniPlayerCorner = 16.dp
    val miniPlayerShape = RoundedCornerShape(topStart = miniPlayerCorner, topEnd = miniPlayerCorner)
    val openProgress = (-animatedPlayerDragOffsetY / swipePreviewLimitPx).coerceIn(0f, 1f)
    val horizontalProgress = (abs(animatedPlayerDragOffsetX) / horizontalPreviewLimitPx).coerceIn(0f, 1f)
    val previewProgress = ((horizontalProgress - 0.42f) / 0.58f).coerceIn(0f, 1f)
    val player = playerState?.player
    val currentIndex = playerState?.mediaItemIndex ?: player?.currentMediaItemIndex ?: 0
    val previousMediaMetadata = if (currentIndex > 0) {
        player?.getMediaItemAt(currentIndex - 1)?.mediaMetadata
    } else {
        playerState?.mediaMetadata
    }
    val nextMediaMetadata = if (player != null && currentIndex < player.mediaItemCount - 1) {
        player.getMediaItemAt(currentIndex + 1).mediaMetadata
    } else {
        null
    }
    val previewMediaMetadata = when {
        animatedPlayerDragOffsetX < 0f -> nextMediaMetadata
        animatedPlayerDragOffsetX > 0f -> previousMediaMetadata
        else -> null
    }

    val showMiniPlayer =
        (playerState?.player?.mediaItemCount ?: 0) != 0

    if (showMiniPlayer)
        Box(
            modifier = modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationY = animatedPlayerDragOffsetY * 0.65f
                }
                .pointerInput(
                    swipeOpenThresholdPx,
                    swipePreviewLimitPx,
                    horizontalSwipeThresholdPx,
                    horizontalPreviewLimitPx,
                    mediaMetadata
                ) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var totalDragX = 0f
                        var totalDragY = 0f
                        var isHorizontalDrag: Boolean? = null

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break

                            val positionChange = change.positionChange()
                            totalDragX += positionChange.x
                            totalDragY += positionChange.y

                            if (isHorizontalDrag == null &&
                                (abs(totalDragX) > dragDirectionSlopPx || abs(totalDragY) > dragDirectionSlopPx)
                            ) {
                                isHorizontalDrag = abs(totalDragX) > abs(totalDragY)
                            }

                            when (isHorizontalDrag) {
                                true -> {
                                    change.consume()
                                    playerDragOffsetX = (playerDragOffsetX + positionChange.x).coerceIn(
                                        -horizontalPreviewLimitPx,
                                        horizontalPreviewLimitPx
                                    )
                                }

                                false -> {
                                    if (positionChange.y < 0f || playerDragOffsetY < 0f) {
                                        change.consume()
                                        playerDragOffsetY = (playerDragOffsetY + positionChange.y).coerceIn(
                                            -swipePreviewLimitPx,
                                            0f
                                        )
                                    }
                                }

                                null -> Unit
                            }
                        }

                        if (isHorizontalDrag == true) {
                            when {
                                playerDragOffsetX <= -horizontalSwipeThresholdPx -> {
                                    mediaController?.seekToNext()
                                }

                                playerDragOffsetX >= horizontalSwipeThresholdPx -> {
                                    val currentPosition = mediaController?.currentPosition
                                        ?: playerState?.player?.currentPosition
                                        ?: position
                                    if (currentPosition > 3000L) {
                                        mediaController?.seekTo(0)
                                    } else {
                                        mediaController?.seekToPrevious()
                                    }
                                }
                            }
                            playerDragOffsetX = 0f
                        } else if (isHorizontalDrag == false) {
                            if (-playerDragOffsetY >= swipeOpenThresholdPx) {
                                playerDragOffsetY = 0f
                                onClick()
                            } else {
                                playerDragOffsetY = 0f
                            }
                        } else {
                            playerDragOffsetY = 0f
                            playerDragOffsetX = 0f
                        }
                    }
                }
                .height(MiniPlayerHeight)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(MiniPlayerHeight)
                    .clip(miniPlayerShape)
                    .clickable { onClick() }
                    .clipToBounds()
            ) {
                AsyncImage(
                    model = mediaMetadata.artworkUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.18f
                            scaleY = 1.18f
                            alpha = 0.34f
                        }
                        .blur(28.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            MaterialTheme.colorScheme.surfaceContainer.copy(
                                alpha = 0.78f
                            )
                    )
                )
            }
            Icon(
                imageVector = ChevronDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        alpha = openProgress * (0.45f + arrowBreath * 0.55f)
                        translationY = -animatedPlayerDragOffsetY * 0.65f - 6f - arrowBreath * 8f
                        rotationZ = 180f
                    }
                    .size(28.dp)
            )
            previewMediaMetadata?.let {
                MiniMediaInfo(
                    mediaMetadata = it,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp)
                        .graphicsLayer {
                            alpha = previewProgress
                            translationX = if (animatedPlayerDragOffsetX < 0f) {
                                horizontalPreviewLimitPx + animatedPlayerDragOffsetX
                            } else {
                                -horizontalPreviewLimitPx + animatedPlayerDragOffsetX
                            }
                        },
                    artworkCorner = miniPlayerCorner
                )
            }
            LinearProgressIndicator(
                progress = { (position.toFloat() / duration).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.BottomCenter),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer {
                            translationX = animatedPlayerDragOffsetX
                            alpha = 1f - previewProgress
                        }
                ) {
                    MiniMediaInfo(
                        mediaMetadata = mediaMetadata,
                        modifier = Modifier.padding(horizontal = 6.dp),
                        imageModifier = imageModifier,
                        artworkCorner = miniPlayerCorner
                    )
                }

                IconButton(
                    enabled = true,
                    onClick = {
                        if (playerState?.isPlaying == true)
                            mediaController?.pause()
                        else
                            mediaController?.play()
                    }
                ) {
                    Icon(
                        imageVector = if (playerState?.isPlaying == true) Pause else PlayArrow,
                        contentDescription = null
                    )
                }
                IconButton(
                    enabled = true,
                    onClick = {
                        mediaController?.seekToNext()
                    }
                ) {
                    Icon(imageVector = SkipNext, contentDescription = null)
                }
            }
        }
}

@Composable
fun MiniMediaInfo(
    mediaMetadata: MediaMetadata,
    modifier: Modifier = Modifier,
    imageModifier: Modifier = Modifier,
    artworkCorner: Dp = 16.dp,
) {

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Box(modifier = Modifier) {
            AsyncImage(
                model = mediaMetadata.artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = imageModifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(artworkCorner))
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 6.dp)
        ) {
            mediaMetadata.title?.let {
                Text(
                    text = it.toString(),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee()
                )
            }
            mediaMetadata.artist?.let {
                Text(
                    text = it.toString(),
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee()
                )
            }
        }
    }
}
