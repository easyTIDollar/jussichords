package com.jussicodes.music.ui.components

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import coil3.compose.AsyncImage
import com.jussicodes.music.LocalPlayerController
import com.jussicodes.music.LocalPlayerState
import com.jussicodes.music.ui.icons.ChevronDown
import com.jussicodes.music.ui.icons.Favorite
import com.jussicodes.music.ui.icons.FavoriteFill
import com.jussicodes.music.ui.icons.ModeComment
import com.jussicodes.music.ui.icons.MoreVert
import com.jussicodes.music.ui.icons.Timelapse
import com.jussicodes.music.ui.icons.Timer
import com.jussicodes.music.utils.makeTimeString
import kotlin.time.DurationUnit
import kotlin.time.toDuration

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
 * - 顶部通栏：返回靠左，**更多靠屏幕最右**。
 * - 左栏（约 45%）：封面（按可用高度自适应，居中）→ 歌名 / 歌手 →
 *   收藏 + 评论（进度条右端上方）→ 进度条 + 时间 → 底部控制组。
 * - 右栏（约 55%）：独占整列高度的同步歌词，自身纵向滚动，当前句突出、其余降对比。
 *
 * 两栏各自管理布局与滚动状态，互不影响。竖屏路径完全不受此文件影响。
 */
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
    onCommentsClick: () -> Unit,
    onTitleClick: () -> Unit,
    onArtistClick: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val playerState = LocalPlayerState.current
    val mediaController = LocalPlayerController.current.controller
    val isPlaying = playerState?.isPlaying == true
    val shuffleOn = playerState?.shuffleModeEnabled == true
    val repeatMode = playerState?.repeatMode ?: androidx.media3.common.Player.REPEAT_MODE_OFF
    val playbackMode = currentPlaybackMode(shuffleOn, repeatMode)

    var sliderPosition by remember { mutableStateOf<Long?>(null) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }
    val shownPosition = sliderPosition ?: position
    val durationUsable = duration > 0 && duration != C.TIME_UNSET

    // 定时播放（睡眠定时）：与竖屏菜单里的定时器同一套逻辑。
    val isSleepTimerSet = playerState?.isSleepTimerSet == true
    val remainingTimeText = playerState?.remainingTime?.toDuration(DurationUnit.SECONDS)
        ?.toComponents { hours, minutes, seconds, _ ->
            if (hours > 0) {
                "%02dh:%02dm".format(hours, minutes)
            } else {
                "%02dm%02ds".format(minutes, seconds)
            }
        }
    var showTimerPicker by remember { mutableStateOf(false) }
    var showCancelTimer by remember { mutableStateOf(false) }

    val titleColor = MaterialTheme.colorScheme.onSurface
    val dimColor = MaterialTheme.colorScheme.onSurfaceVariant
    val panelColor = MaterialTheme.colorScheme.surfaceContainer

    // 沉浸式：横屏播放页额外隐藏导航栏（状态栏由全局的
    // LandscapeStatusBarImmersive 负责）。用 WindowInsetsControllerCompat，
    // 不碰系统 UI flag，旋转/退出时由 DisposableEffect 复原。
    val view = LocalView.current
    if (!view.isInEditMode) {
        DisposableEffect(Unit) {
            val window = view.context.findActivity()?.window
            val controller = window?.let { WindowCompat.getInsetsController(it, view) }
            controller?.let {
                it.hide(WindowInsetsCompat.Type.navigationBars())
                it.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            onDispose {
                controller?.show(WindowInsetsCompat.Type.navigationBars())
            }
        }
    }

    // 封面手势：从竖屏复用（横向拖动切歌、下拉收起），但**不接点击进歌词页**
    // —— 横屏右侧本来就有歌词栏，点封面不再跳转。
    val coverGestures = rememberPlayerCoverGestures(
        onSwipeNext = { mediaController?.seekToNextMediaItem() },
        onSwipePrevious = { mediaController?.seekToPreviousMediaItem() },
        onDismiss = { onBackPressed() }
    )

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
// ── 顶部通栏：返回靠左，更多贴屏幕最右 ────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 16.dp, top = 6.dp, bottom = 2.dp),
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
                IconClick(MoreVert, tint = dimColor, size = 20.dp, touchSize = 36.dp, onClick = onMoreClick)
            }

            Row(modifier = Modifier.weight(1f)) {
// ── 左栏：封面+歌曲信息 / 进度 / 控制 ────────────
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(LandscapeLeftWeight)
                        .fillMaxHeight()
                ) {
                    val leftPadH = 20.dp
                    val leftPadV = 4.dp

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
                            .padding(start = leftPadH, end = leftPadH, top = leftPadV, bottom = 10.dp)
                            .then(coverGestures.dismissGraphics()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // 1) 中部弹性区：封面 + 歌曲信息作为一个整体居中。
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
                                        .then(coverGestures.coverGraphics())
                                        .clip(RoundedCornerShape(14.dp))
                                        .then(coverGestures.coverSwipeGestures())
                                        .then(coverGestures.coverDismissGestures(enabled = true))
                                )
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Spacer(Modifier.height(10.dp))
                                    // 歌名：加粗大字号；歌手：小字号降对比 —— 两行做视觉区分。
                                    mediaMetadata.title?.let {
                                        Text(
                                            text = it.toString(),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = titleColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null,
                                                    onClick = onTitleClick
                                                ),
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
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null,
                                                    onClick = onArtistClick
                                                ),
                                        )
                                    }
                                }
                            }
                        }

                        // 2) 收藏 + 评论：放在进度条右端上方。
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End,
                        ) {
                            IconClick(
                                imageVector = if (liked) FavoriteFill else Favorite,
                                tint = if (liked) MaterialTheme.colorScheme.primary else dimColor,
                                size = 20.dp, touchSize = 34.dp,
                                onClick = onToggleFavorite,
                            )
                            Spacer(Modifier.width(4.dp))
                            IconClick(
                                imageVector = ModeComment,
                                tint = dimColor,
                                size = 20.dp, touchSize = 34.dp,
                                onClick = onCommentsClick,
                            )
                        }

                        // 3) 进度条：统一细样式（3dp 轨道 + 竖条滑块）。
                        PlayerProgressSlider(
                            value = if (durationUsable) {
                                if (sliderPosition != null) scrubFraction
                                else (shownPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                            } else 0f,
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
                            modifier = Modifier.fillMaxWidth(),
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

                        Spacer(Modifier.height(4.dp))

                        // 4) 播放控制：播放模式（循环+随机合并）/ 切歌三键 / 定时播放。
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 左：播放模式（顺序 → 列表循环 → 单曲循环 → 随机）
                            PlaybackModeButton(
                                state = playbackMode,
                                onClick = {
                                    when (nextPlaybackMode(playbackMode)) {
                                        PlaybackModeState.SEQUENTIAL -> {
                                            mediaController?.shuffleModeEnabled = false
                                            mediaController?.repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF
                                        }
                                        PlaybackModeState.REPEAT_ALL -> {
                                            mediaController?.shuffleModeEnabled = false
                                            mediaController?.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
                                        }
                                        PlaybackModeState.REPEAT_ONE -> {
                                            mediaController?.shuffleModeEnabled = false
                                            mediaController?.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE
                                        }
                                        PlaybackModeState.SHUFFLE -> {
                                            mediaController?.shuffleModeEnabled = true
                                            mediaController?.repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF
                                        }
                                    }
                                },
                            )

                            // 中：切歌 + 播放/暂停（复用竖屏按钮样式）
                            PlayerControlButtons(
                                isPlaying = isPlaying,
                                onPrevious = { mediaController?.seekToPreviousMediaItem() },
                                onNext = { mediaController?.seekToNextMediaItem() },
                                onTogglePlay = {
                                    if (isPlaying) mediaController?.pause() else mediaController?.play()
                                },
                                playButtonSize = 60.dp,
                                sideButtonSize = 44.dp,
                                spacing = 12.dp,
                            )

                            // 右：定时播放（占用原循环按钮的位置）
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) {
                                        if (isSleepTimerSet) {
                                            showCancelTimer = true
                                        } else {
                                            showTimerPicker = true
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon2(
                                    imageVector = if (isSleepTimerSet) Timelapse else Timer,
                                    tint = if (isSleepTimerSet) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        dimColor
                                    },
                                    size = 22.dp,
                                )
                            }
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
        }

        // 返回键：横屏下系统导航手势可能不可见，这里是唯一可见退路。
        // 在 Lyric 之后注册，优先级更高。
        BackHandler { onBackPressed() }
    }

    if (showTimerPicker) {
        SleepTimerDialog(
            onDismiss = { showTimerPicker = false },
            onTimeSet = {
                playerState?.startTimer(it)
                showTimerPicker = false
            }
        )
    }

    if (showCancelTimer) {
        Dialog(
            onConfirmation = {
                playerState?.cancelTimer()
                showCancelTimer = false
            },
            onDismissRequest = { showCancelTimer = false },
            dialogTitle = "结束定时播放",
            dialogText = remainingTimeText?.let { "剩余 $it" },
        )
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
