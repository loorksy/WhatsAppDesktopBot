package com.whatsappdesktopbot.local.domain.logic

import com.whatsappdesktopbot.local.domain.model.IncomingMessage

object MessageTextExtractor {
    fun extract(message: IncomingMessage): String {
        var text = message.body.orEmpty()
        if (text.isBlank()) text = message.caption.orEmpty()
        if (text.isBlank()) text = message.quotedBody.orEmpty()
        return text.trim()
    }

    fun snippet(text: String, maxLen: Int = 80): String {
        if (text.length <= maxLen) return text
        return text.take(maxLen - 3) + "..."
    }
}
