package com.whatsappdesktopbot.local.data.repository

import com.whatsappdesktopbot.local.data.local.dao.BotSettingsDao
import com.whatsappdesktopbot.local.data.local.dao.ClientDao
import com.whatsappdesktopbot.local.data.local.dao.ConnectionEventDao
import com.whatsappdesktopbot.local.data.local.dao.InteractionLogDao
import com.whatsappdesktopbot.local.data.local.dao.SkippedLogDao
import com.whatsappdesktopbot.local.data.local.entity.BotSettingsEntity
import com.whatsappdesktopbot.local.data.local.entity.ClientEntity
import com.whatsappdesktopbot.local.data.local.entity.ConnectionEventEntity
import com.whatsappdesktopbot.local.data.local.toDomain
import com.whatsappdesktopbot.local.domain.model.BotClient
import com.whatsappdesktopbot.local.domain.model.BotSettings
import com.whatsappdesktopbot.local.domain.model.ConnectionState
import com.whatsappdesktopbot.local.domain.model.InteractionLogEntry
import com.whatsappdesktopbot.local.domain.model.SkippedLogEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class BotRepository(
    private val clientDao: ClientDao,
    private val settingsDao: BotSettingsDao,
    private val interactionLogDao: InteractionLogDao,
    private val skippedLogDao: SkippedLogDao,
    private val connectionEventDao: ConnectionEventDao,
) {
    fun observeClients(): Flow<List<BotClient>> =
        clientDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeSettings(): Flow<BotSettings> =
        settingsDao.observe().map { it?.toDomain() ?: BotSettings() }

    fun observeInteractionLogs(): Flow<List<InteractionLogEntry>> =
        interactionLogDao.observeRecent().map { list -> list.map { it.toDomain() } }

    fun observeSkippedLogs(): Flow<List<SkippedLogEntry>> =
        skippedLogDao.observeRecent().map { list -> list.map { it.toDomain() } }

    suspend fun upsertClient(name: String, emoji: String) {
        clientDao.upsert(ClientEntity(name = name, emoji = emoji))
    }

    suspend fun deleteClient(id: Long) {
        clientDao.delete(id)
    }

    suspend fun saveSettings(settings: BotSettings) {
        settingsDao.upsert(
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

    suspend fun logConnection(state: ConnectionState, detail: String) {
        connectionEventDao.insert(
            ConnectionEventEntity(
                timestamp = System.currentTimeMillis(),
                state = state.name,
                detail = detail,
            ),
        )
    }

    suspend fun seedDemoDataIfNeeded() {
        if (settingsDao.observe().first() != null) return
        saveSettings(BotSettings())
        upsertClient("محمد", "👍")
        upsertClient("أحمد", "✅")
        logConnection(ConnectionState.NOT_LINKED, "Demo data seeded")
    }
}
