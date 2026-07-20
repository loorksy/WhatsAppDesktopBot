package com.whatsappdesktopbot.local.domain.logic

class BulkJobRunner {
    enum class State { IDLE, RUNNING, PAUSED }

    data class Snapshot(
        val state: State,
        val sent: Int,
        val total: Int,
        val groupId: String?,
    )

    private var state = State.IDLE
    private var sent = 0
    private var total = 0
    private var groupId: String? = null

    fun start(group: String, messageCount: Int) {
        state = State.RUNNING
        sent = 0
        total = messageCount
        groupId = group
    }

    fun pause() {
        if (state == State.RUNNING) state = State.PAUSED
    }

    fun resume() {
        if (state == State.PAUSED) state = State.RUNNING
    }

    fun markSent() {
        if (state != State.RUNNING) return
        sent += 1
        if (sent >= total) {
            reset()
        }
    }

    fun stop() = reset()

    fun snapshot(): Snapshot = Snapshot(state, sent, total, groupId)

    private fun reset() {
        state = State.IDLE
        sent = 0
        total = 0
        groupId = null
    }
}
