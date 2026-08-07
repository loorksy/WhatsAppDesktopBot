package com.whatsappbot.bulk.data

import android.content.Context
import com.whatsappbot.bulk.BuildConfig

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("bulk_sender", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = sp.getString(KEY_SERVER, BuildConfig.DEFAULT_SERVER_URL)?.trimEnd('/')
            ?: BuildConfig.DEFAULT_SERVER_URL
        set(value) = sp.edit().putString(KEY_SERVER, value.trim().trimEnd('/')).apply()

    var cookie: String
        get() = sp.getString(KEY_COOKIE, "") ?: ""
        set(value) = sp.edit().putString(KEY_COOKIE, value).apply()

    var email: String
        get() = sp.getString(KEY_EMAIL, "") ?: ""
        set(value) = sp.edit().putString(KEY_EMAIL, value).apply()

    fun clearSession() {
        sp.edit().remove(KEY_COOKIE).apply()
    }

    fun isLoggedIn(): Boolean = cookie.isNotBlank()

    companion object {
        private const val KEY_SERVER = "server_url"
        private const val KEY_COOKIE = "session_cookie"
        private const val KEY_EMAIL = "email"
    }
}
