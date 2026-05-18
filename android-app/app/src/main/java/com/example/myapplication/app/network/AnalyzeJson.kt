package com.example.myapplication.app.network

import com.example.myapplication.app.model.NormalizedBounds
import com.example.myapplication.app.model.NormalizedNode
import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON serialization for analyze contract objects using Android's built-in org.json.
 *
 * No external dependencies — the network layer can call [AnalyzeMetadata.toJson] and
 * pass the resulting string as the "metadata" part of the multipart/form-data request.
 */
object AnalyzeJson {

    fun AnalyzeMetadata.toJson(): String = JSONObject().apply {
        put("question", question)
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
            answer = obj.getString("answer"),
            target = if (targetObj != null) TargetInfo(
                label = targetObj.getString("label"),
                bounds = NormalizedBounds(
                    left = targetObj.getJSONObject("bounds").getInt("left"),
                    top = targetObj.getJSONObject("bounds").getInt("top"),
                    right = targetObj.getJSONObject("bounds").getInt("right"),
                    bottom = targetObj.getJSONObject("bounds").getInt("bottom")
                )
            ) else null,
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
}
