package com.whatsappbot.bulk.util

import android.view.accessibility.AccessibilityNodeInfo

object WhatsAppNodes {
    val PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")

    private val ENTRY_IDS = listOf(
        "com.whatsapp:id/entry",
        "com.whatsapp.w4b:id/entry",
        "com.whatsapp:id/conversation_entry",
        "com.whatsapp.w4b:id/conversation_entry",
    )

    private val SEND_IDS = listOf(
        "com.whatsapp:id/send",
        "com.whatsapp.w4b:id/send",
        "com.whatsapp:id/conversation_entry_action_button",
        "com.whatsapp.w4b:id/conversation_entry_action_button",
        "com.whatsapp:id/send_container",
        "com.whatsapp.w4b:id/send_container",
    )

    private val SEND_DESCS = listOf(
        "send", "إرسال", "enviar", "ส่ง", "envoyer", "senden", "invia",
    )

    fun findEntry(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        for (id in ENTRY_IDS) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            val editable = nodes?.firstOrNull { it.isEditable || it.isFocused }
            if (editable != null) return editable
            if (!nodes.isNullOrEmpty()) return nodes[0]
        }
        return findEditableFallback(root)
    }

    fun findSend(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        for (id in SEND_IDS) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            val clickable = nodes?.firstOrNull { it.isClickable || it.isEnabled }
            if (clickable != null) return clickable
            if (!nodes.isNullOrEmpty()) {
                val node = nodes[0]
                return findClickableParent(node) ?: node
            }
        }
        return findSendByDescription(root)
    }

    fun isInChat(root: AccessibilityNodeInfo?): Boolean = findEntry(root) != null

    private fun findEditableFallback(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable && node.isVisibleToUser) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findEditableFallback(child)
            if (found != null) return found
            child.recycle()
        }
        return null
    }

    private fun findSendByDescription(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val desc = (node.contentDescription?.toString() ?: "").lowercase()
        val text = (node.text?.toString() ?: "").lowercase()
        if (node.isClickable && SEND_DESCS.any { desc.contains(it) || text.contains(it) }) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findSendByDescription(child)
            if (found != null) return found
            child.recycle()
        }
        return null
    }

    private fun findClickableParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 5) {
            if (current.isClickable) return current
            current = current.parent
            depth++
        }
        return null
    }
}
