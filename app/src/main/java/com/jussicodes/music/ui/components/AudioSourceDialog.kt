package com.jussicodes.music.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.rcmiku.ncmapi.api.AudioSources

data class AudioSourceOption(
    val value: String,
    val label: String,
    val description: String = "",
)

// 单一来源：ncmapi 模块的 AudioSources（LX / 内置直连 / 代理），
// PlayerApi 按 AudioSourceSettings.SELECTED 走对应分支，两者不漂移。
val audioSourceOptions: List<AudioSourceOption> =
    AudioSources.OPTIONS.map { AudioSourceOption(it.value, it.label, it.description) }

/**
 * 音源三选一。选中即回调，由调用方写入 DataStore 并触发当前歌曲重新解析。
 */
@Composable
fun AudioSourceDialog(
    currentSource: String,
    onDismiss: () -> Unit,
    onSourceSelected: (String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        ScaledDialogContent {
            Card(shape = MaterialTheme.shapes.extraLarge) {
                Column(
                    modifier = Modifier
                        .selectableGroup()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 24.dp)
                ) {
                    audioSourceOptions.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .selectable(
                                    selected = option.value == currentSource,
                                    onClick = {
                                        onSourceSelected(option.value)
                                        onDismiss()
                                    },
                                    role = Role.RadioButton,
                                )
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = option.value == currentSource,
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(text = option.label)
                                if (option.description.isNotBlank()) {
                                    Text(
                                        text = option.description,
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