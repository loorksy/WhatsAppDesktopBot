package com.whatsappdesktopbot.local.engine.fake

import com.whatsappdesktopbot.local.domain.engine.WhatsAppEngine
import com.whatsappdesktopbot.local.domain.engine.WhatsAppMessageActions
import com.whatsappdesktopbot.local.domain.logic.BotOrchestrator
import com.whatsappdesktopbot.local.domain.logic.BulkJobRunner
import com.whatsappdesktopbot.local.domain.logic.ReconnectBackoff
import com.whatsappdesktopbot.local.domain.model.BotRuntimeStatus
import com.whatsappdesktopbot.local.domain.model.BulkJobState
import com.whatsappdesktopbot.local.domain.model.ConnectionState
import com.whatsappdesktopbot.local.domain.model.EngineCommand
import com.whatsappdesktopbot.local.domain.model.EngineEvent
import com.whatsappdesktopbot.local.domain.model.IncomingMessage
import com.whatsappdesktopbot.local.domain.model.WhatsAppGroup
import com.whatsappdesktopbot.local.domain.store.BotProcessingStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

class FakeWhatsAppEngine(
    private val store: BotProcessingStore,
) : WhatsAppEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val bulkRunner = BulkJobRunner()
    private val backoff = ReconnectBackoff()
    private val orchestrator = BotOrchestrator(store, FakeMessageActions(this))

    private val _connectionState = MutableStateFlow(ConnectionState.NOT_LINKED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _runtimeStatus = MutableStateFlow(BotRuntimeStatus())
    override val runtimeStatus: StateFlow<BotRuntimeStatus> = _runtimeStatus.asStateFlow()

    private val _groups = MutableStateFlow(demoGroups())
    override val groups: StateFlow<List<WhatsAppGroup>> = _groups.asStateFlow()

    private val _bulkState = MutableStateFlow(BulkJobState())
    override val bulkState: StateFlow<BulkJobState> = _bulkState.asStateFlow()

    private val _events = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<EngineEvent> = _events.asSharedFlow()

    private var linked = false
    private var bulkMpm = 10
    private var messageCounter = 0L

    override suspend fun sendCommand(command: EngineCommand) {
        when (command) {
            is EngineCommand.RequestPairingCode -> requestPairing(command.phoneNumber)
            EngineCommand.ClearSession -> clearSession()
            EngineCommand.Reconnect -> reconnect()
            EngineCommand.StartBot -> startBot()
            EngineCommand.StopBot -> stopBot()
            EngineCommand.RefreshGroups -> refreshGroups()
            is EngineCommand.SetSelectedGroups -> setSelectedGroups(command.groupIds)
            is EngineCommand.SendMessage -> emitLog("Fake send to ${command.chatId}")
            is EngineCommand.StartBulk -> startBulk(command)
            EngineCommand.PauseBulk -> pauseBulk()
            EngineCommand.ResumeBulk -> resumeBulk()
            EngineCommand.StopBulk -> stopBulk()
            is EngineCommand.SimulateIncomingMessage -> simulateIncoming(command)
        }
    }

    private suspend fun requestPairing(phoneNumber: String) {
        if (phoneNumber.length < 8) {
            setConnection(ConnectionState.AUTH_FAILED)
            _events.emit(EngineEvent.ErrorEvent("Invalid phone number", recoverable = true))
            return
        }
        setConnection(ConnectionState.REQUESTING_CODE)
        delay(600)
        val code = (10000000 + Random.nextInt(89999999)).toString()
        setConnection(ConnectionState.PAIRING)
        _events.emit(EngineEvent.PairingCode(code))
        delay(1500)
        setConnection(ConnectionState.CONNECTING)
        delay(800)
        linked = true
        setConnection(ConnectionState.CONNECTED)
        syncGroupsFromStore()
        _events.emit(EngineEvent.GroupsUpdated(_groups.value))
    }

    private suspend fun clearSession() {
        orchestrator.stopBot()
        linked = false
        backoff.reset()
        stopBulkInternal()
        setConnection(ConnectionState.NOT_LINKED)
        updateRuntimeCounts()
        _events.emit(EngineEvent.LogEvent("Session cleared"))
    }

    private suspend fun reconnect() {
        if (!linked) {
            setConnection(ConnectionState.NOT_LINKED)
            return
        }
        setConnection(ConnectionState.CONNECTING)
        val waitSec = backoff.nextDelaySeconds()
        _runtimeStatus.update { it.copy(reconnectAttempts = backoff.currentAttempt()) }
        delay(waitSec * 1000)
        setConnection(ConnectionState.CONNECTED)
        backoff.reset()
        updateRuntimeCounts()
    }

    private suspend fun startBot() {
        if (_connectionState.value != ConnectionState.CONNECTED) return
        orchestrator.startBot()
        updateRuntimeCounts()
        _events.emit(EngineEvent.LogEvent("Bot started"))
    }

    private suspend fun stopBot() {
        orchestrator.stopBot()
        updateRuntimeCounts()
        _events.emit(EngineEvent.LogEvent("Bot stopped"))
    }

    private suspend fun refreshGroups() {
        if (!linked) return
        _groups.value = demoGroups()
        syncGroupsFromStore()
        _events.emit(EngineEvent.GroupsUpdated(_groups.value))
    }

    private suspend fun setSelectedGroups(ids: List<String>) {
        store.setSelectedGroupIds(ids)
        _groups.update { list -> list.map { it.copy(selected = ids.contains(it.id)) } }
    }

    private suspend fun simulateIncoming(command: EngineCommand.SimulateIncomingMessage) {
        if (!linked) return
        messageCounter += 1
        val message = IncomingMessage(
            id = "fake-${messageCounter}",
            chatId = command.chatId,
            groupName = command.groupName,
            fromMe = false,
            body = command.text,
        )
        orchestrator.handleIncoming(message)
        updateRuntimeCounts()
        _events.emit(
            EngineEvent.MessageReceived(
                messageId = message.id,
                chatId = message.chatId,
                text = command.text,
                groupName = command.groupName,
            ),
        )
    }

    private suspend fun syncGroupsFromStore() {
        val selected = store.getSelectedGroupIds().toSet()
        _groups.update { list -> list.map { it.copy(selected = selected.contains(it.id)) } }
    }

    private suspend fun startBulk(command: EngineCommand.StartBulk) {
        if (!linked) return
        bulkMpm = command.messagesPerMinute.coerceIn(1, 10_000)
        bulkRunner.start(command.groupId, command.messages.size)
        updateBulkState()
        scope.launch { runBulkLoop() }
    }

    private suspend fun runBulkLoop() {
        while (bulkRunner.snapshot().state != BulkJobRunner.State.IDLE) {
            val snap = bulkRunner.snapshot()
            if (snap.state == BulkJobRunner.State.PAUSED) {
                delay(300)
                continue
            }
            if (snap.sent >= snap.total) break
            delay((60_000L / bulkMpm).coerceAtLeast(100L))
            bulkRunner.markSent()
            updateBulkState()
            _events.emit(
                EngineEvent.BulkProgress(
                    sent = bulkRunner.snapshot().sent,
                    total = bulkRunner.snapshot().total,
                    paused = bulkRunner.snapshot().state == BulkJobRunner.State.PAUSED,
                ),
            )
        }
        updateBulkState()
    }

    private fun pauseBulk() {
        bulkRunner.pause()
        updateBulkState()
    }

    private fun resumeBulk() {
        bulkRunner.resume()
        scope.launch { runBulkLoop() }
    }

    private fun stopBulk() = stopBulkInternal()

    private fun stopBulkInternal() {
        bulkRunner.stop()
        updateBulkState()
    }

    private fun updateBulkState() {
        val snap = bulkRunner.snapshot()
        _bulkState.value = BulkJobState(
            state = when (snap.state) {
                BulkJobRunner.State.IDLE -> "idle"
                BulkJobRunner.State.RUNNING -> "running"
                BulkJobRunner.State.PAUSED -> "paused"
            },
            sent = snap.sent,
            total = snap.total,
            groupId = snap.groupId,
            paused = snap.state == BulkJobRunner.State.PAUSED,
        )
    }

    private suspend fun updateRuntimeCounts() {
        _runtimeStatus.update {
            it.copy(
                botRunning = orchestrator.botRunning,
                queueLength = orchestrator.queueLength,
                forwardQueueLength = store.getForwardQueue().size,
                lastProcessedAt = if (orchestrator.botRunning) System.currentTimeMillis() else it.lastProcessedAt,
            )
        }
    }

    private suspend fun emitLog(message: String) {
        _events.emit(EngineEvent.LogEvent(message))
    }

    private fun setConnection(state: ConnectionState) {
        _connectionState.value = state
        _runtimeStatus.update { it.copy(connectionState = state) }
        scope.launch { _events.emit(EngineEvent.ConnectionStateChanged(state)) }
    }

    private class FakeMessageActions(
        private val engine: FakeWhatsAppEngine,
    ) : WhatsAppMessageActions {
        override suspend fun react(message: IncomingMessage, emoji: String) {
            engine.emitLog("React ${emoji} on ${message.id}")
        }

        override suspend fun reply(message: IncomingMessage, text: String) {
            engine.emitLog("Reply ${text} on ${message.id}")
        }

        override suspend fun forwardMessage(messageId: String, targetChatId: String): Boolean {
            engine.emitLog("Forward $messageId -> $targetChatId")
            return true
        }
    }

    companion object {
        fun demoGroups(): List<WhatsAppGroup> = listOf(
            WhatsAppGroup("120363001@g.us", "مجموعة العملاء", selected = true),
            WhatsAppGroup("120363002@g.us", "مجموعة الإشعارات", selected = false),
            WhatsAppGroup("120363003@g.us", "مجموعة الدعم", selected = true),
        )
    }
}
