package com.jussicodes.music.lyric

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.datastore.preferences.core.edit
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.jussicodes.music.R
import com.jussicodes.music.constants.desktopLyricStrokeScaleKey
import com.jussicodes.music.constants.desktopLyricStrokeColorKey
import com.jussicodes.music.constants.desktopLyricFontSizeKey
import com.jussicodes.music.constants.desktopLyricLockedKey
import com.jussicodes.music.constants.desktopLyricTextColorKey
import com.jussicodes.music.constants.desktopLyricXFractionKey
import com.jussicodes.music.constants.desktopLyricYFractionKey
import com.jussicodes.music.constants.lyricTranslationEnabledKey
import com.jussicodes.music.playback.PlaybackService
import com.jussicodes.music.utils.AppVisibilityTracker
import com.jussicodes.music.utils.LrcLine
import com.jussicodes.music.utils.dataStore
import com.jussicodes.music.utils.get
import com.jussicodes.music.utils.parseLrc
import com.jussicodes.music.utils.parseYrc
import com.rcmiku.ncmapi.api.player.PlayerApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

/**
 * 桌面歌词悬浮窗。
 *
 * 两个窗口：
 * 1. 歌词窗：宽高固定（宽度按整首歌最长行测量，一首歌内不变），拖动/穿透由它决定；
 * 2. 控制窗：展开时是控制面板，锁定态是右上角的小锁点；位置由歌词窗派生，不参与歌词布局。
 *
 * 关键约束：窗口尺寸固定后，文本长度变化只在内部重排，背景框和文字都不会随句子跳动。
 */
class DesktopLyricService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val windowManager by lazy { getSystemService(Context.WINDOW_SERVICE) as WindowManager }

    private var lyricRoot: LinearLayout? = null
    private var lyricParams: WindowManager.LayoutParams? = null
    private var currentLineView: StrokeTextView? = null
    private var secondaryLineView: StrokeTextView? = null

    private var controlRoot: LinearLayout? = null
    private var controlParams: WindowManager.LayoutParams? = null
    private var panelView: LinearLayout? = null
    private var playPauseButton: ImageButton? = null
    private var translationButton: ImageButton? = null

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var progressJob: Job? = null
    private var preferenceJob: Job? = null
    private var visibilityJob: Job? = null

    private var currentMediaId: String? = null
    private var lyricLines: List<DesktopLyricLine> = emptyList()
    private var currentLineText: String? = null
    private var secondaryLineText: String? = null

    /** 整首歌最长行宽度（px），换歌/改字号/开关翻译时重算。 */
    private var contentWidthPx = 0f

    private var style = DesktopLyricStyle()
    private var xFraction = DesktopLyricDefaultXFraction
    private var yFraction = DesktopLyricDefaultYFraction
    private var panelExpanded = false
    private var hasPlaybackStarted = false
    private var isAppInForeground = AppVisibilityTracker.isAppInForeground.value

    private val lyricBackgroundColor = Color.parseColor("#D92F2F33")
    private val controlBackgroundColor = Color.parseColor("#E62B2B2F")
    private val outlineColor = adjustAlpha(Color.WHITE, 0.1f)
    private val buttonTintColor = Color.parseColor("#F2FFFFFF")
    /** 翻译开启（实心按钮）时图标的挖空色，贴近控制条底色。 */
    private val translationActiveGlyphColor = Color.parseColor("#2A2A2E")
    private val buttonBackgroundColor = adjustAlpha(Color.WHITE, 0.08f)

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            syncTrack(mediaItem)
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            updateLyric()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                hasPlaybackStarted = true
                ensureLyricWindow()
            }
            ensureProgressLoop()
            updatePlaybackButton()
            updateLyric()
        }

        override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
            updateLyric()
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (!DesktopLyricManager.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        xFraction = applicationContext.dataStore.get(desktopLyricXFractionKey, DesktopLyricDefaultXFraction)
        yFraction = applicationContext.dataStore.get(desktopLyricYFractionKey, DesktopLyricDefaultYFraction)
        style = readStyle()
        connectController()
        observePreferences()
        observeAppVisibility()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!DesktopLyricManager.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_RESET_POSITION) {
            resetPosition()
        }
        ensureProgressLoop()
        updatePlaybackButton()
        updateLyric()
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 旋转/分屏/折叠屏：可用区域变了，按比例重新落位（位置比例本身允许落在屏幕外）。
        contentWidthPx = measureContentWidth()
        relayout()
        positionControlWindow()
    }

    override fun onDestroy() {
        progressJob?.cancel()
        preferenceJob?.cancel()
        visibilityJob?.cancel()
        controller?.removeListener(playerListener)
        controllerFuture?.let(MediaController::releaseFuture)
        controller = null
        removeWindows()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun readStyle(): DesktopLyricStyle = DesktopLyricStyle(
        fontSizeSp = applicationContext.dataStore.get(desktopLyricFontSizeKey, DesktopLyricDefaultFontSizeSp),
        textColor = applicationContext.dataStore.get(desktopLyricTextColorKey, Color.WHITE),
        strokeColor = applicationContext.dataStore.get(desktopLyricStrokeColorKey, Color.BLACK),
        strokeScale = applicationContext.dataStore.get(desktopLyricStrokeScaleKey, 1f),
        locked = applicationContext.dataStore.get(desktopLyricLockedKey, false),
        translationEnabled = applicationContext.dataStore.get(lyricTranslationEnabledKey, false),
    )

    private fun connectController() {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync().apply {
            addListener(
                {
                    val mediaController = runCatching { get() }.getOrNull() ?: return@addListener
                    controller?.removeListener(playerListener)
                    controller = mediaController
                    mediaController.addListener(playerListener)
                    hasPlaybackStarted = mediaController.isPlaying
                    if (shouldShowOverlay()) {
                        ensureLyricWindow()
                    }
                    syncTrack(mediaController.currentMediaItem)
                    ensureProgressLoop()
                    updatePlaybackButton()
                    updateLyric()
                },
                mainExecutor
            )
        }
    }

    private fun observePreferences() {
        preferenceJob?.cancel()
        preferenceJob = serviceScope.launch {
            applicationContext.dataStore.data
                .map { prefs ->
                    DesktopLyricStyle(
                        fontSizeSp = prefs[desktopLyricFontSizeKey] ?: DesktopLyricDefaultFontSizeSp,
                        textColor = prefs[desktopLyricTextColorKey] ?: Color.WHITE,
                        strokeColor = prefs[desktopLyricStrokeColorKey] ?: Color.BLACK,
                        strokeScale = prefs[desktopLyricStrokeScaleKey] ?: 1f,
                        locked = prefs[desktopLyricLockedKey] ?: false,
                        translationEnabled = prefs[lyricTranslationEnabledKey] ?: false,
                    )
                }
                .distinctUntilChanged()
                .collectLatest { applyStyle(it) }
        }
    }

    private fun observeAppVisibility() {
        visibilityJob?.cancel()
        visibilityJob = serviceScope.launch {
            AppVisibilityTracker.isAppInForeground.collectLatest { inForeground ->
                isAppInForeground = inForeground
                if (inForeground) {
                    removeWindows()
                } else if (shouldShowOverlay()) {
                    ensureLyricWindow()
                    applyLockState()
                    updateControlVisibility()
                    updatePlaybackButton()
                    updateTranslationState()
                    updateLyric()
                }
            }
        }
    }

    private fun syncTrack(mediaItem: MediaItem?) {
        val mediaId = mediaItem?.mediaId
        if (mediaId.isNullOrBlank()) {
            currentMediaId = null
            lyricLines = emptyList()
            contentWidthPx = 0f
            relayout()
            renderLyric(
                mediaItem?.mediaMetadata?.title?.toString().orEmpty().ifBlank { "暂无歌词" },
                mediaItem?.mediaMetadata?.artist?.toString().orEmpty()
            )
            return
        }
        if (currentMediaId == mediaId && lyricLines.isNotEmpty()) {
            updateLyric()
            return
        }

        currentMediaId = mediaId
        lyricLines = emptyList()
        contentWidthPx = 0f
        relayout()
        renderLyric(
            mediaItem.mediaMetadata.title?.toString().orEmpty().ifBlank { "加载歌词中..." },
            mediaItem.mediaMetadata.artist?.toString().orEmpty()
        )

        serviceScope.launch(Dispatchers.IO) {
            val response = mediaId.toLongOrNull()?.let { PlayerApi.songLyric(it).getOrNull() }
            val parsedLines = response?.let { lyricResponse ->
                val translationLines = lyricResponse.tlyric?.lyric?.parseLrc().orEmpty()
                val translationMap = translationLines
                    .filter { it.text.isNotBlank() }
                    .associate { it.time to it.text }
                val yrcLines = lyricResponse.yrc?.lyric?.parseYrc().orEmpty()
                when {
                    yrcLines.isNotEmpty() -> yrcLines.map { line ->
                        DesktopLyricLine(
                            time = line.time,
                            text = line.text,
                            translation = findTranslationText(line.time, translationMap, translationLines)
                        )
                    }

                    !lyricResponse.lrc?.lyric.isNullOrBlank() -> {
                        lyricResponse.lrc?.lyric?.parseLrc().orEmpty().map { line ->
                            DesktopLyricLine(
                                time = line.time,
                                text = line.text,
                                translation = findTranslationText(line.time, translationMap, translationLines)
                            )
                        }
                    }

                    else -> emptyList()
                }.filter { it.text.isNotBlank() }
            }.orEmpty()

            serviceScope.launch {
                if (currentMediaId != mediaId) return@launch
                lyricLines = parsedLines
                contentWidthPx = measureContentWidth()
                relayout()
                updateLyric()
            }
        }
    }

    private fun ensureProgressLoop() {
        if (progressJob?.isActive == true) return
        progressJob = serviceScope.launch {
            while (isActive) {
                updateLyric()
                delay(if (controller?.isPlaying == true) 250 else 1000)
            }
        }
    }

    private fun updateLyric() {
        if (!hasPlaybackStarted) return
        if (isAppInForeground) {
            removeWindows()
            return
        }
        ensureLyricWindow()

        val mediaController = controller
        val mediaItem = mediaController?.currentMediaItem
        if (mediaItem == null) {
            renderLyric("暂无播放内容", "")
            return
        }

        if (lyricLines.isEmpty()) {
            val fallbackCurrent = mediaItem.mediaMetadata.title?.toString().orEmpty().ifBlank { "暂无歌词" }
            val fallbackSecondary = mediaItem.mediaMetadata.artist?.toString().orEmpty()
            renderLyric(fallbackCurrent, fallbackSecondary)
            return
        }

        val position = mediaController.currentPosition
        val currentIndex = lyricLines.indexOfLast { it.time <= position }
        if (currentIndex < 0) {
            renderLyric(
                mediaItem.mediaMetadata.title?.toString().orEmpty().ifBlank { "暂无歌词" },
                mediaItem.mediaMetadata.artist?.toString().orEmpty()
            )
            return
        }

        val currentLine = lyricLines.getOrNull(currentIndex)
        val currentText = currentLine?.text.orEmpty()
            .ifBlank { mediaItem.mediaMetadata.title?.toString().orEmpty() }
        val secondaryText = if (style.translationEnabled) {
            currentLine?.translation.orEmpty()
                .ifBlank { lyricLines.drop(currentIndex + 1).firstOrNull()?.text.orEmpty() }
        } else {
            lyricLines.drop(currentIndex + 1).firstOrNull()?.text.orEmpty()
        }.ifBlank { mediaItem.mediaMetadata.artist?.toString().orEmpty() }

        renderLyric(currentText, secondaryText)
    }

    private fun renderLyric(current: String, secondary: String) {
        if (current == currentLineText && secondary == secondaryLineText) return
        currentLineText = current
        secondaryLineText = secondary
        currentLineView?.text = current
        secondaryLineView?.text = secondary
        // INVISIBLE 而不是 GONE：保留副行高度，窗口高度和基线都不会跳。
        secondaryLineView?.visibility = if (secondary.isBlank()) View.INVISIBLE else View.VISIBLE
    }

    /** 歌词窗是否应该出现（播过 + App 不在前台）。 */
    private fun shouldShowOverlay(): Boolean = hasPlaybackStarted && !isAppInForeground

    private fun ensureLyricWindow() {
        if (!shouldShowOverlay()) return
        if (lyricRoot != null) return
        createLyricWindow()
    }

    private fun createLyricWindow() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            clipChildren = false
            clipToPadding = false
            setPadding(dp(24), dp(16), dp(24), dp(16))
            // 背景框常态不显示：只在未锁定且展开控制面板时出现（applyBackground 负责切换）。
            background = null
        }

        currentLineView = StrokeTextView(this).apply {
            setTextColor(style.textColor)
            setStrokeColor(style.strokeColor)
            setStrokeWidth(style.primaryStrokeWidth(resources.displayMetrics.density))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, style.fontSizeSp)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            maxLines = 2
            minLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setLineSpacing(0f, 1.06f)
            setShadowLayer(8f, 0f, 2f, adjustAlpha(Color.BLACK, 0.24f))
            text = currentLineText.orEmpty().ifBlank { "暂无歌词" }
        }
        secondaryLineView = StrokeTextView(this).apply {
            setTextColor(style.secondaryTextColor)
            setStrokeColor(style.secondaryStrokeColor)
            setStrokeWidth(style.secondaryStrokeWidth(resources.displayMetrics.density))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, style.secondaryFontSizeSp)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            maxLines = 1
            minLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setLineSpacing(0f, 1.04f)
            setShadowLayer(6f, 0f, 2f, adjustAlpha(Color.BLACK, 0.2f))
            text = secondaryLineText.orEmpty()
            visibility = if (secondaryLineText.isNullOrBlank()) View.INVISIBLE else View.VISIBLE
        }

        root.addView(
            currentLineView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(
            secondaryLineView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(6)
            }
        )

        val width = computeLyricWidth()
        val height = computeLyricHeight()
        val params = WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            lyricWindowFlags(),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // 不做屏幕内夹紧：歌词窗允许拖到屏幕边缘以外。
            x = xFromFraction(width)
            y = yFromFraction(height)
        }

        lyricRoot = root
        lyricParams = params
        root.setOnTouchListener(if (style.locked) null else DragTouchListener(params))
        runCatching { windowManager.addView(root, params) }
    }

    private fun lyricWindowFlags(): Int {
        val base = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        return if (style.locked) base or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else base
    }

    // ------------------------------------------------------------ 尺寸与位置

    private fun computeLyricWidth(): Int {
        val area = screenArea()
        val available = (area.right - area.left).coerceAtLeast(dp(160))
        val maxTotal = ((available * 0.9f).toInt()).coerceAtLeast(dp(120))
        val maxTextWidth = (maxTotal - dp(54)).coerceAtLeast(dp(60))
        val textWidth = if (contentWidthPx > 0f) {
            contentWidthPx
        } else {
            measureTextPaint(style.fontSizeSp)
                .measureText(currentLineText.orEmpty().ifBlank { "暂无歌词" })
        }
        val width = min(textWidth, maxTextWidth.toFloat()).toInt() + dp(54)
        val upperBound = min(maxTotal, available - dp(16)).coerceAtLeast(dp(120))
        return width.coerceIn(dp(120), upperBound)
    }

    private fun computeLyricHeight(): Int {
        val density = resources.displayMetrics.density
        // 注意：Paint 自带 style 属性，apply 里直接写 style.xxx 会被解析成 Paint.Style，
        // 所以先取出外层字段。
        val currentStyle = style
        val mainPaint = Paint().apply {
            textSize = sp(currentStyle.fontSizeSp)
            typeface = Typeface.DEFAULT_BOLD
        }
        val mainMetrics = mainPaint.fontMetrics
        val mainLineHeight = (mainMetrics.descent - mainMetrics.ascent) * 1.06f
        val secondaryPaint = Paint().apply { textSize = sp(currentStyle.secondaryFontSizeSp) }
        val secondaryMetrics = secondaryPaint.fontMetrics
        val secondaryLineHeight = (secondaryMetrics.descent - secondaryMetrics.ascent) * 1.04f
        val strokeSlack = currentStyle.primaryStrokeWidth(density).toInt()
        return dp(16) * 2 + (mainLineHeight * 2f).toInt() + dp(6) +
            secondaryLineHeight.toInt() + strokeSlack + dp(2)
    }

    private fun measureTextPaint(fontSizeSp: Float): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = sp(fontSizeSp)
            typeface = Typeface.DEFAULT_BOLD
        }

    /** 整首歌（主行 + 翻译行）最长行宽度：一首歌内恒定，句句不跳。 */
    private fun measureContentWidth(): Float {
        if (lyricLines.isEmpty()) return 0f
        val mainPaint = measureTextPaint(style.fontSizeSp)
        val secondaryPaint = measureTextPaint(style.secondaryFontSizeSp)
        var maxWidth = 0f
        for (line in lyricLines) {
            maxWidth = maxOf(maxWidth, mainPaint.measureText(line.text))
            if (line.translation.isNotBlank()) {
                maxWidth = maxOf(maxWidth, secondaryPaint.measureText(line.translation))
            }
        }
        return maxWidth
    }

    private fun relayout() {
        val root = lyricRoot ?: return
        val params = lyricParams ?: return
        val width = computeLyricWidth()
        val height = computeLyricHeight()
        if (params.width == width && params.height == height) return
        // 以窗口中心为锚点。整窗宽度是按「本首歌最长行」算的，换歌会变宽/变窄；
        // 只保住左上角的话，窗口内部居中的文字会整体左移右移（用户说的「切歌左移错位」）。
        val centerX = params.x + params.width / 2
        val centerY = params.y + params.height / 2
        params.width = width
        params.height = height
        params.x = centerX - width / 2
        params.y = centerY - height / 2
        runCatching { windowManager.updateViewLayout(root, params) }
        persistPosition()
        positionControlWindow()
    }

    private data class ScreenArea(val left: Int, val top: Int, val right: Int, val bottom: Int)

    private fun screenArea(): ScreenArea {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val bounds = metrics.bounds
            val insets = metrics.windowInsets.getInsets(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )
            return ScreenArea(
                left = bounds.left + insets.left,
                top = bounds.top + insets.top,
                right = bounds.right - insets.right,
                bottom = bounds.bottom - insets.bottom
            )
        }
        val displayMetrics = resources.displayMetrics
        return ScreenArea(0, 0, displayMetrics.widthPixels, displayMetrics.heightPixels)
    }

    private fun clampX(x: Int, width: Int): Int {
        val area = screenArea()
        val minX = area.left + dp(4)
        val maxX = (area.right - dp(4) - width).coerceAtLeast(minX)
        return x.coerceIn(minX, maxX)
    }

    private fun clampY(y: Int, height: Int): Int {
        val area = screenArea()
        val minY = area.top + dp(4)
        val maxY = (area.bottom - dp(4) - height).coerceAtLeast(minY)
        return y.coerceIn(minY, maxY)
    }

    private fun xFromFraction(width: Int): Int {
        val area = screenArea()
        val areaWidth = area.right - area.left
        // 中心锚点：fraction 记录的是中心在屏幕可用宽度里的比例，还原后减半宽得到左边缘。
        return (area.left + areaWidth * xFraction - width / 2f).toInt()
    }

    private fun yFromFraction(height: Int): Int {
        val area = screenArea()
        val areaHeight = area.bottom - area.top
        // 中心锚点：fraction 记录的是中心在屏幕可用高度里的比例，还原后减半高得到顶边缘。
        return (area.top + areaHeight * yFraction - height / 2f).toInt()
    }

    private fun persistPosition() {
        val params = lyricParams ?: return
        val area = screenArea()
        val areaWidth = area.right - area.left
        val areaHeight = area.bottom - area.top
        // 中心锚点：记录窗口中心在屏幕可用区域里的比例，宽度变化时中心不漂移。
        xFraction = if (areaWidth <= 0) 0.5f else (params.x + params.width / 2f - area.left) / areaWidth
        yFraction = if (areaHeight <= 0) 0.5f else (params.y + params.height / 2f - area.top) / areaHeight
        serviceScope.launch {
            applicationContext.dataStore.edit { prefs ->
                prefs[desktopLyricXFractionKey] = xFraction
                prefs[desktopLyricYFractionKey] = yFraction
            }
        }
    }

    private fun resetPosition() {
        xFraction = DesktopLyricDefaultXFraction
        yFraction = DesktopLyricDefaultYFraction
        val params = lyricParams ?: return
        params.x = xFromFraction(params.width)
        params.y = yFromFraction(params.height)
        lyricRoot?.let { root ->
            runCatching { windowManager.updateViewLayout(root, params) }
        }
        positionControlWindow()
        persistPosition()
    }

    // ------------------------------------------------------------ 样式

    private fun applyStyle(newStyle: DesktopLyricStyle) {
        val previous = style
        style = newStyle

        currentLineView?.let { view ->
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, newStyle.fontSizeSp)
            view.setTextColor(newStyle.textColor)
            view.setStrokeWidth(newStyle.primaryStrokeWidth(resources.displayMetrics.density))
            view.setStrokeColor(newStyle.strokeColor)
        }
        secondaryLineView?.let { view ->
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, newStyle.secondaryFontSizeSp)
            view.setTextColor(newStyle.secondaryTextColor)
            view.setStrokeWidth(newStyle.secondaryStrokeWidth(resources.displayMetrics.density))
            view.setStrokeColor(newStyle.secondaryStrokeColor)
        }
        if (previous.locked != newStyle.locked) {
            if (newStyle.locked) panelExpanded = false
            applyBackground()
            applyLockState()
        }
        if (previous.fontSizeSp != newStyle.fontSizeSp ||
            previous.translationEnabled != newStyle.translationEnabled
        ) {
            contentWidthPx = measureContentWidth()
            relayout()
            updateLyric()
        }
        updateTranslationState()
        updateControlVisibility()
    }

    private fun applyBackground() {
        lyricRoot?.background = if (panelExpanded && !style.locked) createLyricBackground() else null
    }

    private fun applyLockState() {
        val params = lyricParams ?: return
        val root = lyricRoot ?: return
        params.flags = lyricWindowFlags()
        root.setOnTouchListener(if (style.locked) null else DragTouchListener(params))
        runCatching { windowManager.updateViewLayout(root, params) }
    }

    private fun createLyricBackground(): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(22).toFloat()
        setColor(lyricBackgroundColor)
        setStroke(dp(1), outlineColor)
    }

    private fun createControlBackground(): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(18).toFloat()
        setColor(controlBackgroundColor)
        setStroke(dp(1), outlineColor)
    }

    // ------------------------------------------------------------ 控制窗

    private fun updateControlVisibility() {
        // 锁定时不再有浮动解锁按钮：解锁入口在通知栏/媒体播放控件的锁图标上。
        if (!shouldShowOverlay() || style.locked || !panelExpanded) {
            removeControlWindow()
            return
        }
        ensureControlWindow()
        panelView?.visibility = View.VISIBLE
        // 面板可能是刚创建的：立刻把播放/翻译状态同步过来，别留在默认图标上。
        updatePlaybackButton()
        updateTranslationState()
        positionControlWindow()
    }

    private fun ensureControlWindow() {
        if (controlRoot != null) return
        val panel = createControlPanel()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        controlRoot = panel
        controlParams = params
        panelView = panel
        runCatching { windowManager.addView(panel, params) }
    }

    private fun removeControlWindow() {
        val root = controlRoot ?: return
        runCatching { windowManager.removeView(root) }
        controlRoot = null
        controlParams = null
        panelView = null
        playPauseButton = null
        translationButton = null
    }

    private fun positionControlWindow() {
        val root = controlRoot ?: return
        val params = controlParams ?: return
        val lyric = lyricParams ?: return
        val measureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        root.measure(measureSpec, measureSpec)
        val controlWidth = root.measuredWidth
        val controlHeight = root.measuredHeight
        if (controlWidth <= 0 || controlHeight <= 0) return

        val area = screenArea()
        var y = lyric.y + lyric.height + dp(8)
        if (y + controlHeight > area.bottom - dp(4)) {
            // 下方放不下就翻到歌词上方
            y = lyric.y - controlHeight - dp(8)
        }
        params.width = controlWidth
        params.height = controlHeight
        params.x = clampX(lyric.x + lyric.width / 2 - controlWidth / 2, controlWidth)
        params.y = clampY(y, controlHeight)
        runCatching { windowManager.updateViewLayout(root, params) }
    }

    private fun createControlPanel(): LinearLayout {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = createControlBackground()
        }

        val previousButton = createActionButton(
            iconRes = android.R.drawable.ic_media_previous,
            description = "上一首",
            sizeDp = 32,
            paddingDp = 6,
            marginDp = 0
        ) {
            controller?.seekToPrevious()
        }

        playPauseButton = createActionButton(
            iconRes = android.R.drawable.ic_media_play,
            description = "播放或暂停",
            sizeDp = 38,
            paddingDp = 8,
            marginDp = 6
        ) {
            val mediaController = controller ?: return@createActionButton
            if (mediaController.isPlaying) mediaController.pause() else mediaController.play()
            updatePlaybackButton()
        }

        val nextButton = createActionButton(
            iconRes = android.R.drawable.ic_media_next,
            description = "下一首",
            sizeDp = 32,
            paddingDp = 6,
            marginDp = 0
        ) {
            controller?.seekToNext()
        }

        val divider = View(this).apply {
            setBackgroundColor(adjustAlpha(Color.WHITE, 0.14f))
        }

        // 翻译开关：只有一个图标按钮，不带任何文字说明；
        // 开启/关闭只用「实心 / 空心」区分（实心 = 白色实底 + 深色图标，空心 = 淡底 + 白色线图标）。
        val translateButton = createActionButton(
            iconRes = R.drawable.ic_lyric_translate,
            description = "歌词翻译",
            sizeDp = 30,
            paddingDp = 6,
            marginDp = 8
        ) {
            toggleTranslation()
        }
        translationButton = translateButton

        val lockButton = createActionButton(
            iconRes = R.drawable.ic_lyric_lock,
            description = "锁定桌面歌词",
            sizeDp = 30,
            paddingDp = 6,
            marginDp = 8
        ) {
            setLocked(true)
        }

        // 关闭：写回 enabled=false 并停掉本 Service（悬浮窗、控制条一起消失，
        // 设置页的开关会因为同一条 DataStore 键自动同步成关闭）。
        val closeButton = createActionButton(
            iconRes = android.R.drawable.ic_menu_close_clear_cancel,
            description = "关闭桌面歌词",
            sizeDp = 30,
            paddingDp = 6,
            marginDp = 8
        ) {
            serviceScope.launch {
                DesktopLyricManager.setEnabled(applicationContext, false)
            }
        }

        panel.addView(previousButton)
        panel.addView(playPauseButton)
        panel.addView(nextButton)
        panel.addView(
            divider,
            LinearLayout.LayoutParams(dp(1), dp(20)).apply {
                marginStart = dp(8)
                marginEnd = dp(6)
            }
        )
        panel.addView(translateButton)
        panel.addView(lockButton)
        panel.addView(closeButton)

        return panel
    }

    private fun createActionButton(
        iconRes: Int,
        description: String,
        sizeDp: Int,
        paddingDp: Int,
        marginDp: Int = 0,
        onClick: () -> Unit
    ): ImageButton = ImageButton(this).apply {
        setImageResource(iconRes)
        contentDescription = description
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(buttonBackgroundColor)
        }
        setColorFilter(buttonTintColor)
        setPadding(dp(paddingDp), dp(paddingDp), dp(paddingDp), dp(paddingDp))
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)).apply {
            marginStart = dp(marginDp)
            marginEnd = dp(marginDp)
        }
        setOnClickListener { onClick() }
    }

    private fun updatePlaybackButton() {
        playPauseButton?.setImageResource(
            if (controller?.isPlaying == true) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        )
        playPauseButton?.setColorFilter(buttonTintColor)
    }

    private fun updateTranslationState() {
        val enabled = style.translationEnabled
        val button = translationButton ?: return
        button.setColorFilter(if (enabled) translationActiveGlyphColor else buttonTintColor)
        button.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (enabled) Color.WHITE else buttonBackgroundColor)
        }
        button.contentDescription = if (enabled) "歌词翻译：开" else "歌词翻译：关"
    }

    private fun togglePanel() {
        if (panelExpanded) {
            collapsePanel()
            return
        }
        panelExpanded = true
        applyBackground()
        updateControlVisibility()
    }

    private fun collapsePanel() {
        if (!panelExpanded) return
        panelExpanded = false
        applyBackground()
        updateControlVisibility()
    }

    private fun toggleTranslation() {
        val target = !style.translationEnabled
        style = style.copy(translationEnabled = target)
        updateTranslationState()
        serviceScope.launch {
            applicationContext.dataStore.edit {
                it[lyricTranslationEnabledKey] = target
            }
        }
    }

    private fun setLocked(locked: Boolean) {
        style = style.copy(locked = locked)
        if (locked) panelExpanded = false
        applyBackground()
        applyLockState()
        updateControlVisibility()
        serviceScope.launch {
            applicationContext.dataStore.edit {
                it[desktopLyricLockedKey] = locked
            }
        }
    }

    // ------------------------------------------------------------ 清理

    private fun removeWindows() {
        runCatching { lyricRoot?.let { windowManager.removeView(it) } }
        removeControlWindow()
        lyricRoot = null
        lyricParams = null
        currentLineView = null
        secondaryLineView = null
        currentLineText = null
        secondaryLineText = null
        panelExpanded = false
    }

    // ------------------------------------------------------------ 工具

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun sp(value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        value,
        resources.displayMetrics
    )

    private fun adjustAlpha(color: Int, alpha: Float): Int {
        val alphaValue = (Color.alpha(color) * alpha).toInt().coerceIn(0, 255)
        return Color.argb(alphaValue, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun findTranslationText(
        time: Long,
        translationMap: Map<Long, String>,
        translationLines: List<LrcLine>
    ): String {
        translationMap[time]?.takeIf { it.isNotBlank() }?.let { return it }
        return translationLines.minByOrNull { abs(it.time - time) }
            ?.takeIf { abs(it.time - time) <= 800 && it.text.isNotBlank() }
            ?.text
            .orEmpty()
    }

    private inner class DragTouchListener(
        private val layoutParams: WindowManager.LayoutParams
    ) : View.OnTouchListener {
        private var startX = 0
        private var startY = 0
        private var touchX = 0f
        private var touchY = 0f
        private var moved = false
        private val touchSlop = dp(8)

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = layoutParams.x
                    startY = layoutParams.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - touchX
                    val deltaY = event.rawY - touchY
                    if (!moved && (abs(deltaX) > touchSlop || abs(deltaY) > touchSlop)) {
                        moved = true
                        // 拖动期间控制面板与背景框保持展开，松手后也留着。
                        if (!panelExpanded) {
                            panelExpanded = true
                            applyBackground()
                            updateControlVisibility()
                        }
                    }
                    // 不做边界夹紧：歌词窗可以拖到屏幕边缘以外。
                    layoutParams.x = startX + deltaX.toInt()
                    layoutParams.y = startY + deltaY.toInt()
                    windowManager.updateViewLayout(v, layoutParams)
                    positionControlWindow()
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        persistPosition()
                    } else {
                        togglePanel()
                    }
                    return true
                }
            }
            return false
        }
    }

    private data class DesktopLyricLine(
        val time: Long,
        val text: String,
        val translation: String = ""
    )

    private class StrokeTextView(context: Context) : TextView(context) {
        private var strokeColor: Int = Color.BLACK
        private var strokeWidthPx: Float = 0f

        fun setStrokeColor(color: Int) {
            strokeColor = color
            invalidate()
        }

        fun setStrokeWidth(widthPx: Float) {
            strokeWidthPx = widthPx
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val fillColor = currentTextColor
            if (strokeWidthPx > 0f) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = strokeWidthPx
                setTextColor(strokeColor)
                super.onDraw(canvas)
            }
            paint.style = Paint.Style.FILL
            paint.strokeWidth = 0f
            setTextColor(fillColor)
            super.onDraw(canvas)
        }
    }

    companion object {
        /** 设置页「重置位置」用：让运行中的 Service 回到默认位置并写回比例。 */
        const val ACTION_RESET_POSITION = "com.jussicodes.music.action.DESKTOP_LYRIC_RESET_POSITION"
    }
}
