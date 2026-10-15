package com.jussicodes.music.ui.components

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

/** 倍速档位，横屏底栏循环切换。 */
private val SPEED_STEPS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

/**
 * 横屏（矮屏）专用播放页。
 *
 * 与竖屏 [Player] 是**两套独立布局**，不是同一棵树换个尺寸 —— 横屏高度只有
 * ~411dp，任何"把竖排挤进窄栏"的做法都会溢出或让控件消失（已踩过）。
 *
 * 结构参照成熟横屏播放器：
 * - 左栏 37%：返回键 + 居中方形封面（按高度取方，永不溢出）
 * - 右栏 63%：歌名/歌手 → 歌词（自适应剩余高度）→ 进度条 → 播放控件 → 底部功能胶囊
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

    val titleColor = MaterialTheme.colorScheme.onSurface
    val dimColor = MaterialTheme.colorScheme.onSurfaceVariant

    Row(modifier = modifier.fillMaxSize()) {
        // ── 左栏：封面 ────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .weight(LandscapeCoverWeight)
                .fillMaxHeight()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onBackPressed
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon2(ChevronDown, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(8.dp))
            // 封面按高度取方：widthIn(max) + aspectRatio 保证既不超宽也不超高。
            AsyncImage(
                model = mediaMetadata.artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 320.dp)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
            )
        }

        // ── 右栏：信息 → 歌词 → 进度 → 控件 ────────────────────────────
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = 8.dp, end = 24.dp, top = 12.dp, bottom = 12.dp),
        ) {
            // 歌名/歌手：单行紧凑排布，把纵向空间尽量留给歌词。
            // 不在这里放「更多」菜单 —— Lyric 自带头部已有一套菜单（横屏
            // showHeader=false 时由底部胶囊的更多按钮统一入口）。
            Row(verticalAlignment = Alignment.CenterVertically) {
                mediaMetadata.title?.let {
                    Text(
                        text = it.toString(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = titleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                Spacer(Modifier.width(8.dp))
                mediaMetadata.artist?.let {
                    Text(
                        text = it.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = dimColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                Spacer(Modifier.weight(0.4f))
            }

            Spacer(Modifier.height(2.dp))

            // 歌词占满剩余高度 —— 横屏的主角。
            // showHeader=false：不要 Lyric 自带的封面+歌名+歌手+菜单头部，
            // 否则整份头部会在右栏重复出现一遍（歌名/歌手/封面各两次）。
            // 加 weight(1f) 让它吃掉标题栏与进度条之间的所有空间；底部进度条与
            // 控件在 Column 里排在它后面，因此歌词不会被压住。
            Lyric(
                position = position,
                mediaMetadata = mediaMetadata,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                imageModifier = Modifier,
                onBackPressed = onBackPressed,
                showHeader = false,
            )

            // 进度条
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

            // 播放控件：随机 / 上一首 / 播放暂停 / 下一首 / 循环
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconClick(Shuffle, tint = if (shuffleOn) MaterialTheme.colorScheme.primary else dimColor) {
                    mediaController?.shuffleModeEnabled = !shuffleOn
                }
                IconClick(SkipPreviousFill, tint = titleColor, size = 40.dp) {
                    mediaController?.seekToPreviousMediaItem()
                }
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (isPlaying) mediaController?.pause() else mediaController?.play()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon2(
                        if (isPlaying) PauseFill else PlayArrow,
                        tint = MaterialTheme.colorScheme.surface,
                        size = 30.dp
                    )
                }
                IconClick(SkipNextFill, tint = titleColor, size = 40.dp) {
                    mediaController?.seekToNextMediaItem()
                }
                IconClick(
                    imageVector = if (repeatMode == Player.REPEAT_MODE_ONE) RepeatOne else Repeat,
                    tint = if (repeatMode == Player.REPEAT_MODE_OFF) dimColor else MaterialTheme.colorScheme.primary
                ) {
                    mediaController?.repeatMode = when (repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // 底部功能胶囊：倍速 + 收藏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SpeedChip()
                Spacer(Modifier.weight(1f))
                IconClick(
                    imageVector = if (liked) FavoriteFill else Favorite,
                    tint = if (liked) MaterialTheme.colorScheme.primary else dimColor,
                    onClick = onToggleFavorite
                )
                Spacer(Modifier.width(8.dp))
                IconClick(MoreVert, tint = dimColor, onClick = onMoreClick)
            }
        }
    }
}

/** 左栏封面占比（左封面 + 右信息）。 */
private const val LandscapeCoverWeight = 0.37f

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

/** 统一的无涟漪图标按钮。 */
@Composable
private fun IconClick(
    imageVector: ImageVector,
    tint: Color,
    size: Dp = 24.dp,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
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