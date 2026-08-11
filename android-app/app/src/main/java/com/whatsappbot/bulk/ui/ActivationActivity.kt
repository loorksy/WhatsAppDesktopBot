package com.whatsappbot.bulk.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.whatsappbot.bulk.R
import com.whatsappbot.bulk.data.LicenseClient
import com.whatsappbot.bulk.data.LicensePrefs
import com.whatsappbot.bulk.databinding.ActivityActivationBinding
import com.whatsappbot.bulk.util.AppUpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ActivationActivity : AppCompatActivity() {
    private lateinit var binding: ActivityActivationBinding
    private lateinit var prefs: LicensePrefs
    private lateinit var updateManager: AppUpdateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityActivationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = LicensePrefs(this)
        updateManager = AppUpdateManager(this)
        binding.inputCode.setText(prefs.licenseCode)

        if (prefs.isActivated() && !intent.getBooleanExtra(EXTRA_FORCE, false)) {
            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) { LicenseClient(prefs).checkStatus() }
                if (result.active) {
                    goHome()
                }
            }
        }

        binding.btnActivate.setOnClickListener { activate() }
        updateManager.check(showDialogIfAvailable = true, notifyIfAvailable = true)
    }

    override fun onDestroy() {
        if (::updateManager.isInitialized) {
            updateManager.unregister()
        }
        super.onDestroy()
    }

    private fun activate() {
        val code = binding.inputCode.text?.toString()?.trim().orEmpty()
        if (code.isBlank()) {
            showError(getString(R.string.activation_required))
            return
        }
        binding.btnActivate.isEnabled = false
        binding.textError.visibility = View.GONE

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                LicenseClient(prefs).activate(code)
            }
            binding.btnActivate.isEnabled = true
            if (result.active) {
                prefs.licenseCode = code
                Toast.makeText(this@ActivationActivity, R.string.activation_success, Toast.LENGTH_SHORT).show()
                goHome()
            } else {
                val msg = when (result.error) {
                    "INVALID_CODE" -> getString(R.string.activation_invalid)
                    "DISABLED" -> getString(R.string.activation_disabled)
                    "DEVICE_MISMATCH" -> getString(R.string.activation_device)
                    "NETWORK" -> getString(R.string.activation_network)
                    else -> getString(R.string.activation_invalid)
                }
                showError(msg)
            }
        }
    }

    private fun goHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }

    private fun showError(msg: String) {
        binding.textError.text = msg
        binding.textError.visibility = View.VISIBLE
    }

    companion object {
        const val EXTRA_FORCE = "force"
    }
}
