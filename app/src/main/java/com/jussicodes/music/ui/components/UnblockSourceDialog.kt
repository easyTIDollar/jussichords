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

data class UnblockSourceOption(
    val value: String,
    val label: String,
)

val unblockSourceOptions = listOf(
    UnblockSourceOption("AUTO", "自动选择音源"),
    UnblockSourceOption("baka", "Baka"),
    UnblockSourceOption("bikoo", "Bikoo"),
    UnblockSourceOption("bytedance", "Byfuns"),
    UnblockSourceOption("kuwo", "Gdmusic"),
    UnblockSourceOption("migu", "Msls"),
    UnblockSourceOption("pyncmd", "pyncmd"),
    UnblockSourceOption("qq", "Uhm"),
    UnblockSourceOption("youtube", "Whitisnot"),
)

@Composable
fun UnblockSourceDialog(
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
                unblockSourceOptions.forEach { option ->
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
                        Text(text = option.label)
                    }
                }
            }
        }
    }
}
