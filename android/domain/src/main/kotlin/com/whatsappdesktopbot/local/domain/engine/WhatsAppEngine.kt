package com.whatsappdesktopbot.local.domain.engine

import com.whatsappdesktopbot.local.domain.model.BotRuntimeStatus
import com.whatsappdesktopbot.local.domain.model.BulkJobState
import com.whatsappdesktopbot.local.domain.model.ConnectionState
import com.whatsappdesktopbot.local.domain.model.EngineCommand
import com.whatsappdesktopbot.local.domain.model.EngineEvent
import com.whatsappdesktopbot.local.domain.model.WhatsAppGroup
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface WhatsAppEngine {
    val connectionState: StateFlow<ConnectionState>
    val runtimeStatus: StateFlow<BotRuntimeStatus>
    val groups: StateFlow<List<WhatsAppGroup>>
    val bulkState: StateFlow<BulkJobState>
    val events: SharedFlow<EngineEvent>

    suspend fun sendCommand(command: EngineCommand)
}
