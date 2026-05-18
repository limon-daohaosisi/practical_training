package com.example.myapplication.app

import android.content.Context
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.example.myapplication.app.theme.MyApplicationTheme
import com.example.myapplication.feature.session.SessionDebugScreen

class MainActivity : ComponentActivity() {

    private val accessibilityEnabledState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshAccessibilityState()
        setContent {
            MyApplicationTheme {
                SessionDebugScreen(
                    context = this,
                    isAccessibilityEnabled = accessibilityEnabledState
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshAccessibilityState()
    }

    private fun refreshAccessibilityState() {
        accessibilityEnabledState.value = isAccessibilityEnabled(this)
    }
}

private fun isAccessibilityEnabled(context: Context): Boolean {
    val serviceName = "${context.packageName}/${context.packageName}.core.accessibility.CaptureAccessibilityService"
    val enabledServices = try {
        Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
    } catch (_: Exception) {
        null
    }
    return enabledServices?.contains(serviceName) == true
}
