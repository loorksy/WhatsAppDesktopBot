package com.whatsappbot.bulk.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.whatsappbot.bulk.R
import com.whatsappbot.bulk.data.ApiClient
import com.whatsappbot.bulk.data.ChatItem
import com.whatsappbot.bulk.data.Prefs
import com.whatsappbot.bulk.databinding.ActivityChatListBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatListActivity : AppCompatActivity() {
    private lateinit var binding: ActivityChatListBinding
    private lateinit var prefs: Prefs
    private lateinit var api: ApiClient
    private lateinit var adapter: ChatAdapter
    private var allChats: List<ChatItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = Prefs(this)
        api = ApiClient(prefs)

        if (!prefs.isLoggedIn()) {
            goLogin()
            return
        }

        binding.toolbar.inflateMenu(R.menu.menu_chat_list)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_settings -> {
                    startActivity(Intent(this, ServerSettingsActivity::class.java))
                    true
                }
                R.id.action_logout -> {
                    prefs.clearSession()
                    goLogin()
                    true
                }
                else -> false
            }
        }

        adapter = ChatAdapter { chat ->
            val intent = Intent(this, BulkSendActivity::class.java).apply {
                putExtra(BulkSendActivity.EXTRA_CHAT_ID, chat.id)
                putExtra(BulkSendActivity.EXTRA_CHAT_NAME, chat.name)
                putExtra(BulkSendActivity.EXTRA_IS_GROUP, chat.isGroup)
            }
            startActivity(intent)
        }
        binding.recyclerChats.layoutManager = LinearLayoutManager(this)
        binding.recyclerChats.adapter = adapter

        binding.btnRefresh.setOnClickListener { loadChats() }
        binding.swipeRefresh.setOnRefreshListener { loadChats() }
        binding.inputSearch.doAfterTextChanged { filterChats(it?.toString().orEmpty()) }

        loadChats()
    }

    override fun onResume() {
        super.onResume()
        if (::api.isInitialized && prefs.isLoggedIn()) {
            // keep list fresh when returning from send screen
        }
    }

    private fun loadChats() {
        binding.swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { api.fetchChats() }
            binding.swipeRefresh.isRefreshing = false
            result.onSuccess { chats ->
                allChats = chats
                filterChats(binding.inputSearch.text?.toString().orEmpty())
            }.onFailure { err ->
                allChats = emptyList()
                filterChats("")
                val msg = when (err.message) {
                    "WA_NOT_READY" -> getString(R.string.wa_not_ready)
                    "UNAUTHORIZED" -> {
                        prefs.clearSession()
                        goLogin()
                        return@onFailure
                    }
                    else -> getString(R.string.network_error)
                }
                Toast.makeText(this@ChatListActivity, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun filterChats(query: String) {
        val q = query.trim().lowercase()
        val filtered = if (q.isEmpty()) {
            allChats
        } else {
            allChats.filter {
                it.name.lowercase().contains(q) || it.id.lowercase().contains(q)
            }
        }
        adapter.submit(filtered)
        binding.textEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun goLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
