package com.whatsappbot.bulk.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.whatsappbot.bulk.R
import com.whatsappbot.bulk.data.Prefs
import com.whatsappbot.bulk.databinding.ActivityServerSettingsBinding

class ServerSettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityServerSettingsBinding
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityServerSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = Prefs(this)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.inputServer.setText(prefs.serverUrl)
        binding.btnSave.setOnClickListener {
            val url = binding.inputServer.text?.toString()?.trim().orEmpty()
            if (url.isBlank()) {
                Toast.makeText(this, R.string.network_error, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.serverUrl = url
            Toast.makeText(this, R.string.save, Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
