package com.jussicodes.music.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jussicodes.music.constants.sleepTimerShortcutsKey
import com.jussicodes.music.utils.rememberPreference

/** 内置的定时播放快捷时长（分钟）。 */
private val BuiltInSleepTimerShortcuts = listOf(15, 30, 45, 60, 90)

/** 用户自定义快捷时长最多保留几个，防止快捷条无限膨胀。 */
private const val MaxUserSleepTimerShortcuts = 8

/**
 * 定时播放（睡眠定时）对话框。
 *
 * UI 结构：
 * - 顶部一排**快捷选择**（内置档位 + 用户自己保存过的档位），点一下即选中；
 * - 中间是**输入框**，可以直接填分钟数或 `1:30` 这种 时:分 格式；
 * - 输入框下方有「保存为快捷」，把当前填的时长存进偏好，下次直接出现在快捷条里。
 *
 * @param onTimeSet 确认时回调，参数为**秒**。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SleepTimerDialog(
    onDismiss: () -> Unit,
    onTimeSet: (Long) -> Unit,
) {
    var inputText by remember { mutableStateOf("30") }
    var shortcutsPref by rememberPreference(sleepTimerShortcutsKey, "")
    val userShortcuts = remember(shortcutsPref) {
        shortcutsPref.split(',')
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..720 }
            .distinct()
    }

    val parsedMinutes = remember(inputText) { parseSleepTimerInput(inputText) }
    // parsedMinutes 是可空 Int，不能直接 `in List<Int>`（元素类型不匹配编译不过），
    // 所以先把可空值收成非空再比较。
    val savedMinutes = parsedMinutes?.takeIf { userShortcuts.contains(it) }

    ResponsiveAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "定时播放") },
        text = {
            Column {
                Text(
                    text = "快捷选择",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    BuiltInSleepTimerShortcuts.forEach { minutes ->
                        FilterChip(
                            selected = parsedMinutes == minutes,
                            onClick = { inputText = minutes.toString() },
                            label = { Text(text = "$minutes 分钟") }
                        )
                    }
                    userShortcuts.forEach { minutes ->
                        InputChip(
                            selected = parsedMinutes == minutes,
                            onClick = { inputText = minutes.toString() },
                            label = { Text(text = "$minutes 分钟") },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "删除快捷时长 $minutes 分钟",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable {
                                            val rest = userShortcuts.filterNot { it == minutes }
                                            shortcutsPref = rest.joinToString(",")
                                        }
                                )
                            }
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    label = { Text(text = "自定义时长") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(
                        onClick = {
                            val minutes = parsedMinutes ?: return@TextButton
                            if (savedMinutes != null) return@TextButton
                            val next = (userShortcuts + minutes).takeLast(MaxUserSleepTimerShortcuts)
                            shortcutsPref = next.joinToString(",")
                        },
                        enabled = parsedMinutes != null && savedMinutes == null
                    ) {
                        Text(text = "保存为快捷")
                    }
                    Spacer(Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val minutes = parsedMinutes ?: return@TextButton
                    onTimeSet(minutes * 60L)
                },
                enabled = parsedMinutes != null
            ) {
                Text(text = "开始")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "取消")
            }
        }
    )
}

/**
 * 解析输入框里的时长文本，支持：
 * - 纯分钟数：`45`
 * - 时:分：`1:30`（= 90 分钟）
 *
 * 非法输入返回 null（界面上提示用户）。
 */
fun parseSleepTimerInput(text: String): Int? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    if (trimmed.contains(':')) {
        val parts = trimmed.split(':')
        if (parts.size != 2) return null
        val hours = parts[0].trim().toIntOrNull() ?: return null
        val minutes = parts[1].trim().toIntOrNull() ?: return null
        if (hours < 0 || minutes !in 0..59) return null
        val total = hours * 60 + minutes
        return total.takeIf { it in 1..720 }
    }
    val minutes = trimmed.toIntOrNull() ?: return null
    return minutes.takeIf { it in 1..720 }
}
