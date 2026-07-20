package com.whatsappdesktopbot.local.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.ui.graphics.vector.ImageVector

enum class AppDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Status("status", "الحالة", Icons.Default.Speed),
    Link("link", "الربط", Icons.Default.Link),
    Groups("groups", "المجموعات", Icons.Default.Group),
    Clients("clients", "العملاء", Icons.Default.List),
    Forwarding("forwarding", "التوجيه", Icons.Default.Share),
    Bulk("bulk", "إرسال", Icons.Default.Send),
    Logs("logs", "السجلات", Icons.Default.List),
    Settings("settings", "الإعدادات", Icons.Default.Settings),
}
