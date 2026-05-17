package com.example.myapplication.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.myapplication.app.model.CaptureResult
import com.example.myapplication.app.model.NormalizedBounds
import com.example.myapplication.app.model.NormalizedNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CaptureAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var pendingRunnable: Runnable? = null
    private var captureSerial = 0
    private var lastCapturePkg = ""
    private var lastCaptureTime = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        val screenshotFlag = 0x00010000  // AccessibilityServiceInfo.FLAG_CAN_TAKE_SCREENSHOT
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_WINDOWS_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 300
            if (Build.VERSION.SDK_INT >= 34) {
                flags = AccessibilityServiceInfo.DEFAULT or screenshotFlag
            } else {
                flags = AccessibilityServiceInfo.DEFAULT
            }
        }
        serviceInfo = info
        appendLog("${timeNow()} SERVICE_CONNECTED")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val typeName = AccessibilityEvent.eventTypeToString(event.eventType)
        val pkg = event.packageName?.toString().orEmpty()
        appendLog("${timeNow()} event | pkg=$pkg | type=$typeName | cls=${event.className}")

        if (pkg == packageName || pkg.isEmpty()) return

        // Don't capture launcher — it always has a ready root so it steals the capture
        if (isLauncherPackage(pkg)) {
            appendLog("${timeNow()} capture SKIP | pkg=$pkg | reason=launcher")
            return
        }

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            scheduleCapture(event)
        }
    }

    private fun isLauncherPackage(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return lower.contains("launcher") || lower.endsWith(".home")
    }

    override fun onInterrupt() {
        appendLog("${timeNow()} SERVICE_INTERRUPTED")
    }

    override fun onDestroy() {
        super.onDestroy()
        pendingRunnable?.let { handler.removeCallbacks(it) }
        appendLog("${timeNow()} SERVICE_DESTROYED")
    }

    private fun scheduleCapture(event: AccessibilityEvent) {
        val targetPkg = event.packageName?.toString().orEmpty()
        // Debounce: skip if same package captured within DEBOUNCE_MS
        if (targetPkg == lastCapturePkg &&
            System.currentTimeMillis() - lastCaptureTime < DEBOUNCE_MS
        ) {
            appendLog("${timeNow()} capture SKIP | pkg=$targetPkg | reason=debounce")
            return
        }
        pendingRunnable?.let { handler.removeCallbacks(it) }
        captureSerial++
        val serial = captureSerial
        attemptCapture(serial, event, targetPkg, 0)
    }

    private fun attemptCapture(serial: Int, event: AccessibilityEvent, targetPkg: String, attempt: Int) {
        val root = findRoot()
        if (root != null) {
            pendingRunnable = null
            doCapture(root, event)
            return
        }
        if (attempt < MAX_RETRIES) {
            val runnable = Runnable {
                if (captureSerial != serial) return@Runnable
                attemptCapture(serial, event, targetPkg, attempt + 1)
            }
            pendingRunnable = runnable
            handler.postDelayed(runnable, RETRY_DELAY_MS)
        } else {
            pendingRunnable = null
            appendLog("${timeNow()} capture SKIP | pkg=$targetPkg | retries=${MAX_RETRIES + 1}")
        }
    }

    @Suppress("DEPRECATION")
    private fun doCapture(root: AccessibilityNodeInfo, event: AccessibilityEvent) {
        val pkg = event.packageName?.toString().orEmpty()
        val disp = resources.displayMetrics
        val sw = disp.widthPixels
        val sh = disp.heightPixels

        // Clear stale screenshot from previous capture
        lastScreenshotPath.value = null
        java.io.File(cacheDir, "captures").listFiles()?.forEach { it.delete() }

        // Fire screenshot BEFORE node traversal — takeScreenshot is async but
        // the earlier we call it the less likely the screen has changed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            takeScreenshotFor(pkg, captureSerial)
        } else {
            appendLog("${timeNow()} screenshot SKIP | reason=API < 34")
        }

        val nodes = mutableListOf<NormalizedNode>()
        val rect = Rect()
        traverse(root, nodes, rect)
        root.recycle()

        nodes.sortWith(compareBy<NormalizedNode> { it.bounds.top }.thenBy { it.bounds.left })

        val result = CaptureResult(
            packageName = pkg,
            className = event.className?.toString().orEmpty(),
            nodes = nodes,
            screenWidth = sw,
            screenHeight = sh
        )
        lastCapture.value = result
        lastCapturePkg = pkg
        lastCaptureTime = System.currentTimeMillis()
        appendLog("${timeNow()} capture OK | pkg=$pkg | nodes=${nodes.size} | clickable=${nodes.count { it.clickable }} | screen=${sw}x${sh}")
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun takeScreenshotFor(targetPkg: String, serial: Int) {
        val wList = windows
        val displayId = if (wList != null && wList.isNotEmpty()) {
            val id = wList[0].displayId
            wList.forEach { it.recycle() }
            id
        } else {
            appendLog("${timeNow()} screenshot FALLBACK | reason=no windows, using display=0")
            0
        }

        try {
            takeScreenshot(
                displayId,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        // Stale — a newer capture has already started
                        if (captureSerial != serial) {
                            appendLog("${timeNow()} screenshot DROP | reason=stale serial=$serial current=$captureSerial")
                            return
                        }
                        val buffer = result.hardwareBuffer
                        val bitmap = Bitmap.wrapHardwareBuffer(buffer, null)
                        if (bitmap != null) {
                            val stream = ByteArrayOutputStream()
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                            val bytes = stream.toByteArray()
                            val current = lastCapture.value
                            if (current != null && current.packageName == targetPkg) {
                                lastCapture.value = current.copy(
                                    screenshotBytes = bytes,
                                    imageWidth = bitmap.width,
                                    imageHeight = bitmap.height
                                )
                                val file = saveScreenshotFile(bytes, targetPkg)
                                appendLog("${timeNow()} screenshot OK | ${bitmap.width}x${bitmap.height} | ${bytes.size / 1024}KB | $file")
                            } else {
                                appendLog("${timeNow()} screenshot DROP | pkg mismatch expected=$targetPkg actual=${current?.packageName}")
                            }
                            bitmap.recycle()
                        }
                        buffer.close()
                    }

                    override fun onFailure(errorCode: Int) {
                        appendLog("${timeNow()} screenshot FAIL | code=$errorCode")
                    }
                }
            )
        } catch (e: Exception) {
            appendLog("${timeNow()} screenshot FAIL | ${e.message}")
        }
    }

    @Suppress("DEPRECATION")
    private fun findRoot(): AccessibilityNodeInfo? {
        // 1. rootInActiveWindow
        rootInActiveWindow?.let { return it }

        // 2. Window list — find active window root
        val wList = windows
        if (wList != null) {
            for (w in wList) {
                if (w.isActive) {
                    val r = w.root
                    if (r != null) {
                        // Recycle other windows
                        wList.filter { it != w }.forEach { it.recycle() }
                        return r
                    }
                }
            }
            wList.forEach { it.recycle() }
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun traverse(
        node: AccessibilityNodeInfo,
        out: MutableList<NormalizedNode>,
        rect: Rect,
        ancestorClickable: Boolean = false
    ) {
        node.getBoundsInScreen(rect)
        var isInteractive = node.isClickable || node.isFocusable || node.isLongClickable
        // Inherit clickability from parent: if an ancestor is clickable,
        // text-bearing descendants should be reported as interactive.
        if (ancestorClickable && !isInteractive) {
            val hasText = !node.text.isNullOrEmpty() ||
                !node.contentDescription.isNullOrEmpty()
            if (hasText) isInteractive = true
        }
        val parentClickable = ancestorClickable || node.isClickable

        val normalized = NormalizedNode(
            text = node.text?.toString().orEmpty(),
            contentDescription = node.contentDescription?.toString().orEmpty(),
            className = node.className?.toString().orEmpty(),
            clickable = node.isClickable,
            enabled = node.isEnabled,
            bounds = NormalizedBounds(rect.left, rect.top, rect.right, rect.bottom)
        )

        if (normalized.text.isNotEmpty() ||
            normalized.contentDescription.isNotEmpty() ||
            isInteractive
        ) {
            out.add(normalized.copy(clickable = isInteractive))
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            traverse(child, out, rect, parentClickable)
            child.recycle()
        }
    }

    private fun saveScreenshotFile(bytes: ByteArray, targetPkg: String): String {
        val dir = java.io.File(cacheDir, "captures")
        dir.mkdirs()
        val name = targetPkg.replace(".", "_") + "_${System.currentTimeMillis()}.jpg"
        val file = java.io.File(dir, name)
        file.writeBytes(bytes)
        lastScreenshotPath.value = file.absolutePath
        return file.absolutePath
    }

    private fun appendLog(line: String) {
        val current = eventLog.value
        eventLog.value = (current + line).takeLast(MAX_LOG_LINES)
    }

    companion object {
        private const val MAX_LOG_LINES = 50
        private const val MAX_RETRIES = 2       // 3 total attempts: 0ms, 150ms, 300ms
        private const val RETRY_DELAY_MS = 150L
        private const val DEBOUNCE_MS = 800L   // Ignore same-pkg events within 800ms
        private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        private val eventLog = MutableStateFlow<List<String>>(emptyList())
        val events: StateFlow<List<String>> = eventLog.asStateFlow()

        private val lastCapture = MutableStateFlow<CaptureResult?>(null)
        val capture: StateFlow<CaptureResult?> = lastCapture.asStateFlow()

        private val lastScreenshotPath = MutableStateFlow<String?>(null)
        val screenshotPath: StateFlow<String?> = lastScreenshotPath.asStateFlow()

        private fun timeNow(): String = dateFormat.format(Date())
    }
}
