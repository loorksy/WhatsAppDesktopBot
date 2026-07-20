package com.whatsappdesktopbot.local.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Indigo = Color(0xFF4F46E5)
private val IndigoLight = Color(0xFF818CF8)
private val SlateBg = Color(0xFFF8FAFC)

private val LightColors = lightColorScheme(
    primary = Indigo,
    secondary = IndigoLight,
    background = SlateBg,
    surface = Color.White,
)

@Composable
fun WhatsAppBotTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content,
    )
}
