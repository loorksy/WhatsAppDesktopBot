package com.whatsappbot.admin.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.whatsappbot.admin.R
import com.whatsappbot.admin.data.AdminApi
import com.whatsappbot.admin.data.AdminPrefs
import com.whatsappbot.admin.databinding.ActivityLoginBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var prefs: AdminPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = AdminPrefs(this)
        binding.inputEmail.setText(prefs.email)

        if (prefs.isLoggedIn()) {
            startActivity(Intent(this, LicensesActivity::class.java))
            finish()
            return
        }

        binding.btnLogin.setOnClickListener { doLogin() }
    }

    private fun doLogin() {
        val email = binding.inputEmail.text?.toString()?.trim().orEmpty()
        val password = binding.inputPassword.text?.toString().orEmpty()
        if (email.isBlank() || password.isBlank()) {
            showError(getString(R.string.login_failed))
            return
        }
        prefs.email = email
        binding.btnLogin.isEnabled = false
        binding.textError.visibility = View.GONE
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { AdminApi(prefs).login(email, password) }.getOrDefault(false)
            }
            binding.btnLogin.isEnabled = true
            if (ok && prefs.cookie.isNotBlank()) {
                startActivity(Intent(this@LoginActivity, LicensesActivity::class.java))
                finish()
            } else if (ok) {
                Toast.makeText(this@LoginActivity, R.string.network_error, Toast.LENGTH_LONG).show()
            } else {
                showError(getString(R.string.login_failed))
            }
        }
    }

    private fun showError(msg: String) {
        binding.textError.text = msg
        binding.textError.visibility = View.VISIBLE
    }
}
