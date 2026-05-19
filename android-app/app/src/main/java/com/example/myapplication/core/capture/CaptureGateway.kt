package com.example.myapplication.core.capture

import com.example.myapplication.core.model.CaptureResult

interface CaptureGateway {
    suspend fun captureNow(): CaptureResult
}
