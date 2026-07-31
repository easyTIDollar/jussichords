package com.jussicodes.music.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jussicodes.music.utils.AppUpdateManager
import com.jussicodes.music.utils.GitHubDownloadSourceStatus
import com.jussicodes.music.utils.UpdateInfo
import kotlin.math.ln
import kotlin.math.pow

@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    isDownloading: Boolean,
    downloadProgress: Float?,
    progressText: String?,
    selectedSourceId: String = AppUpdateManager.downloadSources.first().id,
    sourceStatuses: List<GitHubDownloadSourceStatus> = emptyList(),
    onSourceSelected: (String) -> Unit = {},
    onDismiss: () -> Unit,
    onIgnoreVersion: () -> Unit,
    onDownload: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            if (!isDownloading) onDismiss()
        },
        title = { Text(text = "发现新版本 ${updateInfo.versionName}") },
        text = {
            Column {
                Text(
                    text = buildString {
                        appendLine(updateInfo.releaseName)
                        appendLine("安装包大小: ${formatFileSize(updateInfo.apkSize)}")
                        val notes = updateInfo.body.trim()
                        if (notes.isNotEmpty()) {
                            appendLine()
                            append(notes)
                        }
                    }.trim()
                )
                Text(
                    text = "下载源",
                    modifier = Modifier.padding(top = 12.dp)
                )
                AppUpdateManager.downloadSources.forEach { source ->
                    val status = sourceStatuses.firstOrNull { it.source.id == source.id }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        RadioButton(
                            selected = source.id == selectedSourceId,
                            onClick = { if (!isDownloading) onSourceSelected(source.id) },
                            enabled = !isDownloading
                        )
                        Text(
                            text = buildString {
                                append(source.name)
                                status?.let {
                                    append(" · ")
                                    append(if (it.available) "${it.latencyMs ?: 0} ms" else it.message)
                                }
                            },
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
                if (isDownloading && downloadProgress != null) {
                    LinearProgressIndicator(
                        progress = { downloadProgress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                    )
                    progressText?.let {
                        Text(
                            text = it,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDownload,
                enabled = !isDownloading
            ) {
                Text(if (isDownloading) "下载中" else "下载")
            }
        },
        dismissButton = {
            if (!isDownloading) {
                Row {
                    TextButton(onClick = onIgnoreVersion) {
                        Text("忽略本版本")
                    }
                    TextButton(onClick = onDismiss) {
                        Text("稍后再说")
                    }
                }
            }
        }
    )
}

fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB")
    val digitGroup = (ln(size.toDouble()) / ln(1024.0)).toInt().coerceIn(0, units.lastIndex)
    val scaled = size / 1024.0.pow(digitGroup.toDouble())
    return if (digitGroup == 0) {
        "${scaled.toInt()} ${units[digitGroup]}"
    } else {
        String.format("%.1f %s", scaled, units[digitGroup])
    }
}
