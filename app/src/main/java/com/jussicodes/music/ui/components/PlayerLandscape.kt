package com.jussicodes.music.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import com.jussicodes.music.LocalPlayerController
import com.jussicodes.music.LocalPlayerState
import com.jussicodes.music.ui.icons.ChevronDown
import com.jussicodes.music.ui.icons.Favorite
import com.jussicodes.music.ui.icons.FavoriteFill
import com.jussicodes.music.ui.icons.MoreVert
import com.jussicodes.music.ui.icons.PauseFill
import com.jussicodes.music.ui.icons.PlayArrow
import com.jussicodes.music.ui.icons.Repeat
import com.jussicodes.music.ui.icons.RepeatOne
import com.jussicodes.music.ui.icons.Shuffle
import com.jussicodes.music.ui.icons.SkipNextFill
import com.jussicodes.music.ui.icons.SkipPreviousFill
import com.jussicodes.music.utils.makeTimeString

/** 倍速档位，顶部快捷区点击循环切换。 */
private val SPEED_STEPS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

/** 左右两栏配比：左 = 封面 + 歌词，右 = 信息 + 控制台。 */
private const val LandscapeLeftWeight = 1.1f
private const val LandscapeRightWeight = 0.9f

/** 封面在左栏内占可用高度的比例（按高度取方，垂直居中）。 */
private const val LandscapeCoverHeightFraction = 0.66f

/**
 * 横屏（矮屏）专用播放页 —— 左右两栏。
 *
 * 与竖屏 [Player] 是**两套独立布局**：横屏高度只有 ~411dp。
 *
 * 结构：
 * - 左栏：返回键 + 封面（垂直居中）+ 歌词竖向滚动（点击任意位置全屏展开）。
 * - 右栏：歌名/歌手 + 倍速/收藏/更多 → 全宽进度条 → 横向五键控制组。
 *
 * 竖屏路径完全不受此文件影响。
 */
@Composable
fun PlayerLandscape(
    mediaMetadata: MediaMetadata,
    position: Long,
    duration: Long,
    liked: Boolean,
    onBackPressed: () -> Unit,
    onMoreClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val playerState = LocalPlayerState.current
    val mediaController = LocalPlayerController.current.controller
    val isPlaying = playerState?.isPlaying == true
    val shuffleOn = playerState?.shuffleModeEnabled == true
    val repeatMode = playerState?.repeatMode ?: Player.REPEAT_MODE_OFF

    var sliderPosition by remember { mutableStateOf<Long?>(null) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }
    val shownPosition = sliderPosition ?: position
    val durationUsable = duration > 0 && duration != C.TIME_UNSET

    var lyricExpanded by remember { mutableStateOf(false) }

    val titleColor = MaterialTheme.colorScheme.onSurface
    val dimColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            // ── 左栏：封面 + 歌词 ─────────────────────────────────────────
            Box(
                modifier = Modifier
                    .weight(LandscapeLeftWeight)
                    .fillMaxHeight()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 48.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 封面：按高度取方 → 竖屏不会超高，宽度也不会溢出左栏。
                    AsyncImage(
                        model = mediaMetadata.artworkUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxHeight(LandscapeCoverHeightFraction)
                            .widthIn(max = 240.dp)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                    )

                    // 歌词：竖向多行滚动，占满左栏剩余宽度；点击任意位置全屏展开。
                    Lyric(
                        position = position,
                        mediaMetadata = mediaMetadata,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { lyricExpanded = true },
                        imageModifier = Modifier,
                        onBackPressed = {},
                        // 左栏已有专门的封面，右栏有信息栏，Lyric 自带头部会造成重复内容。
                        showHeader = false,
                    )
                }

                // 返回键：横屏下系统导航手势可能不可见，这里是唯一可见退路。
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 8.dp, top = 8.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onBackPressed
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon2(ChevronDown, tint = MaterialTheme.colorScheme.primary, size = 22.dp)
                }
            }

            // ── 右栏：信息 → 进度条 → 控制台 ─────────────────────────────
            Column(
                modifier = Modifier
                    .weight(LandscapeRightWeight)
                    .fillMaxHeight()
                    .padding(start = 16.dp, end = 24.dp, top = 10.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                // 顶部：歌名/歌手（左对齐）+ 倍速/收藏/更多快捷图标。
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        mediaMetadata.title?.let {
                            Text(
                                text = it.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = titleColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        mediaMetadata.artist?.let {
                            Text(
                                text = it.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = dimColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    SpeedChip()
                    IconClick(
                        imageVector = if (liked) FavoriteFill else Favorite,
                        tint = if (liked) MaterialTheme.colorScheme.primary else dimColor,
                        size = 22.dp, touchSize = 36.dp,
                        onClick = onToggleFavorite,
                    )
                    IconClick(MoreVert, tint = dimColor, size = 22.dp, touchSize = 36.dp, onClick = onMoreClick)
                }

                // 中部：全宽进度条 + 两端时间标注。
                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = if (durationUsable) {
                            if (sliderPosition != null) scrubFraction
                            else (shownPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                        } else 0f,
                        valueRange = 0f..1f,
                        onValueChange = {
                            scrubFraction = it
                            sliderPosition = (it * (if (durationUsable) duration else 0L)).toLong()
                        },
                        onValueChangeFinished = {
                            sliderPosition?.let {
                                onSeek(it)
                                mediaController?.seekTo(it)
                            }
                            sliderPosition = null
                        },
                        colors = SliderDefaults.colors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = makeTimeString(shownPosition),
                            style = MaterialTheme.typography.labelSmall,
                            color = dimColor,
                            textAlign = TextAlign.Start,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = if (durationUsable) makeTimeString(duration) else "--:--",
                            style = MaterialTheme.typography.labelSmall,
                            color = dimColor,
                            textAlign = TextAlign.End,
                        )
                    }
                }

                // 底部：横向五键控制组。
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconClick(
                        imageVector = Shuffle,
                        tint = if (shuffleOn) MaterialTheme.colorScheme.primary else dimColor,
                        size = 22.dp, touchSize = 44.dp,
                        onClick = { mediaController?.shuffleModeEnabled = !shuffleOn },
                    )
                    IconClick(
                        SkipPreviousFill, tint = titleColor, size = 30.dp, touchSize = 44.dp,
                        onClick = { mediaController?.seekToPreviousMediaItem() },
                    )
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                if (isPlaying) mediaController?.pause() else mediaController?.play()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon2(
                            if (isPlaying) PauseFill else PlayArrow,
                            tint = MaterialTheme.colorScheme.surface,
                            size = 28.dp,
                        )
                    }
                    IconClick(
                        SkipNextFill, tint = titleColor, size = 30.dp, touchSize = 44.dp,
                        onClick = { mediaController?.seekToNextMediaItem() },
                    )
                    IconClick(
                        imageVector = if (repeatMode == Player.REPEAT_MODE_ONE) RepeatOne else Repeat,
                        tint = if (repeatMode == Player.REPEAT_MODE_OFF) dimColor else MaterialTheme.colorScheme.primary,
                        size = 22.dp, touchSize = 44.dp,
                        onClick = {
                            mediaController?.repeatMode = when (repeatMode) {
                                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                else -> Player.REPEAT_MODE_OFF
                            }
                        },
                    )
                }
            }
        }

        // ── 歌词全屏浮层：点击歌词区任意位置展开，返回键收起 ───────────────
        if (lyricExpanded) {
            // 后注册 → 优先级高于 Lyric 内部的 BackHandler，返回键收起浮层。
            BackHandler { lyricExpanded = false }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                Lyric(
                    position = position,
                    mediaMetadata = mediaMetadata,
                    modifier = Modifier.fillMaxSize(),
                    imageModifier = Modifier,
                    onBackPressed = {},
                    showHeader = false,
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { lyricExpanded = false },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon2(ChevronDown, tint = MaterialTheme.colorScheme.primary)
                }
            }
        } else {
            // 折叠态：返回键收起播放页（浮层在场时由上面的 BackHandler 接管）。
            BackHandler { onBackPressed() }
        }
    }
}

/** 倍速胶囊：点击循环下一档。 */
@Composable
private fun SpeedChip() {
    val playerState = LocalPlayerState.current
    val mediaController = LocalPlayerController.current.controller
    val current = playerState?.playbackParameters?.speed ?: 1f
    Text(
        text = "${current}x",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                val idx = SPEED_STEPS.indexOfFirst { kotlin.math.abs(it - current) < 0.01f }
                val next = SPEED_STEPS[(idx + 1).takeIf { idx >= 0 } ?: SPEED_STEPS.indexOf(1.0f)]
                mediaController?.setPlaybackSpeed(next)
            }
            .padding(horizontal = 8.dp, vertical = 6.dp)
    )
}

/** 统一的无涟漪图标按钮。[touchSize] 为可点区域（矮屏下比视觉图标大）。 */
@Composable
private fun IconClick(
    imageVector: ImageVector,
    tint: Color,
    size: Dp = 24.dp,
    touchSize: Dp = 40.dp,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(touchSize)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon2(imageVector, tint = tint, size = size)
    }
}

/** 细封装 Icon，避免与 Player.kt 的私有 Icon 重名。 */
@Composable
private fun Icon2(
    imageVector: ImageVector,
    tint: Color,
    size: Dp = 24.dp,
) {
    Icon(
        imageVector = imageVector,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(size)
    )
}