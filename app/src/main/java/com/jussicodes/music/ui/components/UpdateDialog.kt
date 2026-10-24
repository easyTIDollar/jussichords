package com.jussicodes.music.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.jussicodes.music.utils.UpdateSourceStatus
import com.jussicodes.music.utils.UpdateInfo
import com.jussicodes.music.utils.downloadSources
import kotlin.math.ln
import kotlin.math.pow

/**
 * 更新说明区域的最大高度（占屏幕高度的比例）。
 *
 * 更新说明来自 release body，长度不可控（canary 说明是按提交差异自动生成的，
 * 一次发版可能有十几条）。不限制高度的话，AlertDialog 的正文区会把整个弹窗
 * 顶出屏幕 —— Material3 的 AlertDialog 正文本身**不带滚动**，超出的部分直接被裁掉，
 * 用户既看不到尾部内容也点不到下面的按钮。
 *
 * 所以把说明放进一个「高度封顶 + 自身可上下滚动」的容器里：
 * 内容少时正常显示，内容多时在说明内部滚动，弹窗高度始终受控。
 */
private const val NotesMaxHeightRatio = 0.32f
private const val NotesMaxHeightMinDp = 96f
private const val NotesMaxHeightMaxDp = 240f

@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    isDownloading: Boolean,
    downloadProgress: Float?,
    progressText: String?,
    selectedSourceId: String = downloadSources.first().id,
    sourceStatuses: List<UpdateSourceStatus> = emptyList(),
    isMeasuringSources: Boolean = false,
    onMeasureSources: () -> Unit = {},
    onSourceSelected: (String) -> Unit = {},
    onDismiss: () -> Unit,
    onIgnoreVersion: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit = {},
) {
    val notes = updateInfo.body.trim()

    ScaledDialogContent {
    // 说明区高度上限：按屏幕高度比例算，并夹在合理区间内。
    //
    // 界面缩放：弹窗里的 dp 会乘 ScaledDialogDensity.scale 才是真实像素，
    // 而 LocalConfiguration 的 screenHeightDp 是**未缩放**的真实 dp。
    // 所以先按真实 dp 算出高度预算，再除以 scale 转成弹窗内的 dp，
    // 这样缩放一开也不会把说明区撑出屏幕。
    val scale = ScaledDialogDensity.scale.coerceAtLeast(0.01f)
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    // 注意用 coerceAtLeast/coerceAtMost 而不是 coerceIn：后者在值越界时直接抛异常，
    // 屏幕一高（比例算出来 > 上限）就会让弹窗崩掉。
    val realDpBudget = (screenHeightDp * NotesMaxHeightRatio)
        .coerceAtLeast(NotesMaxHeightMinDp)
        .coerceAtMost(NotesMaxHeightMaxDp)
    val notesMaxHeight = (realDpBudget / scale).dp

    ResponsiveAlertDialog(
        onDismissRequest = {
            if (!isDownloading) onDismiss()
        },
        title = { Text(text = "发现新版本 ${updateInfo.versionName}") },
        text = {
            Column {
                Text(
                    text = buildString {
                        appendLine(updateInfo.releaseName)
                        if (updateInfo.apkSize > 0L) {
                            appendLine("安装包大小: ${formatFileSize(updateInfo.apkSize)}")
                        }
                    }.trim()
                )
                if (notes.isNotEmpty()) {
                    // 更新说明：高度封顶 + 内部可上下滑动，长说明不会把弹窗撑爆。
                    // padding 放在滚动容器之外，否则它会跟着内容一起滚走。
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .heightIn(max = notesMaxHeight)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(text = notes)
                    }
                }
                Text(
                    text = "下载源",
                    modifier = Modifier.padding(top = 12.dp)
                )
                downloadSources.forEach { source ->
                    val status = sourceStatuses.firstOrNull { it.source.id == source.id }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        RadioButton(
                            selected = source.id == selectedSourceId,
                            onClick = { if (!isDownloading) onSourceSelected(source.id) },
                            enabled = !isDownloading
                        )
                        Row(
                            modifier = Modifier.padding(top = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = buildString {
                                    append(source.name)
                                    status?.let {
                                        append(" · ")
                                        append(if (it.available) "${it.latencyMs ?: 0} ms" else it.message)
                                    }
                                }
                            )
                            // 测速进行中：源名后面挂一个小转圈。
                            // 不写「测速中…」文字（弹窗里堆字很土），转圈跟文字一样大，
                            // 视觉上就是「这个名字的延迟还在测」的意思。
                            if (isMeasuringSources) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .padding(start = 6.dp)
                                        .size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { if (!isMeasuringSources) onMeasureSources() },
                        enabled = !isDownloading && !isMeasuringSources
                    ) {
                        Text("Ping")
                    }
                }
                if (isDownloading) {
                    if (downloadProgress != null) {
                        LinearProgressIndicator(
                            progress = { (downloadProgress ?: 0f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                        )
                    }
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
            } else {
                // 下载中：原来这里什么都不渲染，用户既关不掉弹窗也停不下下载，
                // 只能等它自己结束（失败时更久）。给出明确的取消入口。
                TextButton(onClick = onCancelDownload) {
                    Text("取消下载")
                }
            }
        }
    )
    }
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
