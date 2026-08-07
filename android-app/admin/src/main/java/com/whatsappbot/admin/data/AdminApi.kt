package com.whatsappbot.admin.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpCookie
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

data class LicenseItem(
    val id: String,
    val code: String,
    val note: String,
    val active: Boolean,
    val deviceId: String?,
    val createdAt: Long,
    val activatedAt: Long?,
    val lastSeenAt: Long?,
)

class AdminApi(private val prefs: AdminPrefs) {
    init {
        ensureCookieManager()
        restoreCookie()
    }

    fun login(email: String, password: String): Boolean {
        val conn = open("POST", "/api/login", auth = false)
        writeJson(conn, JSONObject().put("email", email).put("password", password))
        val code = conn.responseCode
        val setCookie = conn.getHeaderField("Set-Cookie")
        val body = readBody(conn)
        conn.disconnect()
        if (code !in 200..299) return false
        val ok = runCatching { JSONObject(body).optBoolean("success") }.getOrDefault(false)
        if (!setCookie.isNullOrBlank() && setCookie.contains("token=")) {
            val tokenPart = setCookie.split(';').map { it.trim() }.firstOrNull { it.startsWith("token=") }
            if (tokenPart != null) prefs.cookie = tokenPart
        }
        persistCookie()
        return ok && prefs.cookie.isNotBlank()
    }

    fun listLicenses(): List<LicenseItem> {
        val conn = open("GET", "/api/licenses")
        val code = conn.responseCode
        val body = readBody(conn)
        conn.disconnect()
        if (code == 401 || code == 403) error("UNAUTHORIZED")
        if (code !in 200..299) error("ERROR")
        val arr = JSONArray(body)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(parseLicense(o))
            }
        }
    }

    fun createLicense(note: String): LicenseItem {
        val conn = open("POST", "/api/licenses")
        writeJson(conn, JSONObject().put("note", note))
        val code = conn.responseCode
        val body = readBody(conn)
        conn.disconnect()
        if (code !in 200..299) error("ERROR")
        return parseLicense(JSONObject(body).getJSONObject("license"))
    }

    fun updateLicense(id: String, active: Boolean? = null, resetDevice: Boolean = false, note: String? = null) {
        val payload = JSONObject()
        if (active != null) payload.put("active", active)
        if (resetDevice) payload.put("resetDevice", true)
        if (note != null) payload.put("note", note)
        val conn = open("PUT", "/api/licenses/$id")
        writeJson(conn, payload)
        val code = conn.responseCode
        conn.disconnect()
        if (code !in 200..299) error("ERROR")
    }

    fun deleteLicense(id: String) {
        val conn = open("DELETE", "/api/licenses/$id")
        val code = conn.responseCode
        conn.disconnect()
        if (code !in 200..299) error("ERROR")
    }

    private fun parseLicense(o: JSONObject): LicenseItem {
        return LicenseItem(
            id = o.optString("id"),
            code = o.optString("code"),
            note = o.optString("note"),
            active = o.optBoolean("active", true),
            deviceId = o.optString("deviceId").ifBlank { null },
            createdAt = o.optLong("createdAt"),
            activatedAt = o.optLong("activatedAt").takeIf { it > 0 },
            lastSeenAt = o.optLong("lastSeenAt").takeIf { it > 0 },
        )
    }

    private fun open(method: String, path: String, auth: Boolean = true): HttpURLConnection {
        restoreCookie()
        val base = prefs.serverUrl.trimEnd('/')
        return (URL("$base$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (auth && prefs.cookie.isNotBlank()) {
                setRequestProperty("Cookie", prefs.cookie)
            }
        }
    }

    private fun writeJson(conn: HttpURLConnection, json: JSONObject) {
        conn.doOutput = true
        conn.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
    }

    private fun readBody(conn: HttpURLConnection): String {
        val stream = try {
            if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        } catch (_: Exception) {
            null
        }
        return stream?.bufferedReader()?.readText().orEmpty()
    }

    private fun ensureCookieManager() {
        if (CookieHandler.getDefault() == null) {
            CookieHandler.setDefault(CookieManager(null, CookiePolicy.ACCEPT_ALL))
        }
    }

    private fun restoreCookie() {
        val raw = prefs.cookie
        if (raw.isBlank() || prefs.serverUrl.isBlank()) return
        val cm = CookieHandler.getDefault() as? CookieManager ?: return
        val uri = URI.create(prefs.serverUrl)
        val cookie = HttpCookie.parse(raw).firstOrNull() ?: return
        cookie.version = 0
        cm.cookieStore.add(uri, cookie)
    }

    private fun persistCookie() {
        val cm = CookieHandler.getDefault() as? CookieManager ?: return
        if (prefs.serverUrl.isBlank()) return
        val uri = URI.create(prefs.serverUrl)
        val token = cm.cookieStore.get(uri).firstOrNull { it.name == "token" }
            ?: cm.cookieStore.cookies.firstOrNull { it.name == "token" }
        if (token != null) {
            prefs.cookie = "token=${token.value}"
        }
    }
}
