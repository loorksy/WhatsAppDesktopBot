package com.whatsappdesktopbot.local.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.whatsappdesktopbot.local.data.local.entity.BotSettingsEntity
import com.whatsappdesktopbot.local.data.local.entity.ClientEntity
import com.whatsappdesktopbot.local.data.local.entity.ConnectionEventEntity
import com.whatsappdesktopbot.local.data.local.entity.InteractionLogEntity
import com.whatsappdesktopbot.local.data.local.entity.SkippedLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ClientDao {
    @Query("SELECT * FROM clients ORDER BY name ASC")
    fun observeAll(): Flow<List<ClientEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(client: ClientEntity): Long

    @Query("DELETE FROM clients WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface BotSettingsDao {
    @Query("SELECT * FROM bot_settings WHERE id = 1")
    fun observe(): Flow<BotSettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: BotSettingsEntity)
}

@Dao
interface InteractionLogDao {
    @Query("SELECT * FROM interaction_logs ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<InteractionLogEntity>>
}

@Dao
interface SkippedLogDao {
    @Query("SELECT * FROM skipped_logs ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<SkippedLogEntity>>
}

@Dao
interface ConnectionEventDao {
    @Query("SELECT * FROM connection_events ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<ConnectionEventEntity>>

    @Insert
    suspend fun insert(event: ConnectionEventEntity)
}
