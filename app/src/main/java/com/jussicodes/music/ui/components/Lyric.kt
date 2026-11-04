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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Card
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import com.jussicodes.music.constants.lyricSourceKey
import com.jussicodes.music.lyric.model.WordInfo
import com.jussicodes.music.lyric.ui.KaraokeLyricRow
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
    var lyricSource by rememberPreference(lyricSourceKey, "auto")
    var showSourceDialog by remember { mutableStateOf(false) }
    var currentIndex by remember { mutableIntStateOf(-1) }
    var autoScrollEnabled by remember { mutableStateOf(true) }
    var menuExpanded by remember { mutableStateOf(false) }
    val lrcLine = lyric?.lrc?.lyric?.parseLrc()
    val yrcLine = lyric?.yrc?.lyric?.parseYrc()
    val translationLines = lyric?.tlyric?.lyric?.parseLrc().orEmpty()
    val translationMap = remember(translationLines) {
        translationLines
            .filter { it.text.isNotBlank() }
            .associate { it.time to it.text }
    }
    // 智能配对双语歌词（兼顾接口 tlyric 与单文件内连续相同时间戳的原文/译文行）
    val lrcItems = remember(lrcLine, translationLines) {
        if (lrcLine == null) return@remember null
        val items = mutableListOf<Triple<Long, String, String?>>()
        var i = 0
        while (i < lrcLine.size) {
            val cur = lrcLine[i]
            val nxt = lrcLine.getOrNull(i + 1)
            if (nxt != null && kotlin.math.abs(nxt.time - cur.time) <= 300 && cur.text.isNotBlank() && nxt.text.isNotBlank()) {
                items.add(Triple(cur.time, cur.text, nxt.text))
                i += 2
            } else {
                val trans = translationMap[cur.time]
                    ?: translationLines.minByOrNull { kotlin.math.abs(it.time - cur.time) }
                        ?.takeIf { kotlin.math.abs(it.time - cur.time) <= 800 }
                        ?.text
                items.add(Triple(cur.time, cur.text, trans))
                i++
            }
        }
        items
    }
    val hasTranslation = translationLines.any { it.text.isNotBlank() } || (lrcItems?.any { !it.third.isNullOrBlank() } == true)
    val hasWordLyric = !yrcLine.isNullOrEmpty()
    val displayLines = if (showWordLyric && !yrcLine.isNullOrEmpty()) {
        yrcLine.map { it.time to it.text }
    } else {
        lrcItems?.map { it.first to it.second } ?: lrcLine?.map { it.time to it.text }
    }

    LaunchedEffect(currentMediaId, mediaMetadata.title, lyricSource) {
        currentIndex = -1
        autoScrollEnabled = true
        currentMediaId?.toLongOrNull()?.let {
            lyricViewModel.fetchLyric(
                musicId = it,
                title = mediaMetadata.title?.toString(),
                artist = mediaMetadata.artist?.toString(),
                durationMs = mediaController?.duration?.takeIf { d -> d > 0 } ?: 0L,
                source = lyricSource
            )
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
            .then(if (showHeader) Modifier else modifier),
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
                    JussiDropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        JussiMenuItem(
                            text = if (hasTranslation) "歌词翻译" else "歌词翻译（暂无）",
                            icon = Icons.Outlined.Translate,
                            selected = showTranslation,
                            trailing = {
                                Switch(
                                    checked = showTranslation,
                                    onCheckedChange = { showTranslation = it }
                                )
                            },
                            onClick = { showTranslation = !showTranslation }
                        )
                        JussiMenuItem(
                            text = if (hasWordLyric) "逐字歌词" else "逐字歌词（暂无）",
                            icon = Icons.Outlined.Subtitles,
                            selected = showWordLyric,
                            trailing = {
                                Switch(
                                    checked = showWordLyric,
                                    onCheckedChange = { showWordLyric = it }
                                )
                            },
                            onClick = { showWordLyric = !showWordLyric }
                        )
                        JussiMenuItem(
                            text = "逐字歌词来源",
                            icon = Icons.Outlined.Subtitles,
                            trailing = {
                                val sourceLabel = when (lyricSource) {
                                    "ncm" -> "网易云"
                                    "amll" -> "AMLL"
                                    "qq" -> "QQ音乐"
                                    else -> "智能合并"
                                }
                                Text(
                                    text = sourceLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                showSourceDialog = true
                            }
                        )
                    }
                }
            }

            if (showSourceDialog) {
                LyricSourceDialog(
                    currentSource = lyricSource,
                    onDismiss = { showSourceDialog = false },
                    onSourceSelected = { newSource ->
                        lyricSource = newSource
                        currentMediaId?.toLongOrNull()?.let { id ->
                            lyricViewModel.switchLyricSource(
                                source = newSource,
                                musicId = id,
                                title = mediaMetadata.title?.toString(),
                                artist = mediaMetadata.artist?.toString(),
                                durationMs = mediaController?.duration?.takeIf { d -> d > 0 } ?: 0L
                            )
                        }
                        showSourceDialog = false
                    }
                )
            }

            displayLines?.let { lrcLines ->

                // 监听用户真实手指拖拽手势：
                // 使用官方 collectIsDraggedAsState，仅在手指按住屏幕拖动时为 true，
                // 程序化滚动 animateScrollToItem 绝对不会触发，从根源消除误判与竞态。
                val isDragged by listState.interactionSource.collectIsDraggedAsState()
                LaunchedEffect(isDragged) {
                    if (isDragged) {
                        autoScrollEnabled = false
                    } else {
                        // 用户手指松开 3 秒后，自动恢复跟随并平滑滚回当前播放句
                        kotlinx.coroutines.delay(3000L)
                        autoScrollEnabled = true
                    }
                }

                // 播放进度监听：50ms 高频更新活动歌词索引 currentIndex
                LaunchedEffect(lrcLines, mediaController?.isPlaying) {
                    val isPlaying = mediaController?.isPlaying == true
                    if (!isPlaying) {
                        val index = lrcLines.indexOfLast { it.first <= position }
                        if (index != currentIndex) currentIndex = index
                        return@LaunchedEffect
                    }
                    while (true) {
                        val currentPlayTime = mediaController?.currentPosition ?: position
                        val index = lrcLines.indexOfLast { it.first <= currentPlayTime }
                        if (index != currentIndex) {
                            currentIndex = index
                        }
                        kotlinx.coroutines.delay(50L)
                    }
                }

                LaunchedEffect(position) {
                    if (mediaController?.isPlaying != true) {
                        val index = lrcLines.indexOfLast { it.first <= position }
                        if (index != currentIndex) {
                            currentIndex = index
                        }
                    }
                }

                // 当活动歌词索引变化或自动跟随恢复时，平滑定位到当前行（上方保留 1~2 句前文上下文）
                LaunchedEffect(currentIndex, autoScrollEnabled) {
                    if (autoScrollEnabled && currentIndex in 0..lrcLines.lastIndex) {
                        val targetIndex = if (landscape) {
                            maxOf(currentIndex - 1, 0)
                        } else {
                            maxOf(currentIndex - 2, 0)
                        }
                        listState.animateScrollToItem(targetIndex, 0)
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
                        // 紧凑微间距，彻底消除首句上方大块空白
                        top = 8.dp,
                        // 底部留出充足垫底高度，保证末尾几句也能平滑滚到舒适视野，绝不沉底出画
                        bottom = if (landscape) 120.dp else 240.dp,
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
                        val matchedItem = lrcItems?.getOrNull(index)
                        val translation = matchedItem?.third
                            ?: translationMap[lrcLines[index].first]
                            ?: translationLines.minByOrNull { abs(it.time - lrcLines[index].first) }
                                ?.takeIf { abs(it.time - lrcLines[index].first) <= 800 }
                                ?.text

                        val targetAlpha = if (isCurrent) 1f else 0.45f
                        val rowAlpha by animateFloatAsState(
                            targetValue = targetAlpha,
                            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
                            label = "lyric_row_alpha"
                        )

                        if (currentText)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable {
                                        lrcLines[index].first.let {
                                            mediaController?.seekTo(it)
                                            currentIndex = index
                                            autoScrollEnabled = true
                                        }
                                    }
                                    .padding(vertical = if (landscape) 6.dp else 8.dp, horizontal = 4.dp)
                                    .alpha(rowAlpha)
                            ) {
                                val currentYrc = if (showWordLyric && !yrcLine.isNullOrEmpty()) yrcLine.getOrNull(index) else null
                                if (showWordLyric && isCurrent && currentYrc != null && currentYrc.words.isNotEmpty()) {
                                    val wordInfos = remember(currentYrc) {
                                        currentYrc.words.map { word ->
                                            val startOffset = if (word.time >= currentYrc.time) word.time - currentYrc.time else word.time
                                            WordInfo(
                                                text = word.text,
                                                startOffsetMs = startOffset.coerceAtLeast(0),
                                                durationMs = word.duration.coerceAtLeast(0)
                                            )
                                        }
                                    }
                                    KaraokeLyricRow(
                                        text = currentYrc.text,
                                        lineTimeMs = currentYrc.time,
                                        words = wordInfos,
                                        currentPositionProvider = {
                                            mediaController?.currentPosition ?: position
                                        },
                                        activeColor = MaterialTheme.colorScheme.primary,
                                        inactiveColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.65f),
                                        fontSize = if (landscape) 21.sp else 24.sp,
                                        lineHeight = if (landscape) (21 * 1.35f).sp else (24 * 1.35f).sp,
                                        fontWeight = FontWeight.SemiBold,
                                        isPlaying = mediaController?.isPlaying == true,
                                        isActive = true
                                    )
                                } else {
                                    Text(
                                        text = lrcLines[index].second,
                                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = if (landscape) 21.sp else 24.sp,
                                        lineHeight = 1.25.em,
                                    )
                                }
                                if (showTranslation && !translation.isNullOrBlank()) {
                                    Text(
                                        text = translation,
                                        color = if (isCurrent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
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

@Composable
fun LyricSourceDialog(
    currentSource: String,
    onDismiss: () -> Unit,
    onSourceSelected: (String) -> Unit
) {
    val options = listOf(
        Triple("auto", "智能合并", "优先 AMLL 逐字，无则网易云，兜底 QQ 音乐"),
        Triple("ncm", "网易云官方", "网易云官方原版 YRC 逐字歌词"),
        Triple("amll", "AMLL 歌词库", "AMLL 社区高精度 TTML 逐字歌词"),
        Triple("qq", "QQ 音乐", "QQ 音乐曲库匹配 QRC 逐字歌词")
    )
    ResponsiveDialogWindow(onDismissRequest = onDismiss) {
        ScaledDialogContent {
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "逐字歌词来源",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    options.forEach { (value, label, desc) ->
                        val selected = value == currentSource
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { onSourceSelected(value) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (selected) {
                                    Text(
                                        text = "生效中",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
    }
}
