package com.whatsappbot.admin.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.whatsappbot.admin.R
import com.whatsappbot.admin.data.AdminApi
import com.whatsappbot.admin.data.AdminPrefs
import com.whatsappbot.admin.data.LicenseItem
import com.whatsappbot.admin.databinding.ActivityLicensesBinding
import com.whatsappbot.admin.databinding.ItemLicenseBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LicensesActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLicensesBinding
    private lateinit var prefs: AdminPrefs
    private lateinit var api: AdminApi
    private lateinit var adapter: LicenseAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = AdminPrefs(this)
        if (!prefs.isLoggedIn()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        binding = ActivityLicensesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        api = AdminApi(prefs)

        binding.toolbar.inflateMenu(R.menu.menu_licenses)
        binding.toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.action_refresh -> {
                    load()
                    true
                }
                R.id.action_logout -> {
                    prefs.clearSession()
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                    true
                }
                else -> false
            }
        }

        adapter = LicenseAdapter(
            onCopy = { copy(it.code) },
            onToggle = { item ->
                lifecycleScope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) { api.updateLicense(item.id, active = !item.active) }
                    }.onSuccess {
                        load()
                    }.onFailure {
                        Toast.makeText(this@LicensesActivity, R.string.network_error, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onReset = { item ->
                lifecycleScope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) { api.updateLicense(item.id, resetDevice = true) }
                    }.onSuccess { load() }
                }
            },
            onDelete = { item ->
                AlertDialog.Builder(this)
                    .setMessage("حذف ${item.code}؟")
                    .setPositiveButton(R.string.delete) { _, _ ->
                        lifecycleScope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) { api.deleteLicense(item.id) }
                            }.onSuccess { load() }
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            },
        )
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
        binding.swipeRefresh.setOnRefreshListener { load() }
        binding.btnCreate.setOnClickListener { createLicense() }
        load()
    }

    private fun createLicense() {
        val note = binding.inputNote.text?.toString()?.trim().orEmpty()
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { api.createLicense(note) }
            }
            result.onSuccess { license ->
                binding.inputNote.setText("")
                Toast.makeText(this@LicensesActivity, R.string.created, Toast.LENGTH_SHORT).show()
                copy(license.code)
                load()
            }.onFailure {
                Toast.makeText(this@LicensesActivity, R.string.network_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun load() {
        binding.swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { api.listLicenses() }
            }
            binding.swipeRefresh.isRefreshing = false
            result.onSuccess { adapter.submit(it) }
                .onFailure {
                    if (it.message == "UNAUTHORIZED") {
                        prefs.clearSession()
                        startActivity(Intent(this@LicensesActivity, LoginActivity::class.java))
                        finish()
                    } else {
                        Toast.makeText(this@LicensesActivity, R.string.network_error, Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    private fun copy(code: String) {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("license", code))
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    private class LicenseAdapter(
        private val onCopy: (LicenseItem) -> Unit,
        private val onToggle: (LicenseItem) -> Unit,
        private val onReset: (LicenseItem) -> Unit,
        private val onDelete: (LicenseItem) -> Unit,
    ) : RecyclerView.Adapter<LicenseAdapter.VH>() {
        private val items = mutableListOf<LicenseItem>()
        private val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        fun submit(list: List<LicenseItem>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemLicenseBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(binding)
        }

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])
        override fun getItemCount(): Int = items.size

        inner class VH(private val binding: ItemLicenseBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(item: LicenseItem) {
                val ctx = binding.root.context
                binding.textCode.text = item.code
                val note = item.note.ifBlank { "—" }
                val device = if (item.deviceId.isNullOrBlank()) {
                    ctx.getString(R.string.no_device)
                } else {
                    ctx.getString(R.string.device_bound)
                }
                val created = if (item.createdAt > 0) df.format(Date(item.createdAt)) else "—"
                binding.textMeta.text = "$note\n$device · $created"
                binding.textStatus.text = if (item.active) ctx.getString(R.string.active) else ctx.getString(R.string.disabled)
                binding.textStatus.setTextColor(
                    if (item.active) 0xFF047857.toInt() else 0xFFF43F5E.toInt()
                )
                binding.btnToggle.text = if (item.active) ctx.getString(R.string.disable) else ctx.getString(R.string.enable)
                binding.btnCopy.setOnClickListener { onCopy(item) }
                binding.btnToggle.setOnClickListener { onToggle(item) }
                binding.btnReset.setOnClickListener { onReset(item) }
                binding.btnDelete.setOnClickListener { onDelete(item) }
            }
        }
    }
}
