package ru.tsvetaeva.daily.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Бумага, чернила и рябина.
private val Light = lightColorScheme(
    primary = Color(0xFFA33A2C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF3DCD3),
    onPrimaryContainer = Color(0xFF5A1A12),
    secondary = Color(0xFF6B5E4F),
    background = Color(0xFFF7F2E8),
    onBackground = Color(0xFF2A2420),
    surface = Color(0xFFF7F2E8),
    onSurface = Color(0xFF2A2420),
    surfaceVariant = Color(0xFFEAE2D4),
    onSurfaceVariant = Color(0xFF6E6358),
    surfaceContainerHigh = Color(0xFFF1EADD),
    outlineVariant = Color(0xFFDCD2C2),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFE88A78),
    onPrimary = Color(0xFF4A120A),
    primaryContainer = Color(0xFF5C2219),
    onPrimaryContainer = Color(0xFFF8DAD2),
    secondary = Color(0xFFC9BBA8),
    background = Color(0xFF1B1816),
    onBackground = Color(0xFFEDE5D9),
    surface = Color(0xFF1B1816),
    onSurface = Color(0xFFEDE5D9),
    surfaceVariant = Color(0xFF2E2925),
    onSurfaceVariant = Color(0xFFB5A99B),
    surfaceContainerHigh = Color(0xFF2A2522),
    outlineVariant = Color(0xFF3B3530),
)

@Composable
fun TsvetaevaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
