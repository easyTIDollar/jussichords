package com.jussicodes.music.ui.screen

import android.graphics.Color as AndroidColor
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import com.jussicodes.music.constants.desktopLyricBgVisibleKey
import com.jussicodes.music.constants.desktopLyricEnabledKey
import com.jussicodes.music.constants.desktopLyricFontSizeKey
import com.jussicodes.music.constants.desktopLyricLockedKey
import com.jussicodes.music.constants.desktopLyricTextColorKey
import com.jussicodes.music.lyric.DesktopLyricDefaultFontSizeSp
import com.jussicodes.music.lyric.DesktopLyricManager
import com.jussicodes.music.lyric.DesktopLyricMaxFontSizeSp
import com.jussicodes.music.lyric.DesktopLyricMinFontSizeSp
import com.jussicodes.music.utils.rememberPreference
import kotlinx.coroutines.launch

/** 桌面歌词设置：字号 / 颜色 / 背景框 / 锁定 / 位置重置，改动实时作用到悬浮窗。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesktopLyricSettingsScreen(navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var enabled by rememberPreference(desktopLyricEnabledKey, false)
    var fontSize by rememberPreference(desktopLyricFontSizeKey, DesktopLyricDefaultFontSizeSp)
    var textColor by rememberPreference(desktopLyricTextColorKey, AndroidColor.WHITE)
    var backgroundVisible by rememberPreference(desktopLyricBgVisibleKey, true)
    var locked by rememberPreference(desktopLyricLockedKey, false)

    var overlayGranted by remember { mutableStateOf(DesktopLyricManager.canDrawOverlays(context)) }
    var pendingOverlayGrant by rememberSaveable { mutableStateOf(false) }
    var fontSizeDraft by remember(fontSize) { mutableFloatStateOf(fontSize) }
    var hsv by remember(textColor) {
        mutableStateOf(FloatArray(3).also { AndroidColor.colorToHSV(textColor, it) })
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayGranted = DesktopLyricManager.canDrawOverlays(context)
                if (pendingOverlayGrant && overlayGranted) {
                    pendingOverlayGrant = false
                    scope.launch {
                        DesktopLyricManager.setEnabled(context = context, enabled = true)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun toggleEnabled(target: Boolean) {
        if (target && !overlayGranted) {
            pendingOverlayGrant = true
            DesktopLyricManager.requestOverlayPermission(context)
            return
        }
        scope.launch { DesktopLyricManager.setEnabled(context = context, enabled = target) }
    }

    val effectiveOn = enabled && overlayGranted

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("桌面歌词") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            DesktopLyricPreview(
                fontSizeDraft = fontSizeDraft,
                textColor = textColor,
                backgroundVisible = backgroundVisible
            )

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    SettingSwitchRow(
                        title = "启用桌面歌词",
                        subtitle = when {
                            effectiveOn -> "已开启，退出 App 后显示"
                            !overlayGranted -> "需要悬浮窗权限"
                            else -> "已关闭"
                        },
                        checked = effectiveOn,
                        onCheckedChange = { toggleEnabled(it) }
                    )
                    SettingSwitchRow(
                        title = "显示背景框",
                        subtitle = "关闭后只保留描边文字",
                        checked = backgroundVisible,
                        onCheckedChange = { backgroundVisible = it }
                    )
                    SettingSwitchRow(
                        title = "锁定位置",
                        subtitle = "锁定后歌词触摸穿透，点右上角小锁解锁",
                        checked = locked,
                        onCheckedChange = { locked = it },
                        leadingIcon = true
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("歌词大小", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "${fontSizeDraft.toInt()} sp",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Slider(
                        value = fontSizeDraft,
                        onValueChange = { fontSizeDraft = it },
                        onValueChangeFinished = { fontSize = fontSizeDraft },
                        valueRange = DesktopLyricMinFontSizeSp..DesktopLyricMaxFontSizeSp,
                        steps = 19
                    )
                    Text(
                        text = "副行（翻译/下一句）随主行等比缩放",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("字体颜色", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        presetColors.forEach { preset ->
                            ColorSwatch(
                                color = preset,
                                selected = AndroidColor.alpha(textColor) == 255 &&
                                    AndroidColor.red(textColor) == AndroidColor.red(preset) &&
                                    AndroidColor.green(textColor) == AndroidColor.green(preset) &&
                                    AndroidColor.blue(textColor) == AndroidColor.blue(preset),
                                onClick = { textColor = preset }
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    HsvSlider(
                        label = "色相",
                        value = hsv[0],
                        range = 0f..360f,
                        onValueChange = { hsv = floatArrayOf(it, hsv[1], hsv[2]) },
                        onFinished = { textColor = AndroidColor.HSVToColor(hsv) }
                    )
                    HsvSlider(
                        label = "饱和度",
                        value = hsv[1],
                        range = 0f..1f,
                        onValueChange = { hsv = floatArrayOf(hsv[0], it, hsv[2]) },
                        onFinished = { textColor = AndroidColor.HSVToColor(hsv) }
                    )
                    HsvSlider(
                        label = "亮度",
                        value = hsv[2],
                        range = 0f..1f,
                        onValueChange = { hsv = floatArrayOf(hsv[0], hsv[1], it) },
                        onFinished = { textColor = AndroidColor.HSVToColor(hsv) }
                    )
                    Text(
                        text = "描边色按字体亮度自动取黑或白",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("位置", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "拖动歌词即可移动，位置按屏幕比例记住；歌词始终不会移出屏幕。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = {
                        DesktopLyricManager.resetPosition(context)
                        Toast.makeText(context, "已回到默认位置", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("重置位置")
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

private val presetColors = listOf(
    AndroidColor.WHITE,
    AndroidColor.parseColor("#FFF3C4"),
    AndroidColor.parseColor("#FFD166"),
    AndroidColor.parseColor("#8AE9C1"),
    AndroidColor.parseColor("#7CD3FF"),
    AndroidColor.parseColor("#C9A7FF"),
    AndroidColor.parseColor("#FFA7C4"),
    AndroidColor.parseColor("#1A1A1E")
)

@Composable
private fun DesktopLyricPreview(
    fontSizeDraft: Float,
    textColor: Int,
    backgroundVisible: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF101014))
            .padding(vertical = 18.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        if (backgroundVisible) Color(0xCC2F2F33) else Color.Transparent
                    )
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "晚风吹过旧站台",
                        color = Color(textColor),
                        fontSize = fontSizeDraft.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = (fontSizeDraft * 1.15f).sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "下一句歌词预览",
                        color = Color(textColor).copy(alpha = 0.8f),
                        fontSize = (fontSizeDraft * 0.66f).sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    leadingIcon: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ColorSwatch(color: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(Color(color))
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
                shape = CircleShape
            )
            .clickable { onClick() }
    )
}

@Composable
private fun HsvSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onFinished: () -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (range.endInclusive > 1f) "${value.toInt()}" else "${(value * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onFinished,
            valueRange = range
        )
    }
}
