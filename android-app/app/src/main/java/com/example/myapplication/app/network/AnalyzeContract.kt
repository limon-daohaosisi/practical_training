package com.example.myapplication.app.network

import com.example.myapplication.app.model.NormalizedBounds
import com.example.myapplication.app.model.NormalizedNode

/**
 * RFC-compliant request metadata sent as the "metadata" part of multipart/form-data.
 *
 * Field names match the external contract exactly so the network layer
 * serializes them directly without renaming.
 */
data class AnalyzeMetadata(
    val question: String,
    val packageName: String,
    val activityName: String,
    val screenWidth: Int,
    val screenHeight: Int,
    val imageWidth: Int,
    val imageHeight: Int,
    val nodes: List<NormalizedNode>
)

/**
 * RFC Section 8 — success response from [POST /analyze].
 */
data class AnalyzeResponse(
    val answer: String,
    val target: TargetInfo?,
    val action: ActionInfo
)

data class TargetInfo(
    val label: String,
    val bounds: NormalizedBounds
)

data class ActionInfo(
    val type: String // "tap" | "scroll" | "none"
)

/**
 * RFC Section 9 — error response.
 */
data class AnalyzeError(
    val code: String,  // INVALID_REQUEST | ANALYSIS_FAILED | INTERNAL_ERROR
    val message: String
)
