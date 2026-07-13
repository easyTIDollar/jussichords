package com.jussicodes.music.lyric

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
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
import com.jussicodes.music.constants.lyricTranslationEnabledKey
import com.jussicodes.music.playback.PlaybackService
import com.jussicodes.music.utils.LrcLine
import com.jussicodes.music.utils.AppVisibilityTracker
import com.jussicodes.music.utils.dataStore
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

class DesktopLyricService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var rootView: LinearLayout? = null
    private var lyricContainerView: LinearLayout? = null
    private var controlPanelView: LinearLayout? = null
    private var currentLineView: StrokeTextView? = null
    private var secondaryLineView: StrokeTextView? = null
    private var playPauseButton: ImageButton? = null
    private var translationButton: ImageButton? = null
    private var translationStateView: TextView? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var progressJob: Job? = null
    private var preferenceJob: Job? = null
    private var visibilityJob: Job? = null
    private var currentMediaId: String? = null
    private var lyricLines: List<DesktopLyricLine> = emptyList()
    private var currentLineText: String? = null
    private var secondaryLineText: String? = null
    private var translationEnabled = false
    private var panelExpanded = false
    private var hasPlaybackStarted = false
    private var isAppInForeground = AppVisibilityTracker.isAppInForeground.value

    private val lyricBackgroundColor = Color.parseColor("#D92F2F33")
    private val controlBackgroundColor = Color.parseColor("#E62B2B2F")
    private val outlineColor = adjustAlpha(Color.WHITE, 0.1f)
    private val primaryTextColor = Color.WHITE
    private val secondaryTextColor = Color.parseColor("#F0F0F0")
    private val textStrokeColor = adjustAlpha(Color.BLACK, 0.82f)
    private val secondaryStrokeColor = adjustAlpha(Color.BLACK, 0.74f)
    private val buttonTintColor = Color.parseColor("#F2FFFFFF")
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
                ensureOverlayView()
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
        connectController()
        observePreferences()
        observeAppVisibility()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!DesktopLyricManager.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        ensureProgressLoop()
        updatePlaybackButton()
        updateLyric()
        return START_STICKY
    }

    override fun onDestroy() {
        progressJob?.cancel()
        preferenceJob?.cancel()
        visibilityJob?.cancel()
        controller?.removeListener(playerListener)
        controllerFuture?.let(MediaController::releaseFuture)
        controller = null
        removeOverlayView()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

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
                        ensureOverlayView()
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
                .map { it[lyricTranslationEnabledKey] ?: false }
                .collectLatest { enabled ->
                    translationEnabled = enabled
                    updateTranslationButton()
                    updateLyric()
                }
        }
    }

    private fun observeAppVisibility() {
        visibilityJob?.cancel()
        visibilityJob = serviceScope.launch {
            AppVisibilityTracker.isAppInForeground.collectLatest { inForeground ->
                isAppInForeground = inForeground
                if (inForeground) {
                    removeOverlayView()
                } else if (shouldShowOverlay()) {
                    ensureOverlayView()
                    updatePlaybackButton()
                    updateTranslationButton()
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
            removeOverlayView()
            return
        }
        ensureOverlayView()

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
        val secondaryText = if (translationEnabled) {
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
        secondaryLineView?.visibility = if (secondary.isBlank()) View.GONE else View.VISIBLE
    }

    private fun ensureOverlayView() {
        if (!shouldShowOverlay()) return
        if (overlayView != null) return
        createOverlayView()
    }

    private fun shouldShowOverlay(): Boolean =
        hasPlaybackStarted && !isAppInForeground

    private fun createOverlayView() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }
        rootView = container

        lyricContainerView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(24), dp(16), dp(24), dp(16))
            background = null
        }

        currentLineView = StrokeTextView(this).apply {
            setTextColor(primaryTextColor)
            setStrokeColor(textStrokeColor)
            setStrokeWidth(dp(3).toFloat())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            maxLines = 2
            maxWidth = maxContentWidth()
            text = "暂无歌词"
            setLineSpacing(0f, 1.06f)
            setShadowLayer(8f, 0f, 2f, adjustAlpha(Color.BLACK, 0.24f))
            setPadding(dp(4), 0, dp(4), 0)
        }
        secondaryLineView = StrokeTextView(this).apply {
            setTextColor(secondaryTextColor)
            setStrokeColor(secondaryStrokeColor)
            setStrokeWidth(dp(2).toFloat())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            maxLines = 1
            maxWidth = maxContentWidth()
            setLineSpacing(0f, 1.04f)
            setShadowLayer(6f, 0f, 2f, adjustAlpha(Color.BLACK, 0.2f))
            setPadding(dp(3), 0, dp(3), 0)
        }

        lyricContainerView?.addView(
            currentLineView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        lyricContainerView?.addView(
            secondaryLineView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(6)
            }
        )
        container.addView(
            lyricContainerView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        controlPanelView = createControlPanel().also { panel ->
            panel.visibility = View.GONE
            container.addView(
                panel,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(8)
                }
            )
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(120)
        }

        container.setOnTouchListener(DragTouchListener(params))
        overlayView = container
        windowManager.addView(container, params)
        updatePlaybackButton()
        updateTranslationButton()
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

        val translationToggle = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnClickListener { toggleTranslation() }
        }

        translationButton = createActionButton(
            iconRes = R.drawable.ic_lyrics,
            description = "歌词翻译",
            sizeDp = 28,
            paddingDp = 5,
            marginDp = 0
        ) {}
        translationButton?.isClickable = false
        translationButton?.isFocusable = false

        val translationLabel = TextView(this).apply {
            text = "翻译"
            setTextColor(primaryTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(6), 0, 0, 0)
        }

        translationStateView = TextView(this).apply {
            setTextColor(secondaryTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setPadding(dp(4), 0, 0, 0)
        }

        translationToggle.addView(translationButton)
        translationToggle.addView(translationLabel)
        translationToggle.addView(translationStateView)

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
        panel.addView(translationToggle)

        return panel
    }

    private fun createActionButton(
        iconRes: Int,
        description: String,
        sizeDp: Int,
        paddingDp: Int,
        marginDp: Int = 0,
        onClick: () -> Unit
    ): ImageButton {
        return ImageButton(this).apply {
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
    }

    private fun updatePlaybackButton() {
        playPauseButton?.setImageResource(
            if (controller?.isPlaying == true) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        )
        playPauseButton?.setColorFilter(buttonTintColor)
    }

    private fun updateTranslationButton() {
        translationButton?.setImageResource(
            if (translationEnabled) R.drawable.ic_lyrics_on else R.drawable.ic_lyrics
        )
        translationButton?.setColorFilter(if (translationEnabled) Color.WHITE else secondaryTextColor)
        translationStateView?.text = if (translationEnabled) "开" else "关"
    }

    private fun toggleTranslation() {
        serviceScope.launch {
            applicationContext.dataStore.edit {
                it[lyricTranslationEnabledKey] = !translationEnabled
            }
        }
    }

    private fun setExpanded(expanded: Boolean) {
        if (panelExpanded == expanded) return
        panelExpanded = expanded
        lyricContainerView?.background = if (expanded) createLyricBackground() else null
        controlPanelView?.visibility = if (expanded) View.VISIBLE else View.GONE
    }

    private fun createLyricBackground(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(22).toFloat()
            setColor(lyricBackgroundColor)
            setStroke(dp(1), outlineColor)
        }
    }

    private fun createControlBackground(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(18).toFloat()
            setColor(controlBackgroundColor)
            setStroke(dp(1), outlineColor)
        }
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

    private fun removeOverlayView() {
        overlayView?.let {
            runCatching { windowManager.removeView(it) }
        }
        overlayView = null
        rootView = null
        lyricContainerView = null
        controlPanelView = null
        currentLineView = null
        secondaryLineView = null
        currentLineText = null
        secondaryLineText = null
        playPauseButton = null
        translationButton = null
        translationStateView = null
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun maxContentWidth(): Int =
        (resources.displayMetrics.widthPixels * 0.8f).toInt()

    private fun adjustAlpha(color: Int, alpha: Float): Int {
        val alphaValue = (Color.alpha(color) * alpha).toInt().coerceIn(0, 255)
        return Color.argb(alphaValue, Color.red(color), Color.green(color), Color.blue(color))
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
                    }
                    layoutParams.x = startX + deltaX.toInt()
                    layoutParams.y = startY + deltaY.toInt()
                    windowManager.updateViewLayout(v, layoutParams)
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        setExpanded(!panelExpanded)
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
}
