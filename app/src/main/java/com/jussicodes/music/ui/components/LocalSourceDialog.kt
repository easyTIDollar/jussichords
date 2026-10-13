package com.jussicodes.music.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import com.rcmiku.ncmapi.api.LocalSources

data class LocalSourceOption(
    val value: String,
    val label: String,
    val description: String = "",
)

// 单一来源：ncmapi 模块的 LocalSources（自动选择 + 六源），
// PlayerApi 的直连分支引用同一份，避免 UI 与探测链漂移。
val localSourceOptions: List<LocalSourceOption> =
    LocalSources.OPTIONS.map { LocalSourceOption(it.value, it.label, it.description) }

/** 直连源单选弹窗：自动选择（顺序轮询六源）或指定单一源。 */
@Composable
fun LocalSourceDialog(
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
                        .padding(vertical = 24.dp)
                ) {
                    localSourceOptions.forEach { option ->
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