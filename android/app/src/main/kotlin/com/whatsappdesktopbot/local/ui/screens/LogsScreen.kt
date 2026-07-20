package com.whatsappdesktopbot.local.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whatsappdesktopbot.local.ui.viewmodel.BotViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogsScreen(viewModel: BotViewModel) {
    val interaction by viewModel.interactionLogs.collectAsStateWithLifecycle()
    val skipped by viewModel.skippedLogs.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("تم التفاعل", "تم التجاهل", "الاتصال", "الأخطاء")
    val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

    Column(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
        TabRow(selectedTabIndex = tab) {
            tabs.forEachIndexed { index, title ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
            }
        }
        when (tab) {
            0 -> LogList(interaction.map { "${fmt.format(Date(it.timestamp))} • ${it.match} • ${it.snippet}" })
            1 -> LogList(skipped.map { "${fmt.format(Date(it.timestamp))} • ${it.reason} • ${it.snippet}" })
            2 -> LogList(listOf("NOT_LINKED — App initialized (demo seed)"))
            else -> LogList(listOf("لا أخطاء في النسخة التجريبية"))
        }
    }
}

@Composable
private fun LogList(lines: List<String>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(lines) { line ->
            Text(line, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth())
        }
    }
}
