package com.whatsappdesktopbot.local.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whatsappdesktopbot.local.ui.viewmodel.BotViewModel

@Composable
fun ClientsScreen(viewModel: BotViewModel) {
    val clients by viewModel.clients.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("") }
    var emoji by rememberSaveable { mutableStateOf("✅") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("العملاء", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("الاسم") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = emoji, onValueChange = { emoji = it }, label = { Text("الإيموجي") }, modifier = Modifier.fillMaxWidth())
        Button(
            onClick = {
                if (name.isNotBlank()) {
                    viewModel.addClient(name.trim(), emoji.ifBlank { "✅" })
                    name = ""
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("إضافة") }
        Text("استيراد/تصدير — المرحلة 5", style = MaterialTheme.typography.labelSmall)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(clients, key = { it.id }) { client ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${client.emoji} ${client.name}", style = MaterialTheme.typography.bodyLarge)
                    IconButton(onClick = { viewModel.deleteClient(client.id) }) { Text("✕") }
                }
            }
        }
    }
}
