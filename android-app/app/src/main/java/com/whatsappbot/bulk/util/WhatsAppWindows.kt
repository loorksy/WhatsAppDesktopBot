package com.whatsappbot.bulk.util

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Finds a WhatsApp (or dual/clone) chat window across all interactive windows.
 * Important for Samsung Dual Messenger and Xiaomi/Redmi Dual Apps where the dual
 * instance may not always be the "active window" root reported by the system.
 */
object WhatsAppWindows {
    fun findChatRoot(service: AccessibilityService): AccessibilityNodeInfo? {
        service.rootInActiveWindow?.let { root ->
            if (WhatsAppNodes.isInChat(root)) return root
            root.recycle()
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return null
        }

        val windows = runCatching { service.windows }.getOrNull().orEmpty()
        if (windows.isEmpty()) return null

        val ordered = windows.sortedWith(
            compareByDescending<AccessibilityWindowInfo> { it.isFocused }
                .thenByDescending { it.isActive }
                .thenByDescending { it.type == AccessibilityWindowInfo.TYPE_APPLICATION },
        )

        for (window in ordered) {
            val root = runCatching { window.root }.getOrNull() ?: continue
            val pkg = root.packageName?.toString()
            if (!WhatsAppNodes.isSupportedPackage(pkg) && !WhatsAppNodes.isInChat(root)) {
                root.recycle()
                continue
            }
            if (WhatsAppNodes.isInChat(root)) {
                return root
            }
            root.recycle()
        }
        return null
    }

    fun isAnyChatVisible(service: AccessibilityService): Boolean {
        val root = findChatRoot(service) ?: return false
        root.recycle()
        return true
    }

    /** True when WhatsApp / Business / dual clone is already in the foreground windows. */
    fun isAnyWhatsAppVisible(service: AccessibilityService): Boolean {
        service.rootInActiveWindow?.let { root ->
            val pkg = root.packageName?.toString()
            val hit = WhatsAppNodes.isSupportedPackage(pkg) || WhatsAppNodes.isInChat(root)
            root.recycle()
            if (hit) return true
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return false
        val windows = runCatching { service.windows }.getOrNull().orEmpty()
        for (window in windows) {
            val root = runCatching { window.root }.getOrNull() ?: continue
            val pkg = root.packageName?.toString()
            val hit = WhatsAppNodes.isSupportedPackage(pkg) || WhatsAppNodes.isInChat(root)
            root.recycle()
            if (hit) return true
        }
        return false
    }
}
