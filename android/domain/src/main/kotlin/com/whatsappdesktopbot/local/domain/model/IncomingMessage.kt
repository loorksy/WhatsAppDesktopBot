package com.whatsappdesktopbot.local.domain.model

data class IncomingMessage(
    val id: String,
    val chatId: String,
    val groupName: String,
    val fromMe: Boolean,
    val body: String? = null,
    val caption: String? = null,
    val quotedBody: String? = null,
)

data class ForwardQueueItem(
    val messageId: String,
    val sourceChatId: String,
    val timestamp: Long,
)

data class ForwardMeta(
    val lastForwardedAt: Long? = null,
)

data class ProcessMessageResult(
    val processed: Boolean,
    val skippedReason: String? = null,
    val action: String? = null,
)
