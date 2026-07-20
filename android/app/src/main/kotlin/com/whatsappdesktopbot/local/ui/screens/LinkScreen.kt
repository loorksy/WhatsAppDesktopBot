package com.whatsappdesktopbot.local.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whatsappdesktopbot.local.ui.viewmodel.BotViewModel

@Composable
fun LinkScreen(viewModel: BotViewModel) {
    val connection by viewModel.connectionState.collectAsStateWithLifecycle()
    val pairingCode by viewModel.pairingCode.collectAsStateWithLifecycle()
    var phone by rememberSaveable { mutableStateOf("9665") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("ربط واتساب", style = MaterialTheme.typography.headlineSmall)
        Text("أدخل رقم واتساب بصيغة دولية بدون +", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it.filter { ch -> ch.isDigit() } },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("رقم الهاتف") },
            singleLine = true,
        )
        Button(onClick = { viewModel.requestPairingCode(phone) }, modifier = Modifier.fillMaxWidth()) {
            Text("طلب Pairing Code")
        }
        pairingCode?.let { code ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("كود الاقتران", style = MaterialTheme.typography.titleMedium)
                    Text(code, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Text(
                        "WhatsApp → الإعدادات → الأجهزة المرتبطة → ربط جهاز برقم الهاتف",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        InfoLine("حالة الاتصال", connection.name)
        OutlinedButton(onClick = viewModel::clearSession, modifier = Modifier.fillMaxWidth()) {
            Text("حذف الجلسة")
        }
        Text("QR متاح كخيار احتياطي في المرحلة 3", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Text("$label: $value", style = MaterialTheme.typography.bodyLarge)
}
