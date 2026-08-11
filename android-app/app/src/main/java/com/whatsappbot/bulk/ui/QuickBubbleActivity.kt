package com.whatsappbot.bulk.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.whatsappbot.bulk.R
import com.whatsappbot.bulk.data.LicenseClient
import com.whatsappbot.bulk.data.LicensePrefs
import com.whatsappbot.bulk.service.BulkAccessibilityService
import com.whatsappbot.bulk.service.FloatingBubbleService
import com.whatsappbot.bulk.util.ShortcutHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Transparent launcher used by the home-screen shortcut.
 * Starts the floating bubble immediately when license + permissions are ready.
 */
class QuickBubbleActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = LicensePrefs(this)

        if (!prefs.isActivated()) {
            startActivity(
                Intent(this, ActivationActivity::class.java)
                    .putExtra(ActivationActivity.EXTRA_FORCE, true)
            )
            finish()
            return
        }

        if (!isAccessibilityEnabled()) {
            Toast.makeText(this, R.string.accessibility_required, Toast.LENGTH_LONG).show()
            startActivity(Intent(this, HomeActivity::class.java))
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            finish()
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.overlay_required, Toast.LENGTH_LONG).show()
            startActivity(Intent(this, HomeActivity::class.java))
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"),
                )
            )
            finish()
            return
        }

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { LicenseClient(prefs).checkStatus() }
            if (!result.active) {
                if (result.error == "NETWORK") {
                    // Allow offline quick-start if already activated locally.
                    startBubbleAndExit()
                    return@launch
                }
                prefs.clearActivation()
                val msg = when (result.error) {
                    "DISABLED" -> getString(R.string.activation_disabled)
                    "DEVICE_MISMATCH" -> getString(R.string.activation_device)
                    else -> getString(R.string.activation_invalid)
                }
                Toast.makeText(this@QuickBubbleActivity, msg, Toast.LENGTH_LONG).show()
                startActivity(
                    Intent(this@QuickBubbleActivity, ActivationActivity::class.java)
                        .putExtra(ActivationActivity.EXTRA_FORCE, true)
                )
                finish()
                return@launch
            }
            startBubbleAndExit()
        }
    }

    private fun startBubbleAndExit() {
        FloatingBubbleService.start(this)
        ShortcutHelper.ensureDynamicShortcut(this)
        Toast.makeText(this, R.string.bubble_started, Toast.LENGTH_SHORT).show()
        moveTaskToBack(true)
        finish()
    }

    private fun isAccessibilityEnabled(): Boolean {
        if (BulkAccessibilityService.isEnabled()) return true
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        if (!am.isEnabled) return false
        val expected = "$packageName/${BulkAccessibilityService::class.java.canonicalName}"
        return try {
            val enabled = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabled)
            while (splitter.hasNext()) {
                if (splitter.next().equals(expected, ignoreCase = true)) return true
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        const val ACTION_QUICK_BUBBLE = "com.whatsappbot.bulk.action.QUICK_BUBBLE"
    }
}
