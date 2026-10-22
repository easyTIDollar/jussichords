package com.jussicodes.music.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.EaseInOutBack
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaMetadata
import coil3.compose.AsyncImage
import com.jussicodes.music.LocalPlayerController
import com.jussicodes.music.LocalPlayerState
import com.jussicodes.music.constants.MediaItemHeight
import com.jussicodes.music.constants.lyricTranslationEnabledKey
import com.jussicodes.music.constants.wordLyricEnabledKey
import com.jussicodes.music.utils.parseLrc
import com.jussicodes.music.utils.parseYrc
import com.jussicodes.music.utils.rememberPreference
import com.jussicodes.music.viewModel.LyricViewModel
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun Lyric(
    position: Long,
    modifier: Modifier = Modifier,
    imageModifier: Modifier = Modifier,
    mediaMetadata: MediaMetadata,
    onBackPressed: () -> Unit = {},
    /**
     * 是否渲染自带头部（缩略封面 + 歌名 + 歌手 + 设置菜单）。
     *
     * 横屏播放页已经有自己的封面栏和歌名栏，这里再渲染一遍就是重复内容
     * （已踩过：歌名/歌手/封面各出现两次，右栏大片空白）。
     * 横屏传 false 只取歌词本体。
     */
    showHeader: Boolean = true,
    /**
     * 横屏（矮屏）模式：歌词独占一整列高度，字号/行距略小，
     * 自动滚动把当前句停在视口上方约 1/3 处（竖屏是当前句贴顶）。
     */
    landscape: Boolean = false,
    /**
     * 是否注册自身的返回键处理。横屏播放页由外层统一处理返回键，传 false 避免抢占。
     */
    enableBackHandler: Boolean = true,
    lyricViewModel: LyricViewModel = hiltViewModel()
) {
    val mediaController = LocalPlayerController.current.controller
    val playerState = LocalPlayerState.current
    val currentMediaId = playerState?.currentMediaItem?.mediaId
        ?: mediaController?.currentMediaItem?.mediaId
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val lyric by lyricViewModel.lyric.collectAsState()
    var showTranslation by rememberPreference(lyricTranslationEnabledKey, false)
    var showWordLyric by rememberPreference(wordLyricEnabledKey, false)
    var currentIndex by remember { mutableIntStateOf(0) }
    var autoScrollEnabled by remember { mutableStateOf(true) }
    var menuExpanded by remember { mutableStateOf(false) }
    // 横屏：记录歌词视口高度，用来把「当前句停留位」折算成 contentPadding，
    // 从而让每次切换只需要一次 scrollToItem（见下方 highlightAnchorPx）。
    var viewportHeightPx by remember { mutableIntStateOf(0) }
    // 本次滚动是否由我们发起。用于把程序化滚动与用户拖动区分开：
    // isScrollInProgress 对两者都为 true，只有靠这个标记才能判断"用户在翻歌词"。
    var programmaticScroll by remember { mutableStateOf(false) }
    val lrcLine = lyric?.lrc?.lyric?.parseLrc()
    val yrcLine = lyric?.yrc?.lyric?.parseYrc()
    val translationLines = lyric?.tlyric?.lyric?.parseLrc().orEmpty()
    val hasTranslation = translationLines.any { it.text.isNotBlank() }
    val hasWordLyric = !yrcLine.isNullOrEmpty()
    val translationMap = remember(translationLines) {
        translationLines
            .filter { it.text.isNotBlank() }
            .associate { it.time to it.text }
    }
    val displayLines = if (showWordLyric && !yrcLine.isNullOrEmpty()) {
        yrcLine.map { it.time to it.text }
    } else {
        lrcLine?.map { it.time to it.text }
    }

    LaunchedEffect(currentMediaId, mediaMetadata.title) {
        currentIndex = 0
        autoScrollEnabled = true
        programmaticScroll = false
        currentMediaId?.toLongOrNull()?.let {
            lyricViewModel.fetchLyric(it)
        }
        // 换歌：列表回到第一句顶部，避免停在新歌的随机位置。
        listState.scrollToItem(0, 0)
    }

    if (enableBackHandler) {
        BackHandler {
            onBackPressed()
        }
    }

    KeepScreenOn()

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxHeight()
            // showHeader=false（横屏）：modifier 作用于 Surface 本身，用来控制歌词区尺寸；
            // showHeader=true（竖屏）：modifier 仍只作用于内部歌名列（见下方 .then）。
            .then(if (showHeader) Modifier else modifier)
            .onSizeChanged { viewportHeightPx = it.height },
    ) {
        Column(
            modifier = Modifier
                .then(if (showHeader) Modifier.statusBarsPadding() else Modifier.fillMaxSize())
        ) {
            if (showHeader) Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.small)
                        .clickable { onBackPressed() }
                ) {
                    AsyncImage(
                        model = mediaMetadata.artworkUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = imageModifier
                            .size(MediaItemHeight)
                            .clip(MaterialTheme.shapes.small)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .then(modifier)
                    ) {
                        Text(
                            text = mediaMetadata.title.toString(),
                            maxLines = 1,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.basicMarquee()
                        )
                        Text(
                            text = mediaMetadata.artist.toString(),
                            maxLines = 1,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.basicMarquee()
                        )
                    }
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "歌词设置"
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (hasTranslation) "歌词翻译" else "歌词翻译（暂无）") },
                            trailingIcon = {
                                Switch(
                                    checked = showTranslation,
                                    onCheckedChange = { showTranslation = it }
                                )
                            },
                            onClick = { showTranslation = !showTranslation }
                        )
                        DropdownMenuItem(
                            text = { Text(if (hasWordLyric) "逐字歌词" else "逐字歌词（暂无）") },
                            trailingIcon = {
                                Switch(
                                    checked = showWordLyric,
                                    onCheckedChange = { showWordLyric = it }
                                )
                            },
                            onClick = { showWordLyric = !showWordLyric }
                        )
                    }
                }
            }

            displayLines?.let { lrcLines ->

                /**
                 * 「当前句」停留位偏移（像素，负值 = 停在视口顶部之上）。
                 *
                 * 横屏把当前句停在视口上方约 1/3 处。做法是**单次**
                 * animateScrollToItem(index, scrollOffset)，scrollOffset 直接取负值，
                 * 一次到位。绝不能写成 animateScrollToItem(index) + animateScrollBy(补偿)：
                 * 那是两段串行动画打在同一个 LazyListState 上，视觉上就是"先顶到最上、再往回滑"——
                 * 这正是最初的回弹 bug。
                 *
                 * 也不把停留位做成 contentPadding.top：那会让列表总高度随视口变化，
                 * scrollToItem 的落点被反复修正，表现为歌词不滚动 / 抖动。
                 */
                val highlightAnchorOffsetPx: Int =
                    if (landscape && viewportHeightPx > 0) -(viewportHeightPx * 0.30f).toInt() else 0

                // 用户手势滚动：暂停自动跟随；松开后恢复，并把当前句重新拉回停留位。
                // isScrollInProgress 对程序化滚动同样为 true，故用 programmaticScroll 区分。
                LaunchedEffect(listState, lrcLines) {
                    snapshotFlow { listState.isScrollInProgress to programmaticScroll }
                        .collect { (inProgress, byUs) ->
                            if (inProgress && !byUs) {
                                autoScrollEnabled = false
                            } else if (!inProgress && !autoScrollEnabled && byUs) {
                                // 程序化滚动刚结束（用户此前手动翻过）：恢复跟随并归位一次。
                                autoScrollEnabled = true
                                programmaticScroll = false
                                listState.animateScrollToItem(
                                    currentIndex.coerceIn(0, lrcLines.lastIndex),
                                    0
                                )
                            }
                        }
                }

                // 定位当前句。index 非法直接返回；单次 scrollToItem，无二次补偿。
                suspend fun scrollToCurrent(index: Int) {
                    if (index < 0 || index > lrcLines.lastIndex) return
                    programmaticScroll = true
                    try {
                        if (landscape) {
                            // 一次到位：停留位靠负 scrollOffset 实现，不做二次补偿。
                            listState.animateScrollToItem(index, highlightAnchorOffsetPx)
                        } else if (index > 0) {
                            listState.animateScrollToItem(maxOf(index - 2, 0), 0)
                        }
                    } finally {
                        programmaticScroll = false
                    }
                }

                // 仅在「活动歌词索引变化」时触发一次定位。position 每次心跳都变，
                // 这里靠 index != currentIndex 收敛，避免重复位移。
                LaunchedEffect(position) {
                    // indexOfLast 找不到匹配时返回 -1（当前时间早于第一句歌词），
                    // 夹到 0：既避免 -1 传进 scrollToItem，也保证第一句有高亮。
                    val index = lrcLines.indexOfLast { it.first <= position }.coerceAtLeast(0)
                    if (index != currentIndex) {
                        currentIndex = index
                        if (autoScrollEnabled) {
                            coroutineScope.launch { scrollToCurrent(index) }
                        }
                    }
                }

                // 横屏尺寸变化 → 停留位（contentPadding.top）随之变化，重新对齐一次当前句。
                // key 只含停留位，不含 position：避免每次心跳都重复发起滚动。
                LaunchedEffect(highlightAnchorOffsetPx, autoScrollEnabled) {
                    if (landscape && highlightAnchorOffsetPx < 0 && autoScrollEnabled && viewportHeightPx > 0) {
                        scrollToCurrent(currentIndex)
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(if (landscape) 10.dp else 6.dp),
                    contentPadding = PaddingValues(
                        start = if (landscape) 20.dp else 12.dp,
                        end = if (landscape) 20.dp else 12.dp,
                        // 横屏：停留位就是 top padding，定位只需一次 scrollToItem(index, 0)。
                        // 同时充当顶部渐隐下的呼吸空间。
                        // top 保持小值：停留位由 scrollOffset 负责，
                        // 把它加进 contentPadding 会让列表总高度随视口变化而抖动。
                        top = if (landscape) 8.dp else 0.dp,
                        bottom = if (landscape) 56.dp else 0.dp,
                    )
                ) {
                    items(
                        count = lrcLines.size,
                        // 稳定且唯一：以时间戳为主键成分，行号兜底（同一毫秒的两行不冲突），
                        // 翻译文案变化也纳入 key，翻开/关闭翻译不会让整列失配重排。
                        key = { index ->
                            "${lrcLines[index].first}_${index}_${
                                translationMap[lrcLines[index].first]?.hashCode() ?: 0
                            }"
                        },
                    ) { index ->
                        val isCurrent = index == currentIndex
                        val currentText = lrcLines[index].second.isNotEmpty()
                        val translation = translationMap[lrcLines[index].first]
                            ?: translationLines.minByOrNull { abs(it.time - lrcLines[index].first) }
                                ?.takeIf { abs(it.time - lrcLines[index].first) <= 800 }
                                ?.text

                        if (currentText)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable {
                                        lrcLines[index].first.let {
                                            mediaController?.seekTo(it)
                                            coroutineScope.launch {
                                                currentIndex = index
                                            }
                                        }
                                    }
                                    .padding(vertical = if (landscape) 6.dp else 8.dp, horizontal = 4.dp)
                                    .alpha(if (isCurrent) 1f else 0.45f)
                            ) {
                                Text(
                                    text = if (showWordLyric && isCurrent && !yrcLine.isNullOrEmpty()) {
                                        val yrc = yrcLine.getOrNull(index)
                                        val activeEnd = yrc?.words
                                            ?.indexOfLast { position >= it.time }
                                            ?.plus(1) ?: 0
                                        buildAnnotatedString {
                                            yrc?.words?.forEachIndexed { wordIndex, word ->
                                                val style = if (wordIndex < activeEnd) {
                                                    SpanStyle(color = MaterialTheme.colorScheme.onSurface)
                                                } else {
                                                    SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                withStyle(style) {
                                                    append(word.text)
                                                }
                                            } ?: append(lrcLines[index].second)
                                        }
                                    } else {
                                        buildAnnotatedString { append(lrcLines[index].second) }
                                    },
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = if (landscape) 21.sp else 24.sp,
                                    lineHeight = 1.25.em,
                                )
                                if (showTranslation && !translation.isNullOrBlank()) {
                                    Text(
                                        text = translation,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = if (landscape) 14.sp else 16.sp,
                                        lineHeight = 1.2.em,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        else
                            Row(modifier = Modifier.padding(horizontal = 12.dp)) {
                                if (isCurrent) {
                                    lrcLines.getOrNull(index + 1)?.first?.let { time ->
                                        ThreeDotsAnimation(
                                            times = lrcLines[index].first to time,
                                        )
                                    }
                                }
                            }
                    }
                    item {
                        Spacer(Modifier.padding(vertical = if (landscape) 48.dp else 24.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ThreeDotsAnimation(
    modifier: Modifier = Modifier,
    dotColor: Color = MaterialTheme.colorScheme.primary,
    dotSize: Dp = 10.dp,
    times: Pair<Long, Long>,
) {
    if (times.let { it.second - it.first } < 6000L)
        return

    val duration = times.second - times.first
    val transition = rememberInfiniteTransition()
    val scale by
    transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 3000
                1f at 0 using EaseInOutBack
                1.2f at 1500 using EaseInOutBack
                1f at 3000 using EaseInOutBack
            },
            repeatMode = RepeatMode.Reverse
        )
    )

    val alphaAnimations = List(3) { index ->
        transition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = duration.toInt()
                    val start = index * (duration.toInt() / 3)
                    0.5f at start
                    1.0f at start + duration.toInt() / 6
                },
                repeatMode = RepeatMode.Restart
            )
        )
    }

    Canvas(
        modifier = modifier
            .size(48.dp, 16.dp)
            .scale(scale)
    ) {
        val space = 16.dp.toPx()
        val centerY = size.height / 2
        val baseRadius = dotSize.toPx() / 2

        repeat(3) { i ->
            drawCircle(
                color = dotColor.copy(alpha = alphaAnimations[i].value),
                radius = baseRadius,
                center = Offset(
                    x = size.width / 2 - space + i * space,
                    y = centerY
                )
            )
        }
    }
}
