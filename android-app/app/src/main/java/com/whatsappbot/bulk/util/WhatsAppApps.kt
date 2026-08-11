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
            val label = friendlyLabel(pkg, info.loadLabel(pm)?.toString())
            apps.putIfAbsent(pkg, WhatsAppApp(pkg, label, intent))
        }

        // Ensure official apps are included even if query filtering missed them.
        for (pkg in WhatsAppNodes.KNOWN_PACKAGES) {
            if (apps.containsKey(pkg)) continue
            val intent = pm.getLaunchIntentForPackage(pkg) ?: continue
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val raw = runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            }.getOrNull()
            apps[pkg] = WhatsAppApp(pkg, friendlyLabel(pkg, raw), intent)
        }

        return apps.values.toList().sortedBy { sortKey(it.packageName) }
    }

    private fun friendlyLabel(packageName: String, raw: String?): String {
        val base = raw?.trim().orEmpty()
        return when {
            packageName == "com.whatsapp.w4b" ->
                if (base.isBlank() || base.equals("WhatsApp", true)) "WhatsApp Business" else base
            packageName == "com.whatsapp" ->
                if (base.isBlank()) "WhatsApp" else base
            packageName.contains("whatsapp", ignoreCase = true) ->
                base.ifBlank { packageName }
            else -> base.ifBlank { packageName }
        }
    }

    private fun sortKey(packageName: String): Int = when (packageName) {
        "com.whatsapp" -> 0
        "com.whatsapp.w4b" -> 1
        else -> 2
    }
}
