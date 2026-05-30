package com.example.myapplication.core.overlay

import android.app.Service
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import com.example.myapplication.core.accessibility.CaptureAccessibilityService
import com.example.myapplication.core.model.GuidanceCue
import com.example.myapplication.core.session.AnalyzeRuntime
import com.example.myapplication.feature.session.isRunningState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class OverlayEntryService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayButton: View? = null
    private var guidanceView: GuidanceOverlayView? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showGuidanceLayer()
        showOverlayButton()
        observeAnalyzeState()
        observeUserClicks()
    }

    override fun onDestroy() {
        guidanceView?.let(windowManager::removeView)
        guidanceView = null
        overlayButton?.let(windowManager::removeView)
        overlayButton = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun showOverlayButton() {
        if (overlayButton != null) return

        val button = Button(this).apply {
            text = "分析"
            setOnClickListener {
                serviceScope.launch {
                    val wasRunning = AnalyzeRuntime.isRunning(this@OverlayEntryService)
                    runCatching {
                        if (wasRunning) {
                            AnalyzeRuntime.cancel(this@OverlayEntryService)
                        } else {
                            AnalyzeRuntime.analyzeNow(this@OverlayEntryService)
                        }
                    }.onSuccess {
                        Toast.makeText(
                            this@OverlayEntryService,
                            if (wasRunning) "已取消" else "已发送 analyze 请求",
                            Toast.LENGTH_SHORT
                        ).show()
                    }.onFailure { error ->
                        Toast.makeText(
                            this@OverlayEntryService,
                            error.message ?: "Analyze 失败",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 24
            y = 180
        }

        overlayButton = button
        windowManager.addView(button, params)
    }

    private fun showGuidanceLayer() {
        if (guidanceView != null) return

        val view = GuidanceOverlayView(this).apply {
            visibility = View.GONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        guidanceView = view
        windowManager.addView(view, params)
    }

    private fun observeAnalyzeState() {
        serviceScope.launch {
            AnalyzeRuntime.state(this@OverlayEntryService).collectLatest { state ->
                (overlayButton as? Button)?.text =
                    if (state.isRunningState()) "取消" else "分析"
            }
        }
        serviceScope.launch {
            AnalyzeRuntime.guidanceCue(this@OverlayEntryService).collectLatest { cue ->
                guidanceView?.showCue(cue)
            }
        }
    }

    private fun observeUserClicks() {
        serviceScope.launch {
            CaptureAccessibilityService.observedClicks.collect {
                if (AnalyzeRuntime.shouldListenForObservedInteraction(this@OverlayEntryService)) {
                    AnalyzeRuntime.onObservedClick(this@OverlayEntryService)
                }
            }
        }
    }
}

private class GuidanceOverlayView(context: android.content.Context) : View(context) {
    private var cue: GuidanceCue = GuidanceCue.Hidden

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(68, 255, 213, 79)
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 7f
        color = Color.rgb(255, 193, 7)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 42f
        setFakeBoldText(true)
    }
    private val labelBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(220, 32, 32, 32)
    }
    private val scrollPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 12f
        strokeCap = Paint.Cap.ROUND
        color = Color.rgb(255, 193, 7)
    }

    fun showCue(nextCue: GuidanceCue) {
        cue = nextCue
        visibility = if (nextCue == GuidanceCue.Hidden) GONE else VISIBLE
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        when (val current = cue) {
            GuidanceCue.Hidden -> Unit
            is GuidanceCue.TapTarget -> drawTapTarget(canvas, current)
            GuidanceCue.ScrollHint -> drawScrollHint(canvas)
        }
    }

    private fun drawTapTarget(canvas: Canvas, target: GuidanceCue.TapTarget) {
        val rect = RectF(
            target.bounds.left.toFloat(),
            target.bounds.top.toFloat(),
            target.bounds.right.toFloat(),
            target.bounds.bottom.toFloat(),
        )
        rect.intersect(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, 18f, 18f, fillPaint)
        canvas.drawRoundRect(rect, 18f, 18f, strokePaint)

        if (target.label.isNotBlank()) {
            drawLabel(canvas, target.label, rect)
        }
    }

    private fun drawLabel(canvas: Canvas, label: String, targetRect: RectF) {
        val paddingX = 22f
        val paddingY = 14f
        val textWidth = textPaint.measureText(label)
        val textHeight = textPaint.descent() - textPaint.ascent()
        val maxLeft = maxOf(12f, width - textWidth - paddingX * 2 - 12f)
        val left = targetRect.left.coerceIn(12f, maxLeft)
        val top = (targetRect.top - textHeight - paddingY * 2 - 12f)
            .coerceAtLeast(12f)
        val labelRect = RectF(
            left,
            top,
            left + textWidth + paddingX * 2,
            top + textHeight + paddingY * 2,
        )

        canvas.drawRoundRect(labelRect, 16f, 16f, labelBackgroundPaint)
        canvas.drawText(
            label,
            labelRect.left + paddingX,
            labelRect.top + paddingY - textPaint.ascent(),
            textPaint,
        )
    }

    private fun drawScrollHint(canvas: Canvas) {
        val centerX = width / 2f
        val centerY = height / 2f
        val arrowLength = 190f

        drawArrow(canvas, centerX, centerY - 32f, centerX, centerY - arrowLength)
        drawArrow(canvas, centerX, centerY + 32f, centerX, centerY + arrowLength)

        val label = "上下滑动"
        val textWidth = textPaint.measureText(label)
        canvas.drawText(label, centerX - textWidth / 2f, centerY + 16f, textPaint)
    }

    private fun drawArrow(canvas: Canvas, startX: Float, startY: Float, endX: Float, endY: Float) {
        canvas.drawLine(startX, startY, endX, endY, scrollPaint)
        val direction = if (endY < startY) -1f else 1f
        canvas.drawLine(endX, endY, endX - 42f, endY - direction * 42f, scrollPaint)
        canvas.drawLine(endX, endY, endX + 42f, endY - direction * 42f, scrollPaint)
    }
}
