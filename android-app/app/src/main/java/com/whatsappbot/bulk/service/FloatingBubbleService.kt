package com.whatsappbot.bulk.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.whatsappbot.bulk.R
import com.whatsappbot.bulk.data.LicenseClient
import com.whatsappbot.bulk.data.LicensePrefs
import com.whatsappbot.bulk.ui.ActivationActivity
import com.whatsappbot.bulk.ui.HomeActivity
import com.whatsappbot.bulk.util.BulkSession
import com.whatsappbot.bulk.util.BulkState
import com.whatsappbot.bulk.util.DualAppSupport
import com.whatsappbot.bulk.util.MessageParser
import com.whatsappbot.bulk.util.WhatsAppApps
import com.whatsappbot.bulk.util.WhatsAppWindows
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FloatingBubbleService : Service() {
    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var panelView: View? = null
    private var closeZoneView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectJob: Job? = null
    private var licenseJob: Job? = null

    private var panelInput: EditText? = null
    private var panelCount: TextView? = null
    private var panelMpm: TextView? = null
    private var panelSeek: SeekBar? = null
    private var panelStatus: TextView? = null
    private var panelSuccess: TextView? = null
    private var panelSend: Button? = null

    private fun themedInflater(): LayoutInflater {
        val themed = ContextThemeWrapper(this, R.style.Theme_BulkSender)
        return LayoutInflater.from(themed)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForeground(NOTIFICATION_ID, buildNotification())
        showBubble()
        collectJob = scope.launch {
            BulkSession.ui.collectLatest { state ->
                renderPanelState(state.state, state.sent, state.total, state.successMessage)
                if (state.state == BulkState.DONE) {
                    showPanelSafe()
                }
            }
        }
        licenseJob = scope.launch {
            verifyLicenseOrShutdown()
            while (isActive) {
                delay(30_000L)
                verifyLicenseOrShutdown()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                shutdownCompletely()
            }
            ACTION_SHOW_PANEL -> showPanelSafe()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        collectJob?.cancel()
        licenseJob?.cancel()
        scope.cancel()
        hideCloseZone()
        removeBubble()
        hidePanel()
        super.onDestroy()
    }

    private fun overlayType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun showBubble() {
        if (bubbleView != null) return
        try {
            val view = themedInflater().inflate(R.layout.overlay_bubble, null)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 40
                y = 220
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }

            var downX = 0f
            var downY = 0f
            var paramX = 0
            var paramY = 0
            var moved = false
            val screenHeight = resources.displayMetrics.heightPixels
            val closeThresholdY = (screenHeight * 0.78f).toInt()

            view.setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        downY = event.rawY
                        paramX = params.x
                        paramY = params.y
                        moved = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - downX).toInt()
                        val dy = (event.rawY - downY).toInt()
                        if (kotlin.math.abs(dx) > 12 || kotlin.math.abs(dy) > 12) moved = true
                        params.x = paramX + dx
                        params.y = paramY + dy
                        try {
                            windowManager.updateViewLayout(v, params)
                        } catch (_: Exception) {
                        }
                        if (moved) {
                            showCloseZone()
                            highlightCloseZone(event.rawY >= closeThresholdY)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        val dropClose = moved && event.rawY >= closeThresholdY
                        hideCloseZone()
                        if (dropClose) {
                            mainHandler.post {
                                Toast.makeText(this, R.string.closed_by_drop, Toast.LENGTH_SHORT).show()
                                shutdownCompletely()
                            }
                        } else if (!moved) {
                            mainHandler.post { togglePanel() }
                        }
                        true
                    }
                    else -> false
                }
            }

            windowManager.addView(view, params)
            bubbleView = view
            bubbleParams = params
        } catch (e: Exception) {
            Log.e(TAG, "showBubble failed", e)
            Toast.makeText(this, R.string.overlay_show_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun showCloseZone() {
        if (closeZoneView != null) return
        try {
            val view = themedInflater().inflate(R.layout.overlay_close_zone, null)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.BOTTOM
            }
            windowManager.addView(view, params)
            closeZoneView = view
        } catch (e: Exception) {
            Log.e(TAG, "showCloseZone failed", e)
        }
    }

    private fun highlightCloseZone(active: Boolean) {
        val icon = closeZoneView?.findViewById<TextView>(R.id.closeZoneIcon) ?: return
        icon.scaleX = if (active) 1.15f else 1f
        icon.scaleY = if (active) 1.15f else 1f
        icon.alpha = if (active) 1f else 0.85f
    }

    private fun hideCloseZone() {
        closeZoneView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        closeZoneView = null
    }

    private fun shutdownCompletely() {
        BulkSession.stop()
        BulkAccessibilityService.instance?.stopLoop()
        hidePanel()
        hideCloseZone()
        stopSelf()
    }

    /** @return true when license is valid and sending may continue */
    private suspend fun verifyLicenseOrShutdown(): Boolean {
        val prefs = LicensePrefs(this)
        if (!prefs.isActivated()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@FloatingBubbleService, R.string.activation_required, Toast.LENGTH_LONG).show()
                openActivation()
                shutdownCompletely()
            }
            return false
        }
        val result = withContext(Dispatchers.IO) {
            LicenseClient(prefs).checkStatus()
        }
        if (result.active) return true
        // Network blips should not wipe activation, but revoked codes must stop the app.
        if (result.error == "NETWORK" || result.error == "ERROR") {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@FloatingBubbleService, R.string.activation_network, Toast.LENGTH_LONG).show()
            }
            return false
        }
        withContext(Dispatchers.Main) {
            prefs.clearActivation()
            val msg = when (result.error) {
                "DISABLED" -> getString(R.string.activation_disabled)
                "DEVICE_MISMATCH" -> getString(R.string.activation_device)
                else -> getString(R.string.activation_invalid)
            }
            Toast.makeText(this@FloatingBubbleService, msg, Toast.LENGTH_LONG).show()
            openActivation()
            shutdownCompletely()
        }
        return false
    }

    private fun openActivation() {
        val intent = Intent(this, ActivationActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun togglePanel() {
        if (panelView != null) hidePanel() else showPanelSafe()
    }

    private fun showPanelSafe() {
        mainHandler.post {
            try {
                showPanel()
            } catch (e: Exception) {
                Log.e(TAG, "showPanel failed", e)
                Toast.makeText(this, R.string.panel_show_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showPanel() {
        if (panelView != null) {
            panelView?.visibility = View.VISIBLE
            return
        }

        val view = themedInflater().inflate(R.layout.overlay_panel, null)
        val width = (resources.displayMetrics.widthPixels * 0.92f).toInt().coerceAtLeast(280)
        val params = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        panelInput = view.findViewById(R.id.inputMessages)
        panelCount = view.findViewById(R.id.textCount)
        panelMpm = view.findViewById(R.id.textMpmValue)
        panelSeek = view.findViewById(R.id.seekMpm)
        panelStatus = view.findViewById(R.id.textStatus)
        panelSuccess = view.findViewById(R.id.textSuccess)
        panelSend = view.findViewById(R.id.btnSend)

        view.findViewById<TextView>(R.id.btnClosePanel).setOnClickListener { hidePanel() }

        panelSeek?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                panelMpm?.text = mpmValue().toString()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        view.findViewById<Button>(R.id.chip10).setOnClickListener { setMpm(10) }
        view.findViewById<Button>(R.id.chip60).setOnClickListener { setMpm(60) }
        view.findViewById<Button>(R.id.chip300).setOnClickListener { setMpm(300) }
        view.findViewById<Button>(R.id.chip1000).setOnClickListener { setMpm(1000) }
        setMpm(DEFAULT_MPM)

        panelSend?.setOnClickListener { startSending() }
        panelInput?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                updateMessageCountPreview()
            }
        })
        updateMessageCountPreview()

        view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                // keep panel open; user closes with X
                true
            } else {
                false
            }
        }

        windowManager.addView(view, params)
        panelView = view

        // Bring panel above bubble visually by re-adding bubble after if needed.
        bubbleView?.bringToFront()

        val current = BulkSession.ui.value
        renderPanelState(current.state, current.sent, current.total, current.successMessage)
        panelInput?.requestFocus()
    }

    private fun parseMessages(raw: String): List<String> {
        var messages = MessageParser.parse(raw, MessageParser.Mode.PARAGRAPH)
        if (messages.isEmpty()) {
            messages = MessageParser.parse(raw, MessageParser.Mode.LINES)
        }
        return messages
    }

    private fun updateMessageCountPreview() {
        val count = parseMessages(panelInput?.text?.toString().orEmpty()).size
        panelCount?.visibility = View.VISIBLE
        panelCount?.text = getString(R.string.message_count, count)
    }

    private fun hidePanel() {
        panelView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        panelView = null
        panelInput = null
        panelCount = null
        panelMpm = null
        panelSeek = null
        panelStatus = null
        panelSuccess = null
        panelSend = null
    }

    private fun removeBubble() {
        bubbleView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        bubbleView = null
        bubbleParams = null
    }

    private fun mpmValue(): Int =
        ((panelSeek?.progress ?: (DEFAULT_MPM - 1)) + 1).coerceIn(1, BulkSession.MAX_MPM)

    private fun setMpm(value: Int) {
        panelSeek?.progress = (value - 1).coerceIn(0, BulkSession.MAX_MPM - 1)
        panelMpm?.text = value.toString()
    }

    private fun startSending() {
        if (!BulkAccessibilityService.isEnabled() && !isAccessibilitySettingEnabled()) {
            Toast.makeText(this, R.string.accessibility_required, Toast.LENGTH_LONG).show()
            return
        }
        val messages = parseMessages(panelInput?.text?.toString().orEmpty())
        updateMessageCountPreview()
        if (messages.isEmpty()) {
            Toast.makeText(this, R.string.must_paste, Toast.LENGTH_SHORT).show()
            return
        }
        panelSend?.isEnabled = false
        scope.launch {
            val allowed = runCatching { verifyLicenseOrShutdown() }.getOrDefault(false)
            withContext(Dispatchers.Main) {
                panelSend?.isEnabled = true
                if (allowed) beginSend(messages)
            }
        }
    }

    private fun beginSend(messages: List<String>) {
        BulkSession.clearSuccess()
        panelCount?.visibility = View.VISIBLE
        panelCount?.text = getString(R.string.message_count, messages.size)
        panelSuccess?.visibility = View.GONE
        panelSend?.text = getString(R.string.send)
        BulkSession.prepare(messages, mpmValue())
        BulkAccessibilityService.instance?.startLoop()
        hidePanel()
        val openedChooser = openWhatsAppIfNeeded()
        if (!openedChooser) {
            Toast.makeText(this, R.string.must_open_chat, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * @return true when a WhatsApp picker/app was shown.
     * If the user is already inside WhatsApp or dual WhatsApp, do nothing.
     */
    private fun openWhatsAppIfNeeded(): Boolean {
        val service = BulkAccessibilityService.instance
        if (service != null && WhatsAppWindows.isAnyWhatsAppVisible(service)) {
            return false
        }

        val apps = WhatsAppApps.installed(this)
        if (apps.isEmpty()) {
            Toast.makeText(this, R.string.whatsapp_not_found, Toast.LENGTH_LONG).show()
            return false
        }

        // Always offer selection when not already inside WhatsApp.
        val primary = apps.first().launchIntent
        val chooser = Intent.createChooser(primary, getString(R.string.choose_whatsapp)).apply {
            if (apps.size > 1) {
                putExtra(
                    Intent.EXTRA_INITIAL_INTENTS,
                    apps.drop(1).map { it.launchIntent }.toTypedArray(),
                )
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(chooser)
        if (DualAppSupport.shouldAvoidAutoLaunch(this)) {
            Toast.makeText(this, DualAppSupport.guidanceMessage(this), Toast.LENGTH_LONG).show()
        }
        return true
    }

    private fun renderPanelState(state: BulkState, sent: Int, total: Int, success: String?) {
        val status = when (state) {
            BulkState.IDLE -> getString(R.string.status_idle)
            BulkState.WAITING_CHAT -> getString(R.string.status_waiting_chat)
            BulkState.SENDING -> getString(R.string.status_sending) + if (total > 0) " ($sent/$total)" else ""
            BulkState.DONE -> getString(R.string.status_done)
            BulkState.STOPPED -> getString(R.string.status_stopped)
        }
        panelStatus?.text = status

        if (!success.isNullOrBlank() || state == BulkState.DONE) {
            panelSuccess?.visibility = View.VISIBLE
            panelSuccess?.text = success ?: getString(R.string.success_all_sent, total)
            panelSend?.text = getString(R.string.send_again)
        } else {
            if (state != BulkState.SENDING && state != BulkState.WAITING_CHAT) {
                panelSuccess?.visibility = View.GONE
            }
            panelSend?.text = getString(R.string.send)
        }

        panelSend?.isEnabled = state != BulkState.SENDING && state != BulkState.WAITING_CHAT
    }

    private fun isAccessibilitySettingEnabled(): Boolean {
        return try {
            val enabled = android.provider.Settings.Secure.getString(
                contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val expected = "$packageName/${BulkAccessibilityService::class.java.canonicalName}"
            enabled.split(':').any { it.equals(expected, ignoreCase = true) }
        } catch (_: Exception) {
            false
        }
    }

    private fun buildNotification(): Notification {
        ensureChannel()
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, HomeActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val showPanel = PendingIntent.getService(
            this,
            2,
            Intent(this, FloatingBubbleService::class.java).setAction(ACTION_SHOW_PANEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, FloatingBubbleService::class.java).setAction(ACTION_STOP_SERVICE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_send)
            .setContentTitle(getString(R.string.bubble_notification_title))
            .setContentText(getString(R.string.bubble_notification_text))
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(0, getString(R.string.open_form), showPanel)
            .addAction(0, getString(R.string.close_bubble), stop)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
    }

    companion object {
        private const val TAG = "FloatingBubble"
        private const val CHANNEL_ID = "bulk_bubble"
        private const val NOTIFICATION_ID = 4202
        const val ACTION_STOP_SERVICE = "com.whatsappbot.bulk.STOP_BUBBLE"
        const val ACTION_SHOW_PANEL = "com.whatsappbot.bulk.SHOW_PANEL"
        private const val DEFAULT_MPM = 100

        fun start(context: Context) {
            val intent = Intent(context, FloatingBubbleService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingBubbleService::class.java))
        }
    }
}
