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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whatsappdesktopbot.local.ui.viewmodel.BotViewModel

@Composable
fun GroupsScreen(viewModel: BotViewModel) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = groups.filter { it.name.contains(query, ignoreCase = true) }
    val selectedCount = groups.count { it.selected }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("المجموعات", style = MaterialTheme.typography.headlineSmall)
        Text("المختارة: $selectedCount", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("بحث") },
            singleLine = true,
        )
        Button(onClick = viewModel::refreshGroups) { Text("تحديث") }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            items(filtered, key = { it.id }) { group ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = group.selected,
                        onCheckedChange = { checked -> viewModel.toggleGroup(group.id, checked) },
                    )
                    Column {
                        Text(group.name, style = MaterialTheme.typography.bodyLarge)
                        Text(group.id, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
