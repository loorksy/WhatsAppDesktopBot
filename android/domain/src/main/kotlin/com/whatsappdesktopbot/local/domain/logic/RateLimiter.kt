package com.whatsappdesktopbot.local.domain.logic

class RateLimiter(
    private val windowMs: Long = 60_000L,
) {
    private val timestamps = ArrayDeque<Long>()

    fun canProceed(now: Long, rpmLimit: Int): Boolean {
        prune(now)
        return timestamps.size < rpmLimit
    }

    fun record(now: Long, rpmLimit: Int) {
        prune(now)
        if (timestamps.size >= rpmLimit) {
            error("Rate limit exceeded")
        }
        timestamps.addLast(now)
    }

    fun waitMillis(now: Long, rpmLimit: Int): Long {
        prune(now)
        if (timestamps.size < rpmLimit) return 0
        val oldest = timestamps.first()
        return (oldest + windowMs - now).coerceAtLeast(0)
    }

    private fun prune(now: Long) {
        while (timestamps.isNotEmpty() && now - timestamps.first() >= windowMs) {
            timestamps.removeFirst()
        }
    }
}
