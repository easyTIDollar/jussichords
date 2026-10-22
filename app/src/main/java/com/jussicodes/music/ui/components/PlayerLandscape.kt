package com.jussicodes.music.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Brush
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

/** 左右两栏配比：左 = 封面 + 信息 + 控制，右 = 歌词。 */
private const val LandscapeLeftWeight = 0.45f
private const val LandscapeRightWeight = 0.55f

/**
 * 左栏除封面外的固定高度开销（顶栏 / 歌名 / 歌手 / 进度条 / 时间 / 控制组 / 间距）。
 * 封面尺寸 = 可用高度 - 这个开销，再与左栏可用宽度取小。
 * 240dp 是按各控件实际高度加总后的保守值，避免矮屏下把控制组挤出屏幕。
 */
private val LandscapeLeftReserved = 250.dp

/** 歌词栏上下渐隐的高度。 */
private val LandscapeLyricFadeHeight = 36.dp

/**
 * 横屏（矮屏）专用播放页 —— 稳定的左右双栏。
 *
 * 与竖屏 [Player] 是**两套独立布局**：横屏高度只有 ~411dp。
 *
 * 结构：
 * - 左栏（约 45%）：顶栏（返回 / 收藏 / 更多）→ 封面（按可用高度自适应，居中）
 *   → 歌名 / 歌手 → 进度条 + 已播放 / 剩余时间 → 底部横向五键控制组。
 * - 右栏（约 55%）：独占整列高度的同步歌词，自身纵向滚动，当前句突出、其余降对比。
 *
 * 两栏各自管理布局与滚动状态，互不影响。竖屏路径完全不受此文件影响。
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

    val titleColor = MaterialTheme.colorScheme.onSurface
    val dimColor = MaterialTheme.colorScheme.onSurfaceVariant
    val panelColor = MaterialTheme.colorScheme.surfaceContainer

    Box(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            // ── 左栏：封面 + 歌曲信息 + 进度 + 控制组 ───────────────────
            BoxWithConstraints(
                modifier = Modifier
                    .weight(LandscapeLeftWeight)
                    .fillMaxHeight()
            ) {
                // 封面：正方形、圆角，尺寸 = min(可用宽, 可用高 - 固定开销)，整体居中。
                val coverSize: Dp = minOf(
                    maxWidth - 24.dp,
                    maxHeight - LandscapeLeftReserved
                ).coerceIn(72.dp, 320.dp)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 10.dp)
                ) {
                    // 顶栏：返回 / 收藏 / 更多。
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
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
                        Spacer(Modifier.weight(1f))
                        IconClick(
                            imageVector = if (liked) FavoriteFill else Favorite,
                            tint = if (liked) MaterialTheme.colorScheme.primary else dimColor,
                            size = 20.dp, touchSize = 36.dp,
                            onClick = onToggleFavorite,
                        )
                        IconClick(MoreVert, tint = dimColor, size = 20.dp, touchSize = 36.dp, onClick = onMoreClick)
                    }

                    // 封面：左栏上方，正方形圆角，水平居中。
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = mediaMetadata.artworkUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(coverSize)
                                .clip(RoundedCornerShape(14.dp))
                        )
                    }

                    // 歌名 / 歌手：封面下方，居中。
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        mediaMetadata.title?.let {
                            Text(
                                text = it.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = titleColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }
                        mediaMetadata.artist?.let {
                            Text(
                                text = it.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = dimColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }

                    // 进度条 + 已播放 / 剩余时间。
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
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
                                text = if (durationUsable) {
                                    makeTimeString((duration - shownPosition).coerceAtLeast(0L))
                                } else "--:--",
                                style = MaterialTheme.typography.labelSmall,
                                color = dimColor,
                                textAlign = TextAlign.End,
                            )
                        }
                    }

                    // 弹性留白：把控制组推到左栏最底部。
                    Spacer(Modifier.weight(1f))

                    // 底部：横向五键控制组（播放/暂停为视觉中心）。
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

            // ── 右栏：独占整列高度的同步歌词 ───────────────────────────
            Box(
                modifier = Modifier
                    .weight(LandscapeRightWeight)
                    .fillMaxHeight()
            ) {
                Lyric(
                    position = position,
                    mediaMetadata = mediaMetadata,
                    modifier = Modifier.fillMaxSize(),
                    imageModifier = Modifier,
                    onBackPressed = {},
                    // 右栏有独立面板，不再重复渲染 Lyric 自带头部。
                    showHeader = false,
                    landscape = true,
                    enableBackHandler = false,
                )

                // 上下渐隐：歌词过长时裁切，不撑开页面。
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(LandscapeLyricFadeHeight)
                        .background(
                            Brush.verticalGradient(
                                listOf(panelColor, Color.Transparent)
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(LandscapeLyricFadeHeight)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, panelColor)
                            )
                        )
                )
            }
        }

        // 返回键：横屏下系统导航手势可能不可见，这里是唯一可见退路。
        // 在 Lyric 之后注册，优先级更高。
        BackHandler { onBackPressed() }
    }
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
