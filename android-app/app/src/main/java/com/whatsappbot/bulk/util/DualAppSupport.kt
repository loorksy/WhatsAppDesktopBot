package com.whatsappbot.bulk.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.whatsappbot.bulk.R

/**
 * Helpers for OEM dual-app environments (Samsung Dual Messenger, Xiaomi/Redmi Dual apps).
 * Those clones usually share the same package name and cannot be launched via a normal Intent
 * without system privileges, so the user must open the dual WhatsApp manually.
 */
object DualAppSupport {
    fun manufacturer(): String = Build.MANUFACTURER.orEmpty().lowercase()

    fun brand(): String = Build.BRAND.orEmpty().lowercase()

    fun isSamsungDevice(): Boolean {
        val m = manufacturer()
        val b = brand()
        return m.contains("samsung") || b.contains("samsung")
    }

    fun isXiaomiFamilyDevice(): Boolean {
        val tokens = listOf(manufacturer(), brand(), Build.MODEL.orEmpty().lowercase())
        return tokens.any { value ->
            value.contains("xiaomi") ||
                value.contains("redmi") ||
                value.contains("poco") ||
                value.contains("blackshark")
        }
    }

    fun hasSamsungDualMessenger(context: Context): Boolean {
        if (!isSamsungDevice()) return false
        return packageInstalled(context, "com.samsung.android.da.da") ||
            packageInstalled(context, "com.samsung.android.da.daadaptor") ||
            activityExists(context, "com.samsung.android.da.da.action.DUAL_MESSENGER")
    }

    fun hasXiaomiDualApps(context: Context): Boolean {
        if (!isXiaomiFamilyDevice()) return false
        return packageInstalled(context, "com.miui.securitycore") ||
            packageInstalled(context, "com.miui.securityspace") ||
            packageInstalled(context, "com.xiaomi.mireco") ||
            settingsHintEnabled(context)
    }

    /** True when we should avoid forcing the primary WhatsApp to the foreground. */
    fun shouldAvoidAutoLaunch(context: Context): Boolean {
        return isSamsungDevice() ||
            isXiaomiFamilyDevice() ||
            hasSamsungDualMessenger(context) ||
            hasXiaomiDualApps(context)
    }

    fun guidanceMessage(context: Context): String {
        return when {
            isSamsungDevice() || hasSamsungDualMessenger(context) ->
                context.getString(R.string.dual_app_guide_samsung)
            isXiaomiFamilyDevice() || hasXiaomiDualApps(context) ->
                context.getString(R.string.dual_app_guide_xiaomi)
            else ->
                context.getString(R.string.must_open_chat)
        }
    }

    private fun packageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun activityExists(context: Context, action: String): Boolean {
        return try {
            val intent = android.content.Intent(action)
            context.packageManager.resolveActivity(intent, 0) != null
        } catch (_: Exception) {
            false
        }
    }

    private fun settingsHintEnabled(context: Context): Boolean {
        return try {
            val cr = context.contentResolver
            // Some MIUI builds expose dual-app related secure settings.
            android.provider.Settings.Secure.getInt(cr, "dual_app", 0) == 1 ||
                android.provider.Settings.Secure.getInt(cr, "xspace_enabled", 0) == 1
        } catch (_: Exception) {
            false
        }
    }
}
