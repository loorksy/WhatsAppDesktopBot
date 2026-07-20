package com.whatsappdesktopbot.local.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clients")
data class ClientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String,
)

@Entity(tableName = "selected_groups")
data class SelectedGroupEntity(
    @PrimaryKey val groupId: String,
)

@Entity(tableName = "group_directory")
data class GroupDirectoryEntity(
    @PrimaryKey val groupId: String,
    val name: String,
)

@Entity(tableName = "processed_messages")
data class ProcessedMessageEntity(
    @PrimaryKey val messageId: String,
    val processedAt: Long,
)

@Entity(tableName = "interaction_logs")
data class InteractionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val groupId: String,
    val groupName: String,
    val matchName: String,
    val action: String,
    val snippet: String,
    val messageId: String?,
)

@Entity(tableName = "skipped_logs")
data class SkippedLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val groupId: String?,
    val groupName: String?,
    val reason: String,
    val snippet: String,
    val messageId: String?,
)

@Entity(tableName = "bot_settings")
data class BotSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val defaultEmoji: String,
    val normalizeArabicEnabled: Boolean,
    val replyMode: Boolean,
    val cooldownSeconds: Int,
    val rpm: Int,
    val forwardEnabled: Boolean,
    val forwardTargetChatId: String,
    val forwardBatchSize: Int,
    val forwardFlushOnIdle: Boolean,
    val bulkMessagesPerMinute: Int,
    val autoStartOnBoot: Boolean,
    val biometricLockEnabled: Boolean,
)

@Entity(tableName = "forward_queue")
data class ForwardQueueEntity(
    @PrimaryKey val messageId: String,
    val sourceChatId: String,
    val timestamp: Long,
)

@Entity(tableName = "bulk_jobs")
data class BulkJobEntity(
    @PrimaryKey val id: Int = 1,
    val state: String,
    val sent: Int,
    val total: Int,
    val groupId: String?,
    val paused: Boolean,
    val messagesPerMinute: Int,
)

@Entity(tableName = "bulk_messages")
data class BulkMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val jobId: Int = 1,
    val position: Int,
    val text: String,
)

@Entity(tableName = "connection_events")
data class ConnectionEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val state: String,
    val detail: String,
)
