package com.whatsappbot.bulk.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.whatsappbot.bulk.R
import com.whatsappbot.bulk.data.ApiClient
import com.whatsappbot.bulk.data.Prefs
import com.whatsappbot.bulk.databinding.ActivityLoginBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var prefs: Prefs
    private lateinit var api: ApiClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = Prefs(this)
        api = ApiClient(prefs)

        binding.inputServer.setText(prefs.serverUrl)
        binding.inputEmail.setText(prefs.email)

        if (prefs.isLoggedIn()) {
            lifecycleScope.launch {
                val ok = withContext(Dispatchers.IO) { api.me().isSuccess }
                if (ok) {
                    openChats()
                } else {
                    prefs.clearSession()
                }
            }
        }

        binding.btnLogin.setOnClickListener { doLogin() }
    }

    private fun doLogin() {
        val server = binding.inputServer.text?.toString()?.trim().orEmpty()
        val email = binding.inputEmail.text?.toString()?.trim().orEmpty()
        val password = binding.inputPassword.text?.toString().orEmpty()

        if (server.isBlank() || email.isBlank() || password.isBlank()) {
            showError(getString(R.string.login_failed))
            return
        }

        prefs.serverUrl = server
        prefs.email = email
        binding.btnLogin.isEnabled = false
        binding.textError.visibility = View.GONE

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { api.login(email, password) }
            binding.btnLogin.isEnabled = true
            result.onSuccess {
                openChats()
            }.onFailure {
                showError(getString(R.string.login_failed))
                Toast.makeText(this@LoginActivity, R.string.network_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openChats() {
        startActivity(Intent(this, ChatListActivity::class.java))
        finish()
    }

    private fun showError(msg: String) {
        binding.textError.text = msg
        binding.textError.visibility = View.VISIBLE
    }
}
