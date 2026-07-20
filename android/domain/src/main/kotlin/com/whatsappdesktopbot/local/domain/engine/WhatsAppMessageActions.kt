package com.whatsappdesktopbot.local.domain.engine

interface WhatsAppMessageActions {
    suspend fun react(message: com.whatsappdesktopbot.local.domain.model.IncomingMessage, emoji: String)
    suspend fun reply(message: com.whatsappdesktopbot.local.domain.model.IncomingMessage, text: String)
    suspend fun forwardMessage(messageId: String, targetChatId: String): Boolean
}
