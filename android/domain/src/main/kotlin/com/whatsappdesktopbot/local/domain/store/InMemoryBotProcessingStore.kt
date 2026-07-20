package com.whatsappdesktopbot.local.domain.store

import com.whatsappdesktopbot.local.domain.model.BotClient
import com.whatsappdesktopbot.local.domain.model.BotSettings
import com.whatsappdesktopbot.local.domain.model.ForwardMeta
import com.whatsappdesktopbot.local.domain.model.ForwardQueueItem
import com.whatsappdesktopbot.local.domain.model.InteractionLogEntry
import com.whatsappdesktopbot.local.domain.model.SkippedLogEntry
import java.util.ArrayDeque

class InMemoryBotProcessingStore : BotProcessingStore {
    private var settings = BotSettings()
    private val clients = mutableListOf<BotClient>()
    private val selectedGroups = mutableSetOf<String>()
    private val groupDirectory = mutableMapOf<String, String>()
    private val processed = LinkedHashSet<String>()
    private val interactions = ArrayDeque<InteractionLogEntry>()
    private val skipped = ArrayDeque<SkippedLogEntry>()
    private val forwardQueue = mutableListOf<ForwardQueueItem>()
    private var forwardMeta = ForwardMeta()

    override suspend fun getSettings(): BotSettings = settings

    override suspend fun saveSettings(settings: BotSettings) {
        this.settings = settings
    }

    override suspend fun getClients(): List<BotClient> = clients.toList()

    override suspend fun replaceClients(clients: List<BotClient>) {
        this.clients.clear()
        this.clients.addAll(clients)
    }

    override suspend fun getSelectedGroupIds(): List<String> = selectedGroups.toList()

    override suspend fun setSelectedGroupIds(groupIds: List<String>) {
        selectedGroups.clear()
        selectedGroups.addAll(groupIds)
    }

    override suspend fun upsertGroupDirectory(groupId: String, name: String) {
        groupDirectory[groupId] = name
    }

    override suspend fun isProcessed(messageId: String): Boolean = processed.contains(messageId)

    override suspend fun markProcessed(messageId: String) {
        processed.add(messageId)
        while (processed.size > 50_000) {
            processed.remove(processed.first())
        }
    }

    override suspend fun appendInteraction(entry: InteractionLogEntry) {
        interactions.addFirst(entry)
        while (interactions.size > 2_000) interactions.removeLast()
    }

    override suspend fun appendSkipped(entry: SkippedLogEntry) {
        skipped.addFirst(entry)
        while (skipped.size > 2_000) skipped.removeLast()
    }

    override suspend fun getForwardQueue(): List<ForwardQueueItem> = forwardQueue.toList()

    override suspend fun enqueueForward(item: ForwardQueueItem) {
        if (forwardQueue.any { it.messageId == item.messageId }) return
        forwardQueue.add(item)
    }

    override suspend fun removeFromForwardQueue(messageId: String) {
        forwardQueue.removeAll { it.messageId == messageId }
    }

    override suspend fun clearForwardQueue() {
        forwardQueue.clear()
    }

    override suspend fun getForwardMeta(): ForwardMeta = forwardMeta

    override suspend fun setForwardMeta(meta: ForwardMeta) {
        forwardMeta = meta
    }

    override suspend fun trimRetentionLimits() = Unit
}
