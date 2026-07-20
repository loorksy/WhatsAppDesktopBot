package com.whatsappdesktopbot.local.domain.model

sealed interface EngineCommand {
    data class RequestPairingCode(val phoneNumber: String) : EngineCommand
    data object ClearSession : EngineCommand
    data object Reconnect : EngineCommand
    data object StartBot : EngineCommand
    data object StopBot : EngineCommand
    data object RefreshGroups : EngineCommand
    data class SetSelectedGroups(val groupIds: List<String>) : EngineCommand
    data class SendMessage(val chatId: String, val text: String) : EngineCommand
    data class StartBulk(
        val groupId: String,
        val messages: List<String>,
        val messagesPerMinute: Int,
    ) : EngineCommand
    data object PauseBulk : EngineCommand
    data object ResumeBulk : EngineCommand
    data object StopBulk : EngineCommand
    data class SimulateIncomingMessage(
        val chatId: String,
        val groupName: String,
        val text: String,
    ) : EngineCommand
}

sealed interface EngineEvent {
    data class PairingCode(val code: String) : EngineEvent
    data class ConnectionStateChanged(val state: ConnectionState) : EngineEvent
    data class MessageReceived(
        val messageId: String,
        val chatId: String,
        val text: String,
        val groupName: String,
    ) : EngineEvent
    data class GroupsUpdated(val groups: List<WhatsAppGroup>) : EngineEvent
    data class BulkProgress(val sent: Int, val total: Int, val paused: Boolean) : EngineEvent
    data class ErrorEvent(val message: String, val recoverable: Boolean = true) : EngineEvent
    data class LogEvent(val message: String) : EngineEvent
}
