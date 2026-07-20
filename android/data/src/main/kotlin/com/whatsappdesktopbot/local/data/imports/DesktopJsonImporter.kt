package com.whatsappdesktopbot.local.data.imports

import com.whatsappdesktopbot.local.domain.model.BotClient
import com.whatsappdesktopbot.local.domain.model.BotSettings
import com.whatsappdesktopbot.local.domain.model.InteractionLogEntry
import com.whatsappdesktopbot.local.domain.model.SkippedLogEntry
import org.json.JSONArray
import org.json.JSONObject

data class DesktopImportBundle(
    val settings: BotSettings? = null,
    val clients: List<BotClient> = emptyList(),
    val selectedGroupIds: List<String> = emptyList(),
    val groupDirectory: Map<String, String> = emptyMap(),
    val processedMessageIds: List<String> = emptyList(),
    val interactionLogs: List<InteractionLogEntry> = emptyList(),
    val skippedLogs: List<SkippedLogEntry> = emptyList(),
)

object DesktopJsonImporter {
    fun parseBundle(
        settingsJson: String? = null,
        clientsJson: String? = null,
        groupsJson: String? = null,
        groupDirectoryJson: String? = null,
        processedJson: String? = null,
        interactedLogsJson: String? = null,
        skippedLogsJson: String? = null,
    ): DesktopImportBundle {
        val settings = settingsJson?.let { parseSettings(it) }
        val defaultEmoji = settings?.defaultEmoji ?: "✅"
        return DesktopImportBundle(
            settings = settings,
            clients = clientsJson?.let { parseClients(it, defaultEmoji) } ?: emptyList(),
            selectedGroupIds = groupsJson?.let(::parseStringArray) ?: emptyList(),
            groupDirectory = groupDirectoryJson?.let(::parseStringMap) ?: emptyMap(),
            processedMessageIds = processedJson?.let(::parseStringArray) ?: emptyList(),
            interactionLogs = interactedLogsJson?.let(::parseInteractionLogs) ?: emptyList(),
            skippedLogs = skippedLogsJson?.let(::parseSkippedLogs) ?: emptyList(),
        )
    }

    private fun parseSettings(json: String): BotSettings {
        val o = JSONObject(json)
        val bulkMpm = o.optInt("bulkMessagesPerMinute", o.optInt("bulkRpm", 10))
        return BotSettings(
            defaultEmoji = o.optString("defaultEmoji", "✅"),
            normalizeArabicEnabled = o.optBoolean("normalizeArabicEnabled", true),
            replyMode = o.optBoolean("replyMode", false),
            cooldownSeconds = o.optInt("cooldownSeconds", 3),
            rpm = o.optInt("rpm", 20),
            forwardEnabled = o.optBoolean("forwardEnabled", true),
            forwardTargetChatId = o.optString("forwardTargetChatId", ""),
            forwardBatchSize = o.optInt("forwardBatchSize", 10),
            forwardFlushOnIdle = o.optBoolean("forwardFlushOnIdle", true),
            bulkMessagesPerMinute = bulkMpm,
        )
    }

    private fun parseClients(json: String, defaultEmoji: String): List<BotClient> {
        val array = JSONArray(json)
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.get(i)
                when (item) {
                    is JSONObject -> {
                        val name = item.optString("name").trim()
                        if (name.isNotBlank()) {
                            add(BotClient(name = name, emoji = item.optString("emoji", defaultEmoji)))
                        }
                    }
                    is String -> if (item.isNotBlank()) add(BotClient(name = item.trim(), emoji = defaultEmoji))
                }
            }
        }
    }

    private fun parseStringArray(json: String): List<String> {
        val array = JSONArray(json)
        return buildList {
            for (i in 0 until array.length()) {
                val value = array.optString(i).trim()
                if (value.isNotBlank()) add(value)
            }
        }
    }

    private fun parseStringMap(json: String): Map<String, String> {
        val o = JSONObject(json)
        val keys = o.keys()
        val map = linkedMapOf<String, String>()
        while (keys.hasNext()) {
            val key = keys.next()
            map[key] = o.optString(key)
        }
        return map
    }

    private fun parseInteractionLogs(json: String): List<InteractionLogEntry> {
        val array = JSONArray(json)
        return buildList {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                add(
                    InteractionLogEntry(
                        timestamp = o.optLong("ts", System.currentTimeMillis()),
                        groupId = o.optString("groupId"),
                        groupName = o.optString("groupName"),
                        match = o.optString("match"),
                        action = o.optString("action"),
                        snippet = o.optString("snippet"),
                        messageId = o.optString("id").ifBlank { null },
                    ),
                )
            }
        }
    }

    private fun parseSkippedLogs(json: String): List<SkippedLogEntry> {
        val array = JSONArray(json)
        return buildList {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                add(
                    SkippedLogEntry(
                        timestamp = o.optLong("ts", System.currentTimeMillis()),
                        groupId = o.optString("groupId").ifBlank { null },
                        groupName = o.optString("groupName").ifBlank { null },
                        reason = o.optString("reason"),
                        snippet = o.optString("snippet"),
                        messageId = o.optString("id").ifBlank { null },
                    ),
                )
            }
        }
    }
}
