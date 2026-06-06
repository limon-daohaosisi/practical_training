package com.example.myapplication.core.overlay

import android.app.Service
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.IBinder
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import com.example.myapplication.core.accessibility.CaptureAccessibilityService
import com.example.myapplication.core.model.GuidanceCue
import com.example.myapplication.core.session.AnalyzeRuntime
import com.example.myapplication.core.speech.AndroidSpeechController
import com.example.myapplication.feature.session.isRunningState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class OverlayEntryService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var speechController: AndroidSpeechController
    private var overlayControl: View? = null
    private var questionInput: EditText? = null
    private var questionDraft: String = ""
    private var speechFallbackDraft: String? = null
    private var latestRunningState: Boolean = false
    private var isCollapsed: Boolean = false
    private var guidanceView: GuidanceOverlayView? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        speechController = AndroidSpeechController(
            context = this,
            onPartialText = ::fillSpeechPartialText,
            onFinalText = ::commitSpeechText,
            onRecoverableError = ::restoreSpeechFallback,
            onError = ::showToast,
        )
        showGuidanceLayer()
        renderOverlayControl(isRunning = false)
        observeAnalyzeState()
        observeUserClicks()
    }

    override fun onDestroy() {
        guidanceView?.let(windowManager::removeView)
        guidanceView = null
        removeOverlayControl()
        speechController.destroy()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun renderOverlayControl(isRunning: Boolean) {
        latestRunningState = isRunning
        val currentMode = overlayControl?.tag as? OverlayMode
        val nextMode = when {
            isCollapsed -> OverlayMode.Collapsed
            isRunning -> OverlayMode.Running
            else -> OverlayMode.Input
        }
        if (currentMode == nextMode) return

        removeOverlayControl()
        when (nextMode) {
            OverlayMode.Input -> showInputWindow()
            OverlayMode.Running -> showCancelButton()
            OverlayMode.Collapsed -> showCollapsedButton()
        }
    }

    private fun showInputWindow() {
        val input = EditText(this).apply {
            hint = "输入问题"
            minLines = 1
            maxLines = 3
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setText(questionDraft)
            setSelection(questionDraft.length)
        }
        questionInput = input

        val panel = LinearLayout(this).apply {
            tag = OverlayMode.Input
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setBackgroundColor(Color.argb(230, 34, 34, 34))
            addView(
                Button(this@OverlayEntryService).apply {
                    text = "语音"
                    setOnClickListener {
                        speechFallbackDraft = currentQuestionText()
                        speechController.startListening()
                    }
                },
                LinearLayout.LayoutParams(dp(72), WindowManager.LayoutParams.WRAP_CONTENT),
            )
            addView(
                input,
                LinearLayout.LayoutParams(0, WindowManager.LayoutParams.WRAP_CONTENT, 1f).apply {
                    leftMargin = dp(8)
                    rightMargin = dp(8)
                },
            )
            addView(
                Button(this@OverlayEntryService).apply {
                    text = "发送"
                    setOnClickListener {
                        sendQuestionFromInput()
                    }
                },
                LinearLayout.LayoutParams(dp(72), WindowManager.LayoutParams.WRAP_CONTENT),
            )
            addView(
                Button(this@OverlayEntryService).apply {
                    text = "收起"
                    setOnClickListener {
                        collapseOverlay()
                    }
                },
                LinearLayout.LayoutParams(dp(64), WindowManager.LayoutParams.WRAP_CONTENT).apply {
                    leftMargin = dp(8)
                },
            )
        }

        val params = WindowManager.LayoutParams(
            dp(360),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 24
            y = 180
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        }

        overlayControl = panel
        windowManager.addView(panel, params)
    }

    private fun showCancelButton() {
        val panel = LinearLayout(this).apply {
            tag = OverlayMode.Running
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                Button(this@OverlayEntryService).apply {
                    text = "取消"
                    setOnClickListener {
                        serviceScope.launch {
                            runCatching {
                                AnalyzeRuntime.cancel(this@OverlayEntryService)
                            }
                            showToast("已请求取消")
                        }
                    }
                },
                LinearLayout.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT),
            )
            addView(
                Button(this@OverlayEntryService).apply {
                    text = "收起"
                    setOnClickListener {
                        collapseOverlay()
                    }
                },
                LinearLayout.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT).apply {
                    leftMargin = dp(8)
                },
            )
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 24
            y = 180
        }

        overlayControl = panel
        windowManager.addView(panel, params)
    }

    private fun showCollapsedButton() {
        val button = Button(this).apply {
            tag = OverlayMode.Collapsed
            text = "展开"
            setOnClickListener {
                expandOverlay()
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 24
            y = 180
        }

        overlayControl = button
        windowManager.addView(button, params)
    }

    private fun sendQuestionFromInput() {
        val question = questionInput?.text?.toString()?.trim().orEmpty()
        if (question.isBlank()) {
            showToast("请输入问题")
            return
        }

        speechController.stopListening()
        clearQuestionDraft()
        serviceScope.launch {
            runCatching {
                AnalyzeRuntime.analyzeNow(this@OverlayEntryService, question)
            }.onSuccess {
                showToast("已发送 analyze 请求")
            }.onFailure { error ->
                showToast(error.message ?: "Analyze 失败")
            }
        }
    }

    private fun removeOverlayControl() {
        questionInput?.let { input ->
            questionDraft = input.text?.toString().orEmpty()
        }
        overlayControl?.let(windowManager::removeView)
        overlayControl = null
        questionInput = null
    }

    private fun fillSpeechPartialText(text: String) {
        fillQuestionInput(text)
    }

    private fun commitSpeechText(text: String) {
        speechFallbackDraft = null
        fillQuestionInput(text)
    }

    private fun restoreSpeechFallback() {
        speechFallbackDraft?.let(::fillQuestionInput)
        speechFallbackDraft = null
    }

    private fun fillQuestionInput(text: String) {
        questionDraft = text
        questionInput?.setText(text)
        questionInput?.setSelection(text.length)
    }

    private fun clearQuestionDraft() {
        questionDraft = ""
        questionInput?.text?.clear()
    }

    private fun currentQuestionText(): String =
        questionInput?.text?.toString() ?: questionDraft

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun collapseOverlay() {
        isCollapsed = true
        renderOverlayControl(latestRunningState)
    }

    private fun expandOverlay() {
        isCollapsed = false
        renderOverlayControl(latestRunningState)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

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
                renderOverlayControl(state.isRunningState())
            }
        }
        serviceScope.launch {
            AnalyzeRuntime.responseText(this@OverlayEntryService).collectLatest { answer ->
                speechController.speak(answer)
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

private enum class OverlayMode {
    Input,
    Running,
    Collapsed,
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
