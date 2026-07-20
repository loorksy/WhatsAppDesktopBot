package com.whatsappdesktopbot.local.data.repository

import com.whatsappdesktopbot.local.data.imports.DesktopImportBundle
import com.whatsappdesktopbot.local.data.imports.DesktopJsonImporter
import com.whatsappdesktopbot.local.data.local.AppDatabase
import com.whatsappdesktopbot.local.data.local.entity.ClientEntity
import com.whatsappdesktopbot.local.data.local.entity.ConnectionEventEntity
import com.whatsappdesktopbot.local.data.local.entity.InteractionLogEntity
import com.whatsappdesktopbot.local.data.local.entity.ProcessedMessageEntity
import com.whatsappdesktopbot.local.data.local.entity.SkippedLogEntity
import com.whatsappdesktopbot.local.data.local.toDomain
import com.whatsappdesktopbot.local.domain.model.BotClient
import com.whatsappdesktopbot.local.domain.model.BotSettings
import com.whatsappdesktopbot.local.domain.model.ConnectionEventEntry
import com.whatsappdesktopbot.local.domain.model.ConnectionState
import com.whatsappdesktopbot.local.domain.model.InteractionLogEntry
import com.whatsappdesktopbot.local.domain.model.SkippedLogEntry
import com.whatsappdesktopbot.local.domain.store.BotProcessingStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class BotRepository(
    private val db: AppDatabase,
    private val processingStore: BotProcessingStore,
) {
    val store: BotProcessingStore get() = processingStore

    fun observeClients(): Flow<List<BotClient>> =
        db.clientDao().observeAll().map { list -> list.map { it.toDomain() } }

    fun observeSettings(): Flow<BotSettings> =
        db.botSettingsDao().observe().map { it?.toDomain() ?: BotSettings() }

    fun observeInteractionLogs(): Flow<List<InteractionLogEntry>> =
        db.interactionLogDao().observeRecent().map { list -> list.map { it.toDomain() } }

    fun observeSkippedLogs(): Flow<List<SkippedLogEntry>> =
        db.skippedLogDao().observeRecent().map { list -> list.map { it.toDomain() } }

    fun observeConnectionEvents(): Flow<List<ConnectionEventEntry>> =
        db.connectionEventDao().observeRecent().map { events ->
            events.map {
                ConnectionEventEntry(
                    id = it.id,
                    timestamp = it.timestamp,
                    state = runCatching { ConnectionState.valueOf(it.state) }
                        .getOrDefault(ConnectionState.ERROR),
                    detail = it.detail,
                )
            }
        }

    suspend fun forwardQueueLength(): Int = db.forwardQueueDao().count()

    suspend fun upsertClient(name: String, emoji: String) {
        db.clientDao().upsert(ClientEntity(name = name, emoji = emoji))
    }

    suspend fun deleteClient(id: Long) {
        db.clientDao().delete(id)
    }

    suspend fun saveSettings(settings: BotSettings) {
        processingStore.saveSettings(settings)
    }

    suspend fun setSelectedGroupIds(groupIds: List<String>) {
        processingStore.setSelectedGroupIds(groupIds)
    }

    suspend fun logConnection(state: ConnectionState, detail: String) {
        db.connectionEventDao().insert(
            ConnectionEventEntity(
                timestamp = System.currentTimeMillis(),
                state = state.name,
                detail = detail,
            ),
        )
        val count = db.connectionEventDao().count()
        if (count > 1_000) db.connectionEventDao().trimOldest(count - 1_000)
    }

    suspend fun seedDemoDataIfNeeded() {
        if (db.botSettingsDao().get() != null) return
        saveSettings(BotSettings())
        upsertClient("محمد", "👍")
        upsertClient("أحمد", "✅")
        logConnection(ConnectionState.NOT_LINKED, "Demo data seeded")
    }

    suspend fun importDesktopBundle(bundle: DesktopImportBundle): ImportResult {
        bundle.settings?.let { saveSettings(it) }
        if (bundle.clients.isNotEmpty()) {
            processingStore.replaceClients(bundle.clients)
        }
        if (bundle.selectedGroupIds.isNotEmpty()) {
            processingStore.setSelectedGroupIds(bundle.selectedGroupIds)
        }
        bundle.groupDirectory.forEach { (id, name) ->
            processingStore.upsertGroupDirectory(id, name)
        }
        bundle.processedMessageIds.forEach { id ->
            db.processedMessageDao().insert(
                ProcessedMessageEntity(messageId = id, processedAt = System.currentTimeMillis()),
            )
        }
        if (bundle.interactionLogs.isNotEmpty()) {
            db.interactionLogDao().insertAll(
                bundle.interactionLogs.map {
                    InteractionLogEntity(
                        timestamp = it.timestamp,
                        groupId = it.groupId,
                        groupName = it.groupName,
                        matchName = it.match,
                        action = it.action,
                        snippet = it.snippet,
                        messageId = it.messageId,
                    )
                },
            )
        }
        if (bundle.skippedLogs.isNotEmpty()) {
            db.skippedLogDao().insertAll(
                bundle.skippedLogs.map {
                    SkippedLogEntity(
                        timestamp = it.timestamp,
                        groupId = it.groupId,
                        groupName = it.groupName,
                        reason = it.reason,
                        snippet = it.snippet,
                        messageId = it.messageId,
                    )
                },
            )
        }
        processingStore.trimRetentionLimits()
        return ImportResult(
            clients = bundle.clients.size,
            groups = bundle.selectedGroupIds.size,
            processed = bundle.processedMessageIds.size,
        )
    }

    suspend fun importDesktopJson(
        settingsJson: String? = null,
        clientsJson: String? = null,
        groupsJson: String? = null,
        groupDirectoryJson: String? = null,
        processedJson: String? = null,
        interactedLogsJson: String? = null,
        skippedLogsJson: String? = null,
    ): ImportResult {
        val bundle = DesktopJsonImporter.parseBundle(
            settingsJson = settingsJson,
            clientsJson = clientsJson,
            groupsJson = groupsJson,
            groupDirectoryJson = groupDirectoryJson,
            processedJson = processedJson,
            skippedLogsJson = skippedLogsJson,
            interactedLogsJson = interactedLogsJson,
        )
        return importDesktopBundle(bundle)
    }

    data class ImportResult(
        val clients: Int,
        val groups: Int,
        val processed: Int,
    )
}
