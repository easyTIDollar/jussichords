package com.jussicodes.music.lyric

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.jussicodes.music.playback.PlaybackService
import com.jussicodes.music.utils.LrcLine
import com.jussicodes.music.utils.parseLrc
import com.jussicodes.music.utils.parseYrc
import com.rcmiku.ncmapi.api.player.PlayerApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DesktopLyricService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var currentLineView: TextView? = null
    private var nextLineView: TextView? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var progressJob: Job = Job()
    private var currentMediaId: String? = null
    private var lyricLines: List<LrcLine> = emptyList()
    private var currentLineText: String? = null
    private var nextLineText: String? = null

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
            ensureProgressLoop()
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
        createOverlayView()
        connectController()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!DesktopLyricManager.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        ensureProgressLoop()
        updateLyric()
        return START_STICKY
    }

    override fun onDestroy() {
        progressJob.cancel()
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
                    syncTrack(mediaController.currentMediaItem)
                    ensureProgressLoop()
                    updateLyric()
                },
                ContextCompat.getMainExecutor(this@DesktopLyricService)
            )
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
            val parsedLines = response?.let { lyric ->
                val yrcLines = lyric.yrc?.lyric?.parseYrc().orEmpty()
                when {
                    yrcLines.isNotEmpty() -> yrcLines.map { LrcLine(it.time, it.text) }
                    !lyric.lrc?.lyric.isNullOrBlank() -> lyric.lrc?.lyric?.parseLrc().orEmpty()
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
        if (progressJob.isActive) return
        progressJob = serviceScope.launch {
            while (isActive) {
                updateLyric()
                delay(if (controller?.isPlaying == true) 250 else 1000)
            }
        }
    }

    private fun updateLyric() {
        val mediaController = controller
        val mediaItem = mediaController?.currentMediaItem
        if (mediaItem == null) {
            renderLyric("暂无播放内容", "")
            return
        }

        if (lyricLines.isEmpty()) {
            val fallbackCurrent = mediaItem.mediaMetadata.title?.toString().orEmpty().ifBlank { "暂无歌词" }
            val fallbackNext = mediaItem.mediaMetadata.artist?.toString().orEmpty()
            renderLyric(fallbackCurrent, fallbackNext)
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
        val currentText = lyricLines.getOrNull(currentIndex)?.text.orEmpty()
            .ifBlank { mediaItem.mediaMetadata.title?.toString().orEmpty() }
        val nextText = lyricLines.drop(currentIndex + 1).firstOrNull()?.text.orEmpty()
            .ifBlank { mediaItem.mediaMetadata.artist?.toString().orEmpty() }

        renderLyric(currentText, nextText)
    }

    private fun renderLyric(current: String, next: String) {
        if (current == currentLineText && next == nextLineText) return
        currentLineText = current
        nextLineText = next
        currentLineView?.text = current
        nextLineView?.text = next
        nextLineView?.visibility = if (next.isBlank()) View.GONE else View.VISIBLE
    }

    private fun createOverlayView() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(10), dp(18), dp(10))
            background = getDrawable(android.R.drawable.toast_frame)
            alpha = 0.92f
        }
        currentLineView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            maxLines = 2
            text = "暂无歌词"
            setShadowLayer(12f, 0f, 0f, Color.BLACK)
        }
        nextLineView = TextView(this).apply {
            setTextColor(Color.parseColor("#CCFFFFFF"))
            textSize = 14f
            gravity = Gravity.CENTER
            maxLines = 1
            setShadowLayer(10f, 0f, 0f, Color.BLACK)
        }
        container.addView(
            currentLineView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        container.addView(
            nextLineView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(4)
            }
        )

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
    }

    private fun removeOverlayView() {
        overlayView?.let {
            runCatching { windowManager.removeView(it) }
        }
        overlayView = null
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private inner class DragTouchListener(
        private val layoutParams: WindowManager.LayoutParams
    ) : View.OnTouchListener {
        private var startX = 0
        private var startY = 0
        private var touchX = 0f
        private var touchY = 0f

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = layoutParams.x
                    startY = layoutParams.y
                    touchX = event.rawX
                    touchY = event.rawY
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = startX + (event.rawX - touchX).toInt()
                    layoutParams.y = startY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(v, layoutParams)
                    return true
                }
            }
            return false
        }
    }
}
