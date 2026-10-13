package com.jussicodes.music.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 主页背景编辑模式底部面板：透明度 / 模糊度滑块 + 重置 + 取消 / 保存。
 * 双指缩放与单指平移由主页全屏手势层承担，面板只管离散参数。
 * 样式沿用 ThemeColorSourceDialog 主页背景卡片的滑块行（同款 Card + Slider）。
 */
@Composable
fun HomeBgEditPanel(
    opacity: Float,
    blur: Float,
    adjustMode: Boolean,
    onToggleAdjust: () -> Unit,
    onOpacityChanged: (Float) -> Unit,
    onBlurChanged: (Float) -> Unit,
    onReset: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "主页背景",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "缩放/位置调整默认关闭，此时主页左右滑正常切「我的·探索」；开启后全屏只能双指缩放、单指移动图片，调完点按钮关闭",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(onClick = onToggleAdjust) {
                    Text(
                        if (adjustMode) "关闭缩放/位置调整" else "调整图片缩放/位置",
                        fontWeight = if (adjustMode) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
            EditPanelSliderRow(
                label = "透明度",
                valueText = "${(opacity * 100).roundToInt()}%",
                value = opacity,
                valueRange = 0.1f..1f,
                steps = 8,
                onValueChanged = onOpacityChanged,
            )
            EditPanelSliderRow(
                label = "模糊",
                valueText = blur.roundToInt().toString(),
                value = blur,
                valueRange = 0f..40f,
                steps = 39,
                onValueChanged = onBlurChanged,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onReset) { Text("重置") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onCancel) { Text("取消") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onSave) {
                    Text("保存", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun EditPanelSliderRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChanged: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value.coerceIn(valueRange),
            onValueChange = onValueChanged,
            valueRange = valueRange,
            steps = steps,
        )
    }
}
