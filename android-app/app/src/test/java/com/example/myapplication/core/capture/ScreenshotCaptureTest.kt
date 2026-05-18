package com.example.myapplication.core.capture

import com.example.myapplication.core.model.CaptureResult
import com.example.myapplication.core.model.NormalizedBounds
import com.example.myapplication.core.model.NormalizedNode
import com.example.myapplication.core.model.ScreenshotCapture
import com.example.myapplication.core.model.withScreenshot
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenshotCaptureTest {

    @Test
    fun withScreenshot_appliesScreenshotMetadataAndBytes() {
        val base = CaptureResult(
            packageName = "com.example.target",
            activityName = "TargetActivity",
            nodes = listOf(
                NormalizedNode(
                    text = "发送",
                    contentDescription = "",
                    className = "android.widget.Button",
                    clickable = true,
                    editable = false,
                    enabled = true,
                    bounds = NormalizedBounds(900, 2100, 1040, 2220)
                )
            ),
            screenWidth = 1080,
            screenHeight = 2400
        )
        val screenshot = ScreenshotCapture(
            bytes = byteArrayOf(1, 2, 3),
            imageWidth = 1080,
            imageHeight = 2400,
            savedPath = "/tmp/capture.jpg"
        )

        val completed = base.withScreenshot(screenshot)

        assertArrayEquals(byteArrayOf(1, 2, 3), completed.screenshotBytes)
        assertEquals(1080, completed.imageWidth)
        assertEquals(2400, completed.imageHeight)
        assertEquals("com.example.target", completed.packageName)
    }
}
