package com.example.myapplication.app.network

import com.example.myapplication.app.model.CaptureResult

/**
 * Assembles [AnalyzeMetadata] from a [CaptureResult] plus user question.
 *
 * The network layer calls [buildMetadata] to get the JSON-serializable metadata
 * part and reads [CaptureResult.screenshotBytes] as the binary "screenshot" part
 * of the multipart/form-data request.
 */
object AnalyzeRequestBuilder {

    fun buildMetadata(question: String, capture: CaptureResult): AnalyzeMetadata {
        require(question.isNotBlank()) { "question must not be blank" }
        require(capture.packageName.isNotBlank()) { "packageName must not be blank" }
        require(capture.screenWidth > 0) { "screenWidth must be > 0" }
        require(capture.screenHeight > 0) { "screenHeight must be > 0" }
        require(capture.imageWidth > 0) { "imageWidth must be > 0 — screenshot not ready" }
        require(capture.imageHeight > 0) { "imageHeight must be > 0 — screenshot not ready" }

        return AnalyzeMetadata(
            question = question,
            packageName = capture.packageName,
            activityName = capture.activityName,
            screenWidth = capture.screenWidth,
            screenHeight = capture.screenHeight,
            imageWidth = capture.imageWidth,
            imageHeight = capture.imageHeight,
            nodes = capture.nodes
        )
    }
}
