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
        currentMediaId?.toLongOrNull()?.let {
            lyricViewModel.fetchLyric(it)
        }
    }

    BackHandler {
        onBackPressed()
    }

    KeepScreenOn()

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxHeight(),
    ) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
        ) {
            Row(
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

                LaunchedEffect(listState.isScrollInProgress) {
                    autoScrollEnabled = !listState.isScrollInProgress
                }

                LaunchedEffect(position) {
                    val index = lrcLines.indexOfLast { it.first <= position }
                    if (index != currentIndex) {
                        currentIndex = index
                        if (autoScrollEnabled) {
                            coroutineScope.launch {
                                if (index > 0) {
                                    val targetIndex = maxOf(currentIndex - 2, 0)
                                    listState.animateScrollToItem(targetIndex)
                                }
                            }
                        }
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(
                        count = lrcLines.size,
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
                                    .padding(vertical = 8.dp, horizontal = 4.dp)
                                    .alpha(if (isCurrent) 1f else 0.5f)
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
                                                    SpanStyle(color = MaterialTheme.colorScheme.primary)
                                                } else {
                                                    SpanStyle(color = MaterialTheme.colorScheme.secondary)
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
                                    fontSize = 24.sp,
                                    lineHeight = 1.2.em,
                                )
                                if (showTranslation && !translation.isNullOrBlank()) {
                                    Text(
                                        text = translation,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 16.sp,
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
                        Spacer(Modifier.padding(vertical = 24.dp))
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
