package com.jussicodes.music.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jussicodes.music.download.AudioQualityProbeItem
import com.jussicodes.music.download.AudioQualityProber
import com.jussicodes.music.download.ProbeItemStatus
import com.rcmiku.ncmapi.api.player.SongLevel

enum class ProbeDialogMode {
    UPGRADE,     // 场景一：已下载单曲音质体检与升级
    BENCHMARK,   // 场景二：下载偏好设置中的音源能力体检
    RETRY_FAILED // 场景三：下载失败任务换档重试
}

@Composable
fun AudioQualityProbeDialog(
    mode: ProbeDialogMode,
    songId: Long,
    title: String,
    subtitle: String,
    durationMs: Long = 0L,
    currentQualityKey: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (selectedLevel: SongLevel, preloadUrl: String?, preloadSize: Long?) -> Unit
) {
    var probeResults by remember { mutableStateOf<Map<SongLevel, AudioQualityProbeItem>>(emptyMap()) }
    var selectedLevel by remember { mutableStateOf<SongLevel?>(null) }
    var isProbingComplete by remember { mutableStateOf(false) }

    var resolvedTitle by remember { mutableStateOf(title) }
    var resolvedSubtitle by remember { mutableStateOf(subtitle) }
    var resolvedDuration by remember { mutableStateOf(durationMs) }

    LaunchedEffect(songId) {
        val meta = AudioQualityProber.fetchSongMeta(songId)
        if (meta != null) {
            if (mode == ProbeDialogMode.BENCHMARK) {
                resolvedTitle = meta.name
                resolvedSubtitle = "${meta.artists} · ID: $songId"
            }
            if (resolvedDuration <= 0) resolvedDuration = meta.durationMs
        }

        // 统一所有模式（体检、升级、重试），全量交由当前音源进行真实探测，不再因官方元数据误杀第三方源
        AudioQualityProber.probeAll(
            songId = songId,
            durationMs = resolvedDuration,
            availableLevels = null
        ) { updatedItem ->
            probeResults = probeResults + (updatedItem.level to updatedItem)
            // 自动选中最高可用的真实匹配档位
            if (selectedLevel == null && (updatedItem.status == ProbeItemStatus.AVAILABLE || updatedItem.status == ProbeItemStatus.DOWNGRADED)) {
                selectedLevel = updatedItem.level
            }
        }
        isProbingComplete = true
    }

    ResponsiveDialogWindow(onDismissRequest = onDismiss) {
        ScaledDialogContent {
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    // 1. 头部标题栏
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val headerIcon = when (mode) {
                            ProbeDialogMode.UPGRADE -> Icons.Outlined.HighQuality
                            ProbeDialogMode.BENCHMARK -> Icons.Outlined.Speed
                            ProbeDialogMode.RETRY_FAILED -> Icons.Outlined.Refresh
                        }
                        val headerTitle = when (mode) {
                            ProbeDialogMode.UPGRADE -> "音质体检与升级"
                            ProbeDialogMode.BENCHMARK -> "当前音源下载能力体检"
                            ProbeDialogMode.RETRY_FAILED -> "选择其他可用音质"
                        }
                        Icon(
                            imageVector = headerIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = headerTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "$resolvedTitle · $resolvedSubtitle",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // 2. 当前状态提示条
                    if (!currentQualityKey.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = when (mode) {
                                        ProbeDialogMode.UPGRADE -> "当前本地格式: $currentQualityKey"
                                        ProbeDialogMode.RETRY_FAILED -> "原请求档位: $currentQualityKey"
                                        else -> "当前配置音质: $currentQualityKey"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(12.dp))

                    // 3. 档位探测列表
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectableGroup(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AudioQualityProber.PROBE_LEVELS.forEach { (level, pair) ->
                            val (levelLabel, levelDesc) = pair
                            val probeItem = probeResults[level]
                            val status = probeItem?.status ?: ProbeItemStatus.PENDING
                            val isSelectable = (status == ProbeItemStatus.AVAILABLE || status == ProbeItemStatus.DOWNGRADED)
                            val isSelected = (selectedLevel == level)

                            val containerColor = when {
                                isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                status == ProbeItemStatus.NOT_IN_LIBRARY -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            }
                            val borderStroke = if (isSelected) {
                                BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                            } else {
                                null
                            }

                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = containerColor),
                                border = borderStroke,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = isSelectable) {
                                        selectedLevel = level
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = if (isSelectable) { { selectedLevel = level } } else null,
                                        enabled = isSelectable
                                    )
                                    Spacer(Modifier.width(8.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = levelLabel,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            if (status == ProbeItemStatus.DOWNGRADED) {
                                                Spacer(Modifier.width(6.dp))
                                                Surface(
                                                    color = MaterialTheme.colorScheme.errorContainer,
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "实际降档",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }

                                        val descText = when (status) {
                                            ProbeItemStatus.PENDING, ProbeItemStatus.PROBING -> "检测直链中…"
                                            ProbeItemStatus.AVAILABLE -> levelDesc
                                            ProbeItemStatus.DOWNGRADED -> {
                                                val act = when (probeItem?.actualLevel) {
                                                    SongLevel.STANDARD -> "标准 128k"
                                                    SongLevel.HIGHER -> "较高 192k"
                                                    SongLevel.EXHIGH -> "极高 320k"
                                                    SongLevel.LOSSLESS -> "无损 FLAC"
                                                    SongLevel.HIRES -> "Hi-Res"
                                                    SongLevel.JYMASTER -> "超清母带"
                                                    SongLevel.DOLBY -> "杜比全景声"
                                                    SongLevel.SKY -> "沉浸环绕声"
                                                    SongLevel.JYEFFECT -> "高清臻音"
                                                    null -> "低档位"
                                                }
                                                "请求 $levelLabel · 实际降为 $act"
                                            }
                                            ProbeItemStatus.ENCRYPTED_SHELL -> "私有加密格式，无法保存"
                                            ProbeItemStatus.UNAVAILABLE -> "当前音源未提供此档位"
                                            ProbeItemStatus.NOT_IN_LIBRARY -> "当前音源未提供此档位"
                                        }
                                        Text(
                                            text = descText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = when (status) {
                                                ProbeItemStatus.DOWNGRADED -> MaterialTheme.colorScheme.error
                                                ProbeItemStatus.ENCRYPTED_SHELL -> MaterialTheme.colorScheme.error
                                                ProbeItemStatus.UNAVAILABLE, ProbeItemStatus.NOT_IN_LIBRARY -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Spacer(Modifier.width(8.dp))

                                    // 右侧状态指示与大小
                                    when (status) {
                                        ProbeItemStatus.PENDING, ProbeItemStatus.PROBING -> {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        ProbeItemStatus.AVAILABLE -> {
                                            Text(
                                                text = AudioQualityProber.formatFileSize(probeItem?.fileSize ?: 0L),
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        ProbeItemStatus.DOWNGRADED -> {
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(
                                                    text = AudioQualityProber.formatFileSize(probeItem?.fileSize ?: 0L),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.error,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        ProbeItemStatus.ENCRYPTED_SHELL -> {
                                            Text(
                                                text = "不可用",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                        ProbeItemStatus.UNAVAILABLE -> {
                                            Text(
                                                text = "未提供",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                            )
                                        }
                                        ProbeItemStatus.NOT_IN_LIBRARY -> {
                                            Text(
                                                text = "未收录",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 4. 全档位均未提供时的提示
                    if (isProbingComplete && probeResults.values.none { it.status == ProbeItemStatus.AVAILABLE || it.status == ProbeItemStatus.DOWNGRADED }) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "当前音源暂无任何可用的下载音质",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // 5. 底部确认按钮栏
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("取消")
                        }
                        Spacer(Modifier.width(8.dp))

                        val buttonText = when (mode) {
                            ProbeDialogMode.UPGRADE -> "升级并重新下载"
                            ProbeDialogMode.BENCHMARK -> "设为默认音质"
                            ProbeDialogMode.RETRY_FAILED -> "使用此音质重试"
                        }
                        val chosenItem = selectedLevel?.let { probeResults[it] }
                        val canConfirm = (selectedLevel != null && chosenItem != null &&
                                (chosenItem.status == ProbeItemStatus.AVAILABLE || chosenItem.status == ProbeItemStatus.DOWNGRADED))

                        Button(
                            onClick = {
                                if (canConfirm && selectedLevel != null) {
                                    onConfirm(
                                        selectedLevel!!,
                                        chosenItem?.resolvedUrl,
                                        chosenItem?.fileSize
                                    )
                                    onDismiss()
                                }
                            },
                            enabled = canConfirm
                        ) {
                            Text(buttonText)
                        }
                    }
                }
            }
        }
    }
}
