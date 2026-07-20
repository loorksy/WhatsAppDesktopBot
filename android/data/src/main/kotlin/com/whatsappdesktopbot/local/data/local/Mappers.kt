package com.whatsappdesktopbot.local.data.local

import com.whatsappdesktopbot.local.data.local.entity.BotSettingsEntity
import com.whatsappdesktopbot.local.data.local.entity.ClientEntity
import com.whatsappdesktopbot.local.data.local.entity.InteractionLogEntity
import com.whatsappdesktopbot.local.data.local.entity.SkippedLogEntity
import com.whatsappdesktopbot.local.domain.model.BotClient
import com.whatsappdesktopbot.local.domain.model.BotSettings
import com.whatsappdesktopbot.local.domain.model.InteractionLogEntry
import com.whatsappdesktopbot.local.domain.model.SkippedLogEntry

fun ClientEntity.toDomain() = BotClient(id = id, name = name, emoji = emoji)

fun BotSettingsEntity.toDomain() = BotSettings(
    defaultEmoji = defaultEmoji,
    normalizeArabicEnabled = normalizeArabicEnabled,
    replyMode = replyMode,
    cooldownSeconds = cooldownSeconds,
    rpm = rpm,
    forwardEnabled = forwardEnabled,
    forwardTargetChatId = forwardTargetChatId,
    forwardBatchSize = forwardBatchSize,
    forwardFlushOnIdle = forwardFlushOnIdle,
    bulkMessagesPerMinute = bulkMessagesPerMinute,
    autoStartOnBoot = autoStartOnBoot,
    biometricLockEnabled = biometricLockEnabled,
)

fun InteractionLogEntity.toDomain() = InteractionLogEntry(
    id = id,
    timestamp = timestamp,
    groupId = groupId,
    groupName = groupName,
    match = matchName,
    action = action,
    snippet = snippet,
    messageId = messageId,
)

fun SkippedLogEntity.toDomain() = SkippedLogEntry(
    id = id,
    timestamp = timestamp,
    groupId = groupId,
    groupName = groupName,
    reason = reason,
    snippet = snippet,
    messageId = messageId,
)
