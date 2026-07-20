package com.whatsappdesktopbot.local.di

import android.content.Context
import com.whatsappdesktopbot.local.data.local.AppDatabase
import com.whatsappdesktopbot.local.data.local.DataModuleFactory
import com.whatsappdesktopbot.local.data.repository.BotRepository
import com.whatsappdesktopbot.local.data.store.RoomBotProcessingStore
import com.whatsappdesktopbot.local.domain.engine.WhatsAppEngine
import com.whatsappdesktopbot.local.domain.store.BotProcessingStore
import com.whatsappdesktopbot.local.engine.fake.FakeWhatsAppEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        DataModuleFactory.createDatabase(context)

    @Provides
    @Singleton
    fun provideBotProcessingStore(db: AppDatabase): BotProcessingStore =
        RoomBotProcessingStore(db)

    @Provides
    @Singleton
    fun provideBotRepository(db: AppDatabase, store: BotProcessingStore): BotRepository =
        BotRepository(db, store)

    @Provides
    @Singleton
    fun provideWhatsAppEngine(store: BotProcessingStore): WhatsAppEngine =
        FakeWhatsAppEngine(store)
}
