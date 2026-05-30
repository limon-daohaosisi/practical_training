package com.example.myapplication.feature.session

import com.example.myapplication.core.capture.CaptureGateway
import com.example.myapplication.core.model.CaptureResult
import com.example.myapplication.core.model.GuidanceCue
import com.example.myapplication.core.model.NormalizedBounds
import com.example.myapplication.core.model.NormalizedNode
import com.example.myapplication.core.network.ActionInfo
import com.example.myapplication.core.network.AnalyzeGateway
import com.example.myapplication.core.network.AnalyzeRequest
import com.example.myapplication.core.network.AnalyzeResponse
import com.example.myapplication.core.network.CancelResponse
import com.example.myapplication.core.network.TargetInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AnalyzeSessionCoordinatorTest {

    @Test
    fun requestAnalyze_capturesThenUploadsAndEmitsSuccess() = runTest {
        val capture = sampleCapture(
            nodes = listOf(
                NormalizedNode(
                    text = "发送",
                    contentDescription = "",
                    className = "android.widget.Button",
                    clickable = true,
                    editable = false,
                    enabled = true,
                    bounds = NormalizedBounds(900, 2100, 1040, 2220),
                ),
            ),
        )

        val captureGateway = object : CaptureGateway {
            override suspend fun captureNow(): CaptureResult = capture
        }
        val analyzeGateway = object : AnalyzeGateway {
            override suspend fun analyze(
                request: AnalyzeRequest,
                capture: CaptureResult,
            ): AnalyzeResponse {
                assertEquals("请分析当前页面", request.messageText)
                assertEquals(capture, capture)
                return AnalyzeResponse(
                    conversationId = CONVERSATION_ID,
                    runId = "22222222-2222-4222-8222-222222222222",
                    conversationStatus = "waiting_interaction",
                    answer = "请点击发送按钮",
                    target = null,
                    action = ActionInfo(type = "none"),
                )
            }

            override suspend fun cancel(conversationId: String): CancelResponse =
                cancelResponse(conversationId)
        }

        val coordinator = AnalyzeSessionCoordinator(captureGateway, analyzeGateway)

        coordinator.requestAnalyze("请分析当前页面")

        assertEquals(
            AnalyzeRequestState.Success(
                answer = "请点击发送按钮",
                conversationId = CONVERSATION_ID,
                conversationStatus = "waiting_interaction",
            ),
            coordinator.state.value,
        )
    }

    @Test
    fun requestAnalyze_mapsTapTargetToGuidanceCue() = runTest {
        val targetBounds = NormalizedBounds(900, 2100, 1040, 2220)
        val capture = sampleCapture()

        val captureGateway = object : CaptureGateway {
            override suspend fun captureNow(): CaptureResult = capture
        }
        val analyzeGateway = object : AnalyzeGateway {
            override suspend fun analyze(
                request: AnalyzeRequest,
                capture: CaptureResult,
            ): AnalyzeResponse =
                AnalyzeResponse(
                    conversationId = CONVERSATION_ID,
                    runId = "22222222-2222-4222-8222-222222222222",
                    conversationStatus = "waiting_interaction",
                    answer = "请点击发送按钮",
                    target = TargetInfo(
                        label = "发送按钮",
                        bounds = targetBounds,
                    ),
                    action = ActionInfo(type = "tap"),
                )

            override suspend fun cancel(conversationId: String): CancelResponse =
                cancelResponse(conversationId)
        }

        val coordinator = AnalyzeSessionCoordinator(captureGateway, analyzeGateway)

        coordinator.requestAnalyze("请分析当前页面")

        assertEquals(
            AnalyzeRequestState.Success(
                answer = "请点击发送按钮",
                conversationId = CONVERSATION_ID,
                conversationStatus = "waiting_interaction",
                guidanceCue = GuidanceCue.TapTarget(
                    label = "发送按钮",
                    bounds = targetBounds,
                ),
            ),
            coordinator.state.value,
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun observedClick_hidesGuidanceThenSendsFollowUpWithSameConversationId() = runTest {
        val targetBounds = NormalizedBounds(900, 2100, 1040, 2220)
        val capture = sampleCapture()
        val requests = mutableListOf<AnalyzeRequest>()

        val captureGateway = object : CaptureGateway {
            override suspend fun captureNow(): CaptureResult = capture
        }
        val analyzeGateway = object : AnalyzeGateway {
            override suspend fun analyze(
                request: AnalyzeRequest,
                capture: CaptureResult,
            ): AnalyzeResponse {
                requests += request
                return if (request.messageType == "speech_text") {
                    AnalyzeResponse(
                        conversationId = CONVERSATION_ID,
                        runId = "22222222-2222-4222-8222-222222222222",
                        conversationStatus = "waiting_interaction",
                        answer = "请点击发送按钮",
                        target = TargetInfo(
                            label = "发送按钮",
                            bounds = targetBounds,
                        ),
                        action = ActionInfo(type = "tap"),
                    )
                } else {
                    AnalyzeResponse(
                        conversationId = CONVERSATION_ID,
                        runId = "33333333-3333-4333-8333-333333333333",
                        conversationStatus = "waiting_interaction",
                        answer = "已继续分析",
                        target = null,
                        action = ActionInfo(type = "none"),
                    )
                }
            }

            override suspend fun cancel(conversationId: String): CancelResponse =
                cancelResponse(conversationId)
        }

        val coordinator = AnalyzeSessionCoordinator(captureGateway, analyzeGateway)

        coordinator.requestAnalyze("请分析当前页面")
        val followUp = launch {
            coordinator.requestFollowUpAnalyzeAfterClick()
        }
        runCurrent()

        assertEquals(
            AnalyzeRequestState.Success(
                answer = "请点击发送按钮",
                conversationId = CONVERSATION_ID,
                conversationStatus = "waiting_interaction",
                guidanceCue = GuidanceCue.Hidden,
            ),
            coordinator.state.value,
        )

        advanceTimeBy(350)
        followUp.join()

        assertEquals(2, requests.size)
        assertEquals("observed_click", requests[1].messageType)
        assertEquals(null, requests[1].messageText)
        assertEquals(CONVERSATION_ID, requests[1].conversationId)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun observedClick_afterScrollHint_sendsFollowUpWithSameConversationId() = runTest {
        val capture = sampleCapture()
        val requests = mutableListOf<AnalyzeRequest>()

        val captureGateway = object : CaptureGateway {
            override suspend fun captureNow(): CaptureResult = capture
        }
        val analyzeGateway = object : AnalyzeGateway {
            override suspend fun analyze(
                request: AnalyzeRequest,
                capture: CaptureResult,
            ): AnalyzeResponse {
                requests += request
                return if (request.messageType == "speech_text") {
                    AnalyzeResponse(
                        conversationId = CONVERSATION_ID,
                        runId = "22222222-2222-4222-8222-222222222222",
                        conversationStatus = "waiting_interaction",
                        answer = "请上下滑动页面",
                        target = null,
                        action = ActionInfo(type = "scroll"),
                    )
                } else {
                    AnalyzeResponse(
                        conversationId = CONVERSATION_ID,
                        runId = "33333333-3333-4333-8333-333333333333",
                        conversationStatus = "waiting_interaction",
                        answer = "已继续分析",
                        target = null,
                        action = ActionInfo(type = "none"),
                    )
                }
            }

            override suspend fun cancel(conversationId: String): CancelResponse =
                cancelResponse(conversationId)
        }

        val coordinator = AnalyzeSessionCoordinator(captureGateway, analyzeGateway)

        coordinator.requestAnalyze("请分析当前页面")
        val followUp = launch {
            coordinator.requestFollowUpAnalyzeAfterClick()
        }
        runCurrent()
        advanceTimeBy(350)
        followUp.join()

        assertEquals(2, requests.size)
        assertEquals("observed_scroll", requests[1].messageType)
        assertEquals(CONVERSATION_ID, requests[1].conversationId)
    }

    @Test
    fun observedClick_ignoresIdleAndCompletedStates() = runTest {
        val captureGateway = object : CaptureGateway {
            override suspend fun captureNow(): CaptureResult = sampleCapture()
        }
        var requestCount = 0
        val analyzeGateway = object : AnalyzeGateway {
            override suspend fun analyze(
                request: AnalyzeRequest,
                capture: CaptureResult,
            ): AnalyzeResponse {
                requestCount += 1
                return AnalyzeResponse(
                    conversationId = CONVERSATION_ID,
                    runId = "22222222-2222-4222-8222-222222222222",
                    conversationStatus = "completed",
                    answer = "已完成",
                    target = null,
                    action = ActionInfo(type = "none"),
                )
            }

            override suspend fun cancel(conversationId: String): CancelResponse =
                cancelResponse(conversationId)
        }

        val coordinator = AnalyzeSessionCoordinator(captureGateway, analyzeGateway)

        coordinator.requestFollowUpAnalyzeAfterClick()
        assertEquals(0, requestCount)

        coordinator.requestAnalyze("请分析当前页面")
        assertEquals(1, requestCount)

        coordinator.requestFollowUpAnalyzeAfterClick()
        assertEquals(1, requestCount)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun observedClick_consumesOnlyOneFollowUpUntilServerReturnsWaitingAgain() = runTest {
        val targetBounds = NormalizedBounds(900, 2100, 1040, 2220)
        val capture = sampleCapture()
        val requests = mutableListOf<AnalyzeRequest>()

        val captureGateway = object : CaptureGateway {
            override suspend fun captureNow(): CaptureResult = capture
        }
        val analyzeGateway = object : AnalyzeGateway {
            override suspend fun analyze(
                request: AnalyzeRequest,
                capture: CaptureResult,
            ): AnalyzeResponse {
                requests += request
                return if (request.messageType == "speech_text") {
                    AnalyzeResponse(
                        conversationId = CONVERSATION_ID,
                        runId = "22222222-2222-4222-8222-222222222222",
                        conversationStatus = "waiting_interaction",
                        answer = "请点击发送按钮",
                        target = TargetInfo(
                            label = "发送按钮",
                            bounds = targetBounds,
                        ),
                        action = ActionInfo(type = "tap"),
                    )
                } else {
                    AnalyzeResponse(
                        conversationId = CONVERSATION_ID,
                        runId = "33333333-3333-4333-8333-333333333333",
                        conversationStatus = "completed",
                        answer = "已继续分析",
                        target = null,
                        action = ActionInfo(type = "none"),
                    )
                }
            }

            override suspend fun cancel(conversationId: String): CancelResponse =
                cancelResponse(conversationId)
        }

        val coordinator = AnalyzeSessionCoordinator(captureGateway, analyzeGateway)

        coordinator.requestAnalyze("请分析当前页面")
        val first = launch { coordinator.requestFollowUpAnalyzeAfterClick() }
        runCurrent()
        val second = launch { coordinator.requestFollowUpAnalyzeAfterClick() }
        runCurrent()
        advanceTimeBy(350)
        first.join()
        second.join()

        assertEquals(2, requests.size)
        assertEquals("speech_text", requests[0].messageType)
        assertEquals("observed_click", requests[1].messageType)
    }

    @Test
    fun requestAnalyze_retriesAnalyzeFailureAutomatically() = runTest {
        var attempts = 0
        val captureGateway = object : CaptureGateway {
            override suspend fun captureNow(): CaptureResult = sampleCapture()
        }
        val analyzeGateway = object : AnalyzeGateway {
            override suspend fun analyze(
                request: AnalyzeRequest,
                capture: CaptureResult,
            ): AnalyzeResponse {
                attempts += 1
                if (attempts == 1) {
                    error("temporary network failure")
                }
                return AnalyzeResponse(
                    conversationId = CONVERSATION_ID,
                    runId = "22222222-2222-4222-8222-222222222222",
                    conversationStatus = "waiting_interaction",
                    answer = "请点击发送按钮",
                    target = null,
                    action = ActionInfo(type = "none"),
                )
            }

            override suspend fun cancel(conversationId: String): CancelResponse =
                cancelResponse(conversationId)
        }

        val coordinator = AnalyzeSessionCoordinator(captureGateway, analyzeGateway)

        coordinator.requestAnalyze("请分析当前页面")

        assertEquals(2, attempts)
        assertEquals(
            AnalyzeRequestState.Success(
                answer = "请点击发送按钮",
                conversationId = CONVERSATION_ID,
                conversationStatus = "waiting_interaction",
            ),
            coordinator.state.value,
        )
    }

    @Test
    fun requestAnalyze_entersErrorOnlyAfterRetryLimit() = runTest {
        var attempts = 0
        val captureGateway = object : CaptureGateway {
            override suspend fun captureNow(): CaptureResult = sampleCapture()
        }
        val analyzeGateway = object : AnalyzeGateway {
            override suspend fun analyze(
                request: AnalyzeRequest,
                capture: CaptureResult,
            ): AnalyzeResponse {
                attempts += 1
                error("network stays down")
            }

            override suspend fun cancel(conversationId: String): CancelResponse =
                cancelResponse(conversationId)
        }

        val coordinator = AnalyzeSessionCoordinator(captureGateway, analyzeGateway)

        coordinator.requestAnalyze("请分析当前页面")

        assertEquals(3, attempts)
        assertEquals(AnalyzeRequestState.Error("network stays down"), coordinator.state.value)
    }

    @Test
    fun completedResponseReturnsStateToIdle() = runTest {
        val captureGateway = object : CaptureGateway {
            override suspend fun captureNow(): CaptureResult = sampleCapture()
        }
        val analyzeGateway = object : AnalyzeGateway {
            override suspend fun analyze(
                request: AnalyzeRequest,
                capture: CaptureResult,
            ): AnalyzeResponse =
                AnalyzeResponse(
                    conversationId = CONVERSATION_ID,
                    runId = "22222222-2222-4222-8222-222222222222",
                    conversationStatus = "completed",
                    answer = "已完成",
                    target = null,
                    action = ActionInfo(type = "none"),
                )

            override suspend fun cancel(conversationId: String): CancelResponse =
                cancelResponse(conversationId)
        }

        val coordinator = AnalyzeSessionCoordinator(captureGateway, analyzeGateway)

        coordinator.requestAnalyze("请分析当前页面")

        assertEquals(AnalyzeRequestState.Idle, coordinator.state.value)
    }

    @Test
    fun cancelCurrentAnalyze_callsCancelAndReturnsStateToIdle() = runTest {
        val targetBounds = NormalizedBounds(900, 2100, 1040, 2220)
        var cancelledConversationId: String? = null
        val captureGateway = object : CaptureGateway {
            override suspend fun captureNow(): CaptureResult = sampleCapture()
        }
        val analyzeGateway = object : AnalyzeGateway {
            override suspend fun analyze(
                request: AnalyzeRequest,
                capture: CaptureResult,
            ): AnalyzeResponse =
                AnalyzeResponse(
                    conversationId = CONVERSATION_ID,
                    runId = "22222222-2222-4222-8222-222222222222",
                    conversationStatus = "waiting_interaction",
                    answer = "请点击发送按钮",
                    target = TargetInfo(
                        label = "发送按钮",
                        bounds = targetBounds,
                    ),
                    action = ActionInfo(type = "tap"),
                )

            override suspend fun cancel(conversationId: String): CancelResponse {
                cancelledConversationId = conversationId
                return cancelResponse(conversationId)
            }
        }

        val coordinator = AnalyzeSessionCoordinator(captureGateway, analyzeGateway)
        coordinator.requestAnalyze("请分析当前页面")

        coordinator.cancelCurrentAnalyze()

        assertEquals(CONVERSATION_ID, cancelledConversationId)
        assertEquals(AnalyzeRequestState.Idle, coordinator.state.value)
    }

    private fun sampleCapture(
        nodes: List<NormalizedNode> = emptyList(),
    ): CaptureResult = CaptureResult(
        packageName = "com.example.target",
        activityName = "TargetActivity",
        nodes = nodes,
        screenWidth = 1080,
        screenHeight = 2400,
        screenshotBytes = "jpeg".toByteArray(),
        imageWidth = 1080,
        imageHeight = 2400,
    )

    private companion object {
        const val CONVERSATION_ID = "11111111-1111-4111-8111-111111111111"

        fun cancelResponse(conversationId: String): CancelResponse =
            CancelResponse(
                conversationId = conversationId,
                status = "cancelled",
                closedReason = "user_exit",
            )
    }
}
