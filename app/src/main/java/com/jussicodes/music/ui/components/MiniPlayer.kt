package com.jussicodes.music.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jussicodes.music.animation.MotionSpring
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaMetadata
import coil3.compose.AsyncImage
import com.jussicodes.music.LocalPlayerController
import com.jussicodes.music.LocalPlayerState
import com.jussicodes.music.constants.MiniPlayerHeight
import com.jussicodes.music.ui.icons.PauseFill
import com.jussicodes.music.ui.icons.SkipNextFill
import kotlin.math.abs

@Composable
fun MiniPlayer(
    mediaMetadata: MediaMetadata,
    position: Long,
    duration: Long,
    modifier: Modifier = Modifier,
    imageModifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    bottomExtendDp: Dp = 0.dp,
    onDragProgressChange: (Float) -> Unit = {},
    onVerticalDrag: ((Float) -> Unit)? = null,
    onVerticalDragEnd: ((Float) -> Unit)? = null,
    dragProgress: Float = 0f,
    isPillProgress: Boolean = false,
) {
    val mediaController = LocalPlayerController.current.controller
    val playerState = LocalPlayerState.current
    val density = LocalDensity.current
    val swipeOpenThresholdPx = with(density) { 40.dp.toPx() }
    val swipeVelocityThresholdPx = with(density) { 360.dp.toPx() }
    val swipePreviewLimitPx = with(density) { 140.dp.toPx() }
    val horizontalSwipeThresholdPx = with(density) { 120.dp.toPx() }
    val horizontalPreviewLimitPx = with(density) { 220.dp.toPx() }
    val dragDirectionSlopPx = with(density) { 12.dp.toPx() }
    var playerDragOffsetY by remember { mutableFloatStateOf(0f) }
    var playerDragOffsetX by remember { mutableFloatStateOf(0f) }
    var isTransitioningToFull by remember { mutableStateOf(false) }

    LaunchedEffect(mediaMetadata) {
        isTransitioningToFull = false
        playerDragOffsetY = 0f
    }

    LaunchedEffect(Unit) {
        isTransitioningToFull = false
        playerDragOffsetY = 0f
    }

    val animatedPlayerDragOffsetY by animateFloatAsState(
        targetValue = playerDragOffsetY,
        animationSpec = if (isTransitioningToFull) snap() else MotionSpring.dragOffset,
        label = "MiniPlayerDragOffset"
    )
    val animatedPlayerDragOffsetX by animateFloatAsState(
        targetValue = playerDragOffsetX,
        animationSpec = MotionSpring.dragOffset,
        label = "MiniPlayerHorizontalDragOffset"
    )
    val miniPlayerCorner = if (isPillProgress) 26.dp else 16.dp
    val miniPlayerShape = if (isPillProgress) RoundedCornerShape(26.dp) else RoundedCornerShape(topStart = miniPlayerCorner, topEnd = miniPlayerCorner)
    val openProgress = (-animatedPlayerDragOffsetY / swipePreviewLimitPx).coerceIn(0f, 1f)
    SideEffect {
        onDragProgressChange(openProgress)
    }
    val extraBackdropHeight = (-animatedPlayerDragOffsetY * 0.22f).coerceIn(0f, with(density) { 18.dp.toPx() })
    val backdropHeight = MiniPlayerHeight + with(density) { extraBackdropHeight.toDp() } + bottomExtendDp
    val artworkLiftPx = animatedPlayerDragOffsetY * 0.72f
    val artworkScale = 1f + openProgress * 0.28f
    val controlsAlpha = (1f - openProgress * 1.6f).coerceIn(0f, 1f)
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
                .pointerInput(
                    swipeOpenThresholdPx,
                    swipeVelocityThresholdPx,
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
                        val velocityTracker = VelocityTracker()
                        velocityTracker.addPosition(down.uptimeMillis, down.position)

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break

                            velocityTracker.addPosition(change.uptimeMillis, change.position)
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
                                    if (onVerticalDrag != null) {
                                        change.consume()
                                        onVerticalDrag(positionChange.y)
                                    } else if (positionChange.y < 0f || playerDragOffsetY < 0f) {
                                        change.consume()
                                        // 渐进式阻尼：越向上拉阻力越大，手感柔韧不撞墙
                                        val pullRatio = (-playerDragOffsetY / swipePreviewLimitPx).coerceIn(0f, 1f)
                                        val resistance = 1f - pullRatio * 0.45f
                                        playerDragOffsetY = (playerDragOffsetY + positionChange.y * resistance).coerceIn(
                                            -swipePreviewLimitPx,
                                            0f
                                        )
                                    }
                                }

                                null -> Unit
                            }
                        }

                        val velocity = velocityTracker.calculateVelocity()
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
                            if (onVerticalDragEnd != null) {
                                onVerticalDragEnd(velocity.y)
                            } else {
                                val isFlingUp = velocity.y <= -swipeVelocityThresholdPx
                                val isDragUp = -playerDragOffsetY >= swipeOpenThresholdPx
                                if (isFlingUp || isDragUp) {
                                    isTransitioningToFull = true
                                    onClick()
                                } else {
                                    playerDragOffsetY = 0f
                                }
                            }
                        } else {
                            playerDragOffsetY = 0f
                            playerDragOffsetX = 0f
                        }
                    }
                }
                .height(backdropHeight)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(backdropHeight)
                    .clip(miniPlayerShape)
                    .clickable { onClick() }
                    .clipToBounds()
            ) {
                // 移除此处冗余的 ArtworkGlassBackdrop：外层 PlayerTransform 容器已全量承载磨砂玻璃底板，
                // 消除双重高斯模糊计算与重复图片请求产生的绘制开销。

                // 悬浮胶囊底栏模式：以小播放器自身作为流体进度条（主题色半透明充盈）
                if (isPillProgress) {
                    val progressFraction = (position.toFloat() / duration.coerceAtLeast(1L)).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progressFraction)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                            )
                            .graphicsLayer {
                                val combinedProgress = maxOf(openProgress, dragProgress)
                                alpha = (1f - combinedProgress * 2.5f).coerceIn(0f, 1f)
                            }
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(MiniPlayerHeight)
            ) {
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
                if (!isPillProgress) {
                    LinearProgressIndicator(
                        progress = { (position.toFloat() / duration).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.BottomCenter)
                            .graphicsLayer {
                                val combinedProgress = maxOf(openProgress, dragProgress)
                                alpha = if (combinedProgress > 0.01f) 0f else 1f
                            },
                    )
                }
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
                            artworkCorner = miniPlayerCorner,
                            artworkOffsetY = artworkLiftPx,
                            artworkScale = artworkScale,
                            contentAlpha = controlsAlpha,
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.graphicsLayer {
                            alpha = controlsAlpha
                        }
                    ) {
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
                                imageVector = if (playerState?.isPlaying == true) PauseFill else Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        IconButton(
                            enabled = true,
                            onClick = {
                                mediaController?.seekToNext()
                            }
                        ) {
                            Icon(imageVector = SkipNextFill, contentDescription = null, modifier = Modifier.size(24.dp))
                        }
                    }
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
    artworkOffsetY: Float = 0f,
    artworkScale: Float = 1f,
    contentAlpha: Float = 1f,
) {
    val density = LocalDensity.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.graphicsLayer {
                translationY = artworkOffsetY
                scaleX = artworkScale
                scaleY = artworkScale
                if (artworkOffsetY < -2f) {
                    shadowElevation = (abs(artworkOffsetY) * 0.45f).coerceIn(0f, with(density) { 14.dp.toPx() })
                    shape = RoundedCornerShape(artworkCorner)
                    clip = false
                }
            }
        ) {
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
                .graphicsLayer {
                    alpha = contentAlpha
                }
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee()
                )
            }
        }
    }
}
