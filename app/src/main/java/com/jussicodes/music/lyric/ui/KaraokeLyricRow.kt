package com.jussicodes.music.lyric.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jussicodes.music.lyric.model.WordInfo

// 逐字词的物理渲染坐标缓存，彻底避免每帧重复调用 getBoundingBox 的 JNI 开销
private class WordLayout(
    val startMs: Long,
    val endMs: Long,
    val left: Float,
    val right: Float,
    val top: Float,
    val bottom: Float,
    val lineIndex: Int
)

private class LineLayout(
    val left: Float,
    val right: Float,
    val top: Float,
    val bottom: Float
)

private class LyricLayoutInfo(
    val wordLayouts: List<WordLayout>,
    val lineLayouts: List<LineLayout>
)

internal data class KaraokeCharacterBox(val lineIndex: Int, val left: Float, val right: Float)

internal data class KaraokeWordSegment(
    val lineIndex: Int,
    val left: Float,
    val right: Float,
    val startMs: Long,
    val endMs: Long
)

// 一个计时词可能横跨自动换行。按视觉行分段，时间按各段可见字形宽度分配；
// 空格由调用处过滤，避免换行空格的坐标被当成上一行的终点。
internal fun splitTimedWordAcrossLines(
    startMs: Long,
    durationMs: Long,
    characters: List<KaraokeCharacterBox>
): List<KaraokeWordSegment> {
    if (characters.isEmpty()) return emptyList()
    val groups = mutableListOf<MutableList<KaraokeCharacterBox>>()
    characters.forEach { character ->
        if (groups.lastOrNull()?.lastOrNull()?.lineIndex == character.lineIndex) {
            groups.last().add(character)
        } else {
            groups.add(mutableListOf(character))
        }
    }
    val widths = groups.map { group -> group.sumOf { (it.right - it.left).coerceAtLeast(1f).toDouble() } }
    val totalWidth = widths.sum()
    var elapsedWidth = 0.0
    return groups.mapIndexed { index, group ->
        val start = startMs + (durationMs * elapsedWidth / totalWidth).toLong()
        elapsedWidth += widths[index]
        val end = if (index == groups.lastIndex) startMs + durationMs
        else startMs + (durationMs * elapsedWidth / totalWidth).toLong()
        KaraokeWordSegment(
            lineIndex = group.first().lineIndex,
            left = group.minOf { it.left },
            right = group.maxOf { it.right },
            startMs = start,
            endMs = end
        )
    }
}

// 扫色分界处的羽化带总宽度：16.dp 约一个字宽，观感最平滑柔和
private val KaraokeEdgeFeather: Dp = 16.dp

// 每一视觉行的已播范围：clipRight 是实色裁剪右缘，featherCenterX 非空表示该行是正在演唱的行
private data class PlayedSpan(
    val clipRight: Float,
    val featherCenterX: Float?
)

// 按播放进度推导每一视觉行的已播右缘与羽化中心。纯计算、无状态读取，可在绘制阶段每帧调用
private fun computePlayedSpans(
    info: LyricLayoutInfo,
    relativeProgress: Long,
    featherHalfPx: Float
): List<PlayedSpan> {
    return info.lineLayouts.mapIndexed { lineIndex, lineLayout ->
        val lastWordOnLine = info.wordLayouts.lastOrNull { it.lineIndex == lineIndex }
        if (lastWordOnLine != null && relativeProgress >= lastWordOnLine.endMs) {
            // 整行已唱完，直接拉满高亮，不需要羽化
            PlayedSpan(clipRight = lineLayout.right, featherCenterX = null)
        } else {
            var maxRight = lineLayout.left
            var hasActiveWord = false
            info.wordLayouts.forEach { word ->
                if (word.lineIndex == lineIndex) {
                    if (relativeProgress >= word.endMs) {
                        maxRight = maxRight.coerceAtLeast(word.right)
                    } else if (relativeProgress in word.startMs..word.endMs) {
                        // 在当前唱到的字词内进行线性像素高亮插值
                        val ratio = if (word.endMs > word.startMs) {
                            (relativeProgress - word.startMs).toFloat() / (word.endMs - word.startMs)
                        } else 1f
                        val edge = word.left + (word.right - word.left) * ratio
                        maxRight = maxRight.coerceAtLeast(edge)
                        hasActiveWord = true
                    }
                }
            }
            if (hasActiveWord) {
                PlayedSpan(
                    clipRight = (maxRight + featherHalfPx).coerceAtMost(lineLayout.right),
                    featherCenterX = maxRight
                )
            } else {
                PlayedSpan(clipRight = maxRight, featherCenterX = null)
            }
        }
    }
}

/**
 * Compose 高性能卡拉OK逐字平滑扫色行
 */
@Composable
fun KaraokeLyricRow(
    text: String,
    lineTimeMs: Long,
    words: List<WordInfo>,
    currentPositionProvider: () -> Long,
    modifier: Modifier = Modifier,
    inactiveColor: Color,
    activeColor: Color,
    fontSize: TextUnit = 24.sp,
    lineHeight: TextUnit = (fontSize.value * 1.35f).sp,
    textAlign: TextAlign = TextAlign.Start,
    advancedEffect: Boolean = true,
    isPlaying: Boolean = true,
    fontWeight: FontWeight = FontWeight.SemiBold,
    isActive: Boolean = true,
    featherWidth: Dp = KaraokeEdgeFeather,
    shadow: Shadow? = null
) {
    var textLayoutResult by remember(text) { mutableStateOf<TextLayoutResult?>(null) }
    val currentPositionProviderState = rememberUpdatedState(currentPositionProvider)
    // 裁剪路径复用：Path 背后是原生对象且带 finalizer，避免每帧新建制造 GC 压力
    val reusableClipPath = remember { Path() }
    // 逐帧心跳：利用 withFrameNanos 跑满屏幕 60Hz/120Hz 刷新率，解决 50ms 轮询导致的 20Hz 扫色卡顿
    var frameTick by remember(text, lineTimeMs) { mutableLongStateOf(0L) }

    LaunchedEffect(isPlaying, text, lineTimeMs, isActive, advancedEffect) {
        if (isPlaying && isActive) {
            while (true) {
                withFrameNanos { frameNano -> frameTick = frameNano }
            }
        }
    }

    // 排版后仅计算并缓存一次每个字词与行的物理渲染坐标
    val lyricLayoutInfo = remember(text, words, textLayoutResult) {
        val layout = textLayoutResult
        if (layout == null || words.isEmpty()) null else {
            val textLength = text.length
            var currentSearchIndex = 0
            val wordRanges = words.map { word ->
                val startIndex = text.indexOf(word.text, currentSearchIndex)
                if (startIndex != -1) {
                    currentSearchIndex = startIndex + word.text.length
                    startIndex until currentSearchIndex
                } else {
                    val start = currentSearchIndex
                    currentSearchIndex = (currentSearchIndex + word.text.length).coerceAtMost(textLength)
                    start until currentSearchIndex
                }
            }

            val lineLayouts = (0 until layout.lineCount).map { lineIndex ->
                LineLayout(
                    left = layout.getLineLeft(lineIndex),
                    right = layout.getLineRight(lineIndex),
                    top = layout.getLineTop(lineIndex),
                    bottom = layout.getLineBottom(lineIndex)
                )
            }

            val wordLayouts = words.flatMapIndexed { i, word ->
                val characterBoxes = wordRanges[i].mapNotNull { offset ->
                    val character = text[offset]
                    if (character.isWhitespace() ||
                        (Character.isLowSurrogate(character) && offset > 0 && Character.isHighSurrogate(text[offset - 1]))) {
                        null
                    } else {
                        val visualLine = layout.getLineForOffset(offset)
                        try {
                            val bounds = layout.getBoundingBox(offset)
                            KaraokeCharacterBox(visualLine, bounds.left, bounds.right)
                        } catch (_: Exception) {
                            KaraokeCharacterBox(
                                visualLine,
                                layout.getHorizontalPosition(offset, true),
                                layout.getHorizontalPosition(offset + 1, true)
                            )
                        }
                    }
                }
                splitTimedWordAcrossLines(word.startOffsetMs, word.durationMs, characterBoxes).map { segment ->
                    val visualLine = lineLayouts[segment.lineIndex]
                    WordLayout(
                        startMs = segment.startMs,
                        endMs = segment.endMs,
                        left = segment.left.coerceIn(visualLine.left, visualLine.right),
                        right = segment.right.coerceIn(visualLine.left, visualLine.right),
                        top = visualLine.top,
                        bottom = visualLine.bottom,
                        lineIndex = segment.lineIndex
                    )
                }
            }

            LyricLayoutInfo(wordLayouts, lineLayouts)
        }
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = if (textAlign == TextAlign.End) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        // 底层未激活歌词
        Text(
            text = text,
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontWeight = fontWeight,
            color = inactiveColor,
            textAlign = textAlign,
            style = LocalTextStyle.current.copy(shadow = shadow),
            onTextLayout = { textLayoutResult = it },
            modifier = Modifier.fillMaxWidth()
        )

        // 顶层平滑扫色高亮歌词（GPU clip 实色 + DstIn 柔边羽化横向渐变）
        Text(
            text = text,
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontWeight = fontWeight,
            color = activeColor,
            textAlign = textAlign,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val ready = lyricLayoutInfo != null && isActive
                    alpha = if (ready) 1f else 0f
                    compositingStrategy = if (ready && advancedEffect && featherWidth > 0.dp) {
                        CompositingStrategy.Offscreen
                    } else {
                        CompositingStrategy.Auto
                    }
                }
                .drawWithContent {
                    val info = lyricLayoutInfo
                    if (info != null && isActive) {
                        @Suppress("UNUSED_VARIABLE")
                        val frameTickRead = frameTick
                        val featherHalfPx = if (advancedEffect) (featherWidth / 2).toPx() else 0f
                        val relativeProgress = currentPositionProviderState.value() - lineTimeMs
                        val spans = computePlayedSpans(info, relativeProgress, featherHalfPx)
                        val path = reusableClipPath
                        path.rewind()
                        info.lineLayouts.forEachIndexed { lineIndex, lineLayout ->
                            val clipRight = spans[lineIndex].clipRight
                            if (clipRight > lineLayout.left) {
                                path.addRect(
                                    Rect(
                                        left = lineLayout.left,
                                        top = lineLayout.top,
                                        right = clipRight.coerceIn(lineLayout.left, lineLayout.right),
                                        bottom = lineLayout.bottom
                                    )
                                )
                            }
                        }
                        clipPath(path) {
                            this@drawWithContent.drawContent()
                            if (featherHalfPx > 0f) {
                                val featherIndex = spans.indexOfFirst { it.featherCenterX != null }
                                if (featherIndex != -1) {
                                    val lineLayout = info.lineLayouts[featherIndex]
                                    val centerX = spans[featherIndex].featherCenterX!!
                                    val left = (centerX - featherHalfPx).coerceAtLeast(lineLayout.left)
                                    val right = (centerX + featherHalfPx).coerceAtMost(lineLayout.right)
                                    if (right > left) {
                                        drawRect(
                                            brush = Brush.horizontalGradient(
                                                0f to Color.Black,
                                                1f to Color.Transparent,
                                                startX = left,
                                                endX = right
                                            ),
                                            topLeft = Offset(lineLayout.left, lineLayout.top),
                                            size = Size(lineLayout.right - lineLayout.left, lineLayout.bottom - lineLayout.top),
                                            blendMode = BlendMode.DstIn
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        drawContent()
                    }
                }
        )
    }
}
