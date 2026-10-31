package com.jussicodes.music.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

data class DownloadQualityOption(
    val key: String,
    val label: String,
    val description: String
)

val DOWNLOAD_QUALITY_OPTIONS = listOf(
    DownloadQualityOption("standard", "标准", "128 kbps · MP3 · 免费畅听"),
    DownloadQualityOption("exhigh", "极高", "320 kbps · MP3 · 高保真"),
    DownloadQualityOption("lossless", "无损", "16bit / 44.1kHz · FLAC · CD音质"),
    DownloadQualityOption("hires", "Hi-Res", "24bit / ≥48kHz · FLAC · 母带解析"),
    DownloadQualityOption("jymaster", "超清母带", "Master 顶尖母带音频 (需源支持)"),
    DownloadQualityOption("dolby", "杜比全景声", "Dolby Atmos 空间音频 (需源支持)"),
    DownloadQualityOption("sky", "沉浸环绕声", "沉浸式环绕全景声场 (需源支持)"),
    DownloadQualityOption("jyeffect", "高清臻音", "空间高清增强音效 (需源支持)")
)

@Composable
fun DownloadQualityDialog(
    currentKey: String,
    title: String = "选择下载音质",
    includeAskOption: Boolean = false,
    onDismiss: () -> Unit,
    onQualitySelected: (String) -> Unit
) {
    val options = if (includeAskOption) {
        listOf(DownloadQualityOption("ask", "每次下载时询问", "下载前弹出音质选择面板")) + DOWNLOAD_QUALITY_OPTIONS
    } else {
        DOWNLOAD_QUALITY_OPTIONS
    }

    ResponsiveDialogWindow(onDismissRequest = onDismiss) {
        ScaledDialogContent {
            Card(
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    Text(
                        text = "不保证所选LX音源下载高音质音频可用性",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(modifier = Modifier.selectableGroup()) {
                        options.forEach { opt ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = (opt.key == currentKey),
                                        onClick = { onQualitySelected(opt.key) },
                                        role = Role.RadioButton
                                    )
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = (opt.key == currentKey),
                                    onClick = null
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = opt.label,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = opt.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
