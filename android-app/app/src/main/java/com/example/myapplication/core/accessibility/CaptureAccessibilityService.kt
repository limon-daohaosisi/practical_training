package com.example.myapplication.core.accessibility

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
import com.example.myapplication.core.model.CaptureResult
import com.example.myapplication.core.model.NodeNormalizer
import com.example.myapplication.core.model.NormalizedBounds
import com.example.myapplication.core.model.RawNodeSnapshot
import com.example.myapplication.core.model.ScreenshotCapture
import com.example.myapplication.core.model.withScreenshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
        val screenshotFlag = 0x00010000
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

        lastScreenshotPath.value = null
        java.io.File(cacheDir, "captures").listFiles()?.forEach { it.delete() }

        val rect = Rect()
        val snapshot = snapshot(root, rect)
        root.recycle()

        val nodes = NodeNormalizer.normalize(snapshot)

        val result = CaptureResult(
            packageName = pkg,
            activityName = event.className?.toString().orEmpty(),
            nodes = nodes,
            screenWidth = sw,
            screenHeight = sh
        )
        lastCapture.value = result
        lastCapturePkg = pkg
        lastCaptureTime = System.currentTimeMillis()
        appendLog("${timeNow()} capture OK | pkg=$pkg | nodes=${nodes.size} | clickable=${nodes.count { it.clickable }} | screen=${sw}x${sh}")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            takeScreenshotFor(pkg, captureSerial)
        } else {
            appendLog("${timeNow()} screenshot SKIP | reason=API < 30")
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun captureCurrentWindowNow(): CaptureResult {
        val root = findRoot() ?: error("No active accessibility root is available.")
        val disp = resources.displayMetrics
        val rect = Rect()
        val currentPkg = root.packageName?.toString().orEmpty()
        val activityName = root.className?.toString().orEmpty()
        lastScreenshotPath.value = null
        java.io.File(cacheDir, "captures").listFiles()?.forEach { it.delete() }
        val snapshot = snapshot(root, rect)
        root.recycle()
        val nodes = NodeNormalizer.normalize(snapshot)
        val base = CaptureResult(
            packageName = currentPkg,
            activityName = activityName,
            nodes = nodes,
            screenWidth = disp.widthPixels,
            screenHeight = disp.heightPixels
        )
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            error("Explicit screenshot capture requires API 30+.")
        }
        val screenshot = takeScreenshotNow(currentPkg)
        val completed = base.withScreenshot(screenshot)
        lastCapture.value = completed
        lastScreenshotPath.value = screenshot.savedPath
        appendLog("${timeNow()} capture NOW | pkg=$currentPkg | nodes=${nodes.size} | screen=${disp.widthPixels}x${disp.heightPixels} | screenshot=${screenshot.imageWidth}x${screenshot.imageHeight}")
        return completed
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
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
                        val buffer = result.hardwareBuffer
                        try {
                            if (captureSerial != serial) {
                                appendLog("${timeNow()} screenshot DROP | reason=stale serial=$serial current=$captureSerial")
                                return
                            }
                            val currentPkg = rootInActiveWindow?.packageName?.toString().orEmpty()
                            if (currentPkg.isNotEmpty() && currentPkg != targetPkg && currentPkg != packageName) {
                                appendLog("${timeNow()} screenshot DROP | reason=switched expected=$targetPkg actual=$currentPkg")
                                return
                            }
                            val bitmap = Bitmap.wrapHardwareBuffer(buffer, null)
                            if (bitmap != null) {
                                val screenshot = bitmap.toScreenshotCapture(targetPkg)
                                val current = lastCapture.value
                                if (current != null && current.packageName == targetPkg) {
                                    lastCapture.value = current.withScreenshot(screenshot)
                                    lastScreenshotPath.value = screenshot.savedPath
                                    appendLog("${timeNow()} screenshot OK | ${screenshot.imageWidth}x${screenshot.imageHeight} | ${screenshot.bytes.size / 1024}KB | ${screenshot.savedPath}")
                                } else {
                                    appendLog("${timeNow()} screenshot DROP | pkg mismatch expected=$targetPkg actual=${current?.packageName}")
                                }
                                bitmap.recycle()
                            }
                        } finally {
                            buffer?.close()
                        }
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

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private suspend fun takeScreenshotNow(targetPkg: String): ScreenshotCapture {
        val deferred = CompletableDeferred<ScreenshotCapture>()
        val wList = windows
        val displayId = if (wList != null && wList.isNotEmpty()) {
            val id = wList[0].displayId
            wList.forEach { it.recycle() }
            id
        } else {
            0
        }
        takeScreenshot(
            displayId,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    val buffer = result.hardwareBuffer
                    try {
                        val bitmap = Bitmap.wrapHardwareBuffer(buffer, null)
                        if (bitmap == null) {
                            deferred.completeExceptionally(
                                IllegalStateException("Failed to decode screenshot bitmap.")
                            )
                            return
                        }
                        deferred.complete(bitmap.toScreenshotCapture(targetPkg))
                        bitmap.recycle()
                    } finally {
                        buffer?.close()
                    }
                }

                override fun onFailure(errorCode: Int) {
                    deferred.completeExceptionally(
                        IllegalStateException("Screenshot failed with code=$errorCode")
                    )
                }
            }
        )
        return deferred.await()
    }

    @Suppress("DEPRECATION")
    private fun findRoot(): AccessibilityNodeInfo? {
        rootInActiveWindow?.let { return it }

        val wList = windows
        if (wList != null) {
            for (w in wList) {
                if (w.isActive) {
                    val r = w.root
                    if (r != null) {
                        wList.forEach { it.recycle() }
                        return r
                    }
                }
            }
            wList.forEach { it.recycle() }
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun snapshot(
        node: AccessibilityNodeInfo,
        rect: Rect
    ): RawNodeSnapshot {
        node.getBoundsInScreen(rect)
        val bounds = NormalizedBounds(rect.left, rect.top, rect.right, rect.bottom)
        val children = mutableListOf<RawNodeSnapshot>()
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            children += snapshot(child, rect)
            child.recycle()
        }
        return RawNodeSnapshot(
            text = node.text?.toString().orEmpty(),
            contentDescription = node.contentDescription?.toString().orEmpty(),
            className = node.className?.toString().orEmpty(),
            clickable = node.isClickable,
            editable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                node.isEditable
            } else {
                false
            },
            enabled = node.isEnabled,
            bounds = bounds,
            children = children
        )
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

    private fun Bitmap.toScreenshotCapture(targetPkg: String): ScreenshotCapture {
        val stream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, 80, stream)
        val bytes = stream.toByteArray()
        val file = saveScreenshotFile(bytes, targetPkg)
        return ScreenshotCapture(
            bytes = bytes,
            imageWidth = width,
            imageHeight = height,
            savedPath = file
        )
    }

    private fun appendLog(line: String) {
        val current = eventLog.value
        eventLog.value = (current + line).takeLast(MAX_LOG_LINES)
    }

    companion object {
        private const val MAX_LOG_LINES = 50
        private const val MAX_RETRIES = 2
        private const val RETRY_DELAY_MS = 150L
        private const val DEBOUNCE_MS = 800L
        private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        private val eventLog = MutableStateFlow<List<String>>(emptyList())
        val events: StateFlow<List<String>> = eventLog.asStateFlow()

        private val lastCapture = MutableStateFlow<CaptureResult?>(null)
        val capture: StateFlow<CaptureResult?> = lastCapture.asStateFlow()

        private val lastScreenshotPath = MutableStateFlow<String?>(null)
        val screenshotPath: StateFlow<String?> = lastScreenshotPath.asStateFlow()

        @Volatile
        private var activeService: CaptureAccessibilityService? = null

        private fun timeNow(): String = dateFormat.format(Date())

        suspend fun captureNow(): CaptureResult {
            val service = activeService ?: error("Accessibility service is not connected.")
            val deferred = CompletableDeferred<CaptureResult>()
            service.handler.post {
                CoroutineScope(Dispatchers.Main.immediate).launch {
                    runCatching {
                        service.captureCurrentWindowNow()
                    }.onSuccess(deferred::complete)
                        .onFailure(deferred::completeExceptionally)
                }
            }
            return deferred.await()
        }
    }

    override fun onCreate() {
        super.onCreate()
        activeService = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (activeService === this) activeService = null
        return super.onUnbind(intent)
    }
}
