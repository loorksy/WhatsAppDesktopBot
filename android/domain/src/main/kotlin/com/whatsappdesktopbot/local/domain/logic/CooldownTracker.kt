package com.whatsappdesktopbot.local.domain.logic

class CooldownTracker {
    private val lastActionByGroup = mutableMapOf<String, Long>()

    fun millisUntilAllowed(groupId: String, cooldownSeconds: Int, now: Long): Long {
        val last = lastActionByGroup[groupId] ?: 0L
        val cooldownMs = cooldownSeconds.coerceAtLeast(0) * 1000L
        val delta = now - last
        return if (delta < cooldownMs) cooldownMs - delta else 0L
    }

    fun record(groupId: String, now: Long) {
        lastActionByGroup[groupId] = now
    }
}
