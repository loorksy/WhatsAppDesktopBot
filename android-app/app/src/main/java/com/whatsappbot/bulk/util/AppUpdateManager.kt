package com.whatsappbot.bulk.util

import android.app.Activity
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.whatsappbot.bulk.BuildConfig
import com.whatsappbot.bulk.R
import com.whatsappbot.bulk.data.UpdateClient
import com.whatsappbot.bulk.data.UpdateInfo
import com.whatsappbot.bulk.data.UpdatePrefs
import com.whatsappbot.bulk.ui.HomeActivity
import java.io.File

class AppUpdateManager(private val activity: Activity) {
    private val prefs = UpdatePrefs(activity)
    private val client = UpdateClient()
    private var downloadId: Long = -1L
    private var pendingInfo: UpdateInfo? = null
    private var receiverRegistered = false

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: return
            if (id != downloadId) return
            val file = findDownloadedFile(id) ?: run {
                Toast.makeText(activity, R.string.update_download_failed, Toast.LENGTH_LONG).show()
                return
            }
            installApk(file)
        }
    }

    fun check(showDialogIfAvailable: Boolean = true, notifyIfAvailable: Boolean = true) {
        Thread {
            val info = client.fetchUpdateInfo() ?: return@Thread
            if (!client.isNewer(info)) return@Thread
            activity.runOnUiThread {
                if (notifyIfAvailable && prefs.lastNotifiedVersion < info.versionCode) {
                    showUpdateNotification(info)
                    prefs.lastNotifiedVersion = info.versionCode
                }
                val shouldDialog = showDialogIfAvailable &&
                    (info.force || prefs.lastDialogVersion < info.versionCode)
                if (shouldDialog) {
                    prefs.lastDialogVersion = info.versionCode
                    showUpdateDialog(info)
                }
            }
        }.start()
    }

    fun showUpdateDialog(info: UpdateInfo) {
        pendingInfo = info
        val notes = if (info.changelog.isEmpty()) {
            activity.getString(R.string.update_no_notes)
        } else {
            info.changelog.joinToString("\n") { "• $it" }
        }
        val message = activity.getString(
            R.string.update_dialog_message,
            info.versionName.ifBlank { info.versionCode.toString() },
            notes,
        )
        val builder = AlertDialog.Builder(activity)
            .setTitle(info.title.ifBlank { activity.getString(R.string.update_available_title) })
            .setMessage(message)
            .setPositiveButton(R.string.update_now) { _, _ -> startDownload(info) }
            .setCancelable(!info.force)
        if (!info.force) {
            builder.setNegativeButton(R.string.update_later, null)
        }
        builder.show()
    }

    private fun startDownload(info: UpdateInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()
        ) {
            Toast.makeText(activity, R.string.update_allow_install, Toast.LENGTH_LONG).show()
            activity.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${activity.packageName}"),
                )
            )
            return
        }

        ensureReceiver()
        val dm = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(info.apkUrl))
            .setTitle(activity.getString(R.string.update_downloading_title))
            .setDescription(activity.getString(R.string.update_downloading_text, info.versionName))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setDestinationInExternalFilesDir(
                activity,
                Environment.DIRECTORY_DOWNLOADS,
                "bulk-sender-update.apk",
            )
            .setMimeType("application/vnd.android.package-archive")

        downloadId = dm.enqueue(request)
        Toast.makeText(activity, R.string.update_download_started, Toast.LENGTH_SHORT).show()
    }

    private fun ensureReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        ContextCompat.registerReceiver(
            activity,
            downloadReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiverRegistered = true
    }

    fun unregister() {
        if (!receiverRegistered) return
        runCatching { activity.unregisterReceiver(downloadReceiver) }
        receiverRegistered = false
    }

    private fun findDownloadedFile(id: Long): File? {
        val dm = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(id)
        val cursor: Cursor = dm.query(query) ?: return null
        cursor.use {
            if (!it.moveToFirst()) return null
            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            if (status != DownloadManager.STATUS_SUCCESSFUL) return null
            val local = it.getString(it.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                ?: return null
            val uri = Uri.parse(local)
            return when (uri.scheme) {
                "file" -> File(uri.path ?: return null)
                "content" -> {
                    // Copy to cache for FileProvider install
                    val out = File(activity.cacheDir, "bulk-sender-update.apk")
                    activity.contentResolver.openInputStream(uri)?.use { input ->
                        out.outputStream().use { output -> input.copyTo(output) }
                    } ?: return null
                    out
                }
                else -> File(activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "bulk-sender-update.apk")
            }
        }
    }

    private fun installApk(file: File) {
        if (!file.exists()) {
            Toast.makeText(activity, R.string.update_download_failed, Toast.LENGTH_LONG).show()
            return
        }
        val uri = FileProvider.getUriForFile(
            activity,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            activity.startActivity(intent)
        }.onFailure {
            Toast.makeText(activity, R.string.update_install_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun showUpdateNotification(info: UpdateInfo) {
        ensureNotificationChannel()
        val open = PendingIntent.getActivity(
            activity,
            9101,
            Intent(activity, HomeActivity::class.java).putExtra(EXTRA_SHOW_UPDATE, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (info.changelog.isNotEmpty()) {
            info.changelog.first()
        } else {
            activity.getString(R.string.update_available_text, info.versionName)
        }
        val nm = activity.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(activity, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_send)
            .setContentTitle(info.title.ifBlank { activity.getString(R.string.update_available_title) })
            .setContentText(text)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    if (info.changelog.isEmpty()) text
                    else info.changelog.joinToString("\n") { "• $it" }
                )
            )
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = activity.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                activity.getString(R.string.update_channel),
                NotificationManager.IMPORTANCE_HIGH,
            )
        )
    }

    companion object {
        const val EXTRA_SHOW_UPDATE = "show_update"
        private const val CHANNEL_ID = "app_updates"
        private const val NOTIFICATION_ID = 9102
    }
}
