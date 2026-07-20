package com.whatsappdesktopbot.local.domain.logic

class ProcessedMessageTracker(
    private val maxSize: Int = 50_000,
) {
    private val processed = LinkedHashSet<String>()

    fun isProcessed(id: String): Boolean = processed.contains(id)

    fun markProcessed(id: String) {
        if (processed.contains(id)) return
        processed.add(id)
        while (processed.size > maxSize) {
            val oldest = processed.first()
            processed.remove(oldest)
        }
    }

    fun size(): Int = processed.size
}
