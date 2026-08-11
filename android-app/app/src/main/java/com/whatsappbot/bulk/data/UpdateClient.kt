package com.whatsappbot.bulk.data

import com.whatsappbot.bulk.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class UpdateClient(
    private val serverUrl: String = LicensePrefs.DEFAULT_SERVER,
) {
    fun fetchUpdateInfo(): UpdateInfo? {
        return try {
            val url = URL("${serverUrl.trimEnd('/')}/api/app/update")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12000
                readTimeout = 15000
                setRequestProperty("Accept", "application/json")
            }
            val code = conn.responseCode
            val body = try {
                (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()
                    ?.readText()
                    .orEmpty()
            } finally {
                conn.disconnect()
            }
            if (code !in 200..299 || body.isBlank()) return null
            val json = JSONObject(body)
            val changelog = buildList {
                val arr = json.optJSONArray("changelog")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val line = arr.optString(i).trim()
                        if (line.isNotEmpty()) add(line)
                    }
                }
            }
            UpdateInfo(
                versionCode = json.optInt("versionCode", 0),
                versionName = json.optString("versionName"),
                force = json.optBoolean("force", false),
                apkUrl = json.optString("apkUrl").ifBlank {
                    "${serverUrl.trimEnd('/')}/download/bulk-sender.apk"
                },
                title = json.optString("title").ifBlank { "يتوفر تحديث جديد" },
                changelog = changelog,
            )
        } catch (_: Exception) {
            null
        }
    }

    fun isNewer(info: UpdateInfo): Boolean = info.versionCode > BuildConfig.VERSION_CODE
}
