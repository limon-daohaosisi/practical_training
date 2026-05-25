package com.example.myapplication.core.network

import com.example.myapplication.core.model.CaptureResult
import com.example.myapplication.core.network.AnalyzeJson.parseCancelResponse
import com.example.myapplication.core.network.AnalyzeJson.parseResponse
import com.example.myapplication.core.network.AnalyzeJson.toJson
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.text.Charsets.UTF_8

class AnalyzeApiClient(
    private val endpoint: String,
    private val deviceIdProvider: DeviceIdProvider,
) : AnalyzeGateway {

    private val cancelEndpointBase = endpoint.removeSuffix("/analyze")

    override suspend fun analyze(request: AnalyzeRequest, capture: CaptureResult): AnalyzeResponse {
        return withContext(Dispatchers.IO) {
            val metadata =
                AnalyzeRequestBuilder.buildMetadata(
                    deviceId = deviceIdProvider.get(),
                    request = request,
                    capture = capture,
                )
            val screenshot = capture.screenshotBytes
                ?: error("Screenshot bytes are not available yet for explicit analyze requests.")
            val boundary = "----guide-assistant-${System.currentTimeMillis()}"
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                setRequestProperty("Accept", "application/json")
            }

            DataOutputStream(connection.outputStream).use { output ->
                output.writeBytes("--$boundary\r\n")
                output.writeBytes("Content-Disposition: form-data; name=\"metadata\"\r\n")
                output.writeBytes("Content-Type: application/json; charset=utf-8\r\n\r\n")
                output.write(metadata.toJson().toByteArray(UTF_8))
                output.writeBytes("\r\n")

                output.writeBytes("--$boundary\r\n")
                output.writeBytes("Content-Disposition: form-data; name=\"screenshot\"; filename=\"capture.jpg\"\r\n")
                output.writeBytes("Content-Type: image/jpeg\r\n\r\n")
                output.write(screenshot)
                output.writeBytes("\r\n--$boundary--\r\n")
                output.flush()
            }

            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: error("Analyze request failed with ${connection.responseCode}")
            }
            val body = BufferedInputStream(stream).bufferedReader().use { it.readText() }
            if (connection.responseCode in 200..299) {
                return@withContext parseResponse(body)
            }
            val error = AnalyzeJson.parseError(body)
            error("${error.code}: ${error.message}")
        }
    }

    override suspend fun cancel(conversationId: String): CancelResponse {
        return withContext(Dispatchers.IO) {
            val connection = (
                URL("$cancelEndpointBase/conversations/$conversationId/cancel")
                    .openConnection() as HttpURLConnection
                ).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }
            val body = JSONObject()
                .put("deviceId", deviceIdProvider.get())
                .toString()

            DataOutputStream(connection.outputStream).use { output ->
                output.write(body.toByteArray(UTF_8))
                output.flush()
            }

            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: error("Cancel request failed with ${connection.responseCode}")
            }
            val responseBody = BufferedInputStream(stream).bufferedReader().use { it.readText() }
            if (connection.responseCode in 200..299) {
                return@withContext parseCancelResponse(responseBody)
            }
            val error = AnalyzeJson.parseError(responseBody)
            error("${error.code}: ${error.message}")
        }
    }
}
