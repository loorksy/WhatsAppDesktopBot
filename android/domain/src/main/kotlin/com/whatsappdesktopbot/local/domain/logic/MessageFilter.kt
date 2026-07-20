package com.whatsappdesktopbot.local.domain.logic

import com.whatsappdesktopbot.local.domain.model.IncomingMessage

data class MessageEligibility(
    val eligible: Boolean,
    val reason: String? = null,
    val text: String? = null,
)

object MessageFilter {
    fun shouldProcess(
        message: IncomingMessage,
        botRunning: Boolean,
        selectedGroupIds: List<String>,
    ): MessageEligibility {
        if (!botRunning) return MessageEligibility(false, "bot stopped")
        if (!message.chatId.endsWith("@g.us")) return MessageEligibility(false, "not a group")
        if (!selectedGroupIds.contains(message.chatId)) return MessageEligibility(false, "group not selected")
        if (message.fromMe) return MessageEligibility(false, "from self")
        val text = MessageTextExtractor.extract(message)
        if (text.isBlank()) return MessageEligibility(false, "no text")
        return MessageEligibility(true, null, text)
    }
}
