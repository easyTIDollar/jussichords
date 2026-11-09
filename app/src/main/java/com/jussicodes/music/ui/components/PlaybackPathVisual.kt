package com.jussicodes.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jussicodes.music.playback.PlaybackPath
import com.rcmiku.ncmapi.api.player.SongLevel

/**
 * 「可视化播放解析」的公共视觉部件：弹窗与置顶小窗共用同一套分段、配色与行样式。
 *
 * 四段颜色用固定值而不是主题色：主题是从壁纸/封面取色的，primary / secondary / tertiary
 * 经常落在同一个色相上，色带就变成一条纯色块，看不出时间花在哪一段。
 */

/** 真实起播链路的四段：缓存 → 解析 → 播放器 → 校验。 */
internal enum class PathStage(val label: String) {
    CACHE("缓存与直达"),
    RESOLVE("解析音源"),
    PLAYER("播放器就绪"),
    VERIFY("校验与兜底"),
}

/**
 * 固定色板：灰（缓存）· 蓝（解析）· 绿（播放器）· 橙（校验兜底）。
 *
 * 不用主题色是因为主题从壁纸/封面取色，primary / secondary / tertiary 经常落在同一色相上，
 * 色带就变成一整块同色。这里存 ARGB，Compose 面板和原生置顶小窗共用同一份。
 */
internal object PathPalette {
    val cache = 0xFF8A93A5.toInt()
    val resolve = 0xFF3D7DFF.toInt()
    val player = 0xFF17B978.toInt()
    val verify = 0xFFF5A623.toInt()

    fun of(stage: PathStage): Int = when (stage) {
        PathStage.CACHE -> cache
        PathStage.RESOLVE -> resolve
        PathStage.PLAYER -> player
        PathStage.VERIFY -> verify
    }
}

internal val PathStage.color: Color
    get() = Color(PathPalette.of(this))

internal fun stageOf(step: PlaybackPath.Step): PathStage {
    val tags = step.tags
    if (tags.contains("缓存")) return PathStage.CACHE
    if (tags.contains("校验") || tags.contains("预取") || tags.contains("手动")) return PathStage.VERIFY
    if (tags.contains("播放器") || step.name.startsWith("音频数据已就绪") ||
        step.name.startsWith("播放加载失败") || step.name.startsWith("缓冲")
    ) {
        return PathStage.PLAYER
    }
    return PathStage.RESOLVE
}

internal sealed interface RowItem {
    data class Header(val stage: PathStage, val costMs: Long) : RowItem
    data class Step(
        val step: PlaybackPath.Step,
        val stage: PathStage,
        /** 这一步之后到下一步之间的等待时间（最后一步算到面板刷新时刻）。 */
        val gapMs: Long,
        val totalMs: Long,
    ) : RowItem
}

internal class Timeline(
    val rows: List<RowItem>,
    val stageTotals: List<Pair<PathStage, Long>>,
)

/** 把平铺的步骤切成「阶段头 + 步骤」，并算出每一步之后等了多久。 */
internal fun buildTimeline(steps: List<PlaybackPath.Step>, totalMs: Long): Timeline {
    if (steps.isEmpty()) return Timeline(emptyList(), emptyList())
    val totals = linkedMapOf<PathStage, Long>()
    steps.forEachIndexed { index, step ->
        val end = steps.getOrNull(index + 1)?.atMs ?: totalMs.coerceAtLeast(step.atMs)
        totals[stageOf(step)] = (totals[stageOf(step)] ?: 0L) + (end - step.atMs).coerceAtLeast(0)
    }
    val rows = mutableListOf<RowItem>()
    var current: PathStage? = null
    steps.forEachIndexed { index, step ->
        val stage = stageOf(step)
        if (stage != current) {
            rows += RowItem.Header(stage, totals[stage] ?: 0L)
            current = stage
        }
        val end = steps.getOrNull(index + 1)?.atMs ?: totalMs.coerceAtLeast(step.atMs)
        rows += RowItem.Step(
            step = step,
            stage = stage,
            gapMs = (end - step.atMs).coerceAtLeast(0),
            totalMs = totalMs.coerceAtLeast(step.atMs),
        )
    }
    return Timeline(rows, totals.toList())
}

// ---------------------------------------------------------------- 顶部耗时构成条

@Composable
internal fun BreakdownBar(
    totals: List<Pair<PathStage, Long>>,
    compact: Boolean = false,
) {
    val sum = totals.sumOf { it.second }.coerceAtLeast(1L)
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 6.dp else 8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            totals.forEach { (stage, ms) ->
                if (ms > 0) {
                    Box(
                        modifier = Modifier
                            .weight(ms.toFloat() / sum.toFloat())
                            .fillMaxHeight()
                            .background(stage.color),
                    )
                }
            }
        }
        Spacer(Modifier.height(if (compact) 4.dp else 6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            totals.forEach { (stage, ms) ->
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(stage.color),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (compact) {
                            formatSeconds(ms)
                        } else {
                            "${stage.label} ${formatSeconds(ms)}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 阶段头 / 步骤行

@Composable
internal fun StageHeader(row: RowItem.Header) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.stage.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = row.stage.color,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatSeconds(row.costMs),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun TimelineRow(row: RowItem.Step, showGapBar: Boolean = true) {
    val step = row.step
    val dotColor = if (step.status == PlaybackPath.Status.FAIL) {
        MaterialTheme.colorScheme.error
    } else {
        row.stage.color
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        // 左侧竖线 + 状态点：让时间线读起来是「一条链」而不是一列列表
        Box(
            modifier = Modifier
                .width(14.dp)
                .fillMaxHeight(),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp, bottom = 8.dp)
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 7.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = step.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (step.status == PlaybackPath.Status.FAIL) {
                        FontWeight.SemiBold
                    } else {
                        FontWeight.Normal
                    },
                    color = if (step.status == PlaybackPath.Status.FAIL) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "+" + formatSeconds(step.atMs),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(52.dp),
                )
            }
            val detailText = if (step.costMs > 0 && step.detail.isNotBlank()) {
                "${step.detail} · ${step.costMs}ms"
            } else {
                step.detail
            }
            if (detailText.isNotBlank()) {
                Spacer(Modifier.height(1.dp))
                Text(
                    text = detailText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // 间隔条：这一步之后等了多久，宽度按整条链路总时长折算
            if (showGapBar && row.gapMs > 0 && row.totalMs > 0) {
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(
                                (row.gapMs.toFloat() / row.totalMs.toFloat()).coerceIn(0.02f, 1f)
                            )
                            .background(row.stage.color.copy(alpha = 0.55f)),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 文案工具

internal fun formatSeconds(ms: Long): String {
    val seconds = ms / 1000.0
    val text = "%.3f".format(seconds).trimEnd('0').trimEnd('.')
    return if (text.isEmpty()) "0s" else "${text}s"
}

internal fun qualityLabel(value: String): String = when (value) {
    SongLevel.STANDARD.value -> "标准"
    SongLevel.HIGHER.value -> "较高"
    SongLevel.EXHIGH.value -> "极高"
    SongLevel.LOSSLESS.value -> "无损"
    SongLevel.HIRES.value -> "Hi-Res"
    SongLevel.JYEFFECT.value -> "沉浸环绕声"
    SongLevel.SKY.value -> "沉浸环绕声"
    SongLevel.DOLBY.value -> "杜比全景声"
    SongLevel.JYMASTER.value -> "超清母带"
    else -> value.ifBlank { "未知音质" }
}
