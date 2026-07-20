package com.whatsappdesktopbot.local.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whatsappdesktopbot.local.ui.viewmodel.BotViewModel

@Composable
fun ForwardingScreen(viewModel: BotViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val status by viewModel.runtimeStatus.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("إعادة التوجيه", style = MaterialTheme.typography.headlineSmall)
        RowSwitch("تفعيل التوجيه", settings.forwardEnabled) { checked ->
            viewModel.saveSettings(settings.copy(forwardEnabled = checked))
        }
        OutlinedTextField(
            value = settings.forwardTargetChatId,
            onValueChange = { viewModel.saveSettings(settings.copy(forwardTargetChatId = it)) },
            label = { Text("معرف الهدف") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = settings.forwardBatchSize.toString(),
            onValueChange = { v -> v.toIntOrNull()?.let { viewModel.saveSettings(settings.copy(forwardBatchSize = it)) } },
            label = { Text("Batch Size") },
            modifier = Modifier.fillMaxWidth(),
        )
        RowSwitch("Flush on idle", settings.forwardFlushOnIdle) { checked ->
            viewModel.saveSettings(settings.copy(forwardFlushOnIdle = checked))
        }
        Text("طول الطابور: ${status.forwardQueueLength}", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun RowSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
