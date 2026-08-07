package com.whatsappbot.bulk.service

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.whatsappbot.bulk.util.BulkSession
import com.whatsappbot.bulk.util.BulkState
import com.whatsappbot.bulk.util.WhatsAppNodes
import com.whatsappbot.bulk.util.WhatsAppWindows

class BulkAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var loopPosted = false
    private var sendingStep = false

    private val loopRunnable = object : Runnable {
        override fun run() {
            loopPosted = false
            if (sendingStep) return
            tick()
            if (BulkSession.isActive() && !sendingStep) {
                scheduleNext()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        if (BulkSession.isActive()) {
            scheduleNext(300)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!BulkSession.isActive() || sendingStep) return
        val pkg = event?.packageName?.toString()
        // Accept official WhatsApp, Business, Samsung/Xiaomi dual apps, and clones.
        if (pkg != null && !WhatsAppNodes.isSupportedPackage(pkg)) {
            if (!WhatsAppWindows.isAnyChatVisible(this)) return
        }
        if (!loopPosted) {
            scheduleNext(150)
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        loopPosted = false
        sendingStep = false
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun startLoop() {
        scheduleNext(400)
    }

    fun stopLoop() {
        handler.removeCallbacksAndMessages(null)
        loopPosted = false
        sendingStep = false
    }

    private fun scheduleNext(delayMs: Long = BulkSession.delayMs()) {
        if (loopPosted || sendingStep) return
        loopPosted = true
        handler.postDelayed(loopRunnable, delayMs)
    }

    private fun tick() {
        val state = BulkSession.ui.value.state
        if (state == BulkState.IDLE || state == BulkState.DONE || state == BulkState.STOPPED) {
            return
        }

        // Search across all interactive windows so dual WhatsApp on Samsung/Xiaomi is found.
        val root = WhatsAppWindows.findChatRoot(this)
        if (root == null) {
            BulkSession.markWaitingChat()
            return
        }

        val message = BulkSession.currentMessage()
        if (message == null) {
            BulkSession.onMessageSent()
            root.recycle()
            return
        }

        val entry = WhatsAppNodes.findEntry(root)
        if (entry == null) {
            BulkSession.markWaitingChat()
            root.recycle()
            return
        }

        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, message)
        val setOk = entry.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        entry.recycle()
        root.recycle()

        if (!setOk) {
            BulkSession.markWaitingChat()
            return
        }

        sendingStep = true
        BulkSession.markSending()
        handler.postDelayed({ clickSendAndContinue() }, 180)
    }

    private fun clickSendAndContinue() {
        val root = WhatsAppWindows.findChatRoot(this)
        val send = WhatsAppNodes.findSend(root)
        val clicked = send?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        send?.recycle()
        root?.recycle()
        sendingStep = false

        if (clicked) {
            BulkSession.onMessageSent()
            if (!BulkSession.isActive()) {
                return
            }
            scheduleNext(BulkSession.delayMs())
        } else {
            BulkSession.markWaitingChat()
            scheduleNext(700)
        }
    }

    companion object {
        @Volatile
        var instance: BulkAccessibilityService? = null
            private set

        fun isEnabled(): Boolean = instance != null
    }
}
