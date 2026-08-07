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
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.android.material.button.MaterialButton
import com.whatsappbot.bulk.R
import com.whatsappbot.bulk.ui.HomeActivity
import com.whatsappbot.bulk.util.BulkSession
import com.whatsappbot.bulk.util.BulkState
import com.whatsappbot.bulk.util.MessageParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FloatingBubbleService : Service() {
    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var panelView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var panelParams: WindowManager.LayoutParams? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectJob: Job? = null

    private var panelInput: EditText? = null
    private var panelCount: TextView? = null
    private var panelMpm: TextView? = null
    private var panelSeek: SeekBar? = null
    private var panelStatus: TextView? = null
    private var panelSuccess: TextView? = null
    private var panelSend: MaterialButton? = null

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
                    showPanel()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                BulkSession.stop()
                BulkAccessibilityService.instance?.stopLoop()
                stopSelf()
            }
            ACTION_SHOW_PANEL -> showPanel()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        collectJob?.cancel()
        scope.cancel()
        removeBubble()
        removePanel()
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
        val view = LayoutInflater.from(this).inflate(R.layout.overlay_bubble, null)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 220
        }

        var downX = 0f
        var downY = 0f
        var paramX = 0
        var paramY = 0
        var moved = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
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
                    if (kotlin.math.abs(dx) > 8 || kotlin.math.abs(dy) > 8) moved = true
                    params.x = paramX + dx
                    params.y = paramY + dy
                    windowManager.updateViewLayout(view, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        if (panelView == null) showPanel() else hidePanel()
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(view, params)
        bubbleView = view
        bubbleParams = params
    }

    private fun showPanel() {
        if (panelView != null) {
            panelView?.visibility = View.VISIBLE
            return
        }
        val view = LayoutInflater.from(this).inflate(R.layout.overlay_panel, null)
        val width = (resources.displayMetrics.widthPixels * 0.92f).toInt()
        val params = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        panelInput = view.findViewById(R.id.inputMessages)
        panelCount = view.findViewById(R.id.textCount)
        panelMpm = view.findViewById(R.id.textMpmValue)
        panelSeek = view.findViewById(R.id.seekMpm)
        panelStatus = view.findViewById(R.id.textStatus)
        panelSuccess = view.findViewById(R.id.textSuccess)
        panelSend = view.findViewById(R.id.btnSend)

        view.findViewById<ImageButton>(R.id.btnClosePanel).setOnClickListener { hidePanel() }

        panelSeek?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                panelMpm?.text = mpmValue().toString()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        view.findViewById<MaterialButton>(R.id.chip10).setOnClickListener { setMpm(10) }
        view.findViewById<MaterialButton>(R.id.chip60).setOnClickListener { setMpm(60) }
        view.findViewById<MaterialButton>(R.id.chip300).setOnClickListener { setMpm(300) }
        view.findViewById<MaterialButton>(R.id.chip1000).setOnClickListener { setMpm(1000) }
        setMpm(10)

        panelSend?.setOnClickListener { startSending() }

        windowManager.addView(view, params)
        panelView = view
        panelParams = params

        val current = BulkSession.ui.value
        renderPanelState(current.state, current.sent, current.total, current.successMessage)
    }

    private fun hidePanel() {
        panelView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        panelView = null
        panelParams = null
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

    private fun removePanel() = hidePanel()

    private fun mpmValue(): Int = ((panelSeek?.progress ?: 9) + 1).coerceIn(1, BulkSession.MAX_MPM)

    private fun setMpm(value: Int) {
        panelSeek?.progress = (value - 1).coerceIn(0, BulkSession.MAX_MPM - 1)
        panelMpm?.text = value.toString()
    }

    private fun startSending() {
        if (!BulkAccessibilityService.isEnabled() && !isAccessibilitySettingEnabled()) {
            Toast.makeText(this, R.string.accessibility_required, Toast.LENGTH_LONG).show()
            return
        }
        val raw = panelInput?.text?.toString().orEmpty()
        val messages = MessageParser.parse(raw, MessageParser.Mode.PARAGRAPH)
        if (messages.isEmpty()) {
            // also try line mode if single block empty paragraphs
            val lines = MessageParser.parse(raw, MessageParser.Mode.LINES)
            if (lines.isEmpty()) {
                Toast.makeText(this, R.string.must_paste, Toast.LENGTH_SHORT).show()
                return
            }
            beginSend(lines)
            return
        }
        beginSend(messages)
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
        openWhatsApp()
        Toast.makeText(this, R.string.must_open_chat, Toast.LENGTH_LONG).show()
    }

    private fun openWhatsApp() {
        val launch = packageManager.getLaunchIntentForPackage("com.whatsapp")
            ?: packageManager.getLaunchIntentForPackage("com.whatsapp.w4b")
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launch)
        }
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

        if (!success.isNullOrBlank()) {
            panelSuccess?.visibility = View.VISIBLE
            panelSuccess?.text = success
            panelSend?.text = getString(R.string.send_again)
        } else if (state == BulkState.DONE) {
            panelSuccess?.visibility = View.VISIBLE
            panelSuccess?.text = getString(R.string.success_all_sent, total)
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
        private const val CHANNEL_ID = "bulk_bubble"
        private const val NOTIFICATION_ID = 4202
        const val ACTION_STOP_SERVICE = "com.whatsappbot.bulk.STOP_BUBBLE"
        const val ACTION_SHOW_PANEL = "com.whatsappbot.bulk.SHOW_PANEL"

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
