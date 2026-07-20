package com.whatsappdesktopbot.local.data.local

import android.content.Context
import androidx.room.Room
import com.whatsappdesktopbot.local.data.repository.BotRepository

object DataModuleFactory {
    fun createRepository(context: Context): BotRepository {
        val db = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "whatsapp_bot.db",
        ).fallbackToDestructiveMigration().build()
        return BotRepository(
            clientDao = db.clientDao(),
            settingsDao = db.botSettingsDao(),
            interactionLogDao = db.interactionLogDao(),
            skippedLogDao = db.skippedLogDao(),
            connectionEventDao = db.connectionEventDao(),
        )
    }
}
