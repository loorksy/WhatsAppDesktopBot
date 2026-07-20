package com.whatsappdesktopbot.local.domain.logic

import com.whatsappdesktopbot.local.domain.engine.WhatsAppMessageActions
import com.whatsappdesktopbot.local.domain.model.IncomingMessage
import com.whatsappdesktopbot.local.domain.model.InteractionLogEntry
import com.whatsappdesktopbot.local.domain.model.ProcessMessageResult
import com.whatsappdesktopbot.local.domain.model.SkippedLogEntry
import com.whatsappdesktopbot.local.domain.store.BotProcessingStore
import kotlinx.coroutines.delay
import java.util.ArrayDeque

class BotOrchestrator(
    private val store: BotProcessingStore,
    private val actions: WhatsAppMessageActions,
) {
    private val queue = ArrayDeque<IncomingMessage>()
    private val rateLimiter = RateLimiter()
    private val cooldownTracker = CooldownTracker()
    private val forwardProcessor = ForwardQueueProcessor(store)

    var botRunning: Boolean = false
        private set
    var clientReady: Boolean = true
    var processing: Boolean = false
        private set
    var forwardFlushing: Boolean = false
        private set

    val queueLength: Int get() = queue.size

    suspend fun startBot() {
        botRunning = true
    }

    suspend fun stopBot() {
        botRunning = false
        queue.clear()
    }

    suspend fun handleIncoming(message: IncomingMessage): MessageEligibility {
        val eligibility = MessageFilter.shouldProcess(
            message = message,
            botRunning = botRunning,
            selectedGroupIds = store.getSelectedGroupIds(),
        )
        if (!eligibility.eligible) {
            recordSkipped(message, eligibility.reason ?: "filtered")
            return eligibility
        }
        if (store.isProcessed(message.id)) {
            recordSkipped(message, "already processed")
            return MessageEligibility(false, "already processed")
        }
        queue.addLast(message)
        processQueue()
        return eligibility
    }

    suspend fun processQueue() {
        if (processing || forwardFlushing) return
        processing = true
        try {
            while (queue.isNotEmpty()) {
                if (!botRunning) break
                if (!clientReady) break
                if (forwardFlushing) break
                val message = queue.removeFirst()
                runCatching { processMessage(message) }
            }
            val settings = store.getSettings()
            if (!forwardFlushing && settings.forwardFlushOnIdle && queue.isEmpty()) {
                flushForwardBatch(force = true)
            }
        } finally {
            processing = false
        }
    }

    suspend fun processMessage(message: IncomingMessage): ProcessMessageResult {
        val settings = store.getSettings()
        val matcher = ClientMatcher(
            normalizeArabicEnabled = { settings.normalizeArabicEnabled },
            defaultEmoji = { settings.defaultEmoji },
        )
        val text = MessageTextExtractor.extract(message)
        val matchResult = matcher.match(text, store.getClients())
        if (matchResult == null) {
            store.markProcessed(message.id)
            recordSkipped(message, "no match")
            return ProcessMessageResult(false, "no match")
        }

        awaitRateLimit(settings.rpm)
        awaitCooldown(message.chatId, settings.cooldownSeconds)

        val action = if (settings.replyMode) {
            actions.reply(message, matchResult.emoji)
            "reply"
        } else {
            actions.react(message, matchResult.emoji)
            "reaction"
        }

        store.appendInteraction(
            InteractionLogEntry(
                timestamp = System.currentTimeMillis(),
                groupId = message.chatId,
                groupName = message.groupName,
                match = matchResult.match,
                action = action,
                snippet = MessageTextExtractor.snippet(text),
                messageId = message.id,
            ),
        )
        store.markProcessed(message.id)
        forwardProcessor.enqueue(message)

        if (forwardProcessor.shouldFlushOnBatch(settings)) {
            flushForwardBatch(force = false)
        }

        return ProcessMessageResult(true, action = action)
    }

    suspend fun flushForwardBatch(force: Boolean): Int {
        if (forwardFlushing) return 0
        val settings = store.getSettings()
        forwardFlushing = true
        try {
            return forwardProcessor.flushBatch(force, settings) { item ->
                actions.forwardMessage(item.messageId, settings.forwardTargetChatId)
            }
        } finally {
            forwardFlushing = false
            if (queue.isNotEmpty() && botRunning) processQueue()
        }
    }

    private suspend fun awaitRateLimit(rpm: Int) {
        val limit = rpm.coerceIn(1, 20)
        while (true) {
            val now = System.currentTimeMillis()
            val waitMs = rateLimiter.waitMillis(now, limit)
            if (waitMs <= 0) {
                rateLimiter.record(now, limit)
                return
            }
            delay(waitMs)
        }
    }

    private suspend fun awaitCooldown(groupId: String, cooldownSeconds: Int) {
        val waitMs = cooldownTracker.millisUntilAllowed(groupId, cooldownSeconds, System.currentTimeMillis())
        if (waitMs > 0) delay(waitMs)
        cooldownTracker.record(groupId, System.currentTimeMillis())
    }

    private suspend fun recordSkipped(message: IncomingMessage, reason: String) {
        store.appendSkipped(
            SkippedLogEntry(
                timestamp = System.currentTimeMillis(),
                groupId = message.chatId,
                groupName = message.groupName,
                reason = reason,
                snippet = MessageTextExtractor.snippet(MessageTextExtractor.extract(message)),
                messageId = message.id,
            ),
        )
    }
}
