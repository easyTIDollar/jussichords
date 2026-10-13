package com.jussicodes.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.rcmiku.ncmapi.api.AudioSources

data class AudioSourceOption(
    val value: String,
    val label: String,
    val description: String = "",
)

// 单一来源：ncmapi 模块的 AudioSources（LX音源 / 直连音源 / API代理服务器解灰），
// PlayerApi 按 AudioSourceSettings.SELECTED 走对应分支，两者不漂移。
val audioSourceOptions: List<AudioSourceOption> =
    AudioSources.OPTIONS.map { AudioSourceOption(it.value, it.label, it.description) }

/**
 * 音源选择器：三张小卡片横排，点选即生效（无弹窗）。
 * 顺序与命名以 ncmapi 的 [audioSourceOptions] 为准：LX音源 / 直连音源 / API代理服务器解灰。
 */
@Composable
fun AudioSourceSelector(
    currentSource: String,
    onSourceSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // IntrinsicSize.Min：让三张卡片按最高的一张等高，否则内容行数不同会参差。
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        audioSourceOptions.forEach { option ->
            val selected = option.value == currentSource
            Column(
                modifier = Modifier
                    .weight(1f)
                    // IntrinsicSize.Min 下要 fillMaxHeight 才会被拉伸到与最高卡片等高
                    .fillMaxHeight()
                    .heightIn(min = 96.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onSourceSelected(option.value) }
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
                if (option.description.isNotBlank()) {
                    Text(
                        text = option.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3
                    )
                }
            }
        }
    }
}