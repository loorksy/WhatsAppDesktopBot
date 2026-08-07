package com.whatsappbot.bulk.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.whatsappbot.bulk.R
import com.whatsappbot.bulk.databinding.ActivityBulkFormBinding
import com.whatsappbot.bulk.service.BulkAccessibilityService
import com.whatsappbot.bulk.service.BulkForegroundService
import com.whatsappbot.bulk.util.BulkSession
import com.whatsappbot.bulk.util.BulkState
import com.whatsappbot.bulk.util.MessageParser
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class BulkFormActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBulkFormBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBulkFormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        val modes = listOf("فقرات (سطر فارغ)", "كل 3 أسطر", "كل سطر رسالة")
        binding.spinnerParseMode.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, modes)

        binding.seekMpm.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.textMpmValue.text = mpmValue().toString()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        binding.chip10.setOnClickListener { setMpm(10) }
        binding.chip20.setOnClickListener { setMpm(20) }
        binding.chip30.setOnClickListener { setMpm(30) }
        binding.chip60.setOnClickListener { setMpm(60) }
        setMpm(10)

        binding.btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.btnOpenWhatsApp.setOnClickListener { openWhatsApp() }
        binding.btnAnalyze.setOnClickListener { analyze() }
        binding.btnSend.setOnClickListener { startSending() }
        binding.btnPause.setOnClickListener { BulkSession.pause() }
        binding.btnResume.setOnClickListener {
            BulkSession.resume()
            BulkAccessibilityService.instance?.startLoop()
        }
        binding.btnStop.setOnClickListener {
            BulkSession.stop()
            BulkAccessibilityService.instance?.stopLoop()
            BulkForegroundService.stop(this)
        }

        requestNotificationPermission()

        lifecycleScope.launch {
            BulkSession.ui.collectLatest { renderState(it.state, it.sent, it.total) }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshAccessibilityStatus()
    }

    private fun mpmValue(): Int = (binding.seekMpm.progress + 1).coerceIn(1, 120)

    private fun setMpm(value: Int) {
        binding.seekMpm.progress = (value - 1).coerceIn(0, 119)
        binding.textMpmValue.text = value.toString()
        binding.chip10.isChecked = value == 10
        binding.chip20.isChecked = value == 20
        binding.chip30.isChecked = value == 30
        binding.chip60.isChecked = value == 60
    }

    private fun currentMode(): MessageParser.Mode = when (binding.spinnerParseMode.selectedItemPosition) {
        1 -> MessageParser.Mode.FIXED3
        2 -> MessageParser.Mode.LINES
        else -> MessageParser.Mode.PARAGRAPH
    }

    private fun analyze(): List<String> {
        val messages = MessageParser.parse(binding.inputMessages.text?.toString().orEmpty(), currentMode())
        binding.textCount.visibility = View.VISIBLE
        binding.textCount.text = getString(R.string.message_count, messages.size)
        return messages
    }

    private fun startSending() {
        if (!isAccessibilityEnabled()) {
            Toast.makeText(this, R.string.accessibility_required, Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }

        val messages = analyze()
        if (messages.isEmpty()) {
            Toast.makeText(this, R.string.must_paste, Toast.LENGTH_SHORT).show()
            return
        }

        BulkSession.prepare(messages, mpmValue())
        BulkForegroundService.start(this)
        BulkAccessibilityService.instance?.startLoop()
        Toast.makeText(this, R.string.must_open_chat, Toast.LENGTH_LONG).show()
        openWhatsApp()
    }

    private fun openWhatsApp() {
        val launch = packageManager.getLaunchIntentForPackage("com.whatsapp")
            ?: packageManager.getLaunchIntentForPackage("com.whatsapp.w4b")
        if (launch != null) {
            startActivity(launch)
        } else {
            Toast.makeText(this, R.string.must_open_chat, Toast.LENGTH_LONG).show()
        }
    }

    private fun renderState(state: BulkState, sent: Int, total: Int) {
        val status = when (state) {
            BulkState.IDLE -> getString(R.string.status_idle)
            BulkState.WAITING_CHAT -> getString(R.string.status_waiting_chat)
            BulkState.SENDING -> getString(R.string.status_sending)
            BulkState.PAUSED -> getString(R.string.status_paused)
            BulkState.DONE -> getString(R.string.status_done)
            BulkState.STOPPED -> getString(R.string.status_stopped)
        }
        binding.textStatus.text = status
        if (total > 0 && state != BulkState.IDLE) {
            binding.textProgress.visibility = View.VISIBLE
            binding.textProgress.text = getString(R.string.progress_fmt, sent, total)
        } else if (state == BulkState.IDLE) {
            binding.textProgress.visibility = View.GONE
        }
    }

    private fun refreshAccessibilityStatus() {
        val enabled = isAccessibilityEnabled()
        binding.textAccessStatus.text = if (enabled) {
            getString(R.string.accessibility_on)
        } else {
            getString(R.string.accessibility_off)
        }
        binding.btnAccessibility.visibility = if (enabled) View.GONE else View.VISIBLE
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
}
