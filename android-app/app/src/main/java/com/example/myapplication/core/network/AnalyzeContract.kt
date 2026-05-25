package com.example.myapplication.core.network

import com.example.myapplication.core.model.CaptureResult
import com.example.myapplication.core.model.NormalizedBounds
import com.example.myapplication.core.model.NormalizedNode
import org.json.JSONArray
import org.json.JSONObject

data class AnalyzeMetadata(
    val deviceId: String,
    val conversationId: String? = null,
    val messageType: String,
    val messageText: String?,
    val packageName: String,
    val activityName: String,
    val screenWidth: Int,
    val screenHeight: Int,
    val imageWidth: Int,
    val imageHeight: Int,
    val nodes: List<NormalizedNode>
)

data class AnalyzeRequest(
    val messageType: String,
    val messageText: String?,
    val conversationId: String? = null,
)

data class AnalyzeResponse(
    val conversationId: String,
    val runId: String,
    val conversationStatus: String,
    val answer: String,
    val target: TargetInfo?,
    val action: ActionInfo
)

data class TargetInfo(
    val label: String,
    val bounds: NormalizedBounds
)

data class ActionInfo(
    val type: String
)

data class AnalyzeError(
    val code: String,
    val message: String
)

data class CancelResponse(
    val conversationId: String,
    val status: String,
    val closedReason: String,
)

interface AnalyzeGateway {
    suspend fun analyze(request: AnalyzeRequest, capture: CaptureResult): AnalyzeResponse

    suspend fun cancel(conversationId: String): CancelResponse
}

object AnalyzeRequestBuilder {

    fun buildMetadata(
        deviceId: String,
        request: AnalyzeRequest,
        capture: CaptureResult,
    ): AnalyzeMetadata {
        require(deviceId.isNotBlank()) { "deviceId must not be blank" }
        if (request.messageType == "speech_text") {
            require(!request.messageText.isNullOrBlank()) { "messageText must not be blank for speech_text" }
        } else {
            require(!request.conversationId.isNullOrBlank()) { "conversationId must not be blank for observed events" }
        }
        require(capture.packageName.isNotBlank()) { "packageName must not be blank" }
        require(capture.screenWidth > 0) { "screenWidth must be > 0" }
        require(capture.screenHeight > 0) { "screenHeight must be > 0" }
        require(capture.imageWidth > 0) { "imageWidth must be > 0 — screenshot not ready" }
        require(capture.imageHeight > 0) { "imageHeight must be > 0 — screenshot not ready" }

        return AnalyzeMetadata(
            deviceId = deviceId,
            conversationId = request.conversationId,
            messageType = request.messageType,
            messageText = request.messageText,
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

object AnalyzeJson {

    fun AnalyzeMetadata.toJson(): String = JSONObject().apply {
        put("deviceId", deviceId)
        if (conversationId != null) {
            put("conversationId", conversationId)
        }
        put("messageType", messageType)
        put("messageText", messageText ?: JSONObject.NULL)
        put("packageName", packageName)
        put("activityName", activityName)
        put("screenWidth", screenWidth)
        put("screenHeight", screenHeight)
        put("imageWidth", imageWidth)
        put("imageHeight", imageHeight)
        put("nodes", JSONArray().also { arr ->
            nodes.forEach { arr.put(it.toJson()) }
        })
    }.toString()

    private fun NormalizedNode.toJson(): JSONObject = JSONObject().apply {
        put("text", text)
        put("contentDescription", contentDescription)
        put("className", className)
        put("clickable", clickable)
        put("editable", editable)
        put("enabled", enabled)
        put("bounds", JSONObject().apply {
            put("left", bounds.left)
            put("top", bounds.top)
            put("right", bounds.right)
            put("bottom", bounds.bottom)
        })
    }

    fun parseResponse(json: String): AnalyzeResponse {
        val obj = JSONObject(json)
        val targetObj = obj.optJSONObject("target")
        val actionObj = obj.getJSONObject("action")
        return AnalyzeResponse(
            conversationId = obj.getString("conversationId"),
            runId = obj.getString("runId"),
            conversationStatus = obj.getString("conversationStatus"),
            answer = obj.getString("answer"),
            target = if (targetObj != null) {
                TargetInfo(
                    label = targetObj.getString("label"),
                    bounds = NormalizedBounds(
                        left = targetObj.getJSONObject("bounds").getInt("left"),
                        top = targetObj.getJSONObject("bounds").getInt("top"),
                        right = targetObj.getJSONObject("bounds").getInt("right"),
                        bottom = targetObj.getJSONObject("bounds").getInt("bottom")
                    )
                )
            } else {
                null
            },
            action = ActionInfo(type = actionObj.getString("type"))
        )
    }

    fun parseError(json: String): AnalyzeError {
        val obj = JSONObject(json)
        return AnalyzeError(
            code = obj.getString("code"),
            message = obj.getString("message")
        )
    }

    fun parseCancelResponse(json: String): CancelResponse {
        val obj = JSONObject(json)
        return CancelResponse(
            conversationId = obj.getString("conversationId"),
            status = obj.getString("status"),
            closedReason = obj.getString("closedReason"),
        )
    }
}
