package com.jussicodes.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jussicodes.music.ui.theme.ThemeColorSource
import kotlin.math.roundToInt

@Composable
fun ThemeColorSourceDialog(
    currentSource: ThemeColorSource,
    currentUiScale: Float,
    currentFontScale: Float,
    wallpaperColorAvailable: Boolean,
    onDismiss: () -> Unit,
    onSourceSelected: (ThemeColorSource) -> Unit,
    onUiScaleSelected: (Float) -> Unit,
    onFontScaleSelected: (Float) -> Unit,
    floatingNavBarEnabled: Boolean,
    onFloatingNavBarEnabledChange: (Boolean) -> Unit,
    playerBackgroundBlurEnabled: Boolean,
    onPlayerBackgroundBlurEnabledChange: (Boolean) -> Unit,
    homeBg: HomeBackgroundConfig,
    onHomeBgPick: () -> Unit,
    onHomeBgRemoved: () -> Unit,
    onHomeBgTune: () -> Unit,
    homeBgTextColor: String,
    onHomeBgTextColorChanged: (String) -> Unit,
    homeBgTextShadow: Float,
    onHomeBgTextShadowChanged: (Float) -> Unit,
    homeTransparentCard: Boolean,
    onHomeTransparentCardChange: (Boolean) -> Unit,
) {
    // 界面缩放五档：70/80/90/100/110
    var uiScaleValue by remember(currentUiScale) {
        val raw = currentUiScale.coerceIn(0.7f, 1.1f)
        mutableFloatStateOf((0.7f + ((raw - 0.7f) / 0.1f).roundToInt() * 0.1f).coerceIn(0.7f, 1.1f))
    }
    val uiScaleTicks = listOf(0.7f, 0.8f, 0.9f, 1f, 1.1f)
    fun commitUiScale() {
        val snapped = uiScaleTicks.minByOrNull { kotlin.math.abs(it - uiScaleValue) } ?: 1f
        uiScaleValue = snapped
        onUiScaleSelected(snapped)
    }

    // 字体缩放六档：80/90/100/110/120/130
    var fontScaleValue by remember(currentFontScale) {
        val raw = currentFontScale.coerceIn(0.8f, 1.3f)
        mutableFloatStateOf((0.8f + ((raw - 0.8f) / 0.1f).roundToInt() * 0.1f).coerceIn(0.8f, 1.3f))
    }
    val fontScaleTicks = listOf(0.8f, 0.9f, 1f, 1.1f, 1.2f, 1.3f)
    fun commitFontScale() {
        val snapped = fontScaleTicks.minByOrNull { kotlin.math.abs(it - fontScaleValue) } ?: 1f
        fontScaleValue = snapped
        onFontScaleSelected(snapped)
    }

    // 文字柔和轮廓/阴影半径 (0 ~ 10 dp)
    var textShadowValue by remember(homeBgTextShadow) {
        mutableFloatStateOf(homeBgTextShadow.coerceIn(0f, 10f))
    }
    fun commitTextShadow() {
        val snapped = ((textShadowValue * 10).roundToInt() / 10f).coerceIn(0f, 10f)
        textShadowValue = snapped
        onHomeBgTextShadowChanged(snapped)
    }

    ResponsiveDialogWindow(onDismissRequest = onDismiss) {
        ScaledDialogContent {
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // ==================== 1. 主题颜色卡片组 ====================
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "主题颜色",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )

                        // 复用音源选择卡片样式（AudioSourceSelector）：三列等高干净小卡片
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(androidx.compose.foundation.layout.IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ThemeColorSource.entries.forEach { source ->
                                val selected = source == currentSource
                                val tag = when (source) {
                                    ThemeColorSource.WALLPAPER -> "系统壁纸"
                                    ThemeColorSource.ARTWORK -> "切歌更新"
                                    ThemeColorSource.HOME_BG -> "主页背景"
                                }
                                val sub = when (source) {
                                    ThemeColorSource.WALLPAPER -> "Material You"
                                    ThemeColorSource.ARTWORK -> "提取封面"
                                    ThemeColorSource.HOME_BG -> "自定义图"
                                }
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .heightIn(min = 84.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .clickable { onSourceSelected(source) }
                                        .padding(horizontal = 10.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = source.label,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = tag,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.tertiary,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = sub,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        // 下方动态副标题
                        val sourceDesc = when (currentSource) {
                            ThemeColorSource.WALLPAPER -> if (wallpaperColorAvailable) {
                                "跟随系统壁纸颜色（Material You）"
                            } else {
                                "需要 Android 12+；当前使用默认配色"
                            }
                            ThemeColorSource.ARTWORK -> "切歌时更新；无封面时自动使用壁纸配色"
                            ThemeColorSource.HOME_BG -> "从主页背景提取配色；未设置背景时使用默认"
                        }
                        Text(
                            text = sourceDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }

                    // ==================== 2. 视觉与动效 ====================
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "视觉与动效",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
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
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                            ) {
                                // 悬浮胶囊底栏
                                DialogSwitchRow(
                                    title = "悬浮胶囊底栏",
                                    subtitle = if (floatingNavBarEnabled) {
                                        "已开启 · 悬浮胶囊样式"
                                    } else {
                                        "已关闭 · 经典贴底通栏"
                                    },
                                    checked = floatingNavBarEnabled,
                                    onCheckedChange = onFloatingNavBarEnabledChange,
                                )

                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )

                                // 上滑背景高斯模糊
                                DialogSwitchRow(
                                    title = "上滑背景高斯模糊",
                                    subtitle = if (playerBackgroundBlurEnabled) {
                                        "已开启 · 动态高斯模糊"
                                    } else {
                                        "已关闭 · 景深遮罩（省电流畅）"
                                    },
                                    checked = playerBackgroundBlurEnabled,
                                    onCheckedChange = onPlayerBackgroundBlurEnabledChange,
                                )

                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )

                                // 主页透明卡片
                                DialogSwitchRow(
                                    title = "主页透明卡片",
                                    subtitle = if (homeTransparentCard) {
                                        "已开启 · 半透明毛玻璃质感"
                                    } else {
                                        "已关闭 · 实色卡片容器"
                                    },
                                    checked = homeTransparentCard,
                                    onCheckedChange = onHomeTransparentCardChange,
                                )
                            }
                        }
                    }

                    // ==================== 3. 界面与字体缩放 ====================
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "界面与字体缩放",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
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
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                // 界面缩放
                                HomeBgSliderRow(
                                    label = "界面缩放",
                                    value = uiScaleValue,
                                    valueText = "${(uiScaleValue * 100).roundToInt()}%",
                                    valueRange = 0.7f..1.1f,
                                    steps = 3,
                                    onValueChanged = { uiScaleValue = it },
                                    onValueChangeFinished = { commitUiScale() },
                                )

                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(vertical = 2.dp),
                                )

                                // 字体大小缩放
                                HomeBgSliderRow(
                                    label = "字体大小",
                                    value = fontScaleValue,
                                    valueText = "${(fontScaleValue * 100).roundToInt()}%",
                                    valueRange = 0.8f..1.3f,
                                    steps = 4,
                                    onValueChanged = { fontScaleValue = it },
                                    onValueChangeFinished = { commitFontScale() },
                                )
                            }
                        }
                    }

                    // ==================== 4. 主页文字效果 ====================
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "主页文字效果",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "自定义「我的」与「探索」页在壁纸下的文字颜色与柔和阴影轮廓",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 2.dp),
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
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                // 颜色预设横向滚动
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "字体颜色",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        val colorPresets = listOf(
                                            Triple("auto", "自动", null),
                                            Triple("white", "纯白", Color(0xFFF5F5F5)),
                                            Triple("warm_white", "米白", Color(0xFFFFF8E7)),
                                            Triple("black", "纯黑", Color(0xDE000000)),
                                            Triple("yellow", "暖黄", Color(0xFFFFD54F)),
                                            Triple("cyan", "青绿", Color(0xFF80DEEA)),
                                            Triple("blue", "冰蓝", Color(0xFF90CAF9)),
                                            Triple("pink", "淡粉", Color(0xFFF48FB1)),
                                        )
                                        colorPresets.forEach { (mode, label, previewColor) ->
                                            val isSelected = homeBgTextColor == mode
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = { onHomeBgTextColorChanged(mode) },
                                                leadingIcon = {
                                                    if (previewColor != null) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(14.dp)
                                                                .clip(CircleShape)
                                                                .background(previewColor)
                                                                .border(
                                                                    1.dp,
                                                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                                                    CircleShape,
                                                                ),
                                                        )
                                                    }
                                                },
                                                label = { Text(label) },
                                            )
                                        }
                                    }
                                }

                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(vertical = 2.dp),
                                )

                                // 柔和轮廓/阴影滑块
                                HomeBgSliderRow(
                                    label = "柔和轮廓",
                                    value = textShadowValue,
                                    valueText = if (textShadowValue <= 0.05f) {
                                        "关闭"
                                    } else {
                                        "${(textShadowValue * 10).roundToInt() / 10f} dp"
                                    },
                                    valueRange = 0f..10f,
                                    steps = 19,
                                    onValueChanged = { textShadowValue = it },
                                    onValueChangeFinished = { commitTextShadow() },
                                )
                            }
                        }
                    }

                    // ==================== 5. 主页背景 ====================
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "主页背景",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "在「我的」和「探索」页使用的本地图片背景",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 2.dp),
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
                                            TextButton(onClick = onHomeBgTune) {
                                                Text("在主页调整")
                                            }
                                            TextButton(onClick = onHomeBgRemoved) {
                                                Text("移除")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
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
