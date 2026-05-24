package com.example.myapplication.core.session

import android.content.Context
import com.example.myapplication.core.capture.CaptureGateway
import com.example.myapplication.core.capture.AccessibilityCaptureGateway
import com.example.myapplication.core.network.AnalyzeApiClient
import com.example.myapplication.core.network.AnalyzeGateway
import com.example.myapplication.core.network.DeviceIdProvider
import com.example.myapplication.feature.session.AnalyzeSessionCoordinator

object AnalyzeRuntime {
    private const val defaultQuestion = "请分析当前页面"
    private const val defaultEndpoint = "http://10.0.2.2:3000/analyze"

    private val captureGateway: CaptureGateway = AccessibilityCaptureGateway()
    @Volatile
    private var coordinatorRef: AnalyzeSessionCoordinator? = null

    private fun coordinator(context: Context): AnalyzeSessionCoordinator {
        val current = coordinatorRef
        if (current != null) return current

        return synchronized(this) {
            coordinatorRef
                ?: AnalyzeSessionCoordinator(
                    captureGateway = captureGateway,
                    analyzeGateway =
                        AnalyzeApiClient(
                            endpoint = defaultEndpoint,
                            deviceIdProvider = DeviceIdProvider(context.applicationContext),
                        ),
                ).also { created ->
                    coordinatorRef = created
                }
        }
    }

    fun state(context: Context) = coordinator(context).state

    suspend fun analyzeNow(context: Context) {
        coordinator(context).requestAnalyze(defaultQuestion)
    }
}
