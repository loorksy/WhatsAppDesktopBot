package com.whatsappdesktopbot.local.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whatsappdesktopbot.local.ui.viewmodel.BotViewModel

@Composable
fun BulkSendScreen(viewModel: BotViewModel) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val bulk by viewModel.bulkState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var messages by rememberSaveable { mutableStateOf("") }
    var groupIndex by rememberSaveable { mutableStateOf(0) }
    var mpm by rememberSaveable { mutableFloatStateOf(settings.bulkMessagesPerMinute.toFloat()) }
    val perSec = (mpm / 60f).let { if (it >= 10) it.toInt().toString() else String.format("%.1f", it) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("الإرسال الجماعي", style = MaterialTheme.typography.headlineSmall)
        val group = groups.getOrNull(groupIndex)
        Text("المجموعة: ${group?.name ?: "—"}", style = MaterialTheme.typography.bodyMedium)
        if (groups.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { groupIndex = (groupIndex - 1).coerceAtLeast(0) }) { Text("السابق") }
                OutlinedButton(onClick = { groupIndex = (groupIndex + 1).coerceAtMost(groups.lastIndex) }) { Text("التالي") }
            }
        }
        OutlinedTextField(
            value = messages,
            onValueChange = { messages = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("الرسائل (فصل بسطر فارغ)") },
            minLines = 5,
        )
        Text("سرعة الإرسال: ${mpm.toInt()} رسالة/دقيقة ≈ ~$perSec رسالة/ث")
        Slider(value = mpm, onValueChange = { mpm = it }, valueRange = 1f..10000f)
        if (bulk.total > 0) {
            LinearProgressIndicator(
                progress = { bulk.sent.toFloat() / bulk.total.toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("${bulk.sent}/${bulk.total} — ${bulk.state}")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { group?.let { viewModel.startBulk(it.id, messages, mpm.toInt()) } },
                modifier = Modifier.weight(1f),
            ) { Text("بدء") }
            OutlinedButton(onClick = viewModel::pauseBulk, modifier = Modifier.weight(1f)) { Text("إيقاف مؤقت") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = viewModel::resumeBulk, modifier = Modifier.weight(1f)) { Text("استئناف") }
            OutlinedButton(onClick = viewModel::stopBulk, modifier = Modifier.weight(1f)) { Text("إلغاء") }
        }
    }
}
