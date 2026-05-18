package com.example.myapplication.feature.session

import com.example.myapplication.core.capture.CaptureGateway
import com.example.myapplication.core.network.AnalyzeGateway
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface AnalyzeRequestState {
    data object Idle : AnalyzeRequestState
    data object Capturing : AnalyzeRequestState
    data object Uploading : AnalyzeRequestState
    data class Success(val answer: String) : AnalyzeRequestState
    data class Error(val message: String) : AnalyzeRequestState
}

class AnalyzeSessionCoordinator(
    private val captureGateway: CaptureGateway,
    private val analyzeGateway: AnalyzeGateway
) {
    private val mutableState = MutableStateFlow<AnalyzeRequestState>(AnalyzeRequestState.Idle)
    val state: StateFlow<AnalyzeRequestState> = mutableState.asStateFlow()

    suspend fun requestAnalyze(question: String) {
        try {
            mutableState.value = AnalyzeRequestState.Capturing
            val capture = captureGateway.captureNow()
            mutableState.value = AnalyzeRequestState.Uploading
            val response = analyzeGateway.analyze(question, capture)
            mutableState.value = AnalyzeRequestState.Success(response.answer)
        } catch (error: Exception) {
            mutableState.value = AnalyzeRequestState.Error(
                error.message ?: "Analyze request failed."
            )
        }
    }
}
