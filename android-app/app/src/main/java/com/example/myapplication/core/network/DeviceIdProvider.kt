package com.example.myapplication.core.network

import android.content.Context
import android.provider.Settings

class DeviceIdProvider(
    private val context: Context,
) {
    fun get(): String {
        val androidId =
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID,
            )?.trim().orEmpty()

        return if (androidId.isNotBlank()) {
            "android-$androidId"
        } else {
            "android-${context.packageName}"
        }
    }
}
