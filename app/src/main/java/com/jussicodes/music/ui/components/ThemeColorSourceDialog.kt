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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.jussicodes.music.ui.theme.ThemeColorSource
import kotlin.math.roundToInt

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
    // 界面缩放五档：70/80/90/100/110。跟下方「主页背景」滑块完全同款（复用 HomeBgSliderRow，
    // steps=4 → 五档）。关键跟手修复：拖动期间只改本地 uiScaleValue（驱动 % 文案+滑块，
    // 不动 LocalDensity），弹窗布局稳定、滑块跟手；松手时(onValueChangeFinished)才吸附到
    // 最近档并 commit → 此时才触发弹窗缩放重排，手指已抬起不冲突。最终落值仍只有 5 档。
    var uiScaleValue by remember {
        // 初值吸附到最近的 0.7 + k*0.1 档，避免 Slider 拿到非 step 位报错。
        val raw = currentUiScale.coerceIn(0.7f, 1.1f)
        mutableFloatStateOf((0.7f + ((raw - 0.7f) / 0.1f).roundToInt() * 0.1f).coerceIn(0.7f, 1.1f))
    }
    val uiScaleTicks = listOf(0.7f, 0.8f, 0.9f, 1f, 1.1f)
    fun commitUiScale() {
        // 松手时把当前档位吸附值 commit 出去（写 liveUiScale + ScaledDialogDensity，触发弹窗
        // 缩放重排）。分档滑块拖出的值已是档位值，这里**不判等、无条件 commit**：
        // 若保留 `snapped == uiScaleValue` 早返回，因拖出值恒等于最近档，判等恒成立、
        // onUiScaleSelected 永不触发 → 松手后缩放不生效（用户报告的 bug）。
        val snapped = uiScaleTicks.minByOrNull { kotlin.math.abs(it - uiScaleValue) } ?: 1f
        uiScaleValue = snapped
        onUiScaleSelected(snapped)
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
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // 跟下方「主页背景」三个滑块完全同款：复用 HomeBgSliderRow（steps=4 → 五档）。
                        // 跟手修复：拖动期间只更新本地 uiScaleValue（驱动滑块+ %文案，不动 LocalDensity），
                        // 弹窗不重排、滑块跟手；松手时才 commit → 弹窗一次性缩放重排。
                        HomeBgSliderRow(
                            label = "缩放",
                            value = uiScaleValue,
                            valueText = "${(uiScaleValue * 100).roundToInt()}%",
                            valueRange = 0.7f..1.1f,
                            steps = 3,
                            onValueChanged = { v ->
                                uiScaleValue = v
                            },
                            onValueChangeFinished = {
                                commitUiScale()
                            },
                        )
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
                            valueText = "${(homeBg.scale * 100).roundToInt()}%",
                            valueRange = 1f..2f,
                            steps = 9,
                            onValueChanged = onHomeBgScaleChanged,
                        )
                        HomeBgSliderRow(
                            label = "透明度",
                            value = homeBg.opacity,
                            valueText = "${(homeBg.opacity * 100).roundToInt()}%",
                            valueRange = 0.1f..1f,
                            steps = 8,
                            onValueChanged = onHomeBgOpacityChanged,
                        )
                        HomeBgSliderRow(
                            label = "模糊度",
                            value = homeBg.blur,
                            valueText = "${homeBg.blur.roundToInt()}",
                            valueRange = 0f..40f,
                            steps = 7,
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
    onValueChangeFinished: (() -> Unit)? = null,
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
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps,
        )
    }
}
