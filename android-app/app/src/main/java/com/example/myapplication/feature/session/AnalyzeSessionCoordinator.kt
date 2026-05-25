package com.example.myapplication.feature.session

import com.example.myapplication.core.capture.CaptureGateway
import com.example.myapplication.core.model.GuidanceCue
import com.example.myapplication.core.network.AnalyzeGateway
import com.example.myapplication.core.network.AnalyzeRequest
import com.example.myapplication.core.network.AnalyzeResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface AnalyzeRequestState {
    data object Idle : AnalyzeRequestState
    data object Capturing : AnalyzeRequestState
    data object Uploading : AnalyzeRequestState
    data class Success(
        val answer: String,
        val conversationId: String,
        val conversationStatus: String,
        val guidanceCue: GuidanceCue = GuidanceCue.Hidden,
    ) : AnalyzeRequestState

    data class Error(val message: String) : AnalyzeRequestState
}

class AnalyzeSessionCoordinator(
    private val captureGateway: CaptureGateway,
    private val analyzeGateway: AnalyzeGateway
) {
    private val mutableState = MutableStateFlow<AnalyzeRequestState>(AnalyzeRequestState.Idle)
    private val followUpMutex = Mutex()
    private var sessionVersion = 0
    val state: StateFlow<AnalyzeRequestState> = mutableState.asStateFlow()

    val isRunning: Boolean
        get() = mutableState.value.isRunningState()

    suspend fun requestAnalyze(question: String) {
        val version = ++sessionVersion
        requestAnalyze(
            AnalyzeRequest(
                messageType = "speech_text",
                messageText = question,
            ),
            version = version,
        )
    }

    suspend fun requestFollowUpAnalyzeAfterClick() {
        followUpMutex.withLock {
            val current = mutableState.value
            val version = sessionVersion
            if (current !is AnalyzeRequestState.Success ||
                current.conversationStatus != "waiting_interaction"
            ) {
                return
            }

            mutableState.value = current.copy(guidanceCue = GuidanceCue.Hidden)
            delay(FOLLOW_UP_DELAY_MS)
            requestAnalyze(
                AnalyzeRequest(
                    conversationId = current.conversationId,
                    messageType = "observed_click",
                    messageText = null,
                ),
                version = version,
            )
        }
    }

    suspend fun cancelCurrentAnalyze() {
        val current = mutableState.value
        if (current == AnalyzeRequestState.Idle) return

        sessionVersion++
        mutableState.value = AnalyzeRequestState.Idle
        if (current is AnalyzeRequestState.Success) {
            runCatching {
                analyzeGateway.cancel(current.conversationId)
            }.onFailure { error ->
                mutableState.value = AnalyzeRequestState.Error(
                    error.message ?: "Cancel request failed.",
                )
            }
        }
    }

    private suspend fun requestAnalyze(
        request: AnalyzeRequest,
        version: Int,
    ) {
        var lastError: Exception? = null

        repeat(MAX_ANALYZE_ATTEMPTS) { attemptIndex ->
            if (version != sessionVersion) return
            try {
                mutableState.value = AnalyzeRequestState.Capturing
                val capture = captureGateway.captureNow()
                if (version != sessionVersion) return

                mutableState.value = AnalyzeRequestState.Uploading
                val response = analyzeGateway.analyze(request, capture)
                if (version != sessionVersion) return

                mutableState.value = response.toRequestState()
                return
            } catch (error: Exception) {
                lastError = error
                if (version != sessionVersion) return
                if (attemptIndex < MAX_ANALYZE_ATTEMPTS - 1) {
                    delay(RETRY_DELAY_MS)
                }
            }
        }

        mutableState.value = AnalyzeRequestState.Error(
            lastError?.message ?: "Analyze request failed.",
        )
    }

    private fun AnalyzeResponse.toRequestState(): AnalyzeRequestState = when (conversationStatus) {
        "waiting_interaction" -> AnalyzeRequestState.Success(
            answer = answer,
            conversationId = conversationId,
            conversationStatus = conversationStatus,
            guidanceCue = toGuidanceCue(),
        )
        "completed" -> AnalyzeRequestState.Idle
        "failed" -> AnalyzeRequestState.Error(answer)
        else -> AnalyzeRequestState.Error("Unsupported conversation status: $conversationStatus")
    }

    private fun AnalyzeResponse.toGuidanceCue(): GuidanceCue = when (action.type) {
        "tap" -> target?.let {
            GuidanceCue.TapTarget(
                label = it.label,
                bounds = it.bounds,
            )
        } ?: GuidanceCue.Hidden
        "scroll" -> GuidanceCue.ScrollHint
        else -> GuidanceCue.Hidden
    }

    private companion object {
        const val FOLLOW_UP_DELAY_MS = 350L
        const val MAX_ANALYZE_ATTEMPTS = 3
        const val RETRY_DELAY_MS = 600L
    }
}

fun AnalyzeRequestState.isRunningState(): Boolean = when (this) {
    AnalyzeRequestState.Capturing,
    AnalyzeRequestState.Uploading,
    is AnalyzeRequestState.Success,
    -> true
    AnalyzeRequestState.Idle,
    is AnalyzeRequestState.Error,
    -> false
}
