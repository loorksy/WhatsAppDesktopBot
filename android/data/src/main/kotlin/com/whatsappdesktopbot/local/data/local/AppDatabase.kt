package com.whatsappdesktopbot.local.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.whatsappdesktopbot.local.data.local.dao.BotSettingsDao
import com.whatsappdesktopbot.local.data.local.dao.ClientDao
import com.whatsappdesktopbot.local.data.local.dao.ConnectionEventDao
import com.whatsappdesktopbot.local.data.local.dao.InteractionLogDao
import com.whatsappdesktopbot.local.data.local.dao.SkippedLogDao
import com.whatsappdesktopbot.local.data.local.entity.BotSettingsEntity
import com.whatsappdesktopbot.local.data.local.entity.BulkJobEntity
import com.whatsappdesktopbot.local.data.local.entity.BulkMessageEntity
import com.whatsappdesktopbot.local.data.local.entity.ClientEntity
import com.whatsappdesktopbot.local.data.local.entity.ConnectionEventEntity
import com.whatsappdesktopbot.local.data.local.entity.ForwardQueueEntity
import com.whatsappdesktopbot.local.data.local.entity.GroupDirectoryEntity
import com.whatsappdesktopbot.local.data.local.entity.InteractionLogEntity
import com.whatsappdesktopbot.local.data.local.entity.ProcessedMessageEntity
import com.whatsappdesktopbot.local.data.local.entity.SelectedGroupEntity
import com.whatsappdesktopbot.local.data.local.entity.SkippedLogEntity

@Database(
    entities = [
        ClientEntity::class,
        SelectedGroupEntity::class,
        GroupDirectoryEntity::class,
        ProcessedMessageEntity::class,
        InteractionLogEntity::class,
        SkippedLogEntity::class,
        BotSettingsEntity::class,
        ForwardQueueEntity::class,
        BulkJobEntity::class,
        BulkMessageEntity::class,
        ConnectionEventEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun clientDao(): ClientDao
    abstract fun botSettingsDao(): BotSettingsDao
    abstract fun interactionLogDao(): InteractionLogDao
    abstract fun skippedLogDao(): SkippedLogDao
    abstract fun connectionEventDao(): ConnectionEventDao
}
