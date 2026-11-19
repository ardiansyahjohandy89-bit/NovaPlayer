package com.nova.player.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NovaColors = darkColorScheme(
    primary = Color(0xFF8B7BFF),
    onPrimary = Color(0xFF0B0B12),
    secondary = Color(0xFF3DD6E8),
    background = Color(0xFF0B0B12),
    onBackground = Color(0xFFECECF5),
    surface = Color(0xFF14141F),
    onSurface = Color(0xFFECECF5),
    surfaceVariant = Color(0xFF1E1E2D),
    onSurfaceVariant = Color(0xFFA0A0B8),
)

@Composable
fun NovaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NovaColors, content = content)
}
