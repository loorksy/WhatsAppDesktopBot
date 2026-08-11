package com.whatsappbot.bulk.data

import android.content.Context

class UpdatePrefs(context: Context) {
    private val sp = context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)

    var lastNotifiedVersion: Int
        get() = sp.getInt(KEY_NOTIFIED, 0)
        set(value) = sp.edit().putInt(KEY_NOTIFIED, value).apply()

    var lastDialogVersion: Int
        get() = sp.getInt(KEY_DIALOG, 0)
        set(value) = sp.edit().putInt(KEY_DIALOG, value).apply()

    companion object {
        private const val KEY_NOTIFIED = "last_notified_version"
        private const val KEY_DIALOG = "last_dialog_version"
    }
}
