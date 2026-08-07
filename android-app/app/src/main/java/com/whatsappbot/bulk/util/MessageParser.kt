package com.whatsappbot.bulk.util

object MessageParser {
    enum class Mode { PARAGRAPH, FIXED3, LINES }

    fun parse(rawText: String, mode: Mode): List<String> {
        return when (mode) {
            Mode.FIXED3 -> {
                val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
                lines.chunked(3).map { it.joinToString("\n") }.filter { it.isNotBlank() }
            }
            Mode.LINES -> rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
            Mode.PARAGRAPH -> rawText
                .split(Regex("\\n\\s*\\n+"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }
    }
}
