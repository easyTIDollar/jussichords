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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jussicodes.music.playback.PlaybackPath
import com.rcmiku.ncmapi.api.player.SongLevel
import kotlinx.coroutines.launch

/**
 * 「播放解析链路」面板：解析层埋点（[PlaybackPath]）的回放，外加一个「重新解析当前音乐」。
 *
 * 设计上刻意不做任何推断：面板上出现的每一行都是一次真实打点，没有打点就显示空态，
 * 不让「看起来像解析了」的假步骤混进排查现场。
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

    LaunchedEffect(openBottomSheet) {
        if (openBottomSheet) bottomSheetState.show() else bottomSheetState.hide()
    }

    if (!openBottomSheet) return

    ResponsiveModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = bottomSheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .padding(horizontal = 18.dp)
                .padding(bottom = 18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "播放解析链路",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = songTitle ?: snapshot.songId.ifBlank { "当前歌曲" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                if (snapshot.steps.isNotEmpty() && !snapshot.finished) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
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

            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = snapshot.route.ifBlank { "网易云 · 等待解析" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TagChip(qualityLabel(snapshot.quality))
            }

            Spacer(Modifier.height(12.dp))

            if (snapshot.steps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 16.dp, vertical = 22.dp),
                ) {
                    Column {
                        Text(
                            text = "还没有解析记录",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "本次播放可能直接命中了直链缓存/本地文件，没走网络解析。点下面的按钮可以完整跑一遍当前歌曲的解析链路。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(snapshot.steps) { step ->
                        PathStepRow(step)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

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
        }
    }
}

@Composable
private fun PathStepRow(step: PlaybackPath.Step) {
    val dotColor: Color = when (step.status) {
        PlaybackPath.Status.OK -> MaterialTheme.colorScheme.primary
        PlaybackPath.Status.FAIL -> MaterialTheme.colorScheme.error
        PlaybackPath.Status.RUNNING -> MaterialTheme.colorScheme.tertiary
        PlaybackPath.Status.INFO -> MaterialTheme.colorScheme.outline
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        // 左侧状态点
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(Modifier.width(10.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = step.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (step.status == PlaybackPath.Status.FAIL) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (step.status == PlaybackPath.Status.FAIL) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "+" + formatSeconds(step.atMs),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (step.detail.isNotBlank()) {
                Text(
                    text = if (step.costMs > 0) "${step.detail} · 本步 ${step.costMs}ms" else step.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (step.tags.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    step.tags.filter { it.isNotBlank() }.forEach { TagChip(it) }
                }
            }
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
