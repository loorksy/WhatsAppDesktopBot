package com.whatsappdesktopbot.local.domain.model

data class BotClient(
    val id: Long = 0,
    val name: String,
    val emoji: String,
)

data class WhatsAppGroup(
    val id: String,
    val name: String,
    val selected: Boolean = false,
)

data class BotSettings(
    val defaultEmoji: String = "✅",
    val normalizeArabicEnabled: Boolean = true,
    val replyMode: Boolean = false,
    val cooldownSeconds: Int = 3,
    val rpm: Int = 20,
    val forwardEnabled: Boolean = true,
    val forwardTargetChatId: String = "",
    val forwardBatchSize: Int = 10,
    val forwardFlushOnIdle: Boolean = true,
    val bulkMessagesPerMinute: Int = 10,
    val autoStartOnBoot: Boolean = false,
    val biometricLockEnabled: Boolean = false,
)

data class BotRuntimeStatus(
    val connectionState: ConnectionState = ConnectionState.NOT_LINKED,
    val botRunning: Boolean = false,
    val queueLength: Int = 0,
    val forwardQueueLength: Int = 0,
    val lastProcessedAt: Long? = null,
    val reconnectAttempts: Int = 0,
    val networkType: String = "Unknown",
    val vpnActive: Boolean = false,
    val lastNetworkChangeAt: Long? = null,
)

data class InteractionLogEntry(
    val id: Long = 0,
    val timestamp: Long,
    val groupId: String,
    val groupName: String,
    val match: String,
    val action: String,
    val snippet: String,
    val messageId: String?,
)

data class SkippedLogEntry(
    val id: Long = 0,
    val timestamp: Long,
    val groupId: String?,
    val groupName: String?,
    val reason: String,
    val snippet: String,
    val messageId: String?,
)

data class ConnectionEventEntry(
    val id: Long = 0,
    val timestamp: Long,
    val state: ConnectionState,
    val detail: String,
)

data class BulkJobState(
    val state: String = "idle",
    val sent: Int = 0,
    val total: Int = 0,
    val groupId: String? = null,
    val paused: Boolean = false,
)
