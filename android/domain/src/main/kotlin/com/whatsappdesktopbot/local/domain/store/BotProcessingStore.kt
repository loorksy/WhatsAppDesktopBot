package com.whatsappdesktopbot.local.domain.store

import com.whatsappdesktopbot.local.domain.model.BotClient
import com.whatsappdesktopbot.local.domain.model.BotSettings
import com.whatsappdesktopbot.local.domain.model.ForwardMeta
import com.whatsappdesktopbot.local.domain.model.ForwardQueueItem
import com.whatsappdesktopbot.local.domain.model.InteractionLogEntry
import com.whatsappdesktopbot.local.domain.model.SkippedLogEntry

interface BotProcessingStore {
    suspend fun getSettings(): BotSettings
    suspend fun saveSettings(settings: BotSettings)
    suspend fun getClients(): List<BotClient>
    suspend fun replaceClients(clients: List<BotClient>)
    suspend fun getSelectedGroupIds(): List<String>
    suspend fun setSelectedGroupIds(groupIds: List<String>)
    suspend fun upsertGroupDirectory(groupId: String, name: String)
    suspend fun isProcessed(messageId: String): Boolean
    suspend fun markProcessed(messageId: String)
    suspend fun appendInteraction(entry: InteractionLogEntry)
    suspend fun appendSkipped(entry: SkippedLogEntry)
    suspend fun getForwardQueue(): List<ForwardQueueItem>
    suspend fun enqueueForward(item: ForwardQueueItem)
    suspend fun removeFromForwardQueue(messageId: String)
    suspend fun clearForwardQueue()
    suspend fun getForwardMeta(): ForwardMeta
    suspend fun setForwardMeta(meta: ForwardMeta)
    suspend fun trimRetentionLimits()
}
