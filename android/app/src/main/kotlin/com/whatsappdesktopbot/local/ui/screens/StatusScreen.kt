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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whatsappdesktopbot.local.domain.model.ConnectionState
import com.whatsappdesktopbot.local.ui.components.InfoCard
import com.whatsappdesktopbot.local.ui.viewmodel.BotViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatusScreen(viewModel: BotViewModel) {
    val connection by viewModel.connectionState.collectAsStateWithLifecycle()
    val status by viewModel.runtimeStatus.collectAsStateWithLifecycle()
    val bulk by viewModel.bulkState.collectAsStateWithLifecycle()
    val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("شاشة الحالة", style = MaterialTheme.typography.headlineSmall)
        InfoCard("حالة واتساب", connection.name)
        InfoCard("البوت", if (status.botRunning) "يعمل" else "متوقف")
        InfoCard("الشبكة", status.networkType)
        InfoCard("VPN", if (status.vpnActive) "فعّال" else "غير فعّال")
        InfoCard("طابور الرسائل", status.queueLength.toString())
        InfoCard("طابور التوجيه", status.forwardQueueLength.toString())
        InfoCard("محاولات إعادة الاتصال", status.reconnectAttempts.toString())
        InfoCard(
            "آخر معالجة",
            status.lastProcessedAt?.let { fmt.format(Date(it)) } ?: "—",
        )
        InfoCard("الإرسال الجماعي", "${bulk.sent}/${bulk.total} (${bulk.state})")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = viewModel::startBot,
                enabled = connection == ConnectionState.CONNECTED && !status.botRunning,
                modifier = Modifier.weight(1f),
            ) { Text("تشغيل") }
            OutlinedButton(onClick = viewModel::stopBot, modifier = Modifier.weight(1f)) { Text("إيقاف") }
        }
        OutlinedButton(onClick = viewModel::reconnect, modifier = Modifier.fillMaxWidth()) {
            Text("إعادة الاتصال")
        }
    }
}
