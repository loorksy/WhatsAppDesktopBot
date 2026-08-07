package com.whatsappbot.bulk.data

import android.content.Context
import android.provider.Settings

class LicensePrefs(private val context: Context) {
    private val sp = context.getSharedPreferences("license_prefs", Context.MODE_PRIVATE)

    val serverUrl: String
        get() = DEFAULT_SERVER

    var licenseCode: String
        get() = sp.getString(KEY_CODE, "") ?: ""
        set(value) = sp.edit().putString(KEY_CODE, value.trim().uppercase()).apply()

    fun deviceId(): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        return androidId ?: "unknown-device"
    }

    fun isActivated(): Boolean = licenseCode.isNotBlank()

    fun clearActivation() {
        sp.edit().remove(KEY_CODE).apply()
    }

    companion object {
        const val DEFAULT_SERVER = "https://bot.lork.cloud"
        private const val KEY_CODE = "license_code"
    }
}
