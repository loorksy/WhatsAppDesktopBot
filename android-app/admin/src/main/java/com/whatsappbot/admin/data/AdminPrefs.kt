package com.whatsappbot.admin.data

import android.content.Context

class AdminPrefs(context: Context) {
    private val sp = context.getSharedPreferences("admin_prefs", Context.MODE_PRIVATE)

    val serverUrl: String
        get() = DEFAULT_SERVER

    var email: String
        get() = sp.getString(KEY_EMAIL, "") ?: ""
        set(value) = sp.edit().putString(KEY_EMAIL, value.trim()).apply()

    var cookie: String
        get() = sp.getString(KEY_COOKIE, "") ?: ""
        set(value) = sp.edit().putString(KEY_COOKIE, value).apply()

    fun isLoggedIn(): Boolean = cookie.isNotBlank()

    fun clearSession() {
        sp.edit().remove(KEY_COOKIE).apply()
    }

    companion object {
        const val DEFAULT_SERVER = "https://bot.lork.cloud"
        private const val KEY_EMAIL = "email"
        private const val KEY_COOKIE = "cookie"
    }
}
