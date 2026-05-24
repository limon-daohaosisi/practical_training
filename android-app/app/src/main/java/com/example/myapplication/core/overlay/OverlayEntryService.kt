package com.example.myapplication.core.overlay

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import com.example.myapplication.core.session.AnalyzeRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class OverlayEntryService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayButton: View? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showOverlayButton()
    }

    override fun onDestroy() {
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
                    runCatching {
                        AnalyzeRuntime.analyzeNow(this@OverlayEntryService)
                    }.onSuccess {
                        Toast.makeText(
                            this@OverlayEntryService,
                            "已发送 analyze 请求",
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
}
