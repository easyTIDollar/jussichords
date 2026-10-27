package com.jussicodes.music.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.rcmiku.ncmapi.api.LocalSources

data class LocalSourceOption(
    val value: String,
    val label: String,
    val description: String = "",
    val maxQuality: String = "",
)

// 单一来源：ncmapi 模块的 LocalSources（自动选择 + 六源），
// PlayerApi 的直连分支引用同一份，避免 UI 与探测链漂移。
val localSourceOptions: List<LocalSourceOption> =
    LocalSources.OPTIONS.map { LocalSourceOption(it.value, it.label, it.description, it.maxQuality) }

/** 直连源单选弹窗：自动选择（顺序轮询六源）或指定单一源。 */
@Composable
fun LocalSourceDialog(
    currentSource: String,
    onDismiss: () -> Unit,
    onSourceSelected: (String) -> Unit
) {
    val isLandscape = isLandscapeOrientation()

    ResponsiveDialogWindow(onDismissRequest = onDismiss) {
        ScaledDialogContent {
            Card(shape = MaterialTheme.shapes.extraLarge) {
                if (isLandscape) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        Text(
                            text = "选择直连音源",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                        val pairs = localSourceOptions.chunked(2)
                        pairs.forEach { rowOptions ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowOptions.forEach { option ->
                                    LocalSourceItem(
                                        option = option,
                                        selected = option.value == currentSource,
                                        onClick = {
                                            onSourceSelected(option.value)
                                            onDismiss()
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (rowOptions.size < 2) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .selectableGroup()
                            .padding(vertical = 20.dp)
                    ) {
                        Text(
                            text = "选择直连音源",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                        )
                        localSourceOptions.forEach { option ->
                            LocalSourceItem(
                                option = option,
                                selected = option.value == currentSource,
                                onClick = {
                                    onSourceSelected(option.value)
                                    onDismiss()
                                },
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalSourceItem(
    option: LocalSourceOption,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            )
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (option.maxQuality.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f),
                    ) {
                        Text(
                            text = option.maxQuality,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }
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
