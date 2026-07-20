package com.whatsappdesktopbot.local.data.store

import com.whatsappdesktopbot.local.data.local.AppDatabase
import com.whatsappdesktopbot.local.data.local.entity.BotSettingsEntity
import com.whatsappdesktopbot.local.data.local.entity.ClientEntity
import com.whatsappdesktopbot.local.data.local.entity.ForwardQueueEntity
import com.whatsappdesktopbot.local.data.local.entity.GroupDirectoryEntity
import com.whatsappdesktopbot.local.data.local.entity.InteractionLogEntity
import com.whatsappdesktopbot.local.data.local.entity.ProcessedMessageEntity
import com.whatsappdesktopbot.local.data.local.entity.SelectedGroupEntity
import com.whatsappdesktopbot.local.data.local.entity.SkippedLogEntity
import com.whatsappdesktopbot.local.data.local.toDomain
import com.whatsappdesktopbot.local.domain.model.BotClient
import com.whatsappdesktopbot.local.domain.model.BotSettings
import com.whatsappdesktopbot.local.domain.model.ForwardMeta
import com.whatsappdesktopbot.local.domain.model.ForwardQueueItem
import com.whatsappdesktopbot.local.domain.model.InteractionLogEntry
import com.whatsappdesktopbot.local.domain.model.SkippedLogEntry
import com.whatsappdesktopbot.local.domain.store.BotProcessingStore

class RoomBotProcessingStore(
    private val db: AppDatabase,
) : BotProcessingStore {
    private var forwardMeta = ForwardMeta()

    override suspend fun getSettings(): BotSettings {
        return db.botSettingsDao().get()?.toDomain() ?: BotSettings()
    }

    override suspend fun saveSettings(settings: BotSettings) {
        db.botSettingsDao().upsert(
            BotSettingsEntity(
                defaultEmoji = settings.defaultEmoji,
                normalizeArabicEnabled = settings.normalizeArabicEnabled,
                replyMode = settings.replyMode,
                cooldownSeconds = settings.cooldownSeconds,
                rpm = settings.rpm,
                forwardEnabled = settings.forwardEnabled,
                forwardTargetChatId = settings.forwardTargetChatId,
                forwardBatchSize = settings.forwardBatchSize,
                forwardFlushOnIdle = settings.forwardFlushOnIdle,
                bulkMessagesPerMinute = settings.bulkMessagesPerMinute,
                autoStartOnBoot = settings.autoStartOnBoot,
                biometricLockEnabled = settings.biometricLockEnabled,
            ),
        )
    }

    override suspend fun getClients(): List<BotClient> =
        db.clientDao().getAll().map { it.toDomain() }

    override suspend fun replaceClients(clients: List<BotClient>) {
        db.clientDao().deleteAll()
        db.clientDao().upsertAll(clients.map { ClientEntity(name = it.name, emoji = it.emoji) })
    }

    override suspend fun getSelectedGroupIds(): List<String> =
        db.selectedGroupDao().getAllIds()

    override suspend fun setSelectedGroupIds(groupIds: List<String>) {
        db.selectedGroupDao().deleteAll()
        db.selectedGroupDao().upsertAll(groupIds.map { SelectedGroupEntity(it) })
    }

    override suspend fun upsertGroupDirectory(groupId: String, name: String) {
        db.groupDirectoryDao().upsert(GroupDirectoryEntity(groupId, name))
    }

    override suspend fun isProcessed(messageId: String): Boolean =
        db.processedMessageDao().exists(messageId)

    override suspend fun markProcessed(messageId: String) {
        db.processedMessageDao().insert(
            ProcessedMessageEntity(messageId = messageId, processedAt = System.currentTimeMillis()),
        )
        val count = db.processedMessageDao().count()
        if (count > 50_000) {
            db.processedMessageDao().trimOldest(count - 50_000)
        }
    }

    override suspend fun appendInteraction(entry: InteractionLogEntry) {
        db.interactionLogDao().insert(
            InteractionLogEntity(
                timestamp = entry.timestamp,
                groupId = entry.groupId,
                groupName = entry.groupName,
                matchName = entry.match,
                action = entry.action,
                snippet = entry.snippet,
                messageId = entry.messageId,
            ),
        )
        val count = db.interactionLogDao().count()
        if (count > 2_000) db.interactionLogDao().trimOldest(count - 2_000)
    }

    override suspend fun appendSkipped(entry: SkippedLogEntry) {
        db.skippedLogDao().insert(
            SkippedLogEntity(
                timestamp = entry.timestamp,
                groupId = entry.groupId,
                groupName = entry.groupName,
                reason = entry.reason,
                snippet = entry.snippet,
                messageId = entry.messageId,
            ),
        )
        val count = db.skippedLogDao().count()
        if (count > 2_000) db.skippedLogDao().trimOldest(count - 2_000)
    }

    override suspend fun getForwardQueue(): List<ForwardQueueItem> =
        db.forwardQueueDao().getAll().map {
            ForwardQueueItem(it.messageId, it.sourceChatId, it.timestamp)
        }

    override suspend fun enqueueForward(item: ForwardQueueItem) {
        db.forwardQueueDao().insert(
            ForwardQueueEntity(item.messageId, item.sourceChatId, item.timestamp),
        )
    }

    override suspend fun removeFromForwardQueue(messageId: String) {
        db.forwardQueueDao().delete(messageId)
    }

    override suspend fun clearForwardQueue() {
        db.forwardQueueDao().deleteAll()
    }

    override suspend fun getForwardMeta(): ForwardMeta = forwardMeta

    override suspend fun setForwardMeta(meta: ForwardMeta) {
        forwardMeta = meta
    }

    override suspend fun trimRetentionLimits() {
        val processedCount = db.processedMessageDao().count()
        if (processedCount > 50_000) db.processedMessageDao().trimOldest(processedCount - 50_000)
        val interactionCount = db.interactionLogDao().count()
        if (interactionCount > 2_000) db.interactionLogDao().trimOldest(interactionCount - 2_000)
        val skippedCount = db.skippedLogDao().count()
        if (skippedCount > 2_000) db.skippedLogDao().trimOldest(skippedCount - 2_000)
        val connectionCount = db.connectionEventDao().count()
        if (connectionCount > 1_000) db.connectionEventDao().trimOldest(connectionCount - 1_000)
    }
}
