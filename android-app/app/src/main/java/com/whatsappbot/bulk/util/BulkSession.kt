package com.whatsappbot.bulk.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class BulkState {
    IDLE,
    WAITING_CHAT,
    SENDING,
    PAUSED,
    DONE,
    STOPPED,
}

data class BulkUiState(
    val state: BulkState = BulkState.IDLE,
    val sent: Int = 0,
    val total: Int = 0,
    val messagesPerMinute: Int = 10,
)

object BulkSession {
    private val _ui = MutableStateFlow(BulkUiState())
    val ui: StateFlow<BulkUiState> = _ui.asStateFlow()

    @Volatile
    var messages: List<String> = emptyList()
        private set

    @Volatile
    var messagesPerMinute: Int = 10
        private set

    @Volatile
    var index: Int = 0
        private set

    fun prepare(messages: List<String>, mpm: Int) {
        this.messages = messages.toList()
        this.messagesPerMinute = mpm.coerceIn(1, 120)
        this.index = 0
        _ui.value = BulkUiState(
            state = BulkState.WAITING_CHAT,
            sent = 0,
            total = messages.size,
            messagesPerMinute = messagesPerMinute,
        )
    }

    fun markSending() {
        _ui.value = _ui.value.copy(state = BulkState.SENDING)
    }

    fun markWaitingChat() {
        if (_ui.value.state == BulkState.SENDING || _ui.value.state == BulkState.WAITING_CHAT) {
            _ui.value = _ui.value.copy(state = BulkState.WAITING_CHAT)
        }
    }

    fun pause() {
        if (_ui.value.state == BulkState.SENDING || _ui.value.state == BulkState.WAITING_CHAT) {
            _ui.value = _ui.value.copy(state = BulkState.PAUSED)
        }
    }

    fun resume() {
        if (_ui.value.state == BulkState.PAUSED) {
            _ui.value = _ui.value.copy(state = BulkState.WAITING_CHAT)
        }
    }

    fun stop() {
        messages = emptyList()
        index = 0
        _ui.value = BulkUiState(state = BulkState.STOPPED, messagesPerMinute = messagesPerMinute)
    }

    fun onMessageSent() {
        index += 1
        val total = messages.size
        if (index >= total) {
            messages = emptyList()
            _ui.value = BulkUiState(
                state = BulkState.DONE,
                sent = total,
                total = total,
                messagesPerMinute = messagesPerMinute,
            )
        } else {
            _ui.value = _ui.value.copy(
                state = BulkState.SENDING,
                sent = index,
                total = total,
            )
        }
    }

    fun currentMessage(): String? = messages.getOrNull(index)

    fun isActive(): Boolean {
        val s = _ui.value.state
        return s == BulkState.WAITING_CHAT || s == BulkState.SENDING || s == BulkState.PAUSED
    }

    fun delayMs(): Long {
        val mpm = messagesPerMinute.coerceAtLeast(1)
        return (60_000L / mpm).coerceAtLeast(500L)
    }

    fun resetIdle() {
        if (!isActive()) {
            _ui.value = BulkUiState(state = BulkState.IDLE, messagesPerMinute = messagesPerMinute)
        }
    }
}
