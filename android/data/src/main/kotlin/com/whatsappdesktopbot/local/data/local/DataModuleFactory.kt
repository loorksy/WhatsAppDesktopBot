package com.whatsappdesktopbot.local.data.local

import android.content.Context
import androidx.room.Room
import com.whatsappdesktopbot.local.data.repository.BotRepository
import com.whatsappdesktopbot.local.data.store.RoomBotProcessingStore
import com.whatsappdesktopbot.local.domain.store.BotProcessingStore

object DataModuleFactory {
    fun createDatabase(context: Context): AppDatabase =
        Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "whatsapp_bot.db",
        ).fallbackToDestructiveMigration()
            .build()

    fun createProcessingStore(context: Context): BotProcessingStore =
        RoomBotProcessingStore(createDatabase(context))

    fun createRepository(context: Context): BotRepository {
        val db = createDatabase(context)
        return BotRepository(
            db = db,
            processingStore = RoomBotProcessingStore(db),
        )
    }
}
