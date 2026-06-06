package com.example.myapplication.core.session

import android.content.Context
import com.example.myapplication.core.capture.CaptureGateway
import com.example.myapplication.core.capture.AccessibilityCaptureGateway
import com.example.myapplication.core.model.GuidanceCue
import com.example.myapplication.core.network.AnalyzeApiClient
import com.example.myapplication.core.network.AnalyzeGateway
import com.example.myapplication.core.network.DeviceIdProvider
import com.example.myapplication.feature.session.AnalyzeRequestState
import com.example.myapplication.feature.session.AnalyzeSessionCoordinator
import kotlinx.coroutines.flow.map

object AnalyzeRuntime {
    private const val defaultQuestion = "帮我找一下“我的”在哪里"
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

    fun responseText(context: Context) = coordinator(context).responseText

    fun isRunning(context: Context): Boolean = coordinator(context).isRunning

    fun shouldListenForObservedInteraction(context: Context): Boolean =
        coordinator(context).shouldListenForObservedInteraction

    fun guidanceCue(context: Context) = state(context).map { state ->
        when (state) {
            is AnalyzeRequestState.Success -> state.guidanceCue
            else -> GuidanceCue.Hidden
        }
    }

    suspend fun analyzeNow(context: Context, question: String = defaultQuestion) {
        coordinator(context).requestAnalyze(question)
    }

    suspend fun onObservedClick(context: Context) {
        coordinator(context).requestFollowUpAnalyzeAfterClick()
    }

    suspend fun cancel(context: Context) {
        coordinator(context).cancelCurrentAnalyze()
    }
}
