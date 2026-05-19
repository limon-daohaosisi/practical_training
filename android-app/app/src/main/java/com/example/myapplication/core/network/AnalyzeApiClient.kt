package com.example.myapplication.core.network

import com.example.myapplication.core.model.CaptureResult
import com.example.myapplication.core.network.AnalyzeJson.parseResponse
import com.example.myapplication.core.network.AnalyzeJson.toJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.text.Charsets.UTF_8

class AnalyzeApiClient(
    private val endpoint: String
) : AnalyzeGateway {

    override suspend fun analyze(question: String, capture: CaptureResult): AnalyzeResponse {
        return withContext(Dispatchers.IO) {
            val metadata = AnalyzeRequestBuilder.buildMetadata(question, capture)
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
}
