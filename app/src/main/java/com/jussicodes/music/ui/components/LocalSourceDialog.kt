package com.jussicodes.music.ui.components

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
import androidx.compose.ui.window.Dialog

data class LocalSourceOption(
    val value: String,
    val label: String,
    val description: String = "",
)

// 单一来源：ncmapi 模块的 LocalSources（照 Melodia 的本地直连六源 + 关闭/智能切换），
// PlayerApi 的本地源探测引用同一份，避免 UI 与探测链漂移。
val localSourceOptions: List<LocalSourceOption> =
    com.rcmiku.ncmapi.api.LocalSources.OPTIONS.map {
        LocalSourceOption(it.value, it.label, it.description)
    }

@Composable
fun LocalSourceDialog(
    currentSource: String,
    onDismiss: () -> Unit,
    onSourceSelected: (String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = MaterialTheme.shapes.extraLarge) {
            Column(
                modifier = Modifier
                    .selectableGroup()
                    .padding(vertical = 24.dp)
            ) {
                localSourceOptions.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .selectable(
                                selected = option.value == currentSource,
                                onClick = {
                                    onSourceSelected(option.value)
                                    onDismiss()
                                },
                                role = Role.RadioButton,
                            )
                            .padding(horizontal = 24.dp),
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
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
