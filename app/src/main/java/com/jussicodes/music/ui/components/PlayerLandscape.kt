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
import androidx.compose.foundation.layout.height
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

/** 倍速档位，右栏胶囊循环切换。 */
private val SPEED_STEPS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

/** 三栏配比：封面 / 歌词 / 信息+控制。 */
private const val LandscapeCoverWeight = 0.28f
private const val LandscapeLyricWeight = 0.44f

/** 歌词默认占可用高度的比例；剩余部分留给「点击展开」提示。 */
private const val LandscapeLyricCollapsedFraction = 0.62f

/**
 * 横屏（矮屏）专用播放页 —— 三栏桌面式布局。
 *
 * 与竖屏 [Player] 是**两套独立布局**：横屏高度只有 ~411dp，任何「把竖排挤进窄栏」
 * 的做法都会溢出或让控件消失（已踩过）。
 *
 * 结构：左栏封面 / 中栏歌词（主角，默认折叠半屏，点击全屏）/ 右栏信息+控制。
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

    var lyricExpanded by remember { mutableStateOf(false) }

    val titleColor = MaterialTheme.colorScheme.onSurface
    val dimColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            // ── 左栏：封面 ────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .weight(LandscapeCoverWeight)
                    .fillMaxHeight()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                // 封面按高度取方：weight + widthIn(max) + aspectRatio 保证既不超宽也不超高。
                AsyncImage(
                    model = mediaMetadata.artworkUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth(0.86f)
                        .widthIn(max = 320.dp)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                )

                // 返回键：横屏下系统导航手势可能不可见，这里是唯一可见退路。
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onBackPressed
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon2(ChevronDown, tint = MaterialTheme.colorScheme.primary)
                }
            }

            // ── 中栏：歌词（主角，默认半屏） ───────────────────────────────
            Column(
                modifier = Modifier
                    .weight(LandscapeLyricWeight)
                    .fillMaxHeight()
                    .padding(top = 12.dp, bottom = 12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(LandscapeLyricCollapsedFraction)
                ) {
                    Lyric(
                        position = position,
                        mediaMetadata = mediaMetadata,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { lyricExpanded = true },
                        imageModifier = Modifier,
                        onBackPressed = {},
                        // 不要 Lyric 自带的封面/歌名/歌手/菜单头部 —— 三栏里已有专门的
                        // 封面栏和信息栏，再渲染一遍就是歌名/封面各两次（已踩过）。
                        showHeader = false,
                    )
                }

                // 折叠时剩下的那块：提示 + 展开按钮。展开后让位给空隙。
                if (!lyricExpanded) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f - LandscapeLyricCollapsedFraction)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { lyricExpanded = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "点击展开歌词",
                            style = MaterialTheme.typography.labelSmall,
                            color = dimColor.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            // ── 右栏：歌名/歌手 → 进度条 → 控件 → 底部功能胶囊 ─────────────
            Column(
                modifier = Modifier
                    .weight(1f - LandscapeCoverWeight - LandscapeLyricWeight)
                    .fillMaxHeight()
                    .padding(start = 8.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
            ) {
                mediaMetadata.title?.let {
                    Text(
                        text = it.toString(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = titleColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                mediaMetadata.artist?.let {
                    Text(
                        text = it.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = dimColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.weight(1f))

                // 进度条：横向 Slider，右栏 ~250dp 足够。
                Slider(
                    value = if (duration > 0 && duration != C.TIME_UNSET) {
                        if (sliderPosition != null) scrubFraction
                        else (shownPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                    } else 0f,
                    valueRange = 0f..1f,
                    onValueChange = {
                        scrubFraction = it
                        val d = if (duration > 0 && duration != C.TIME_UNSET) duration else 0L
                        sliderPosition = (it * d).toLong()
                    },
                    onValueChangeFinished = {
                        sliderPosition?.let {
                            onSeek(it)
                            mediaController?.seekTo(it)
                        }
                        sliderPosition = null
                    },
                    colors = SliderDefaults.colors(),
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(makeTimeString(shownPosition), style = MaterialTheme.typography.labelSmall, color = dimColor)
                    Spacer(Modifier.weight(1f))
                    Text(makeTimeString(duration), style = MaterialTheme.typography.labelSmall, color = dimColor)
                }

                Spacer(Modifier.height(6.dp))

                // 控件竖排：右栏只有 ~250dp 宽，横排五个会把主键挤到栏边；
                // 竖排也符合桌面播放器的信息侧栏习惯。
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    IconClick(
                        imageVector = Shuffle,
                        tint = if (shuffleOn) MaterialTheme.colorScheme.primary else dimColor,
                        size = 20.dp, touchSize = 32.dp,
                        onClick = { mediaController?.shuffleModeEnabled = !shuffleOn },
                    )
                    IconClick(
                        SkipPreviousFill, tint = titleColor, size = 26.dp, touchSize = 32.dp,
                        onClick = { mediaController?.seekToPreviousMediaItem() },
                    )
                    Box(
                        modifier = Modifier
                            .size(48.dp)
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
                            size = 24.dp,
                        )
                    }
                    IconClick(
                        SkipNextFill, tint = titleColor, size = 26.dp, touchSize = 32.dp,
                        onClick = { mediaController?.seekToNextMediaItem() },
                    )
                    IconClick(
                        imageVector = if (repeatMode == Player.REPEAT_MODE_ONE) RepeatOne else Repeat,
                        tint = if (repeatMode == Player.REPEAT_MODE_OFF) dimColor else MaterialTheme.colorScheme.primary,
                        size = 20.dp, touchSize = 32.dp,
                        onClick = {
                            mediaController?.repeatMode = when (repeatMode) {
                                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                else -> Player.REPEAT_MODE_OFF
                            }
                        },
                    )
                }

                Spacer(Modifier.weight(1f))

                // 底部功能胶囊：倍速 + 收藏 + 更多
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f))
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SpeedChip()
                    Spacer(Modifier.weight(1f))
                    IconClick(
                        imageVector = if (liked) FavoriteFill else Favorite,
                        tint = if (liked) MaterialTheme.colorScheme.primary else dimColor,
                        touchSize = 32.dp,
                        onClick = onToggleFavorite,
                    )
                    IconClick(MoreVert, tint = dimColor, touchSize = 32.dp, onClick = onMoreClick)
                }
            }
        }

        // ── 歌词全屏浮层：展开时盖住三栏 ─────────────────────────────────
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
            .padding(horizontal = 8.dp, vertical = 4.dp)
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