package com.jussicodes.music.playback

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.jussicodes.music.lyric.DesktopLyricManager
import com.jussicodes.music.ui.components.PathPalette
import com.jussicodes.music.ui.components.PathStage
import com.jussicodes.music.ui.components.RowItem
import com.jussicodes.music.ui.components.buildTimeline
import com.jussicodes.music.ui.components.formatSeconds
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 「可视化播放解析」的置顶小窗。
 *
 * 两个决定：
 * 1. 用系统悬浮窗（TYPE_APPLICATION_OVERLAY）：面板的用途是「边听边看」，用户会切去别的 App，
 *    只有系统级窗口能跨应用留在最上层。权限复用桌面歌词那套，不额外引导。
 * 2. 用原生 View 而不是 ComposeView：Compose 挂到 Service 上要自己当 Lifecycle / SavedState /
 *    ViewModel 三个 Owner，而本工程的依赖里取不到 `ViewTreeLifecycleOwner`（编译期 Unresolved）。
 *    小窗内容只是几行字加一条色带，原生 View 更稳。
 */
class PlaybackPathOverlayService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val windowManager by lazy {
        getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    private var root: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var routeView: TextView? = null
    private var totalView: TextView? = null
    private var barView: LinearLayout? = null
    private var legendView: TextView? = null
    private var stepsView: LinearLayout? = null
    private var emptyView: TextView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (!DesktopLyricManager.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        addWindow()
        scope.launch {
            PlaybackPath.state.collectLatest { bind(it) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onDestroy() {
        scope.cancel()
        root?.let { runCatching { windowManager.removeView(it) } }
        root = null
        super.onDestroy()
    }

    // ------------------------------------------------------------ 窗口骨架

    private fun addWindow() {
        val metrics = resources.displayMetrics
        val width = minOf((metrics.widthPixels * 0.92f).toInt(), dp(336))
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(0xF21C1C1E.toInt())
                setStroke(dp(1), 0x33FFFFFF)
            }
        }
        container.addView(buildHeader())
        container.addView(buildBar())
        container.addView(buildLegend())
        stepsView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }.also { container.addView(it) }
        emptyView = TextView(this).apply {
            setTextColor(0xFFB0B3BA.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            visibility = View.GONE
        }.also { container.addView(it) }

        val layoutParams = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (metrics.widthPixels - width) / 2
            y = (metrics.heightPixels * 0.14f).toInt()
        }

        if (runCatching { windowManager.addView(container, layoutParams) }.isFailure) {
            stopSelf()
            return
        }
        root = container
        params = layoutParams
        container.setOnTouchListener(DragTouchListener())
    }

    private fun buildHeader(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleColumn.addView(TextView(this).apply {
            text = "可视化播放解析"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        })
        routeView = TextView(this).apply {
            setTextColor(0xFFB0B3BA.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            maxLines = 1
        }
        titleColumn.addView(routeView)
        row.addView(
            titleColumn,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )

        totalView = TextView(this).apply {
            setTextColor(PathPalette.of(PathStage.RESOLVE))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(dp(8), 0, dp(8), 0)
        }
        row.addView(totalView)

        row.addView(TextView(this).apply {
            text = "×"
            setTextColor(0xFFD5D7DC.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
            setPadding(dp(10), 0, 0, 0)
            setOnClickListener { stopSelf() }
        })
        return row
    }

    private fun buildBar(): View {
        barView = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(6)
            ).apply { topMargin = dp(10) }
            background = GradientDrawable().apply {
                cornerRadius = dp(3).toFloat()
                setColor(0x33FFFFFF)
            }
            clipToOutline = true
        }
        return barView!!
    }

    private fun buildLegend(): View {
        legendView = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setPadding(0, dp(4), 0, 0)
            maxLines = 1
        }
        return legendView!!
    }

    // ------------------------------------------------------------ 数据绑定

    private fun bind(snapshot: PlaybackPath.Snapshot) {
        routeView?.text = snapshot.route.ifBlank {
            snapshot.songId.ifBlank { "等待解析" }
        }
        totalView?.text = formatSeconds(snapshot.totalMs)

        val timeline = buildTimeline(snapshot.steps, snapshot.totalMs)

        barView?.removeAllViews()
        val sum = timeline.stageTotals.sumOf { it.second }.coerceAtLeast(1L)
        timeline.stageTotals.forEach { (stage, ms) ->
            if (ms > 0) {
                barView?.addView(
                    View(this).apply { setBackgroundColor(PathPalette.of(stage)) },
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        ms.toFloat() / sum
                    )
                )
            }
        }

        // 图例：■ 段名 耗时，四种颜色各自上色
        legendView?.text = SpannableStringBuilder().apply {
            timeline.stageTotals.forEachIndexed { index, (stage, ms) ->
                if (index > 0) append("  ")
                val start = length
                append("■ ${stage.label} ${formatSeconds(ms)}")
                setSpan(
                    ForegroundColorSpan(PathPalette.of(stage)),
                    start,
                    length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        val steps = timeline.rows.filterIsInstance<RowItem.Step>().takeLast(6)
        stepsView?.removeAllViews()
        if (steps.isEmpty()) {
            emptyView?.visibility = View.VISIBLE
            emptyView?.text =
                if (snapshot.totalMs > 0) "0 解析直达（缓存或本地文件）" else "等待起播…"
            return
        }
        emptyView?.visibility = View.GONE
        steps.forEach { stepRow ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(6), 0, 0)
            }
            row.addView(View(this).apply {
                setBackgroundColor(PathPalette.of(stepRow.stage))
                layoutParams = LinearLayout.LayoutParams(dp(6), dp(6)).apply {
                    topMargin = dp(5)
                    rightMargin = dp(7)
                }
            })

            val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            column.addView(TextView(this).apply {
                text = stepRow.step.name
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                maxLines = 1
            })
            if (stepRow.step.detail.isNotBlank()) {
                column.addView(TextView(this).apply {
                    text = stepRow.step.detail
                    setTextColor(0xFF9EA2AA.toInt())
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                    maxLines = 2
                })
            }
            row.addView(
                column,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
            row.addView(TextView(this).apply {
                text = "+${formatSeconds(stepRow.step.atMs)}"
                setTextColor(0xFF9EA2AA.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                gravity = Gravity.TOP or Gravity.END
            })
            stepsView?.addView(row)
        }
    }

    // ------------------------------------------------------------ 拖动

    private inner class DragTouchListener : View.OnTouchListener {
        private var startX = 0
        private var startY = 0
        private var touchX = 0f
        private var touchY = 0f
        private var moved = false
        private val touchSlop = dp(8)

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            val lp = params ?: return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x
                    startY = lp.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    return false
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - touchX
                    val deltaY = event.rawY - touchY
                    if (!moved && (abs(deltaX) > touchSlop || abs(deltaY) > touchSlop)) {
                        moved = true
                    }
                    if (!moved) return false
                    val metrics = resources.displayMetrics
                    val minX = -dp(40)
                    val maxX = maxOf(minX + 1, metrics.widthPixels - v.width + dp(40))
                    lp.x = (startX + deltaX.toInt()).coerceIn(minX, maxX)
                    lp.y = (startY + deltaY.toInt())
                        .coerceIn(0, (metrics.heightPixels - v.height).coerceAtLeast(0))
                    runCatching { windowManager.updateViewLayout(v, lp) }
                    return true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val wasDragging = moved
                    moved = false
                    return wasDragging
                }
            }
            return false
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
