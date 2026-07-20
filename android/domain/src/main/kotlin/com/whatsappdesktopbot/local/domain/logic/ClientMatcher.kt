package com.whatsappdesktopbot.local.domain.logic

import com.whatsappdesktopbot.local.domain.model.BotClient

data class ClientMatchResult(
    val match: String,
    val emoji: String,
)

class ClientMatcher(
    private val normalizeArabicEnabled: () -> Boolean,
    private val defaultEmoji: () -> String,
) {
    fun match(text: String?, clients: List<BotClient>): ClientMatchResult? {
        if (text.isNullOrBlank()) return null
        val target = if (normalizeArabicEnabled()) ArabicNormalizer.normalize(text) else text
        for (client in clients) {
            val name = client.name
            if (name.isBlank()) continue
            val candidate = if (normalizeArabicEnabled()) ArabicNormalizer.normalize(name) else name
            val regex = Regex(Regex.escape(candidate), RegexOption.IGNORE_CASE)
            if (regex.containsMatchIn(target)) {
                return ClientMatchResult(match = name, emoji = client.emoji.ifBlank { defaultEmoji() })
            }
        }
        return null
    }
}
