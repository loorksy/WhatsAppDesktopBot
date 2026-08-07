package com.whatsappbot.bulk.data

import android.content.Context
import android.provider.Settings

class LicensePrefs(private val context: Context) {
    private val sp = context.getSharedPreferences("license_prefs", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = sp.getString(KEY_SERVER, "")?.trimEnd('/') ?: ""
        set(value) = sp.edit().putString(KEY_SERVER, value.trim().trimEnd('/')).apply()

    var licenseCode: String
        get() = sp.getString(KEY_CODE, "") ?: ""
        set(value) = sp.edit().putString(KEY_CODE, value.trim().uppercase()).apply()

    fun deviceId(): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        return androidId ?: "unknown-device"
    }

    fun isActivated(): Boolean = serverUrl.isNotBlank() && licenseCode.isNotBlank()

    fun clearActivation() {
        sp.edit().remove(KEY_CODE).apply()
    }

    companion object {
        private const val KEY_SERVER = "server_url"
        private const val KEY_CODE = "license_code"
    }
}
