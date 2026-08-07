package com.whatsappbot.bulk.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.whatsappbot.bulk.R
import com.whatsappbot.bulk.data.LicenseClient
import com.whatsappbot.bulk.data.LicensePrefs
import com.whatsappbot.bulk.databinding.ActivityHomeBinding
import com.whatsappbot.bulk.service.BulkAccessibilityService
import com.whatsappbot.bulk.service.FloatingBubbleService
import com.whatsappbot.bulk.util.ShortcutHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHomeBinding
    private lateinit var prefs: LicensePrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = LicensePrefs(this)
        if (!prefs.isActivated()) {
            goActivation(force = true)
            return
        }

        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.btnOverlay.setOnClickListener { requestOverlayPermission() }
        binding.btnStartBubble.setOnClickListener { startBubbleAndMinimize() }
        binding.btnAddShortcut.setOnClickListener {
            ShortcutHelper.requestPinShortcut(this)
        }
        binding.btnLogoutLicense.setOnClickListener {
            prefs.clearActivation()
            FloatingBubbleService.stop(this)
            goActivation(force = true)
        }

        requestNotificationPermission()
        ShortcutHelper.ensureDynamicShortcut(this)
        verifyLicense()
    }

    override fun onResume() {
        super.onResume()
        if (!::binding.isInitialized) return
        if (!prefs.isActivated()) {
            goActivation(force = true)
            return
        }
        refreshStatus()
        verifyLicense()
    }

    private fun verifyLicense() {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { LicenseClient(prefs).checkStatus() }
            if (!result.active) {
                if (result.error == "DISABLED" || result.error == "INVALID_CODE" || result.error == "DEVICE_MISMATCH") {
                    prefs.clearActivation()
                    val msg = when (result.error) {
                        "DISABLED" -> getString(R.string.activation_disabled)
                        "DEVICE_MISMATCH" -> getString(R.string.activation_device)
                        else -> getString(R.string.activation_invalid)
                    }
                    Toast.makeText(this@HomeActivity, msg, Toast.LENGTH_LONG).show()
                    goActivation(force = true)
                }
            }
        }
    }

    private fun refreshStatus() {
        val accessOn = isAccessibilityEnabled()
        binding.textAccessStatus.text = if (accessOn) {
            getString(R.string.accessibility_on)
        } else {
            getString(R.string.accessibility_off)
        }
        binding.btnAccessibility.visibility = if (accessOn) View.GONE else View.VISIBLE

        val overlayOn = Settings.canDrawOverlays(this)
        binding.textOverlayStatus.text = if (overlayOn) {
            getString(R.string.overlay_on)
        } else {
            getString(R.string.overlay_off)
        }
        binding.btnOverlay.visibility = if (overlayOn) View.GONE else View.VISIBLE
    }

    private fun startBubbleAndMinimize() {
        if (!prefs.isActivated()) {
            Toast.makeText(this, R.string.activation_required, Toast.LENGTH_LONG).show()
            goActivation(force = true)
            return
        }
        if (!isAccessibilityEnabled()) {
            Toast.makeText(this, R.string.accessibility_required, Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.overlay_required, Toast.LENGTH_LONG).show()
            requestOverlayPermission()
            return
        }

        binding.btnStartBubble.isEnabled = false
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { LicenseClient(prefs).checkStatus() }
            binding.btnStartBubble.isEnabled = true
            if (!result.active) {
                if (result.error == "NETWORK") {
                    Toast.makeText(this@HomeActivity, R.string.activation_network, Toast.LENGTH_LONG).show()
                    return@launch
                }
                prefs.clearActivation()
                val msg = when (result.error) {
                    "DISABLED" -> getString(R.string.activation_disabled)
                    "DEVICE_MISMATCH" -> getString(R.string.activation_device)
                    else -> getString(R.string.activation_invalid)
                }
                Toast.makeText(this@HomeActivity, msg, Toast.LENGTH_LONG).show()
                goActivation(force = true)
                return@launch
            }
            FloatingBubbleService.start(this@HomeActivity)
            ShortcutHelper.ensureDynamicShortcut(this@HomeActivity)
            Toast.makeText(this@HomeActivity, R.string.bubble_started, Toast.LENGTH_SHORT).show()
            moveTaskToBack(true)
        }
    }

    private fun requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) return
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName"),
        )
        startActivity(intent)
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

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) return
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            1001,
        )
    }

    private fun goActivation(force: Boolean) {
        startActivity(
            Intent(this, ActivationActivity::class.java).putExtra(ActivationActivity.EXTRA_FORCE, force)
        )
        finish()
    }
}
