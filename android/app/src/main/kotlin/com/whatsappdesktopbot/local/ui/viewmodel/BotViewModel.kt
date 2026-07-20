package com.whatsappdesktopbot.local.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatsappdesktopbot.local.data.repository.BotRepository
import com.whatsappdesktopbot.local.domain.engine.WhatsAppEngine
import com.whatsappdesktopbot.local.domain.model.BotClient
import com.whatsappdesktopbot.local.domain.model.BotRuntimeStatus
import com.whatsappdesktopbot.local.domain.model.BotSettings
import com.whatsappdesktopbot.local.domain.model.BulkJobState
import com.whatsappdesktopbot.local.domain.model.ConnectionEventEntry
import com.whatsappdesktopbot.local.domain.model.ConnectionState
import com.whatsappdesktopbot.local.domain.model.EngineCommand
import com.whatsappdesktopbot.local.domain.model.EngineEvent
import com.whatsappdesktopbot.local.domain.model.InteractionLogEntry
import com.whatsappdesktopbot.local.domain.model.SkippedLogEntry
import com.whatsappdesktopbot.local.domain.model.WhatsAppGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class BotViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val engine: WhatsAppEngine,
    private val repository: BotRepository,
) : ViewModel() {
    val connectionState: StateFlow<ConnectionState> = engine.connectionState
    val runtimeStatus: StateFlow<BotRuntimeStatus> = engine.runtimeStatus
    val groups: StateFlow<List<WhatsAppGroup>> = engine.groups
    val bulkState: StateFlow<BulkJobState> = engine.bulkState

    val clients: StateFlow<List<BotClient>> = repository.observeClients()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<BotSettings> = repository.observeSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BotSettings())

    val interactionLogs: StateFlow<List<InteractionLogEntry>> = repository.observeInteractionLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val skippedLogs: StateFlow<List<SkippedLogEntry>> = repository.observeSkippedLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val connectionEvents: StateFlow<List<ConnectionEventEntry>> = repository.observeConnectionEvents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _pairingCode = MutableStateFlow<String?>(null)
    val pairingCode: StateFlow<String?> = _pairingCode.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    init {
        viewModelScope.launch { repository.seedDemoDataIfNeeded() }
        viewModelScope.launch {
            engine.events.collect { event ->
                when (event) {
                    is EngineEvent.PairingCode -> _pairingCode.value = event.code
                    is EngineEvent.ConnectionStateChanged ->
                        repository.logConnection(event.state, "Engine state changed")
                    is EngineEvent.ErrorEvent -> _toast.value = event.message
                    is EngineEvent.MessageReceived -> _toast.value = "تمت معالجة رسالة وهمية"
                    is EngineEvent.LogEvent -> Unit
                    else -> Unit
                }
            }
        }
    }

    fun clearToast() {
        _toast.value = null
    }

    fun requestPairingCode(phone: String) = viewModelScope.launch {
        engine.sendCommand(EngineCommand.RequestPairingCode(phone))
    }

    fun clearSession() = viewModelScope.launch {
        _pairingCode.value = null
        engine.sendCommand(EngineCommand.ClearSession)
    }

    fun reconnect() = viewModelScope.launch {
        engine.sendCommand(EngineCommand.Reconnect)
    }

    fun startBot() = viewModelScope.launch { engine.sendCommand(EngineCommand.StartBot) }
    fun stopBot() = viewModelScope.launch { engine.sendCommand(EngineCommand.StopBot) }
    fun refreshGroups() = viewModelScope.launch { engine.sendCommand(EngineCommand.RefreshGroups) }

    fun toggleGroup(groupId: String, selected: Boolean) = viewModelScope.launch {
        val ids = groups.value.filter { it.selected }.map { it.id }.toMutableSet()
        if (selected) ids.add(groupId) else ids.remove(groupId)
        val idList = ids.toList()
        repository.setSelectedGroupIds(idList)
        engine.sendCommand(EngineCommand.SetSelectedGroups(idList))
    }

    fun addClient(name: String, emoji: String) = viewModelScope.launch {
        repository.upsertClient(name, emoji)
    }

    fun deleteClient(id: Long) = viewModelScope.launch {
        repository.deleteClient(id)
    }

    fun saveSettings(newSettings: BotSettings) = viewModelScope.launch {
        repository.saveSettings(newSettings)
    }

    fun startBulk(groupId: String, rawMessages: String, mpm: Int) = viewModelScope.launch {
        val messages = rawMessages.split("\n\n").map { it.trim() }.filter { it.isNotEmpty() }
        if (messages.isEmpty()) {
            _toast.value = "لا توجد رسائل"
            return@launch
        }
        engine.sendCommand(EngineCommand.StartBulk(groupId, messages, mpm))
    }

    fun pauseBulk() = viewModelScope.launch { engine.sendCommand(EngineCommand.PauseBulk) }
    fun resumeBulk() = viewModelScope.launch { engine.sendCommand(EngineCommand.ResumeBulk) }
    fun stopBulk() = viewModelScope.launch { engine.sendCommand(EngineCommand.StopBulk) }

    fun simulateTestMessage() = viewModelScope.launch {
        val group = groups.value.firstOrNull { it.selected } ?: groups.value.firstOrNull()
        if (group == null) {
            _toast.value = "لا توجد مجموعة"
            return@launch
        }
        val clientName = clients.value.firstOrNull()?.name ?: "محمد"
        engine.sendCommand(
            EngineCommand.SimulateIncomingMessage(
                chatId = group.id,
                groupName = group.name,
                text = "رسالة تجريبية من $clientName",
            ),
        )
    }

    fun importBundledDesktopJson() = viewModelScope.launch {
        runCatching {
            val settings = readAsset("migration/settings.json")
            val groups = readAsset("migration/groups.json")
            val clients = readAsset("migration/clients.sample.json")
            repository.importDesktopJson(
                settingsJson = settings,
                groupsJson = groups,
                clientsJson = clients,
            )
        }.onSuccess { result ->
            _toast.value = "تم الاستيراد: ${result.clients} عميل، ${result.groups} مجموعة"
            engine.sendCommand(EngineCommand.RefreshGroups)
        }.onFailure {
            _toast.value = "فشل الاستيراد: ${it.message}"
        }
    }

    private fun readAsset(path: String): String =
        appContext.assets.open(path).bufferedReader().use { it.readText() }
}
