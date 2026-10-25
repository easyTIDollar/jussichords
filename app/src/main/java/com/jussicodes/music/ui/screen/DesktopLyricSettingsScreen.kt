package com.jussicodes.music.ui.screen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.ComposeShader
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import com.jussicodes.music.constants.MiniPlayerHeight
import com.jussicodes.music.constants.desktopLyricEnabledKey
import com.jussicodes.music.constants.desktopLyricFontSizeKey
import com.jussicodes.music.constants.desktopLyricLockedKey
import com.jussicodes.music.constants.desktopLyricStrokeScaleKey
import com.jussicodes.music.constants.desktopLyricTextColorKey
import com.jussicodes.music.constants.isLandscapePhone
import com.jussicodes.music.lyric.DesktopLyricDefaultFontSizeSp
import com.jussicodes.music.lyric.DesktopLyricDefaultStrokeScale
import com.jussicodes.music.lyric.DesktopLyricManager
import com.jussicodes.music.lyric.DesktopLyricMaxFontSizeSp
import com.jussicodes.music.lyric.DesktopLyricMaxStrokeScale
import com.jussicodes.music.lyric.DesktopLyricMinFontSizeSp
import com.jussicodes.music.playback.PlayerController
import com.jussicodes.music.utils.parseLrc
import com.jussicodes.music.utils.parseYrc
import com.jussicodes.music.utils.rememberPreference
import com.jussicodes.music.viewModel.LyricViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 桌面歌词设置：字号 / 描边 / 颜色（预设 + 调色盘）/ 锁定 / 位置重置。
 * 改动直接写 DataStore，DesktopLyricService 监听后实时生效。
 * 横屏时预览+开关在左列，字号/颜色/位置在右列。
 */
@OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)
@Composable
fun DesktopLyricSettingsScreen(navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var enabled by rememberPreference(desktopLyricEnabledKey, false)
    var fontSize by rememberPreference(desktopLyricFontSizeKey, DesktopLyricDefaultFontSizeSp)
    var textColor by rememberPreference(desktopLyricTextColorKey, AndroidColor.WHITE)
    var strokeScale by rememberPreference(desktopLyricStrokeScaleKey, DesktopLyricDefaultStrokeScale)
    var locked by rememberPreference(desktopLyricLockedKey, false)

    var overlayGranted by remember { mutableStateOf(DesktopLyricManager.canDrawOverlays(context)) }
    var pendingOverlayGrant by rememberSaveable { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }

    var fontSizeDraft by remember(fontSize) { mutableFloatStateOf(fontSize) }
    var strokeDraft by remember(strokeScale) { mutableFloatStateOf(strokeScale) }

    // 预览用当前播放歌词：复用 LyricViewModel（同一份缓存），位置每 400ms 取一次。
    val mediaController = PlayerController.controller
    val lyricViewModel: LyricViewModel = hiltViewModel()
    val lyricResponse by lyricViewModel.lyric.collectAsState()
    val currentMediaId = mediaController?.currentMediaItem?.mediaId
    val lyricLines = remember(lyricResponse) {
        val yrcLines = lyricResponse?.yrc?.lyric?.parseYrc().orEmpty()
        val lines = if (yrcLines.isNotEmpty()) {
            yrcLines.map { it.time to it.text }
        } else {
            lyricResponse?.lrc?.lyric?.parseLrc().orEmpty().map { it.time to it.text }
        }
        lines.filter { it.second.isNotBlank() }
    }
    var previewIndex by remember { mutableIntStateOf(-1) }

    LaunchedEffect(currentMediaId) {
        currentMediaId?.toLongOrNull()?.let { lyricViewModel.fetchLyric(it) }
    }
    LaunchedEffect(lyricLines, mediaController) {
        if (lyricLines.isEmpty()) {
            previewIndex = -1
            return@LaunchedEffect
        }
        while (true) {
            val position = mediaController?.currentPosition ?: 0L
            previewIndex = lyricLines.indexOfLast { it.first <= position }
            delay(400)
        }
    }

    val previewCurrent = lyricLines.getOrNull(previewIndex)?.second ?: "choke enough"
    val previewNext = lyricLines.getOrNull(previewIndex + 1)?.second ?: "oklou"

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
    val landscape = isLandscapePhone()

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
        val bottomInset = MiniPlayerHeight +
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (landscape) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PreviewCard(
                            fontSize = fontSizeDraft,
                            textColor = textColor,
                            currentLine = previewCurrent,
                            nextLine = previewNext
                        )
                        EnableCard(
                            effectiveOn = effectiveOn,
                            overlayGranted = overlayGranted,
                            locked = locked,
                            onToggleEnabled = { toggleEnabled(it) },
                            onToggleLocked = { locked = it }
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SizeCard(
                            fontSize = fontSizeDraft,
                            strokeScale = strokeDraft,
                            onFontSizeChange = { fontSizeDraft = it },
                            onFontSizeFinished = { fontSize = fontSizeDraft },
                            onStrokeChange = { strokeDraft = it },
                            onStrokeFinished = { strokeScale = strokeDraft }
                        )
                        ColorCard(
                            textColor = textColor,
                            onPick = { textColor = it },
                            onOpenPicker = { showColorPicker = true }
                        )
                        PositionCard {
                            DesktopLyricManager.resetPosition(context)
                            Toast.makeText(context, "已回到默认位置", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                PreviewCard(
                    fontSize = fontSizeDraft,
                    textColor = textColor,
                    currentLine = previewCurrent,
                    nextLine = previewNext
                )
                EnableCard(
                    effectiveOn = effectiveOn,
                    overlayGranted = overlayGranted,
                    locked = locked,
                    onToggleEnabled = { toggleEnabled(it) },
                    onToggleLocked = { locked = it }
                )
                SizeCard(
                    fontSize = fontSizeDraft,
                    strokeScale = strokeDraft,
                    onFontSizeChange = { fontSizeDraft = it },
                    onFontSizeFinished = { fontSize = fontSizeDraft },
                    onStrokeChange = { strokeDraft = it },
                    onStrokeFinished = { strokeScale = strokeDraft }
                )
                ColorCard(
                    textColor = textColor,
                    onPick = { textColor = it },
                    onOpenPicker = { showColorPicker = true }
                )
                PositionCard {
                    DesktopLyricManager.resetPosition(context)
                    Toast.makeText(context, "已回到默认位置", Toast.LENGTH_SHORT).show()
                }
            }
            Spacer(Modifier.height(bottomInset + 16.dp))
        }
    }

    if (showColorPicker) {
        ColorPickerDialog(
            initialColor = textColor,
            onDismiss = { showColorPicker = false },
            onConfirm = {
                textColor = it
                showColorPicker = false
            }
        )
    }
}

private val presetColors = listOf(
    AndroidColor.WHITE,
    AndroidColor.parseColor("#FFF3C4"),
    AndroidColor.parseColor("#FFD166"),
    AndroidColor.parseColor("#8AE9C1"),
    AndroidColor.parseColor("#7CD3FF"),
    AndroidColor.parseColor("#C9A7FF"),
    AndroidColor.parseColor("#1A1A1E")
)

@Composable
private fun PreviewCard(
    fontSize: Float,
    textColor: Int,
    currentLine: String,
    nextLine: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF101014))
            .padding(vertical = 20.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = currentLine,
                color = Color(textColor),
                fontSize = fontSize.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = (fontSize * 1.15f).sp,
                maxLines = 2
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = nextLine,
                color = Color(textColor).copy(alpha = 0.8f),
                fontSize = (fontSize * 0.66f).sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun EnableCard(
    effectiveOn: Boolean,
    overlayGranted: Boolean,
    locked: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onToggleLocked: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            SwitchRow(
                title = "启用桌面歌词",
                subtitle = when {
                    effectiveOn -> "已开启，退出 App 后显示"
                    !overlayGranted -> "需要悬浮窗权限"
                    else -> "已关闭"
                },
                checked = effectiveOn,
                onCheckedChange = onToggleEnabled
            )
            SwitchRow(
                title = "锁定位置",
                subtitle = "锁定歌词后歌词触摸穿透，点击歌词上方小锁解锁",
                checked = locked,
                onCheckedChange = onToggleLocked,
                leadingLockIcon = true
            )
        }
    }
}

@Composable
private fun SizeCard(
    fontSize: Float,
    strokeScale: Float,
    onFontSizeChange: (Float) -> Unit,
    onFontSizeFinished: () -> Unit,
    onStrokeChange: (Float) -> Unit,
    onStrokeFinished: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            LabeledValueRow(label = "歌词大小", value = "${fontSize.toInt()} sp")
            Slider(
                value = fontSize,
                onValueChange = onFontSizeChange,
                onValueChangeFinished = onFontSizeFinished,
                valueRange = DesktopLyricMinFontSizeSp..DesktopLyricMaxFontSizeSp,
                steps = 19
            )
            LabeledValueRow(
                label = "描边粗细",
                value = if (strokeScale < 0.05f) "关闭" else String.format("%.1f×", strokeScale)
            )
            Slider(
                value = strokeScale,
                onValueChange = onStrokeChange,
                onValueChangeFinished = onStrokeFinished,
                valueRange = 0f..DesktopLyricMaxStrokeScale,
                steps = 19
            )
            Text(
                text = "副行（翻译/下一句）随主行等比缩放，描边颜色按字体亮度自动取黑或白",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ColorCard(
    textColor: Int,
    onPick: (Int) -> Unit,
    onOpenPicker: () -> Unit
) {
    val customSelected = presetColors.none { sameRgb(it, textColor) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("字体颜色", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presetColors.forEach { preset ->
                    ColorSwatch(
                        color = preset,
                        selected = !customSelected && sameRgb(preset, textColor),
                        onClick = { onPick(preset) }
                    )
                }
                // 末尾调色盘：自定义任意颜色
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.sweepGradient(
                                listOf(
                                    Color(0xFFFF5252),
                                    Color(0xFFFFD166),
                                    Color(0xFF8AE9C1),
                                    Color(0xFF7CD3FF),
                                    Color(0xFFC9A7FF),
                                    Color(0xFFFF5252)
                                )
                            )
                        )
                        .border(
                            width = if (customSelected) 3.dp else 1.dp,
                            color = if (customSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        )
                        .clickable { onOpenPicker() }
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "最后一个色圈是调色盘，可自定义任意颜色",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PositionCard(onReset: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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
            TextButton(onClick = onReset) { Text("重置位置") }
        }
    }
}

@Composable
private fun LabeledValueRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    leadingLockIcon: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingLockIcon) {
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
            .size(28.dp)
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

private fun sameRgb(a: Int, b: Int): Boolean =
    AndroidColor.red(a) == AndroidColor.red(b) &&
        AndroidColor.green(a) == AndroidColor.green(b) &&
        AndroidColor.blue(a) == AndroidColor.blue(b)

@Composable
private fun ColorPickerDialog(
    initialColor: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val initialHsv = remember(initialColor) {
        FloatArray(3).also { AndroidColor.colorToHSV(initialColor, it) }
    }
    // 变量名刻意避开 hue/saturation/brightness：HsvWheelView.apply{} 里同名成员会遮蔽外层局部变量。
    var pickerHue by remember(initialColor) { mutableFloatStateOf(initialHsv[0]) }
    var pickerSaturation by remember(initialColor) { mutableFloatStateOf(initialHsv[1]) }
    var pickerBrightness by remember(initialColor) {
        mutableFloatStateOf(initialHsv[2].coerceIn(0.35f, 1f))
    }
    val picked = AndroidColor.HSVToColor(floatArrayOf(pickerHue, pickerSaturation, pickerBrightness))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义颜色") },
        text = {
            Column {
                AndroidView(
                    factory = { ctx ->
                        HsvWheelView(ctx).apply {
                            hue = pickerHue
                            saturation = pickerSaturation
                            onColorChanged = { h, s ->
                                pickerHue = h
                                pickerSaturation = s
                            }
                        }
                    },
                    update = { view ->
                        if (view.hue != pickerHue || view.saturation != pickerSaturation) {
                            view.hue = pickerHue
                            view.saturation = pickerSaturation
                            view.invalidate()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(picked))
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("亮度", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = pickerBrightness,
                        onValueChange = { pickerBrightness = it },
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(picked) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * HSV 色轮：角度=色相，半径=饱和度。亮度由外部滑条控制（色轮按 value=1 绘制）。
 * 用原生 View 是为了直接复用 SweepGradient / ComposeShader。
 */
private class HsvWheelView(context: Context) : View(context) {
    var hue: Float = 0f
    var saturation: Float = 0f
    var onColorChanged: ((Float, Float) -> Unit)? = null

    private val densityValue = resources.displayMetrics.density
    private var centerX = 0f
    private var centerY = 0f
    private var radiusPx = 0f

    private val hueColors = IntArray(361) { index ->
        AndroidColor.HSVToColor(floatArrayOf(index.toFloat(), 1f, 1f))
    }
    private val wheelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markerShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f * densityValue
        color = AndroidColor.argb(70, 0, 0, 0)
    }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * densityValue
        color = AndroidColor.WHITE
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        radiusPx = min(w, h) / 2f - 8f * densityValue
        if (radiusPx <= 0f) return
        val sweep = SweepGradient(centerX, centerY, hueColors, null)
        val radial = RadialGradient(
            centerX,
            centerY,
            radiusPx,
            AndroidColor.WHITE,
            0x00FFFFFF,
            Shader.TileMode.CLAMP
        )
        wheelPaint.shader = ComposeShader(sweep, radial, PorterDuff.Mode.SRC_OVER)
    }

    override fun onDraw(canvas: Canvas) {
        if (radiusPx <= 0f) return
        canvas.drawCircle(centerX, centerY, radiusPx, wheelPaint)
        val radians = Math.toRadians(hue.toDouble())
        val distance = saturation.coerceIn(0f, 1f) * radiusPx
        val markerX = centerX + (cos(radians) * distance).toFloat()
        val markerY = centerY + (sin(radians) * distance).toFloat()
        canvas.drawCircle(markerX, markerY, 9f * densityValue, markerShadowPaint)
        canvas.drawCircle(markerX, markerY, 9f * densityValue, markerPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                if (radiusPx <= 0f) return false
                val dx = event.x - centerX
                val dy = event.y - centerY
                saturation = (sqrt(dx * dx + dy * dy) / radiusPx).coerceIn(0f, 1f)
                hue = ((Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 360f) % 360f)
                onColorChanged?.invoke(hue, saturation)
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
