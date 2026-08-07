package com.whatsappbot.bulk.data

data class ChatItem(
    val id: String,
    val name: String,
    val isGroup: Boolean = false,
    val unreadCount: Int = 0,
    val timestamp: Long = 0L,
)

data class BulkStatus(
    val state: String = "idle",
    val sent: Int = 0,
    val total: Int = 0,
    val groupId: String? = null,
    val paused: Boolean = false,
)

data class ApiError(
    val error: String? = null,
    val status: Int = 0,
)
