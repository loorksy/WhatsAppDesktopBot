package com.whatsappdesktopbot.local.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.whatsappdesktopbot.local.data.local.entity.BotSettingsEntity
import com.whatsappdesktopbot.local.data.local.entity.ClientEntity
import com.whatsappdesktopbot.local.data.local.entity.ConnectionEventEntity
import com.whatsappdesktopbot.local.data.local.entity.ForwardQueueEntity
import com.whatsappdesktopbot.local.data.local.entity.GroupDirectoryEntity
import com.whatsappdesktopbot.local.data.local.entity.InteractionLogEntity
import com.whatsappdesktopbot.local.data.local.entity.ProcessedMessageEntity
import com.whatsappdesktopbot.local.data.local.entity.SelectedGroupEntity
import com.whatsappdesktopbot.local.data.local.entity.SkippedLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ClientDao {
    @Query("SELECT * FROM clients ORDER BY name ASC")
    fun observeAll(): Flow<List<ClientEntity>>

    @Query("SELECT * FROM clients ORDER BY name ASC")
    suspend fun getAll(): List<ClientEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(client: ClientEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(clients: List<ClientEntity>)

    @Query("DELETE FROM clients")
    suspend fun deleteAll()

    @Query("DELETE FROM clients WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface BotSettingsDao {
    @Query("SELECT * FROM bot_settings WHERE id = 1")
    fun observe(): Flow<BotSettingsEntity?>

    @Query("SELECT * FROM bot_settings WHERE id = 1")
    suspend fun get(): BotSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: BotSettingsEntity)
}

@Dao
interface SelectedGroupDao {
    @Query("SELECT groupId FROM selected_groups")
    suspend fun getAllIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(groups: List<SelectedGroupEntity>)

    @Query("DELETE FROM selected_groups")
    suspend fun deleteAll()
}

@Dao
interface GroupDirectoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: GroupDirectoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<GroupDirectoryEntity>)
}

@Dao
interface ProcessedMessageDao {
    @Query("SELECT COUNT(*) > 0 FROM processed_messages WHERE messageId = :messageId")
    suspend fun exists(messageId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: ProcessedMessageEntity)

    @Query("SELECT COUNT(*) FROM processed_messages")
    suspend fun count(): Int

    @Query(
        """
        DELETE FROM processed_messages WHERE messageId IN (
            SELECT messageId FROM processed_messages ORDER BY processedAt ASC LIMIT :count
        )
        """,
    )
    suspend fun trimOldest(count: Int)
}

@Dao
interface InteractionLogDao {
    @Query("SELECT * FROM interaction_logs ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<InteractionLogEntity>>

    @Insert
    suspend fun insert(entry: InteractionLogEntity)

    @Insert
    suspend fun insertAll(entries: List<InteractionLogEntity>)

    @Query("SELECT COUNT(*) FROM interaction_logs")
    suspend fun count(): Int

    @Query(
        """
        DELETE FROM interaction_logs WHERE id IN (
            SELECT id FROM interaction_logs ORDER BY timestamp ASC LIMIT :count
        )
        """,
    )
    suspend fun trimOldest(count: Int)
}

@Dao
interface SkippedLogDao {
    @Query("SELECT * FROM skipped_logs ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<SkippedLogEntity>>

    @Insert
    suspend fun insert(entry: SkippedLogEntity)

    @Insert
    suspend fun insertAll(entries: List<SkippedLogEntity>)

    @Query("SELECT COUNT(*) FROM skipped_logs")
    suspend fun count(): Int

    @Query(
        """
        DELETE FROM skipped_logs WHERE id IN (
            SELECT id FROM skipped_logs ORDER BY timestamp ASC LIMIT :count
        )
        """,
    )
    suspend fun trimOldest(count: Int)
}

@Dao
interface ForwardQueueDao {
    @Query("SELECT * FROM forward_queue ORDER BY timestamp ASC")
    suspend fun getAll(): List<ForwardQueueEntity>

    @Query("SELECT COUNT(*) FROM forward_queue")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: ForwardQueueEntity)

    @Query("DELETE FROM forward_queue WHERE messageId = :messageId")
    suspend fun delete(messageId: String)

    @Query("DELETE FROM forward_queue")
    suspend fun deleteAll()
}

@Dao
interface ConnectionEventDao {
    @Query("SELECT * FROM connection_events ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<ConnectionEventEntity>>

    @Insert
    suspend fun insert(event: ConnectionEventEntity)

    @Query("SELECT COUNT(*) FROM connection_events")
    suspend fun count(): Int

    @Query(
        """
        DELETE FROM connection_events WHERE id IN (
            SELECT id FROM connection_events ORDER BY timestamp ASC LIMIT :count
        )
        """,
    )
    suspend fun trimOldest(count: Int)
}
