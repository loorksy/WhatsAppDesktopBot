package com.whatsappdesktopbot.local.domain.logic

class ReconnectBackoff(
    private val stepsSeconds: List<Long> = listOf(5, 10, 20, 40, 60),
) {
    private var attempt = 0

    fun nextDelaySeconds(): Long {
        val index = attempt.coerceAtMost(stepsSeconds.lastIndex)
        val delay = stepsSeconds[index]
        attempt = (attempt + 1).coerceAtMost(stepsSeconds.lastIndex)
        return delay
    }

    fun reset() {
        attempt = 0
    }

    fun currentAttempt(): Int = attempt
}
