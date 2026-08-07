package com.whatsappbot.bulk.data

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ApiClient(private val prefs: Prefs) {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private val cookieJar = object : CookieJar {
        private val memory = mutableListOf<Cookie>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            memory.removeAll { it.name == "token" }
            memory.addAll(cookies.filter { it.name == "token" })
            val token = memory.firstOrNull { it.name == "token" }
            if (token != null) {
                prefs.cookie = "token=${token.value}"
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            if (memory.isNotEmpty()) return memory
            val raw = prefs.cookie
            if (raw.startsWith("token=")) {
                val value = raw.removePrefix("token=")
                val cookie = Cookie.Builder()
                    .name("token")
                    .value(value)
                    .domain(url.host)
                    .path("/")
                    .build()
                memory.clear()
                memory.add(cookie)
                return memory
            }
            return emptyList()
        }
    }

    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun base(): String = prefs.serverUrl.trimEnd('/')

    private fun requestBuilder(path: String): Request.Builder {
        val builder = Request.Builder().url("${base()}$path")
        val cookie = prefs.cookie
        if (cookie.isNotBlank()) {
            builder.header("Cookie", cookie)
        }
        return builder
    }

    fun login(email: String, password: String): Result<Unit> = runCatching {
        val body = JSONObject()
            .put("email", email)
            .put("password", password)
            .toString()
            .toRequestBody(jsonMedia)
        val req = requestBuilder("/api/login").post(body).build()
        client.newCall(req).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                val err = runCatching { JSONObject(text).optString("error") }.getOrDefault("")
                error(err.ifBlank { "LOGIN_FAILED" })
            }
        }
        Unit
    }

    fun me(): Result<JSONObject> = runCatching {
        val req = requestBuilder("/api/me").get().build()
        client.newCall(req).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) error("UNAUTHORIZED")
            JSONObject(text)
        }
    }

    fun fetchChats(): Result<List<ChatItem>> = runCatching {
        val req = requestBuilder("/api/chats").get().build()
        client.newCall(req).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (res.code == 409) error("WA_NOT_READY")
            if (res.code == 401) error("UNAUTHORIZED")
            if (!res.isSuccessful) {
                val err = runCatching { JSONObject(text).optString("error") }.getOrDefault("ERROR")
                error(err.ifBlank { "ERROR" })
            }
            val arr = JSONArray(text)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        ChatItem(
                            id = o.optString("id"),
                            name = o.optString("name").ifBlank { o.optString("id") },
                            isGroup = o.optBoolean("isGroup"),
                            unreadCount = o.optInt("unreadCount"),
                            timestamp = o.optLong("timestamp"),
                        )
                    )
                }
            }
        }
    }

    fun startBulk(groupId: String, messages: List<String>, rpm: Int): Result<Unit> = runCatching {
        val arr = JSONArray()
        messages.forEach { arr.put(it) }
        val delaySeconds = 60.0 / rpm.coerceAtLeast(1)
        val body = JSONObject()
            .put("groupId", groupId)
            .put("messages", arr)
            .put("rpm", rpm)
            .put("delaySeconds", delaySeconds)
            .toString()
            .toRequestBody(jsonMedia)
        val req = requestBuilder("/api/bulk/start").post(body).build()
        client.newCall(req).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (res.code == 409) error("WA_NOT_READY")
            if (!res.isSuccessful) {
                val err = runCatching { JSONObject(text).optString("error") }.getOrDefault("ERROR")
                error(err.ifBlank { "ERROR" })
            }
        }
        Unit
    }

    fun pauseBulk(): Result<Unit> = postEmpty("/api/bulk/pause")
    fun resumeBulk(): Result<Unit> = postEmpty("/api/bulk/resume")
    fun stopBulk(): Result<Unit> = postEmpty("/api/bulk/stop")

    fun bulkStatus(): Result<BulkStatus> = runCatching {
        val req = requestBuilder("/api/bulk/status").get().build()
        client.newCall(req).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) error("ERROR")
            val o = JSONObject(text)
            BulkStatus(
                state = o.optString("state", "idle"),
                sent = o.optInt("sent"),
                total = o.optInt("total"),
                groupId = o.optString("groupId").ifBlank { null },
                paused = o.optBoolean("paused"),
            )
        }
    }

    private fun postEmpty(path: String): Result<Unit> = runCatching {
        val body = "{}".toRequestBody(jsonMedia)
        val req = requestBuilder(path).post(body).build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) error("ERROR")
        }
        Unit
    }
}
