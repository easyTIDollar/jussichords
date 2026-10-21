package com.jussicodes.music.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jussicodes.music.utils.CacheManager

/**
 * 「缓存」弹窗：环形占比图 + 分项明细 + 手动清理 + 阈值自动清理开关。
 *
 * @param snapshot 当前缓存统计；null 表示还没测过（进来先自动测一次）。
 * @param measuring 是否正在统计（统计走 IO 线程，期间按钮禁用）。
 * @param clearing 是否正在清理。
 * @param autoCleanEnabled 阈值自动清理开关。
 * @param autoCleanThresholdBytes 当前阈值（字节）；0 表示关闭。
 * @param onMeasure 重新统计。
 * @param onClear 手动清理全部缓存。
 * @param onAutoCleanEnabledChange 开关变化。
 * @param onThresholdSelected 选择新阈值。
 * @param onDismiss 关闭。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CacheManagerDialog(
    snapshot: CacheManager.Snapshot?,
    measuring: Boolean,
    clearing: Boolean,
    autoCleanEnabled: Boolean,
    autoCleanThresholdBytes: Long,
    onMeasure: () -> Unit,
    onClear: () -> Unit,
    onAutoCleanEnabledChange: (Boolean) -> Unit,
    onThresholdSelected: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    // 阈值档位（字节）：512MB / 1GB / 2GB。
    val thresholdOptions = remember {
        listOf(
            512L * 1024 * 1024,
            1024L * 1024 * 1024,
            2L * 1024 * 1024 * 1024,
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("缓存", fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "缓存是已下载/已解码的音频与图片，占用手机存储；清空后不影响账号与设置，" +
                        "下次播放或加载封面会重新下载。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (snapshot == null) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                    )
                } else {
                    CacheDonutChart(
                        buckets = listOf(snapshot.audio, snapshot.image, snapshot.other),
                        totalBytes = snapshot.totalBytes,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        CacheBucketRow(snapshot.audio)
                        CacheBucketRow(snapshot.image)
                        CacheBucketRow(snapshot.other)
                    }
                }

                Text(
                    text = "自动清理",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (autoCleanEnabled) "已开启" else "已关闭",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "缓存总大小超过所选阈值时自动清理（启动应用时检查）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = autoCleanEnabled,
                        onCheckedChange = onAutoCleanEnabledChange,
                    )
                }
                if (autoCleanEnabled) {
                    // 三档阈值等宽占满一行：FlowRow 保留（窄屏兜底换行），
                    // 每个 chip 用 weight(1f) 均分宽度，右侧不留空。
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        thresholdOptions.forEach { bytes ->
                            FilterChip(
                                selected = autoCleanThresholdBytes == bytes,
                                onClick = { onThresholdSelected(bytes) },
                                label = {
                                    Text(
                                        text = formatFileSize(bytes),
                                        maxLines = 1,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                Text(
                    text = "开启自动清理后，缓存总大小超过所选阈值即自动清理（启动应用时检查）；" +
                        "「其它缓存」含更新安装包与临时文件，可随时清理。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onClear,
                enabled = !clearing && snapshot != null && snapshot.totalBytes > 0L,
            ) {
                Text(if (clearing) "清理中…" else "立即清理")
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = onMeasure,
                    enabled = !measuring,
                ) {
                    Text(if (measuring) "统计中…" else "重新统计")
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        },
    )
}

/** 环形占比图：音频 / 图片 / 其它三段，中心写总大小。 */
@Composable
private fun CacheDonutChart(
    buckets: List<CacheManager.Bucket>,
    totalBytes: Long,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val segments = remember(buckets) {
        buckets.mapIndexed { index, bucket ->
            bucket to when (index) {
                0 -> colors.primary
                1 -> colors.tertiary
                else -> colors.secondary
            }
        }
    }
    // 打开弹窗时画一段 0→实际占比的动画，让"占比图"有反馈。
    var animationPlayed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { animationPlayed = true }
    val progress by animateFloatAsState(
        targetValue = if (animationPlayed) 1f else 0f,
        animationSpec = tween(durationMillis = 600),
        label = "cacheDonut",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(160.dp)) {
            val strokeWidth = 26.dp.toPx()
            val inset = strokeWidth / 2
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(inset, inset)

            if (totalBytes <= 0L) {
                // 空缓存：只画一圈底环
                drawArc(
                    color = colors.surfaceVariant,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
                return@Canvas
            }

            var startAngle = -90f
            segments.forEach { (bucket, color) ->
                if (bucket.bytes <= 0L) return@forEach
                val sweep = (bucket.bytes.toFloat() / totalBytes) * 360f * progress
                drawArc(
                    color = color,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                )
                startAngle += (bucket.bytes.toFloat() / totalBytes) * 360f * progress
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = formatFileSize(totalBytes),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "当前占用",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 分项一行：色点 + 名称 + 占用大小。 */
@Composable
private fun CacheBucketRow(bucket: CacheManager.Bucket) {
    val colors = MaterialTheme.colorScheme
    val dotColor = when (bucket.name) {
        "音频缓存" -> colors.primary
        "图片缓存" -> colors.tertiary
        else -> colors.secondary
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(10.dp)) {
            drawCircle(color = dotColor)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = bucket.name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(72.dp),
        )
        Text(
            text = formatFileSize(bucket.bytes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 供设置行副标题用的简短文案。 */
internal fun cacheSubtitle(
    enabled: Boolean,
    thresholdBytes: Long,
    currentBytes: Long,
): String = if (enabled) {
    "自动清理 · 阈值 ${formatFileSize(thresholdBytes)} · 当前 ${formatFileSize(currentBytes)}"
} else {
    "当前占用 ${formatFileSize(currentBytes)} · 仅手动清理"
}
