package com.example.myapplication.core.network

import com.example.myapplication.core.model.CaptureResult
import com.example.myapplication.core.model.NormalizedBounds
import com.example.myapplication.core.model.NormalizedNode
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class AnalyzeContractTest {

    @Test
    fun buildMetadata_matchesAnalyzeContractForMockSpeechRequest() {
        val capture = CaptureResult(
            packageName = "com.google.android.youtube",
            activityName = "com.google.android.apps.youtube.app.WatchWhileActivity",
            nodes = listOf(
                NormalizedNode(
                    text = "搜索",
                    contentDescription = "",
                    className = "android.widget.ImageButton",
                    clickable = true,
                    editable = false,
                    enabled = true,
                    bounds = NormalizedBounds(840, 108, 960, 228),
                ),
            ),
            screenWidth = 1080,
            screenHeight = 2400,
            screenshotBytes = "jpeg".toByteArray(),
            imageWidth = 1080,
            imageHeight = 2400,
        )

        val metadata = AnalyzeRequestBuilder.buildMetadata(
            deviceId = "device-001",
            request = AnalyzeRequest(
                messageType = "speech_text",
                messageText = "请分析当前页面",
            ),
            capture = capture,
        )

        val json =
            JSONObject(
                with(AnalyzeJson) {
                    metadata.toJson()
                },
            )

        assertEquals("device-001", json.getString("deviceId"))
        assertEquals("speech_text", json.getString("messageType"))
        assertEquals("请分析当前页面", json.getString("messageText"))
        assertEquals("com.google.android.youtube", json.getString("packageName"))
        assertEquals(
            "com.google.android.apps.youtube.app.WatchWhileActivity",
            json.getString("activityName"),
        )
        assertEquals(1080, json.getInt("screenWidth"))
        assertEquals(2400, json.getInt("screenHeight"))
        assertEquals(1080, json.getInt("imageWidth"))
        assertEquals(2400, json.getInt("imageHeight"))
        assertFalse(json.has("question"))
        assertFalse(json.has("conversationId"))
        assertEquals(1, json.getJSONArray("nodes").length())
    }

    @Test
    fun parseResponse_ignoresConversationFieldsForCurrentMockUiFlow() {
        val json =
            """
            {
              "conversationId": "11111111-1111-4111-8111-111111111111",
              "runId": "22222222-2222-4222-8222-222222222222",
              "conversationStatus": "waiting_interaction",
              "answer": "请先点击底部的我的",
              "target": null,
              "action": { "type": "none" }
            }
            """.trimIndent()

        val response = AnalyzeJson.parseResponse(json)

        assertEquals("请先点击底部的我的", response.answer)
        assertNull(response.target)
        assertEquals("none", response.action.type)
        assertEquals("11111111-1111-4111-8111-111111111111", response.conversationId)
        assertEquals("waiting_interaction", response.conversationStatus)
    }

    @Test
    fun parseResponse_readsTapTargetBoundsFromAnalyzeContract() {
        val json =
            """
            {
              "conversationId": "11111111-1111-4111-8111-111111111111",
              "runId": "22222222-2222-4222-8222-222222222222",
              "conversationStatus": "waiting_interaction",
              "answer": "请先点击底部的我的",
              "target": {
                "label": "我的",
                "bounds": { "left": 270, "top": 2280, "right": 540, "bottom": 2400 }
              },
              "action": { "type": "tap" }
            }
            """.trimIndent()

        val response = AnalyzeJson.parseResponse(json)

        assertEquals("tap", response.action.type)
        assertEquals("我的", response.target?.label)
        assertEquals(NormalizedBounds(270, 2280, 540, 2400), response.target?.bounds)
    }

    @Test
    fun buildMetadata_keepsNullMessageTextForObservedClick() {
        val capture = CaptureResult(
            packageName = "com.google.android.youtube",
            activityName = "com.google.android.apps.youtube.app.WatchWhileActivity",
            nodes = emptyList(),
            screenWidth = 1080,
            screenHeight = 2400,
            screenshotBytes = "jpeg".toByteArray(),
            imageWidth = 1080,
            imageHeight = 2400,
        )

        val metadata = AnalyzeRequestBuilder.buildMetadata(
            deviceId = "device-001",
            request = AnalyzeRequest(
                conversationId = "11111111-1111-4111-8111-111111111111",
                messageType = "observed_click",
                messageText = null,
            ),
            capture = capture,
        )

        val json =
            JSONObject(
                with(AnalyzeJson) {
                    metadata.toJson()
                },
            )

        assertEquals(true, json.has("messageText"))
        assertEquals(true, json.isNull("messageText"))
        assertEquals("observed_click", json.getString("messageType"))
        assertEquals(
            "11111111-1111-4111-8111-111111111111",
            json.getString("conversationId"),
        )
    }
}
