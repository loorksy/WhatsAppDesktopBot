package com.whatsappbot.bulk.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class LicenseResult(
    val active: Boolean,
    val error: String? = null,
    val note: String? = null,
)

class LicenseClient(private val prefs: LicensePrefs) {
    fun activate(code: String): LicenseResult {
        return post("/api/license/activate", JSONObject()
            .put("code", code)
            .put("deviceId", prefs.deviceId()))
    }

    fun checkStatus(): LicenseResult {
        if (!prefs.isActivated()) {
            return LicenseResult(active = false, error = "MISSING_FIELDS")
        }
        return post("/api/license/status", JSONObject()
            .put("code", prefs.licenseCode)
            .put("deviceId", prefs.deviceId()))
    }

    private fun post(path: String, body: JSONObject): LicenseResult {
        val base = prefs.serverUrl.trimEnd('/')
        if (base.isBlank()) {
            return LicenseResult(active = false, error = "MISSING_FIELDS")
        }
        return try {
            val url = URL("$base$path")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 20000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            conn.outputStream.use { os ->
                os.write(body.toString().toByteArray(Charsets.UTF_8))
            }
            val code = conn.responseCode
            val text = try {
                (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()
                    ?.readText()
                    .orEmpty()
            } catch (_: Exception) {
                ""
            } finally {
                conn.disconnect()
            }
            val json = runCatching { JSONObject(text.ifBlank { "{}" }) }.getOrElse { JSONObject() }
            if (code in 200..299 && json.optBoolean("active", json.optBoolean("ok"))) {
                LicenseResult(active = true, note = json.optString("note"))
            } else {
                LicenseResult(
                    active = false,
                    error = json.optString("error").ifBlank { "ERROR" },
                )
            }
        } catch (_: Exception) {
            LicenseResult(active = false, error = "NETWORK")
        }
    }
}
