package com.jussicodes.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jussicodes.music.playback.PlaybackPath
import com.rcmiku.ncmapi.api.player.SongLevel
import kotlinx.coroutines.launch

/**
 * 「可视化播放解析」面板：解析层埋点（[PlaybackPath]）的回放，外加一个「重新解析当前音乐」。
 *
 * 设计上刻意不做任何推断：面板上出现的每一行都是一次真实打点，没有打点就显示空态，
 * 不让「看起来像解析了」的假步骤混进排查现场。
 *
 * 视觉上按真实起播链路分段（缓存与直达 → 解析音源 → 播放器就绪 → 校验兜底）。
 * 顶部一条构成条回答「时间花在哪一段」，每行下面一条细条回答「这一步等了多少」，
 * 数字统一右对齐等宽，扫一眼就能定位慢点。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackPathSheet(
    openBottomSheet: Boolean,
    onDismiss: () -> Unit,
    songTitle: String? = null,
    onReResolve: () -> Unit = {},
) {
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val snapshot by PlaybackPath.state.collectAsState()
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(openBottomSheet) {
        if (openBottomSheet) bottomSheetState.show() else bottomSheetState.hide()
    }

    if (!openBottomSheet) return

    val timeline = remember(snapshot.steps, snapshot.totalMs) {
        buildTimeline(snapshot.steps, snapshot.totalMs)
    }

    ResponsiveModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = bottomSheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 660.dp)
                .padding(horizontal = 18.dp)
                .padding(bottom = 18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "可视化播放解析",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = songTitle ?: snapshot.songId.ifBlank { "当前歌曲" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (snapshot.steps.isNotEmpty() && !snapshot.finished) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    text = formatSeconds(snapshot.totalMs),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = snapshot.route.ifBlank { "网易云 · 等待解析" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TagChip(qualityLabel(snapshot.quality))
            }

            Spacer(Modifier.height(12.dp))

            if (snapshot.steps.isEmpty()) {
                EmptyState()
            } else {
                BreakdownBar(timeline.stageTotals, snapshot.totalMs)
                Spacer(Modifier.height(10.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    items(timeline.rows) { row ->
                        when (row) {
                            is RowItem.Header -> StageHeader(row)
                            is RowItem.Step -> TimelineRow(row)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable {
                        onReResolve()
                        scope.launch { bottomSheetState.hide() }
                        onDismiss()
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "重新解析当前音乐",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }

            if (snapshot.steps.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "复制整条链路",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            clipboard.setText(
                                AnnotatedString(
                                    buildString {
                                        appendLine("可视化播放解析 · ${songTitle ?: snapshot.songId}")
                                        appendLine(
                                            "${snapshot.route} · ${qualityLabel(snapshot.quality)} · " +
                                                "总耗时 ${formatSeconds(snapshot.totalMs)}"
                                        )
                                        snapshot.steps.forEach {
                                            appendLine(
                                                "+${formatSeconds(it.atMs)}  ${it.name}" +
                                                    (if (it.detail.isNotBlank()) " — ${it.detail}" else "")
                                            )
                                        }
                                    }
                                )
                            )
                        }
                        .padding(vertical = 8.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- 阶段与行模型

/** 真实起播链路的四段：缓存 → 解析 → 播放器 → 校验。 */
private enum class Stage(val label: String) {
    CACHE("缓存与直达"),
    RESOLVE("解析音源"),
    PLAYER("播放器就绪"),
    VERIFY("校验与兜底"),
}

private fun stageOf(step: PlaybackPath.Step): Stage {
    val tags = step.tags
    if (tags.contains("缓存")) return Stage.CACHE
    if (tags.contains("校验") || tags.contains("预取") || tags.contains("手动")) return Stage.VERIFY
    if (tags.contains("播放器") || step.name.startsWith("音频数据已就绪") ||
        step.name.startsWith("播放加载失败")
    ) {
        return Stage.PLAYER
    }
    return Stage.RESOLVE
}

private sealed interface RowItem {
    data class Header(val stage: Stage, val costMs: Long) : RowItem
    data class Step(
        val step: PlaybackPath.Step,
        val stage: Stage,
        /** 这一步之后到下一步之间的等待时间（最后一步算到面板刷新时刻）。 */
        val gapMs: Long,
        val totalMs: Long,
    ) : RowItem
}

private class Timeline(
    val rows: List<RowItem>,
    val stageTotals: List<Pair<Stage, Long>>,
)

/** 把平铺的步骤切成「阶段头 + 步骤」，并算出每一步之后等了多久。 */
private fun buildTimeline(steps: List<PlaybackPath.Step>, totalMs: Long): Timeline {
    if (steps.isEmpty()) return Timeline(emptyList(), emptyList())
    val totals = linkedMapOf<Stage, Long>()
    steps.forEachIndexed { index, step ->
        val end = steps.getOrNull(index + 1)?.atMs ?: totalMs.coerceAtLeast(step.atMs)
        totals[stageOf(step)] = (totals[stageOf(step)] ?: 0L) + (end - step.atMs).coerceAtLeast(0)
    }
    val rows = mutableListOf<RowItem>()
    var current: Stage? = null
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

@Composable
private fun stageColor(stage: Stage): Color = when (stage) {
    Stage.CACHE -> MaterialTheme.colorScheme.outline
    Stage.RESOLVE -> MaterialTheme.colorScheme.primary
    Stage.PLAYER -> MaterialTheme.colorScheme.tertiary
    Stage.VERIFY -> MaterialTheme.colorScheme.secondary
}

// ---------------------------------------------------------------- 顶部耗时构成条

@Composable
private fun BreakdownBar(totals: List<Pair<Stage, Long>>, totalMs: Long) {
    val sum = totals.sumOf { it.second }.coerceAtLeast(1L)
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            totals.forEach { (stage, ms) ->
                if (ms > 0) {
                    Box(
                        modifier = Modifier
                            .weight(ms.toFloat() / sum.toFloat())
                            .fillMaxHeight()
                            .background(stageColor(stage).copy(alpha = 0.85f)),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            totals.forEach { (stage, ms) ->
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(stageColor(stage)),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${stage.label} ${formatSeconds(ms)}",
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
private fun StageHeader(row: RowItem.Header) {
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
            color = stageColor(row.stage),
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
private fun TimelineRow(row: RowItem.Step) {
    val step = row.step
    val dotColor: Color = when (step.status) {
        PlaybackPath.Status.OK -> MaterialTheme.colorScheme.primary
        PlaybackPath.Status.FAIL -> MaterialTheme.colorScheme.error
        PlaybackPath.Status.RUNNING -> MaterialTheme.colorScheme.tertiary
        PlaybackPath.Status.INFO -> MaterialTheme.colorScheme.outline
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
            if (step.detail.isNotBlank()) {
                Spacer(Modifier.height(1.dp))
                Text(
                    text = step.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // 间隔条：这一步之后等了多久，宽度按整条链路总时长折算
            if (row.gapMs > 0 && row.totalMs > 0) {
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
                            .background(stageColor(row.stage).copy(alpha = 0.55f)),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 空态与小组件

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 20.dp),
    ) {
        Column {
            Text(
                text = "0 解析直达",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "这一轮播放没有向音源要地址，所以没有解析记录。通常是：",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            listOf(
                "整首已在分片缓存里，播放器直接读盘",
                "直链缓存还在有效期（内存 30 分钟 / 磁盘 15 分钟）",
                "已下载或外部歌单的本地文件",
            ).forEach {
                Text(
                    text = "· $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "点下面按钮可以丢掉这两层缓存，强制完整跑一遍。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun TagChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

private fun formatSeconds(ms: Long): String {
    val seconds = ms / 1000.0
    val text = "%.3f".format(seconds).trimEnd('0').trimEnd('.')
    return if (text.isEmpty()) "0s" else "${text}s"
}

private fun qualityLabel(value: String): String = when (value) {
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
