package com.whatsappdesktopbot.local.di

import android.content.Context
import com.whatsappdesktopbot.local.data.local.DataModuleFactory
import com.whatsappdesktopbot.local.data.repository.BotRepository
import com.whatsappdesktopbot.local.domain.engine.WhatsAppEngine
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
    fun provideWhatsAppEngine(): WhatsAppEngine = FakeWhatsAppEngine()

    @Provides
    @Singleton
    fun provideBotRepository(@ApplicationContext context: Context): BotRepository =
        DataModuleFactory.createRepository(context)
}
