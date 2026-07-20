package com.whatsappdesktopbot.local.domain.logic

object ArabicNormalizer {
    private val arabicIndic = "٠١٢٣٤٥٦٧٨٩"

    fun normalize(text: String?): String {
        if (text.isNullOrBlank()) return ""
        var normalized = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFKD)
        normalized = normalized.replace(Regex("[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06ED]"), "")
        normalized = normalized.replace(Regex("[أإآ]"), "ا").replace("ى", "ي")
        arabicIndic.forEachIndexed { index, char ->
            normalized = normalized.replace(char.toString(), index.toString())
        }
        normalized = normalized.replace(Regex("\\s+"), " ").trim()
        return normalized
    }
}
