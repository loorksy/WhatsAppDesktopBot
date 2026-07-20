package com.whatsappdesktopbot.local.domain.logic

import com.whatsappdesktopbot.local.domain.model.BotSettings
import com.whatsappdesktopbot.local.domain.model.ForwardQueueItem
import com.whatsappdesktopbot.local.domain.model.IncomingMessage
import com.whatsappdesktopbot.local.domain.store.BotProcessingStore

class ForwardQueueProcessor(
    private val store: BotProcessingStore,
) {
    suspend fun shouldFlushOnBatch(settings: BotSettings): Boolean {
        if (!settings.forwardEnabled || settings.forwardTargetChatId.isBlank()) return false
        val queue = store.getForwardQueue()
        return queue.size >= settings.forwardBatchSize
    }

    suspend fun enqueue(message: IncomingMessage) {
        val settings = store.getSettings()
        if (!settings.forwardEnabled || settings.forwardTargetChatId.isBlank()) return
        store.enqueueForward(
            ForwardQueueItem(
                messageId = message.id,
                sourceChatId = message.chatId,
                timestamp = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun flushBatch(
        force: Boolean,
        settings: BotSettings,
        onForward: suspend (ForwardQueueItem) -> Boolean,
    ): Int {
        if (!settings.forwardEnabled || settings.forwardTargetChatId.isBlank()) return 0
        val queue = store.getForwardQueue()
        if (!force && queue.size < settings.forwardBatchSize) return 0
        var forwarded = 0
        for (item in queue.toList()) {
            val ok = runCatching { onForward(item) }.getOrDefault(false)
            if (ok) {
                store.removeFromForwardQueue(item.messageId)
                forwarded += 1
            }
        }
        if (forwarded > 0) {
            store.setForwardMeta(
                com.whatsappdesktopbot.local.domain.model.ForwardMeta(lastForwardedAt = System.currentTimeMillis()),
            )
        }
        return forwarded
    }
}
