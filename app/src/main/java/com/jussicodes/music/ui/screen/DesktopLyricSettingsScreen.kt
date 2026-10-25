package com.jussicodes.music.ui.screen

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.ComposeShader
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import com.jussicodes.music.R
import com.jussicodes.music.constants.MiniPlayerHeight
import com.jussicodes.music.constants.desktopLyricEnabledKey
import com.jussicodes.music.constants.desktopLyricFontSizeKey
import com.jussicodes.music.constants.desktopLyricLockedKey
import com.jussicodes.music.constants.desktopLyricPreviewBgKey
import com.jussicodes.music.constants.desktopLyricStrokeColorKey
import com.jussicodes.music.constants.desktopLyricStrokeScaleKey
import com.jussicodes.music.constants.desktopLyricTextColorKey
import com.jussicodes.music.constants.isLandscapePhone
import com.jussicodes.music.data.DesktopLyricPreviewBgStore
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    var strokeColor by rememberPreference(desktopLyricStrokeColorKey, AndroidColor.BLACK)
    var locked by rememberPreference(desktopLyricLockedKey, false)
    var previewBgPath by rememberPreference(desktopLyricPreviewBgKey, "")

    var overlayGranted by remember { mutableStateOf(DesktopLyricManager.canDrawOverlays(context)) }
    var pendingOverlayGrant by rememberSaveable { mutableStateOf(false) }

    var fontSizeDraft by remember(fontSize) { mutableFloatStateOf(fontSize) }
    var strokeDraft by remember(strokeScale) { mutableFloatStateOf(strokeScale) }

    // 点预览背景 → 系统相册（Photo Picker；设备不支持时 contract 自动退回 ACTION_OPEN_DOCUMENT）。
    val previewBgPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val path = DesktopLyricPreviewBgStore.save(context, uri)
            if (path != null) {
                previewBgPath = path
                Toast.makeText(context, "已更换预览背景", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "图片读取失败，换一张试试", Toast.LENGTH_SHORT).show()
            }
        }
    }

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
                } else {
                    DesktopLyricManager.syncService(context)
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
                        strokeScale = strokeDraft,
                        textColor = textColor,
                        strokeColor = strokeColor,
                        currentLine = previewCurrent,
                        nextLine = previewNext,
                        customBgPath = previewBgPath,
                        onPickBackground = {
                            previewBgPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onResetBackground = {
                            previewBgPath = ""
                            DesktopLyricPreviewBgStore.clear(context)
                        }
                        )
                        QuickActionsCard(
                        locked = locked,
                        onToggleLocked = { locked = it },
                        onResetPosition = {
                            DesktopLyricManager.resetPosition(context)
                            Toast.makeText(context, "已回到默认位置", Toast.LENGTH_SHORT).show()
                        }
                        )
                        EnableCard(
                        effectiveOn = effectiveOn,
                        overlayGranted = overlayGranted,
                        onToggleEnabled = { toggleEnabled(it) }
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
                        ColorCards()
                    }
                }
            } else {
                PreviewCard(
                fontSize = fontSizeDraft,
                strokeScale = strokeDraft,
                textColor = textColor,
                strokeColor = strokeColor,
                currentLine = previewCurrent,
                nextLine = previewNext,
                customBgPath = previewBgPath,
                onPickBackground = {
                    previewBgPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onResetBackground = {
                    previewBgPath = ""
                    DesktopLyricPreviewBgStore.clear(context)
                }
                )
                QuickActionsCard(
                locked = locked,
                onToggleLocked = { locked = it },
                onResetPosition = {
                    DesktopLyricManager.resetPosition(context)
                    Toast.makeText(context, "已回到默认位置", Toast.LENGTH_SHORT).show()
                }
                )
                EnableCard(
                effectiveOn = effectiveOn,
                overlayGranted = overlayGranted,
                onToggleEnabled = { toggleEnabled(it) }
                )
                SizeCard(
                fontSize = fontSizeDraft,
                strokeScale = strokeDraft,
                onFontSizeChange = { fontSizeDraft = it },
                onFontSizeFinished = { fontSize = fontSizeDraft },
                onStrokeChange = { strokeDraft = it },
                onStrokeFinished = { strokeScale = strokeDraft }
                )
                ColorCards()
            }
            Spacer(Modifier.height(bottomInset + 16.dp))
        }
    }

}

/** 字体颜色预设：白、红、暖黄、青、蓝、紫、近黑。 */
private val textPresetColors = listOf(
    AndroidColor.WHITE,
    AndroidColor.parseColor("#FF5252"),
    AndroidColor.parseColor("#FFD166"),
    AndroidColor.parseColor("#8AE9C1"),
    AndroidColor.parseColor("#7CD3FF"),
    AndroidColor.parseColor("#C9A7FF"),
    AndroidColor.parseColor("#1A1A1E")
)

/** 描边颜色预设：黑、白、红、黄、蓝、绿、紫。 */
private val strokePresetColors = listOf(
    AndroidColor.BLACK,
    AndroidColor.WHITE,
    AndroidColor.parseColor("#FF5252"),
    AndroidColor.parseColor("#FFD166"),
    AndroidColor.parseColor("#4FA3FF"),
    AndroidColor.parseColor("#7BE495"),
    AndroidColor.parseColor("#C9A7FF")
)

@Composable
private fun PreviewCard(
    fontSize: Float,
    strokeScale: Float,
    textColor: Int,
    strokeColor: Int,
    currentLine: String,
    nextLine: String,
    customBgPath: String,
    onPickBackground: () -> Unit,
    onResetBackground: () -> Unit
) {
    val context = LocalContext.current
    var background by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(customBgPath) {
        background = withContext(Dispatchers.IO) {
            val bitmap = DesktopLyricPreviewBgStore.load(customBgPath)
                ?: BitmapFactory.decodeResource(
                    context.resources,
                    R.drawable.desktop_lyric_preview_bg
                )
            bitmap?.asImageBitmap()
        }
    }
    val paint = remember {
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(132.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable { onPickBackground() }
    ) {
        val bg = background
        if (bg != null) {
            Image(
                bitmap = bg,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                Modifier
                    .matchParentSize()
                    .background(Color(0xFF101014))
            )
        }
        // 歌词用原生 Canvas 画（与悬浮窗同一套描边算法），字号/描边/颜色所见即所得。
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawBehind {
                    drawLyricPreview(
                        paint = paint,
                        primaryText = currentLine,
                        secondaryText = nextLine,
                        fontSizeSp = fontSize,
                        textColor = textColor,
                        strokeColor = strokeColor,
                        strokeScale = strokeScale
                    )
                }
        )
        if (customBgPath.isNotBlank()) {
            TextButton(
                onClick = onResetBackground,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
                Text(
                    text = "恢复默认",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
        }
    }
    Text(
        text = "点预览换背景图",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp)
    )
    }
}

/** 在预览卡片里画主行 + 副行，几何与 DesktopLyricService 一致。 */
private fun DrawScope.drawLyricPreview(
    paint: TextPaint,
    primaryText: String,
    secondaryText: String,
    fontSizeSp: Float,
    textColor: Int,
    strokeColor: Int,
    strokeScale: Float
) {
    val primarySize = fontSizeSp.sp.toPx()
    val secondarySize = primarySize * 0.66f
    val maxTextWidth = (size.width - 24.dp.toPx()).coerceAtLeast(1f)

    paint.textSize = primarySize
    val primaryMetrics = paint.fontMetrics
    val primaryHeight = primaryMetrics.descent - primaryMetrics.ascent
    paint.textSize = secondarySize
    val secondaryMetrics = paint.fontMetrics
    val secondaryHeight = secondaryMetrics.descent - secondaryMetrics.ascent
    val gap = 6.dp.toPx()
    val top = (size.height - (primaryHeight + gap + secondaryHeight)) / 2f
    val secondaryColor = AndroidColor.argb(
        204,
        AndroidColor.red(textColor),
        AndroidColor.green(textColor),
        AndroidColor.blue(textColor)
    )

    drawIntoCanvas { canvas ->
        val nativeCanvas = canvas.nativeCanvas
        paint.textSize = primarySize
        val primary = TextUtils.ellipsize(
            primaryText,
            paint,
            maxTextWidth,
            TextUtils.TruncateAt.END
        )
        drawStrokedText(
            canvas = nativeCanvas,
            paint = paint,
            text = primary,
            centerX = size.width / 2f,
            baseline = top - primaryMetrics.ascent,
            sizePx = primarySize,
            fillColor = textColor,
            strokeColor = strokeColor,
            strokeScale = strokeScale
        )
        paint.textSize = secondarySize
        val secondary = TextUtils.ellipsize(
            secondaryText,
            paint,
            maxTextWidth,
            TextUtils.TruncateAt.END
        )
        drawStrokedText(
            canvas = nativeCanvas,
            paint = paint,
            text = secondary,
            centerX = size.width / 2f,
            baseline = top + primaryHeight + gap - secondaryMetrics.ascent,
            sizePx = secondarySize,
            fillColor = secondaryColor,
            strokeColor = strokeColor,
            strokeScale = strokeScale
        )
    }
}

private fun drawStrokedText(
    canvas: Canvas,
    paint: Paint,
    text: CharSequence,
    centerX: Float,
    baseline: Float,
    sizePx: Float,
    fillColor: Int,
    strokeColor: Int,
    strokeScale: Float
) {
    if (text.isEmpty()) return
    val strokeWidth = sizePx * 0.17f * strokeScale
    if (strokeWidth > 0.05f) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokeWidth
        paint.color = strokeColor
        canvas.drawText(text, 0, text.length, centerX, baseline, paint)
    }
    paint.style = Paint.Style.FILL
    paint.color = fillColor
    canvas.drawText(text, 0, text.length, centerX, baseline, paint)
}

@Composable
private fun EnableCard(
    effectiveOn: Boolean,
    overlayGranted: Boolean,
    onToggleEnabled: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
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
    }
}

/** 顶部横置小卡片：锁定开关 + 重置位置。 */
@Composable
private fun QuickActionsCard(
    locked: Boolean,
    onToggleLocked: (Boolean) -> Unit,
    onResetPosition: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("锁定位置", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "触摸穿透 · 通知栏开锁",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onResetPosition) { Text("重置位置") }
            Spacer(Modifier.width(4.dp))
            Switch(checked = locked, onCheckedChange = onToggleLocked)
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
                text = "副行（翻译/下一句）随主行等比缩放",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ColorCards() {
    var textColor by rememberPreference(desktopLyricTextColorKey, AndroidColor.WHITE)
    var strokeColor by rememberPreference(desktopLyricStrokeColorKey, AndroidColor.BLACK)
    // 0=关闭，1=字体颜色，2=描边颜色
    var pickerTarget by remember { mutableIntStateOf(0) }

    ColorRowCard(
        title = "字体颜色",
        color = textColor,
        presets = textPresetColors,
        onPick = { textColor = it },
        onOpenPicker = { pickerTarget = 1 }
    )
    ColorRowCard(
        title = "描边颜色",
        color = strokeColor,
        presets = strokePresetColors,
        onPick = { strokeColor = it },
        onOpenPicker = { pickerTarget = 2 }
    )

    if (pickerTarget != 0) {
        ColorPickerDialog(
            initialColor = if (pickerTarget == 1) textColor else strokeColor,
            onDismiss = { pickerTarget = 0 },
            onConfirm = {
                if (pickerTarget == 1) textColor = it else strokeColor = it
                pickerTarget = 0
            }
        )
    }
}

@Composable
private fun ColorRowCard(
    title: String,
    color: Int,
    presets: List<Int>,
    onPick: (Int) -> Unit,
    onOpenPicker: () -> Unit
) {
    val customSelected = presets.none { sameRgb(it, color) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { preset ->
                    ColorSwatch(
                        color = preset,
                        selected = !customSelected && sameRgb(preset, color),
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
