package com.jussicodes.music.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import kotlin.math.abs
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
    /**
     * 歌词区「下拉收起播放页」的跟手位移（正数=手指向下）/ 松手垂直速度。
     *
     * 歌词列表自己滚动时会先消费事件，外层 PlayerTransform 的统一手势读到 0 位移、
     * 接管不了；歌词列表滚到顶之后继续下拉、以及歌词上下的空白处，由右栏这一份手势补上。
     */
    onSwipeDownDrag: (Float) -> Unit = {},
    onSwipeDownEnd: (velocityY: Float) -> Unit = {},
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

    // 沉浸式：导航栏由全局 LandscapeStatusBarImmersive 统一隐藏/恢复。
    // 这里不要再自己 hide/show —— 本页 onDispose 里 show(navigationBars()) 会把
    // 全局的隐藏状态撤掉，横屏转到别的页面就又冒出小白条（曾经就是这么漏的）。

    // 歌词区下拉：LazyColumn 即使已经滚到顶，也会为了 overscroll 继续消费 pointer 事件，
    // 所以栏上那层 pointerInput 永远读到位移 0、接管不了。必须走 nestedScroll：
    // 列表消费不完的 delta 通过 onPostScroll 传上来，这时才驱动播放页跟手收起。
    val currentSwipeDownDrag by rememberUpdatedState(onSwipeDownDrag)
    val currentSwipeDownEnd by rememberUpdatedState(onSwipeDownEnd)
    val lyricNestedScroll = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // 只接管手指拖拽（UserInput）。松手后的 fling 惯性（SideEffect）不接管：
                // 那段会持续喂 delta，把正在跑的收起动画 snapTo 掉，面板就卡在半屏。
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                // 手指向下、列表已无法再滚：剩余量全部转成面板下拉。
                if (available.y > 0f) {
                    currentSwipeDownDrag(available.y)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (available.y > 0f) {
                    currentSwipeDownEnd(available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    // 封面手势：横向拖动切歌，横屏下已移除下滑退出播放页手势
    val coverGestures = rememberPlayerCoverGestures(
        onSwipeNext = { mediaController?.seekToNextMediaItem() },
        onSwipePrevious = { mediaController?.seekToPreviousMediaItem() },
        onDismiss = {}
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
                    // 上限：手机横屏沿用 260dp；平板/大屏横屏（左栏高 700dp+）按
                    // 左栏高度的 62% 放开，否则 1280dp 宽的屏幕上封面只有 260dp，
                    // 左右会留出大片空白。下限不强行抬高——空间真不够时宁可小一点，
                    // 也不让封面把底部进度条/控制组顶出屏幕。
                    val coverCap = maxOf(260.dp, maxHeight * 0.62f)
                    val coverSizeDp = with(LocalDensity.current) { coverSizePx.toDp() }
                        .coerceAtMost(coverCap)
                        .coerceAtLeast(0.dp)

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = leftPadH, end = leftPadH, top = leftPadV, bottom = 10.dp),
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
                                val context = androidx.compose.ui.platform.LocalContext.current
                                val currentMediaIdLong = remember(playerState?.currentMediaItem?.mediaId) {
                                    playerState?.currentMediaItem?.mediaId?.toLongOrNull()
                                }
                                val effectiveArtwork = remember(currentMediaIdLong, mediaMetadata.artworkUri) {
                                    if (currentMediaIdLong != null) {
                                        com.jussicodes.music.data.offline.OfflineMusicMetadataManager.getOfflineCover(
                                            context,
                                            currentMediaIdLong,
                                            mediaMetadata.artworkUri?.toString()
                                        )
                                    } else {
                                        mediaMetadata.artworkUri
                                    }
                                }
                                AsyncImage(
                                    model = effectiveArtwork,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(coverSizeDp)
                                        .then(coverGestures.coverGraphics())
                                        .clip(RoundedCornerShape(14.dp))
                                        .then(coverGestures.coverSwipeGestures())
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
                        val isSongBuffering = playerState?.playbackState == androidx.media3.common.Player.STATE_BUFFERING
                        PlayerProgressSlider(
                            value = if (durationUsable) {
                                if (sliderPosition != null) scrubFraction
                                else (shownPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                            } else 0f,
                            isLoading = isSongBuffering,
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
                        // 列表滚到顶后继续下拉 / 歌词上下空白处的下拉 → 收起播放页。
                        .nestedScroll(lyricNestedScroll)
                        .swipeDownToDismiss(onSwipeDownDrag, onSwipeDownEnd)
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

/**
 * 纵向「跟手下拉」手势：只接管以纵向为主的拖拽，横向滑动一律放行给子组件。
 *
 * 为什么需要这一份：Compose 的 `PointerInputChange.positionChange()` 在事件已被子级
 * 消费后返回 0，歌词 LazyColumn 一滚动，外层 PlayerTransform 的统一手势就读不到位移。
 * 挂在歌词栏上：子级没消费（歌词滚到顶、歌词上下的空白处）就由它接管并 consume，
 * 外层读到 0，不会双计位移。
 */
private fun Modifier.swipeDownToDismiss(
    onDrag: (Float) -> Unit,
    onDragEnd: (velocityY: Float) -> Unit,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val velocityTracker = VelocityTracker()
        velocityTracker.addPosition(down.uptimeMillis, down.position)
        var totalX = 0f
        var totalY = 0f
        // null = 还没过 touchSlop 判不出方向；false = 横向，交还子组件；true = 纵向，接管。
        var isVertical: Boolean? = null

        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break

            velocityTracker.addPosition(change.uptimeMillis, change.position)
            val delta = change.positionChange()
            totalX += delta.x
            totalY += delta.y

            if (isVertical == null &&
                (abs(totalX) > viewConfiguration.touchSlop || abs(totalY) > viewConfiguration.touchSlop)
            ) {
                isVertical = abs(totalY) > abs(totalX)
            }

            if (isVertical == false) break

            if (isVertical == true) {
                change.consume()
                onDrag(delta.y)
            }
        }

        if (isVertical == true) {
            onDragEnd(velocityTracker.calculateVelocity().y)
        }
    }
}
