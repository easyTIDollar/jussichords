package com.jussicodes.music.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.jussicodes.music.ui.theme.ThemeColorSource

private const val UiScaleSnapDistance = 0.045f

@Composable
fun ThemeColorSourceDialog(
    currentSource: ThemeColorSource,
    currentUiScale: Float,
    wallpaperColorAvailable: Boolean,
    onDismiss: () -> Unit,
    onSourceSelected: (ThemeColorSource) -> Unit,
    onUiScaleSelected: (Float) -> Unit,
    homeBg: HomeBackgroundConfig,
    onHomeBgPick: () -> Unit,
    onHomeBgRemoved: () -> Unit,
    onHomeBgScaleChanged: (Float) -> Unit,
    onHomeBgOpacityChanged: (Float) -> Unit,
    onHomeBgBlurChanged: (Float) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    // 界面缩放吸附档：70/80/90/100/110 对应 index 0..4（value 0.7f..1.1f，步进 0.1）。
    val uiScaleTicks = listOf(0.7f, 0.8f, 0.9f, 1f, 1.1f)
    var lastUiScaleTick by remember {
        mutableIntStateOf(uiScaleTicks.indexOfFirst { kotlin.math.abs(it - currentUiScale) < 0.001f })
    }
    Dialog(onDismissRequest = onDismiss) {
        ScaledDialogContent {
        Card(shape = MaterialTheme.shapes.extraLarge) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "主题颜色",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "选择应用配色的来源",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                ThemeColorSource.entries.forEach { source ->
                    val selected = source == currentSource
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            },
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSourceSelected(source)
                                onDismiss()
                            },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(source.label, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = when (source) {
                                        ThemeColorSource.WALLPAPER -> if (wallpaperColorAvailable) {
                                            "跟随系统壁纸颜色"
                                        } else {
                                            "需要 Android 12；当前使用默认配色"
                                        }

                                        ThemeColorSource.ARTWORK ->
                                            "切歌时更新；无封面时自动使用壁纸配色"

                                        ThemeColorSource.HOME_BG ->
                                            "未设置主页背景图时使用默认配色"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            RadioButton(selected = selected, onClick = null)
                        }
                    }
                }

                Text(
                    text = "界面缩放",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp),
                )

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "${(currentUiScale * 100).toInt()}%",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 8.dp),
                        )
                        // 自由拖动；拖到 70/80/90/100/110 档位附近时吸附并震动（detent 手感）。
                        Slider(
                            value = currentUiScale,
                            onValueChange = { v ->
                                val nearestIdx = uiScaleTicks.indexOfFirst { kotlin.math.abs(it - v) < UiScaleSnapDistance }
                                if (nearestIdx >= 0) {
                                    val snapped = uiScaleTicks[nearestIdx]
                                    if (nearestIdx != lastUiScaleTick) {
                                        lastUiScaleTick = nearestIdx
                                        haptics.performHapticFeedback(HapticFeedbackType.Click)
                                    }
                                    onUiScaleSelected(snapped)
                                } else {
                                    lastUiScaleTick = -1
                                    onUiScaleSelected(v)
                                }
                            },
                            valueRange = 0.7f..1.1f,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            listOf("70%", "80%", "90%", "100%", "110%").forEach { label ->
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "主页背景",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    text = "在「我的」和「探索」页使用的本地图片背景",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (homeBg.enabled) "已设置背景图" else "未设置背景图",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Row {
                                TextButton(onClick = onHomeBgPick) {
                                    Text(if (homeBg.enabled) "更换" else "选取图片")
                                }
                                if (homeBg.enabled) {
                                    TextButton(onClick = onHomeBgRemoved) {
                                        Text("移除")
                                    }
                                }
                            }
                        }

                        HomeBgSliderRow(
                            label = "缩放",
                            value = homeBg.scale,
                            valueText = "${(homeBg.scale * 100).toInt()}%",
                            valueRange = 1f..2f,
                            steps = 10,
                            onValueChanged = onHomeBgScaleChanged,
                        )
                        HomeBgSliderRow(
                            label = "透明度",
                            value = homeBg.opacity,
                            valueText = "${(homeBg.opacity * 100).toInt()}%",
                            valueRange = 0.1f..1f,
                            steps = 9,
                            onValueChanged = onHomeBgOpacityChanged,
                        )
                        HomeBgSliderRow(
                            label = "模糊度",
                            value = homeBg.blur,
                            valueText = "${homeBg.blur.toInt()}",
                            valueRange = 0f..40f,
                            steps = 20,
                            onValueChanged = onHomeBgBlurChanged,
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun HomeBgSliderRow(
    label: String,
    value: Float,
    valueText: String,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChanged: (Float) -> Unit,
) {
    Column {
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
