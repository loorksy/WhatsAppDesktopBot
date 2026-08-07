package com.whatsappbot.bulk.util

import android.view.accessibility.AccessibilityNodeInfo

object WhatsAppNodes {
    val KNOWN_PACKAGES = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
    )

    private val ENTRY_SUFFIXES = listOf(
        "id/entry",
        "id/conversation_entry",
        "id/input",
        "id/message_entry",
    )

    private val SEND_SUFFIXES = listOf(
        "id/send",
        "id/conversation_entry_action_button",
        "id/send_container",
        "id/send_button",
        "id/compose_btn_send",
    )

    private val SEND_DESCS = listOf(
        "send", "إرسال", "ارسال", "enviar", "envoyer", "senden", "invia",
    )

    fun isSupportedPackage(packageName: String?): Boolean {
        val pkg = packageName?.lowercase() ?: return false
        if (pkg in KNOWN_PACKAGES) return true
        return pkg.contains("whatsapp")
    }

    fun isInChat(root: AccessibilityNodeInfo?): Boolean = findEntry(root) != null

    fun isWhatsAppWindow(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        return isSupportedPackage(root.packageName?.toString()) || isInChat(root)
    }

    fun findEntry(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        // Fast path: official package ids
        for (pkg in KNOWN_PACKAGES) {
            for (suffix in listOf("entry", "conversation_entry")) {
                val nodes = root.findAccessibilityNodeInfosByViewId("$pkg:id/$suffix")
                val editable = nodes?.firstOrNull { it.isEditable || it.isFocused }
                if (editable != null) return editable
                if (!nodes.isNullOrEmpty()) return nodes[0]
            }
        }
        // Dual / clone apps: match by view-id suffix across the tree
        findNodeByIdSuffix(root, ENTRY_SUFFIXES)?.let { return it }
        return findEditableFallback(root)
    }

    fun findSend(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        for (pkg in KNOWN_PACKAGES) {
            for (suffix in listOf("send", "conversation_entry_action_button", "send_container")) {
                val nodes = root.findAccessibilityNodeInfosByViewId("$pkg:id/$suffix")
                val clickable = nodes?.firstOrNull { it.isClickable || it.isEnabled }
                if (clickable != null) return clickable
                if (!nodes.isNullOrEmpty()) {
                    val node = nodes[0]
                    return findClickableParent(node) ?: node
                }
            }
        }
        findNodeByIdSuffix(root, SEND_SUFFIXES, preferClickable = true)?.let { return it }
        return findSendByDescription(root)
    }

    private fun findNodeByIdSuffix(
        node: AccessibilityNodeInfo,
        suffixes: List<String>,
        preferClickable: Boolean = false,
    ): AccessibilityNodeInfo? {
        val viewId = node.viewIdResourceName?.lowercase().orEmpty()
        if (viewId.isNotBlank() && suffixes.any { viewId.endsWith(it) }) {
            if (preferClickable) {
                if (node.isClickable || node.isEnabled) return AccessibilityNodeInfo.obtain(node)
                findClickableParent(node)?.let { return it }
            } else if (node.isEditable || node.isFocused || node.isVisibleToUser) {
                return AccessibilityNodeInfo.obtain(node)
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeByIdSuffix(child, suffixes, preferClickable)
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    private fun findEditableFallback(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val className = node.className?.toString().orEmpty()
        val looksLikeInput = node.isEditable || className.contains("EditText", ignoreCase = true)
        if (looksLikeInput && node.isVisibleToUser) {
            return AccessibilityNodeInfo.obtain(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findEditableFallback(child)
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    private fun findSendByDescription(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val desc = (node.contentDescription?.toString() ?: "").lowercase()
        val text = (node.text?.toString() ?: "").lowercase()
        if (node.isClickable && SEND_DESCS.any { desc.contains(it) || text.contains(it) }) {
            return AccessibilityNodeInfo.obtain(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findSendByDescription(child)
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    private fun findClickableParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node.parent
        var depth = 0
        while (current != null && depth < 5) {
            if (current.isClickable) return current
            val parent = current.parent
            current.recycle()
            current = parent
            depth++
        }
        return null
    }
}
