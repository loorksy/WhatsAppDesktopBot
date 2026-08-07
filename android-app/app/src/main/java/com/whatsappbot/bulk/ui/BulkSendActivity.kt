package com.whatsappbot.bulk.ui

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.whatsappbot.bulk.R
import com.whatsappbot.bulk.data.ApiClient
import com.whatsappbot.bulk.data.Prefs
import com.whatsappbot.bulk.databinding.ActivityBulkSendBinding
import com.whatsappbot.bulk.util.MessageParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BulkSendActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBulkSendBinding
    private lateinit var prefs: Prefs
    private lateinit var api: ApiClient
    private var chatId: String = ""
    private var chatName: String = ""
    private var isGroup: Boolean = true
    private var pollJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBulkSendBinding.inflate(layoutInflater)
        setContentView(binding.root)

        chatId = intent.getStringExtra(EXTRA_CHAT_ID).orEmpty()
        chatName = intent.getStringExtra(EXTRA_CHAT_NAME).orEmpty()
        isGroup = intent.getBooleanExtra(EXTRA_IS_GROUP, true)

        if (chatId.isBlank()) {
            Toast.makeText(this, R.string.must_enter_chat, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        prefs = Prefs(this)
        api = ApiClient(prefs)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.title = getString(R.string.bulk_title)
        binding.textChatName.text = chatName.ifBlank { chatId }
        binding.avatar.text = chatName.take(1).ifBlank { "م" }
        binding.avatar.setBackgroundResource(
            if (isGroup) R.drawable.bg_avatar_group else R.drawable.bg_avatar_dm
        )

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
        binding.chip60.setOnClickListener { setMpm(60) }
        binding.chip300.setOnClickListener { setMpm(300) }
        binding.chip1000.setOnClickListener { setMpm(1000) }
        setMpm(10)

        binding.btnAnalyze.setOnClickListener { analyze() }
        binding.btnSend.setOnClickListener { startSend() }
        binding.btnPause.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) { api.pauseBulk() }
        }
        binding.btnResume.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) { api.resumeBulk() }
        }
        binding.btnStop.setOnClickListener {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { api.stopBulk() }
                stopPolling()
                binding.textProgress.visibility = View.GONE
            }
        }

        startPolling()
    }

    override fun onDestroy() {
        stopPolling()
        super.onDestroy()
    }

    private fun mpmValue(): Int = (binding.seekMpm.progress + 1).coerceIn(1, 10000)

    private fun setMpm(value: Int) {
        binding.seekMpm.progress = (value - 1).coerceIn(0, 9999)
        binding.textMpmValue.text = value.toString()
        binding.chip10.isChecked = value == 10
        binding.chip60.isChecked = value == 60
        binding.chip300.isChecked = value == 300
        binding.chip1000.isChecked = value == 1000
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

    private fun startSend() {
        if (chatId.isBlank()) {
            Toast.makeText(this, R.string.must_enter_chat, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val messages = analyze()
        if (messages.isEmpty()) {
            Toast.makeText(this, R.string.must_paste, Toast.LENGTH_SHORT).show()
            return
        }
        val rpm = mpmValue()
        binding.btnSend.isEnabled = false
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { api.startBulk(chatId, messages, rpm) }
            binding.btnSend.isEnabled = true
            result.onSuccess {
                Toast.makeText(this@BulkSendActivity, R.string.send_started, Toast.LENGTH_SHORT).show()
                binding.textProgress.visibility = View.VISIBLE
                binding.textProgress.text = getString(R.string.progress_fmt, 0, messages.size)
                startPolling()
            }.onFailure { err ->
                val msg = when (err.message) {
                    "WA_NOT_READY" -> getString(R.string.wa_not_ready)
                    else -> getString(R.string.network_error)
                }
                Toast.makeText(this@BulkSendActivity, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = lifecycleScope.launch {
            while (isActive) {
                val status = withContext(Dispatchers.IO) { api.bulkStatus().getOrNull() }
                if (status != null && status.state == "running") {
                    binding.textProgress.visibility = View.VISIBLE
                    binding.textProgress.text = getString(R.string.progress_fmt, status.sent, status.total)
                } else if (status != null && status.state == "idle" && status.total == 0 && status.sent == 0) {
                    // idle
                }
                delay(1500)
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    companion object {
        const val EXTRA_CHAT_ID = "chat_id"
        const val EXTRA_CHAT_NAME = "chat_name"
        const val EXTRA_IS_GROUP = "is_group"
    }
}
