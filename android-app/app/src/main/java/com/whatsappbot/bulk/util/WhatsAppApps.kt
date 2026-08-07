package com.whatsappbot.bulk.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

data class WhatsAppApp(
    val packageName: String,
    val label: String,
    val launchIntent: Intent,
)

object WhatsAppApps {
    fun installed(context: Context): List<WhatsAppApp> {
        val pm = context.packageManager
        val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(launch, PackageManager.MATCH_ALL)
        val apps = linkedMapOf<String, WhatsAppApp>()

        for (info in resolved) {
            val pkg = info.activityInfo?.packageName ?: continue
            if (!WhatsAppNodes.isSupportedPackage(pkg)) continue
            val intent = pm.getLaunchIntentForPackage(pkg) ?: continue
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val label = runCatching {
                info.loadLabel(pm)?.toString()
            }.getOrNull().orEmpty().ifBlank { pkg }
            apps.putIfAbsent(pkg, WhatsAppApp(pkg, label, intent))
        }

        // Ensure official apps are included even if query filtering missed them.
        for (pkg in WhatsAppNodes.KNOWN_PACKAGES) {
            if (apps.containsKey(pkg)) continue
            val intent = pm.getLaunchIntentForPackage(pkg) ?: continue
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val label = runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            }.getOrDefault(pkg)
            apps[pkg] = WhatsAppApp(pkg, label, intent)
        }

        return apps.values.toList().sortedBy { it.label.lowercase() }
    }
}
