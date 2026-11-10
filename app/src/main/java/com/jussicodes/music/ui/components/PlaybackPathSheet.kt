package com.jussicodes.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jussicodes.music.playback.PlaybackPath
import kotlinx.coroutines.launch

/**
 * 「可视化播放解析」面板：解析层埋点（[PlaybackPath]）的回放，外加一个「重新解析当前音乐」。
 *
 * 设计上刻意不做任何推断：面板上出现的每一行都是一次真实打点，没有打点就显示空态，
 * 不让「看起来像解析了」的假步骤混进排查现场。
 *
 * 视觉部件（分段、色带、时间线行）在 [PlaybackPathVisual]，置顶小窗共用同一套。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackPathSheet(
    openBottomSheet: Boolean,
    onDismiss: () -> Unit,
    songTitle: String? = null,
    onReResolve: () -> Unit = {},
    onPinWindow: () -> Unit = {},
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
                    text = snapshot.route.ifBlank { "等待播放" },
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
                BreakdownBar(timeline.stageTotals)
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

            Row(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .weight(1f)
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
                Spacer(Modifier.width(10.dp))
                // 置顶小窗：悬浮在系统层，边听边看，不用回到播放页
                Box(
                    modifier = Modifier
                        .width(96.dp)
                        .height(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .clickable {
                            onPinWindow()
                            scope.launch { bottomSheetState.hide() }
                            onDismiss()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "置顶小窗",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
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
                text = "暂无链路记录",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "这首歌还没有产生任何解析或读取打点。刚切过来时等一两秒，这里会按真实顺序刷出来；" +
                    "如果一直空着，说明播放器没经过解析层，音频是直接从本地读的，属于下面三种情况：",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            listOf(
                "分片缓存命中：整首音频已在本地，不解析、不下载（面板会补一行「分片缓存命中」）",
                "本地文件：已下载或外部歌单导入的音频，直接读盘",
                "直链还在有效期：面板会出现「直链缓存命中」，解析 0 次，但音频仍从网络边下边播",
            ).forEach {
                Text(
                    text = "· $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "想强制完整跑一遍：点下方「重新解析当前音乐」（会清掉这首歌的直链缓存与音频分片缓存）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
internal fun TagChip(text: String) {
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
