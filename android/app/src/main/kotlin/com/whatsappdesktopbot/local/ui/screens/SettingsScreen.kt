package com.whatsappdesktopbot.local.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
fun SettingsScreen(viewModel: BotViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("الإعدادات", style = MaterialTheme.typography.headlineSmall)
        SettingSwitch("تطبيع العربية", settings.normalizeArabicEnabled) {
            viewModel.saveSettings(settings.copy(normalizeArabicEnabled = it))
        }
        SettingSwitch("وضع الرد", settings.replyMode) {
            viewModel.saveSettings(settings.copy(replyMode = it))
        }
        SettingSwitch("تشغيل تلقائي بعد الإقلاع", settings.autoStartOnBoot) {
            viewModel.saveSettings(settings.copy(autoStartOnBoot = it))
        }
        SettingSwitch("قفل بالبصمة", settings.biometricLockEnabled) {
            viewModel.saveSettings(settings.copy(biometricLockEnabled = it))
        }
        OutlinedTextField(
            value = settings.defaultEmoji,
            onValueChange = { viewModel.saveSettings(settings.copy(defaultEmoji = it)) },
            label = { Text("الإيموجي الافتراضي") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = settings.cooldownSeconds.toString(),
            onValueChange = { v -> v.toIntOrNull()?.let { viewModel.saveSettings(settings.copy(cooldownSeconds = it)) } },
            label = { Text("Cooldown (ث)") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = settings.rpm.toString(),
            onValueChange = { v -> v.toIntOrNull()?.let { viewModel.saveSettings(settings.copy(rpm = it)) } },
            label = { Text("RPM") },
            modifier = Modifier.fillMaxWidth(),
        )
        Text("تعطيل تحسين البطارية — المرحلة 4", style = MaterialTheme.typography.bodySmall)
        Text("WhatsApp Bot v1.0.0-phase2", style = MaterialTheme.typography.labelMedium)
        OutlinedButton(onClick = viewModel::importBundledDesktopJson, modifier = Modifier.fillMaxWidth()) {
            Text("استيراد JSON من Desktop (عينة)")
        }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
