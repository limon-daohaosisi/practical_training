package com.example.myapplication.core.session

import com.example.myapplication.core.capture.CaptureGateway
import com.example.myapplication.core.capture.AccessibilityCaptureGateway
import com.example.myapplication.core.network.AnalyzeApiClient
import com.example.myapplication.core.network.AnalyzeGateway
import com.example.myapplication.feature.session.AnalyzeSessionCoordinator

object AnalyzeRuntime {
    private const val defaultQuestion = "请分析当前页面"
    private const val defaultEndpoint = "http://10.0.2.2:3000/analyze"

    private val captureGateway: CaptureGateway = AccessibilityCaptureGateway()
    private val analyzeGateway: AnalyzeGateway = AnalyzeApiClient(defaultEndpoint)

    val coordinator = AnalyzeSessionCoordinator(captureGateway, analyzeGateway)

    suspend fun analyzeNow() {
        coordinator.requestAnalyze(defaultQuestion)
    }
}
