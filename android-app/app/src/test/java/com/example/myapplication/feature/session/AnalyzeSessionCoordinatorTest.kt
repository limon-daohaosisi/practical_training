package com.example.myapplication.feature.session

import com.example.myapplication.core.capture.CaptureGateway
import com.example.myapplication.core.model.CaptureResult
import com.example.myapplication.core.model.NormalizedBounds
import com.example.myapplication.core.model.NormalizedNode
import com.example.myapplication.core.network.AnalyzeGateway
import com.example.myapplication.core.network.AnalyzeResponse
import com.example.myapplication.core.network.ActionInfo
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AnalyzeSessionCoordinatorTest {

    @Test
    fun requestAnalyze_capturesThenUploadsAndEmitsSuccess() = runTest {
        val capture = CaptureResult(
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
            screenHeight = 2400,
            screenshotBytes = "jpeg".toByteArray(),
            imageWidth = 1080,
            imageHeight = 2400
        )

        val captureGateway = object : CaptureGateway {
            override suspend fun captureNow(): CaptureResult = capture
        }
        val analyzeGateway = object : AnalyzeGateway {
            override suspend fun analyze(question: String, capture: CaptureResult): AnalyzeResponse {
                assertEquals("请分析当前页面", question)
                assertEquals(capture, capture)
                return AnalyzeResponse(
                    answer = "请点击发送按钮",
                    target = null,
                    action = ActionInfo(type = "none")
                )
            }
        }

        val coordinator = AnalyzeSessionCoordinator(captureGateway, analyzeGateway)

        coordinator.requestAnalyze("请分析当前页面")

        assertEquals(AnalyzeRequestState.Success("请点击发送按钮"), coordinator.state.value)
    }
}
