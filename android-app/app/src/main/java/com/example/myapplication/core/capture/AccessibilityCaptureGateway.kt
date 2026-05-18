package com.example.myapplication.core.capture

import com.example.myapplication.core.accessibility.CaptureAccessibilityService
import com.example.myapplication.core.model.CaptureResult

class AccessibilityCaptureGateway : CaptureGateway {
    override suspend fun captureNow(): CaptureResult {
        return CaptureAccessibilityService.captureNow()
    }
}
