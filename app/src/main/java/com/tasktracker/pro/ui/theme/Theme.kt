package com.tasktracker.pro.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.tasktracker.pro.data.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B6EF3),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8FD),
    onPrimaryContainer = Color(0xFF0A2F6B),
    secondary = Color(0xFF5A6B87),
    surface = Color(0xFFFDFDFD),
    surfaceVariant = Color(0xFFF1F4F9),
    error = Color(0xFFB3261E),
    tertiary = Color(0xFF0E7C5B)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FB6FF),
    onPrimary = Color(0xFF0A2F6B),
    primaryContainer = Color(0xFF1E3A66),
    onPrimaryContainer = Color(0xFFDCE8FD),
    secondary = Color(0xFF9FB2CC),
    surface = Color(0xFF121417),
    surfaceVariant = Color(0xFF1D2126),
    error = Color(0xFFFFB4AB),
    tertiary = Color(0xFF5FD3A5)
)

/** App semantic colors. */
object TaskColors {
    val HighPriority = Color(0xFFC62828)
    val MediumPriority = Color(0xFFEF6C00)
    val LowPriority = Color(0xFF2E7D32)
    val Overdue = Color(0xFFB3261E)
}

@Composable
fun TaskTrackerProTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content
    )
}
