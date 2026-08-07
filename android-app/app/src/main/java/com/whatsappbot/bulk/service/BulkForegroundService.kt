package com.whatsappbot.bulk.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.whatsappbot.bulk.R
import com.whatsappbot.bulk.ui.BulkFormActivity
import com.whatsappbot.bulk.util.BulkSession

class BulkForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                BulkSession.stop()
                BulkAccessibilityService.instance?.stopLoop()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                BulkSession.pause()
                updateNotification()
                return START_STICKY
            }
            ACTION_RESUME -> {
                BulkSession.resume()
                BulkAccessibilityService.instance?.startLoop()
                updateNotification()
                return START_STICKY
            }
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        BulkAccessibilityService.instance?.startLoop()
        return START_STICKY
    }

    private fun updateNotification() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        ensureChannel()
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, BulkFormActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, BulkForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val progress = BulkSession.ui.value
        val text = if (progress.total > 0) {
            getString(R.string.progress_fmt, progress.sent, progress.total)
        } else {
            getString(R.string.notification_text)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_send)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(0, getString(R.string.stop), stop)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        nm.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "bulk_sender"
        private const val NOTIFICATION_ID = 4201
        const val ACTION_STOP = "com.whatsappbot.bulk.STOP"
        const val ACTION_PAUSE = "com.whatsappbot.bulk.PAUSE"
        const val ACTION_RESUME = "com.whatsappbot.bulk.RESUME"

        fun start(context: Context) {
            val intent = Intent(context, BulkForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BulkForegroundService::class.java))
        }
    }
}
