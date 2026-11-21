package com.jussicodes.music.ui.screen

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitDragOrCancellation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavHostController
import com.jussicodes.music.LocalUiScaleController
import com.jussicodes.music.constants.MiniPlayerHeight
import com.jussicodes.music.constants.floatingNavBarEnabledKey
import com.jussicodes.music.constants.homeBgBlurKey
import com.jussicodes.music.constants.homeBgImageKey
import com.jussicodes.music.constants.homeBgOffsetXKey
import com.jussicodes.music.constants.homeBgOffsetYKey
import com.jussicodes.music.constants.homeBgOpacityKey
import com.jussicodes.music.constants.homeBgScaleKey
import com.jussicodes.music.constants.isLandscapePhone
import com.jussicodes.music.constants.isLandscapeWide
import com.jussicodes.music.constants.lyricFontScaleKey
import com.jussicodes.music.constants.playerBackgroundBlurEnabledKey
import com.jussicodes.music.constants.themeColorSourceKey
import com.jussicodes.music.constants.themeModeKey
import com.jussicodes.music.constants.themePrimaryColorKey
import com.jussicodes.music.constants.themeSecondaryColorKey
import com.jussicodes.music.data.HomeBgEditor
import com.jussicodes.music.data.HomeBgStore
import com.jussicodes.music.data.HomeBgTextStore
import com.jussicodes.music.data.HomeTransparentCardStore
import com.jussicodes.music.ui.components.HomeBackgroundConfig
import com.jussicodes.music.ui.components.ResponsiveAlertDialog
import com.jussicodes.music.ui.components.isLandscapeOrientation
import com.jussicodes.music.ui.navigation.Screen
import com.jussicodes.music.ui.theme.ThemeColorSource
import com.jussicodes.music.utils.dataStore
import com.jussicodes.music.utils.rememberEnumPreference
import com.jussicodes.music.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(navController: NavHostController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val uiScaleController = LocalUiScaleController.current
    var uiScaleDraft by remember { mutableFloatStateOf(uiScaleController.scale) }
    var fontScaleDraft by remember { mutableFloatStateOf(uiScaleController.fontScale) }
    var uiScaleDragging by remember { mutableStateOf(false) }
    var fontScaleDragging by remember { mutableStateOf(false) }
    LaunchedEffect(uiScaleController.scale, uiScaleDragging) {
        if (!uiScaleDragging) uiScaleDraft = uiScaleController.scale
    }
    LaunchedEffect(uiScaleController.fontScale, fontScaleDragging) {
        if (!fontScaleDragging) fontScaleDraft = uiScaleController.fontScale
    }

    fun updateUiScaleDraft(value: Float) {
        uiScaleDragging = true
        uiScaleDraft = value
    }

    fun finishUiScaleDrag() {
        val snapped = listOf(0.7f, 0.8f, 0.9f, 1f, 1.1f).minByOrNull { abs(it - uiScaleDraft) } ?: 1f
        uiScaleDraft = snapped
        uiScaleController.setScale(snapped)
        uiScaleDragging = false
    }

    fun updateFontScaleDraft(value: Float) {
        fontScaleDragging = true
        fontScaleDraft = value
    }

    fun finishFontScaleDrag() {
        val snapped = listOf(0.8f, 0.9f, 1f, 1.1f, 1.2f, 1.3f).minByOrNull { abs(it - fontScaleDraft) } ?: 1f
        fontScaleDraft = snapped
        uiScaleController.setFontScale(snapped)
        fontScaleDragging = false
    }

    var textShadowDraft by remember { mutableFloatStateOf(0f) }
    var bgSelectFailureReason by remember { mutableStateOf<String?>(null) }
    val wallpaperColorAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    var themeMode by rememberPreference(themeModeKey, "system")
    var themePrimaryColor by rememberPreference(themePrimaryColorKey, 0L)
    var themeSecondaryColor by rememberPreference(themeSecondaryColorKey, 0L)
    val customColors = themePrimaryColor != 0L || themeSecondaryColor != 0L
    var showPaletteDialog by remember { mutableStateOf(false) }
    var lyricFontScale by rememberPreference(lyricFontScaleKey, 0f)
    val themeColorSource by rememberEnumPreference(
        themeColorSourceKey,
        defaultValue = ThemeColorSource.WALLPAPER,
    )
    var floatingNavBarEnabled by rememberPreference(floatingNavBarEnabledKey, true)
    var playerBackgroundBlurEnabled by rememberPreference(playerBackgroundBlurEnabledKey, true)
    val homeBgPath by rememberPreference(homeBgImageKey, "")
    val homeBgScale by rememberPreference(homeBgScaleKey, 1f)
    val homeBgOpacity by rememberPreference(homeBgOpacityKey, 1f)
    val homeBgBlur by rememberPreference(homeBgBlurKey, 0f)
    val homeBgOffsetX by rememberPreference(homeBgOffsetXKey, 0f)
    val homeBgOffsetY by rememberPreference(homeBgOffsetYKey, 0f)
    val homeTransparentCard = HomeTransparentCardStore.state.collectAsState().value
    val homeBgTextColor = HomeBgTextStore.state.collectAsState().value
    val homeBgTextShadow = HomeBgTextStore.shadow.collectAsState().value
    androidx.compose.runtime.LaunchedEffect(homeBgTextShadow) {
        textShadowDraft = homeBgTextShadow.coerceIn(0f, 10f)
    }
    val homeBg = remember(homeBgPath, homeBgScale, homeBgOpacity, homeBgBlur, homeBgOffsetX, homeBgOffsetY) {
        HomeBackgroundConfig(homeBgPath, homeBgScale, homeBgOpacity, homeBgBlur, homeBgOffsetX, homeBgOffsetY)
    }

    fun selectThemeColorSource(source: ThemeColorSource? = null) {
        coroutineScope.launch {
            context.dataStore.edit { prefs ->
                if (source != null) prefs[themeColorSourceKey] = source.name
                prefs[themePrimaryColorKey] = 0L
                prefs[themeSecondaryColorKey] = 0L
            }
        }
    }

    fun startHomeBgEdit(config: HomeBackgroundConfig, ownsFile: Boolean) {
        HomeBgEditor.begin(config, ownsFile)
        navController.navigate(Screen.Library.route) {
            popUpTo(Screen.Settings.route) { inclusive = true }
            launchSingleTop = true
        }
    }

    val homeBgPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                when (val result = HomeBgStore.select(context, uri)) {
                    is HomeBgStore.HomeBgSelectResult.Success -> startHomeBgEdit(
                        HomeBackgroundConfig(
                            path = result.path,
                            scale = homeBgScale,
                            opacity = homeBgOpacity,
                            blur = homeBgBlur,
                            offsetX = homeBgOffsetX,
                            offsetY = homeBgOffsetY,
                        ),
                        ownsFile = true,
                    )
                    is HomeBgStore.HomeBgSelectResult.Failure -> bgSelectFailureReason = result.reason
                }
            }
        }
    }

    fun removeHomeBg() {
        coroutineScope.launch {
            context.dataStore.edit { prefs ->
                prefs[homeBgImageKey] = ""
                prefs[homeBgScaleKey] = 1f
                prefs[homeBgOpacityKey] = 1f
                prefs[homeBgBlurKey] = 0f
                prefs[homeBgOffsetXKey] = 0f
                prefs[homeBgOffsetYKey] = 0f
            }
            withContext(Dispatchers.IO) {
                HomeBgEditor.cancel(context)
                HomeBgStore.clear(context)
            }
        }
    }

    val landscape = isLandscapePhone() || isLandscapeWide()
    val bottomInset = MiniPlayerHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("外观") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (landscape) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ThemeModeGroup(themeMode) { themeMode = it }
                        ThemeColorGroup(
                            current = themeColorSource,
                            wallpaperColorAvailable = wallpaperColorAvailable,
                            customColors = customColors,
                            onSelected = ::selectThemeColorSource,
                            onCustomColorsClick = { showPaletteDialog = true },
                        )
                        VisualEffectsGroup(
                            floatingNavBarEnabled,
                            { floatingNavBarEnabled = it },
                            playerBackgroundBlurEnabled,
                            { playerBackgroundBlurEnabled = it },
                            homeTransparentCard,
                        ) { value -> coroutineScope.launch { HomeTransparentCardStore.set(context, value) } }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ScaleGroup(
                            uiScaleDraft, ::updateUiScaleDraft, ::finishUiScaleDrag,
                            fontScaleDraft, ::updateFontScaleDraft, ::finishFontScaleDrag,
                        )
                        LyricGroup(lyricFontScale) { lyricFontScale = it }
                        TextEffectsGroup(homeBgTextColor, textShadowDraft, { textShadowDraft = it }, context, coroutineScope)
                        BackgroundGroup(homeBg, homeBgPicker, ::removeHomeBg) {
                            startHomeBgEdit(homeBg, ownsFile = false)
                        }
                    }
                }
            } else {
                ThemeModeGroup(themeMode) { themeMode = it }
                ThemeColorGroup(
                    current = themeColorSource,
                    wallpaperColorAvailable = wallpaperColorAvailable,
                    customColors = customColors,
                    onSelected = ::selectThemeColorSource,
                    onCustomColorsClick = { showPaletteDialog = true },
                )
                VisualEffectsGroup(
                    floatingNavBarEnabled,
                    { floatingNavBarEnabled = it },
                    playerBackgroundBlurEnabled,
                    { playerBackgroundBlurEnabled = it },
                    homeTransparentCard,
                ) { value -> coroutineScope.launch { HomeTransparentCardStore.set(context, value) } }
                ScaleGroup(
                    uiScaleDraft, ::updateUiScaleDraft, ::finishUiScaleDrag,
                    fontScaleDraft, ::updateFontScaleDraft, ::finishFontScaleDrag,
                )
                LyricGroup(lyricFontScale) { lyricFontScale = it }
                TextEffectsGroup(homeBgTextColor, textShadowDraft, { textShadowDraft = it }, context, coroutineScope)
                BackgroundGroup(homeBg, homeBgPicker, ::removeHomeBg) {
                    startHomeBgEdit(homeBg, ownsFile = false)
                }
            }
            Spacer(Modifier.height(bottomInset))
        }
    }

    bgSelectFailureReason?.let { reason ->
        AlertDialog(
            onDismissRequest = { bgSelectFailureReason = null },
            title = { Text("选取图片失败") },
            text = {
                Text(
                    "原因：$reason\n\n多为该图片源不可用（文件被删/移动，或临时读授权失效），请重选一张。",
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = { bgSelectFailureReason = null }) { Text("知道了") }
            },
        )
    }

    if (showPaletteDialog) {
        val paletteLandscape = isLandscapeOrientation()
        ResponsiveAlertDialog(
            onDismissRequest = { showPaletteDialog = false },
            title = { Text("自定义主题颜色") },
            text = {
                ThemeColorPaletteContent(
                    primaryColor = themePrimaryColor,
                    secondaryColor = themeSecondaryColor,
                    customColors = customColors,
                    landscape = paletteLandscape,
                    onPrimaryColorChange = { themePrimaryColor = it },
                    onSecondaryColorChange = { themeSecondaryColor = it },
                )
            },
            confirmButton = {
                TextButton(onClick = { showPaletteDialog = false }) { Text("完成") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        selectThemeColorSource()
                        showPaletteDialog = false
                    },
                    enabled = customColors,
                ) { Text("恢复取色") }
            },
        )
    }
}

@Composable
private fun ThemeModeGroup(current: String, onSelected: (String) -> Unit) {
    AppearanceGroup(title = "显示模式") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (mode, label) ->
                val isDarkCard = mode == "dark"
                val selected = mode == current || (isDarkCard && current == "amoled")
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = {
                            onSelected(if (isDarkCard && current == "dark") "amoled" else mode)
                        })
                        .padding(horizontal = if (isDarkCard) 4.dp else 8.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                    if (isDarkCard) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            when (current) {
                                "dark" -> "深色"
                                "amoled" -> "AMOLED 深色"
                                else -> "深色 / 纯黑"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeColorGroup(
    current: ThemeColorSource,
    wallpaperColorAvailable: Boolean,
    customColors: Boolean,
    onSelected: (ThemeColorSource) -> Unit,
    onCustomColorsClick: () -> Unit,
) {
    AppearanceGroup(title = "主题颜色") {
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (ThemeColorSource.entries + listOf(null)).forEach { source ->
                val selected = if (source == null) customColors else !customColors && source == current
                val (title, subtitle) = when (source) {
                    ThemeColorSource.WALLPAPER -> "壁纸" to (if (wallpaperColorAvailable) "系统原色" else "不可用")
                    ThemeColorSource.ARTWORK -> "封面" to "抽取封面"
                    ThemeColorSource.HOME_BG -> "主页" to "抽取主页图"
                    null -> "自定义" to (if (customColors) "已启用" else "自选")
                }
                val accessibility = if (source == null) {
                    Modifier.semantics { contentDescription = "自定义主题颜色" }
                } else Modifier
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = {
                            if (source == null) onCustomColorsClick() else onSelected(source)
                        })
                        .then(accessibility)
                        .padding(horizontal = 2.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                    Spacer(Modifier.height(2.dp))
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}

private data class PaletteHsv(val hue: Float, val saturation: Float, val value: Float) {
    fun toColor() = Color.hsv(hue, saturation, value)
}

@Composable
private fun ThemeColorPaletteContent(
    primaryColor: Long,
    secondaryColor: Long,
    customColors: Boolean,
    landscape: Boolean,
    onPrimaryColorChange: (Long) -> Unit,
    onSecondaryColorChange: (Long) -> Unit,
) {
    var editingPrimary by remember { mutableStateOf(primaryColor != 0L || secondaryColor == 0L) }
    val scheme = MaterialTheme.colorScheme
    val primary = if (primaryColor != 0L) Color(primaryColor.toInt()) else scheme.primary
    val secondary = if (secondaryColor != 0L) Color(secondaryColor.toInt()) else scheme.secondary
    val activeColor = if (editingPrimary) primary else secondary
    val activeSeed = if (editingPrimary) primaryColor else secondaryColor
    val initial = remember(activeColor) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(activeColor.toArgb(), hsv)
        PaletteHsv(hsv[0], hsv[1], hsv[2].coerceIn(0.2f, 1f))
    }
    var draft by remember(editingPrimary, activeSeed, activeColor) { mutableStateOf<PaletteHsv?>(null) }
    var dragPreview by remember(editingPrimary) { mutableStateOf<PaletteHsv?>(null) }
    val previewHsv = dragPreview ?: draft
    val hsv = previewHsv ?: initial
    val preview = previewHsv?.toColor()

    fun commitColor(selection: PaletteHsv) {
        draft = selection
        val color = selection.toColor().toArgb().toLong()
        if (editingPrimary) onPrimaryColorChange(color) else onSecondaryColorChange(color)
    }

    val colorChips: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(true to "主色", false to "副色").forEach { (isPrimary, label) ->
                val selected = editingPrimary == isPrimary
                val storedColor = if (isPrimary) primaryColor else secondaryColor
                val color = if (selected && preview != null) preview else if (isPrimary) primary else secondary
                FilterChip(
                    selected = selected,
                    onClick = { editingPrimary = isPrimary },
                    label = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(label)
                            Box(
                                Modifier.size(16.dp)
                                    .background(color, CircleShape)
                                    .border(1.dp, scheme.outline.copy(alpha = if (storedColor == 0L) 0.35f else 1f), CircleShape),
                            )
                        }
                    },
                )
            }
        }
    }
    val wheel: @Composable () -> Unit = {
        key(editingPrimary) {
            ThemeColorWheel(
                hue = hsv.hue,
                saturation = hsv.saturation,
                onTapColor = { hue, saturation ->
                    commitColor((draft ?: initial).copy(hue = hue, saturation = saturation))
                },
                onDragColor = { hue, saturation ->
                    dragPreview = (dragPreview ?: draft ?: initial).copy(hue = hue, saturation = saturation)
                },
                onDragEnd = { hue, saturation ->
                    val selection = (dragPreview ?: draft ?: initial).copy(hue = hue, saturation = saturation)
                    dragPreview = null
                    commitColor(selection)
                },
                onDragCancel = { dragPreview = null },
            )
        }
    }
    val colorControls: @Composable () -> Unit = {
        AppearanceSliderRow(
            label = "明度",
            value = hsv.value,
            valueText = "${(hsv.value * 100).roundToInt()}%",
            range = 0.2f..1f,
            steps = 15,
            onValueChange = { draft = (draft ?: initial).copy(value = it) },
            sliderModifier = Modifier.fillMaxWidth(),
            onFinished = { draft?.let(::commitColor) },
        )
        Text(
            if (customColors) "正在使用自定义配色" else "当前跟随取色来源",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
        )
    }

    if (landscape) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { wheel() }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                colorChips()
                colorControls()
            }
        }
    } else {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            colorChips()
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { wheel() }
            colorControls()
        }
    }
}

@Composable
private fun ThemeColorWheel(
    hue: Float,
    saturation: Float,
    onTapColor: (Float, Float) -> Unit,
    onDragColor: (Float, Float) -> Unit,
    onDragEnd: (Float, Float) -> Unit,
    onDragCancel: () -> Unit,
) {
    val tap by rememberUpdatedState(onTapColor)
    val previewDrag by rememberUpdatedState(onDragColor)
    val finishDrag by rememberUpdatedState(onDragEnd)
    val cancelDrag by rememberUpdatedState(onDragCancel)
    val hueBrush = remember {
        Brush.sweepGradient(List(7) { Color.hsv((it % 6) * 60f, 1f, 1f) })
    }
    val saturationBrush = remember {
        Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0f)))
    }

    fun pickColor(position: Offset, bounds: IntSize, onPicked: (Float, Float) -> Unit) {
        val radius = minOf(bounds.width, bounds.height) / 2f
        if (radius <= 0f) return
        val delta = position - Offset(bounds.width / 2f, bounds.height / 2f)
        val angle = atan2(delta.y, delta.x) * (180f / PI.toFloat())
        onPicked((angle + 360f) % 360f, (delta.getDistance() / radius).coerceIn(0f, 1f))
    }

    Canvas(
        Modifier.widthIn(max = 220.dp)
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(6.dp)
            .semantics { contentDescription = "色轮，点按或拖动取色" }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { pickColor(it, size, tap) })
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                        ?: return@awaitEachGesture
                    pickColor(start.position, size, previewDrag)

                    var pointerId = start.id
                    // 读取抬起事件的位置，避免提交上一帧的拖动坐标。
                    while (true) {
                        val change = awaitDragOrCancellation(pointerId)
                        if (change == null) {
                            cancelDrag()
                            break
                        }
                        if (change.changedToUpIgnoreConsumed()) {
                            change.consume()
                            pickColor(change.position, size, finishDrag)
                            break
                        }
                        change.consume()
                        pickColor(change.position, size, previewDrag)
                        pointerId = change.id
                    }
                }
            },
    ) {
        drawCircle(hueBrush)
        drawCircle(saturationBrush)
        val angle = hue * (PI.toFloat() / 180f)
        val distance = size.minDimension / 2f * saturation
        val position = center + Offset(cos(angle), sin(angle)) * distance
        drawCircle(Color.White, radius = 5.dp.toPx(), center = position)
        drawCircle(Color.Black, radius = 5.dp.toPx(), center = position, style = Stroke(width = 1.dp.toPx()))
    }
}

@Composable
private fun VisualEffectsGroup(
    floating: Boolean,
    onFloatingChange: (Boolean) -> Unit,
    blur: Boolean,
    onBlurChange: (Boolean) -> Unit,
    transparentCard: Boolean,
    onTransparentCardChange: (Boolean) -> Unit,
) {
    val blurAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    AppearanceGroup(title = "视觉与动效") {
        AppearanceCard {
            AppearanceSwitchRow("悬浮胶囊底栏", if (floating) "已开启 · 悬浮胶囊样式" else "已关闭 · 经典贴底通栏", floating, enabled = true, onCheckedChange = onFloatingChange)
            AppearanceSwitchRow(
                title = "上滑背景高斯模糊",
                subtitle = when {
                    !blurAvailable -> "Android 12 及以上可用"
                    blur -> "已开启 · 动态高斯模糊"
                    else -> "已关闭 · 景深遮罩（省电流畅）"
                },
                checked = blurAvailable && blur,
                enabled = blurAvailable,
                onCheckedChange = onBlurChange,
            )
            AppearanceSwitchRow("主页透明卡片", if (transparentCard) "已开启 · 半透明毛玻璃质感" else "已关闭 · 实色卡片容器", transparentCard, enabled = true, onCheckedChange = onTransparentCardChange)
        }
    }
}

@Composable
private fun ScaleGroup(
    uiScaleValue: Float,
    onUiScaleChange: (Float) -> Unit,
    onUiScaleFinished: () -> Unit,
    fontScaleValue: Float,
    onFontScaleChange: (Float) -> Unit,
    onFontScaleFinished: () -> Unit,
) {
    AppearanceGroup(title = "界面与字体缩放") {
        AppearanceCard {
            AppearanceSliderRow(
                "界面缩放", uiScaleValue, "${(uiScaleValue * 100).roundToInt()}%", 0.7f..1.1f, 3, onUiScaleChange,
                sliderModifier = Modifier.semantics { contentDescription = "界面缩放" },
                onFinished = onUiScaleFinished,
            )
            AppearanceSliderRow(
                "字体大小", fontScaleValue, "${(fontScaleValue * 100).roundToInt()}%", 0.8f..1.3f, 4, onFontScaleChange,
                sliderModifier = Modifier.semantics { contentDescription = "字体大小" },
                onFinished = onFontScaleFinished,
            )
        }
    }
}

@Composable
private fun LyricGroup(currentScale: Float, onScaleChange: (Float) -> Unit) {
    val followDefault = currentScale == 0f
    var draft by remember(currentScale) {
        mutableFloatStateOf(if (followDefault) 1f else currentScale.coerceIn(0.8f, 1.4f))
    }
    AppearanceGroup(title = "歌词") {
        AppearanceCard {
            AppearanceSwitchRow("跟随默认", if (followDefault) "使用原有歌词字号" else "使用独立歌词字号", followDefault, enabled = true) { follow ->
                onScaleChange(if (follow) 0f else draft)
            }
            if (!followDefault) {
                AppearanceSliderRow(
                    "歌词字号", draft, "${(draft * 100).roundToInt()}%", 0.8f..1.4f, 5, { draft = it },
                    sliderModifier = Modifier.semantics { contentDescription = "歌词字号" },
                ) {
                    val snapped = (0.8f + ((draft - 0.8f) / 0.1f).roundToInt() * 0.1f).coerceIn(0.8f, 1.4f)
                    draft = snapped
                    onScaleChange(snapped)
                }
            }
        }
    }
}

@Composable
private fun TextEffectsGroup(
    textColor: String,
    textShadow: Float,
    onTextShadowChange: (Float) -> Unit,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    val presets = listOf(
        Triple("auto", "自动", null), Triple("white", "纯白", Color(0xFFF5F5F5)),
        Triple("warm_white", "米白", Color(0xFFFFF8E7)), Triple("black", "纯黑", Color(0xDE000000)),
        Triple("yellow", "暖黄", Color(0xFFFFD54F)), Triple("cyan", "青绿", Color(0xFF80DEEA)),
        Triple("blue", "冰蓝", Color(0xFF90CAF9)), Triple("pink", "淡粉", Color(0xFFF48FB1)),
    )
    AppearanceGroup(title = "主页文字效果", subtitle = "自定义「我的」与「探索」页在壁纸下的文字颜色与柔和阴影轮廓") {
        AppearanceCard {
            Text("字体颜色", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                presets.forEach { (mode, label, preview) ->
                    FilterChip(
                        selected = textColor == mode,
                        onClick = { scope.launch { HomeBgTextStore.set(context, mode) } },
                        leadingIcon = if (preview != null) {
                            {
                                Box(Modifier.size(14.dp).clip(CircleShape).background(preview).border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), CircleShape))
                            }
                        } else null,
                        label = { Text(label) },
                    )
                }
            }
            AppearanceSliderRow("柔和轮廓", textShadow, if (textShadow <= 0.05f) "关闭" else "${(textShadow * 10).roundToInt() / 10f} dp", 0f..10f, 19, onTextShadowChange, sliderModifier = Modifier) {
                val snapped = ((textShadow * 10).roundToInt() / 10f).coerceIn(0f, 10f)
                onTextShadowChange(snapped)
                scope.launch { HomeBgTextStore.setShadow(context, snapped) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BackgroundGroup(
    homeBg: HomeBackgroundConfig,
    picker: androidx.activity.result.ActivityResultLauncher<String>,
    onRemove: () -> Unit,
    onTune: () -> Unit,
) {
    AppearanceGroup(title = "主页背景", subtitle = "在「我的」和「探索」页使用的本地图片背景") {
        AppearanceCard {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (homeBg.enabled) "已设置背景图" else "未设置背景图", style = MaterialTheme.typography.bodyMedium)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextButton(onClick = { picker.launch("image/*") }) { Text(if (homeBg.enabled) "更换" else "选取图片") }
                    if (homeBg.enabled) {
                        TextButton(onClick = onTune) { Text("在主页调整") }
                        TextButton(onClick = onRemove) { Text("移除") }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppearanceGroup(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 2.dp))
        content()
    }
}

@Composable
private fun AppearanceCard(content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

@Composable
private fun AppearanceSwitchRow(title: String, subtitle: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun AppearanceSliderRow(
    label: String,
    value: Float,
    valueText: String,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    sliderModifier: Modifier,
    onFinished: () -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(valueText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(modifier = sliderModifier, value = value.coerceIn(range), onValueChange = onValueChange, onValueChangeFinished = onFinished, valueRange = range, steps = steps)
    }
}
