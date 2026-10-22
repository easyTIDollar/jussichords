package com.jussicodes.music.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalView
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
 * 歌名 / 歌手文本块的参考高度。
 *
 * 封面边长 = 中部弹性区实际高度 - 这个高度，所以这里只需要给歌名+歌手+间距一个
 * 稳定估值即可（文本本身用 heightIn(max=...) 兜住，换歌不会导致封面尺寸抖动）。
 */
private val SongInfoHeight = 52.dp

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
// Slider 的自定义 track / thumb 插槽仍属实验 API（实验注解是 error 级，故必须显式 opt-in）。
@OptIn(ExperimentalMaterial3Api::class)
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

    // 沉浸式：进入横屏播放页隐藏状态栏/导航栏，离开时恢复。
    // 用 WindowInsetsControllerCompat，不碰系统 UI flag，旋转/退出时由 DisposableEffect 复原。
    val view = LocalView.current
    if (!view.isInEditMode) {
        DisposableEffect(Unit) {
            val window = view.context.findActivity()?.window
            val controller = window?.let { WindowCompat.getInsetsController(it, view) }
            controller?.let {
                it.hide(WindowInsetsCompat.Type.systemBars())
                it.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            onDispose {
                controller?.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
// ── 左栏：顶部导航 / 封面+歌曲信息 / 进度 / 控制 ────────────
            //
            // 结构（纯纵向 Column，无绝对定位、无互相冲突的 Spacer）：
            //   1) 顶栏：返回在左，收藏 + 更多在右（两者靠右成组）。
            //   2) 中部 weight(1f) 弹性区：封面 + 歌名/歌手作为**一个整体**垂直+水平居中。
            //   3) 底部固定区：进度条（横跨左栏宽度）+ 时间标签两端对齐 + 控制按钮行。
            //
            // 封面尺寸由 BoxWithConstraints 的实际可用宽高直接算：
            //   封面区可用高度 = 中部弹性区高度 - 歌名/歌手文本高度 - 少量间距
            //   封面边长 = min(可用宽 - 水平padding, 可用高) 并夹在合理区间内。
            // 不再用写死的"预留 250dp"常数估算，所以矮屏不会挤爆、宽屏不会过大。
            BoxWithConstraints(
                modifier = Modifier
                    .weight(LandscapeLeftWeight)
                    .fillMaxHeight()
            ) {
                val leftPadH = 20.dp
                val leftPadV = 8.dp

                // 中部弹性区的实际高度（用来定封面边长），这里先给默认值，
                // 真实值在下面的 Box(Modifier.weight(1f)) 上用 onSizeChanged 回填。
                var middleHeightPx by remember { mutableIntStateOf(0) }

                val infoBlockHeightPx = with(LocalDensity.current) {
                    (SongInfoHeight).roundToPx()
                }
                val coverMaxHeightPx = (middleHeightPx - infoBlockHeightPx).coerceAtLeast(0)
                val coverMaxWidthPx = with(LocalDensity.current) {
                    (maxWidth - leftPadH * 2).roundToPx()
                }
                val coverSizePx = minOf(coverMaxWidthPx, coverMaxHeightPx)
                // 上限 260dp；下限不强行抬高——空间真不够时宁可小一点，
                // 也不让封面把底部进度条/控制组顶出屏幕。
                val coverSizeDp = with(LocalDensity.current) { coverSizePx.toDp() }
                    .coerceAtMost(260.dp)
                    .coerceAtLeast(0.dp)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = leftPadH, end = leftPadH, top = leftPadV, bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // 1) 顶栏：返回靠左，收藏 / 更多靠右成组。
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
                        Spacer(Modifier.width(4.dp))
                        IconClick(MoreVert, tint = dimColor, size = 20.dp, touchSize = 36.dp, onClick = onMoreClick)
                    }

                    // 2) 中部弹性区：封面 + 歌曲信息作为一个整体居中。
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        // 用子 Box 的 onSizeChanged 量出"扣除进度条和控制组后"的真实可用高度，
                        // 避免靠固定常数估算封面大小。
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .onSizeChanged { middleHeightPx = it.height }
                        )

                        Column(
                            modifier = Modifier,
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            AsyncImage(
                                model = mediaMetadata.artworkUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(coverSizeDp)
                                    .clip(RoundedCornerShape(14.dp))
                            )
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Spacer(Modifier.height(10.dp))
                                mediaMetadata.title?.let {
                                    Text(
                                        text = it.toString(),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = titleColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth(),
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
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }

                    // 3) 进度条：横跨左栏可用宽度。
                    // Material3 Slider 默认轨道/滑块偏粗，在矮屏横屏里会抢走封面的视觉重心。
                    // 整体压扁（height 28dp，低于 48dp 触控下限的部分交给 track 自身命中区），
                    // 视觉上只保留一条细线 + 小圆点，把注意力还给封面。
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
                        colors = SliderDefaults.colors(
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                            thumbColor = MaterialTheme.colorScheme.primary,
                        ),
                        track = { sliderState ->
                            // 3dp 细线：紧凑，不与封面争视觉重心。
                            SliderDefaults.Track(
                                sliderState = sliderState,
                                modifier = Modifier.height(3.dp),
                                thumbTrackGapSize = 0.dp,
                                drawStopIndicator = null,
                            )
                        },
                        thumb = {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp),
                    )

                    // 时间标签：左对齐已播放、右对齐剩余，紧贴进度条两端。
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
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

                    Spacer(Modifier.height(6.dp))

                    // 4) 播放控制：单一水平 Row，整体居中、间距均匀。
                    //    主控件 = 上一首 / 播放暂停 / 下一首（更大、带主色实心播放键）；
                    //    次级控件 = 随机 / 循环（小一号、降对比）。
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconClick(
                            imageVector = Shuffle,
                            tint = if (shuffleOn) MaterialTheme.colorScheme.primary else dimColor,
                            size = 20.dp, touchSize = 40.dp,
                            onClick = { mediaController?.shuffleModeEnabled = !shuffleOn },
                        )
                        IconClick(
                            SkipPreviousFill, tint = titleColor, size = 28.dp, touchSize = 44.dp,
                            onClick = { mediaController?.seekToPreviousMediaItem() },
                        )
                        Box(
                            modifier = Modifier
                                .size(52.dp)
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
                                size = 26.dp,
                            )
                        }
                        IconClick(
                            SkipNextFill, tint = titleColor, size = 28.dp, touchSize = 44.dp,
                            onClick = { mediaController?.seekToNextMediaItem() },
                        )
                        IconClick(
                            imageVector = if (repeatMode == Player.REPEAT_MODE_ONE) RepeatOne else Repeat,
                            tint = if (repeatMode == Player.REPEAT_MODE_OFF) dimColor else MaterialTheme.colorScheme.primary,
                            size = 20.dp, touchSize = 40.dp,
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

/** 沿 ContextWrapper 链找到 Activity，拿它的 Window 做沉浸式系统栏控制。 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
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
