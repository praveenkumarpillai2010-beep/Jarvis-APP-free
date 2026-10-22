package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val JarvisDarkColorScheme = darkColorScheme(
    primary = JarvisCyan,
    onPrimary = Color(0xFF00202F),
    primaryContainer = JarvisSurfaceVariant,
    onPrimaryContainer = JarvisCyan,
    secondary = JarvisElectricBlue,
    onSecondary = Color(0xFF001F4D),
    secondaryContainer = Color(0xFF0E2750),
    onSecondaryContainer = JarvisTextPrimary,
    tertiary = JarvisAqua,
    onTertiary = Color(0xFF002E29),
    background = JarvisBackground,
    onBackground = JarvisTextPrimary,
    surface = JarvisSurface,
    onSurface = JarvisTextPrimary,
    surfaceVariant = JarvisSurfaceVariant,
    onSurfaceVariant = JarvisTextSecondary,
    outline = JarvisBorder,
    error = JarvisError,
    onError = Color(0xFF370008)
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    // JARVIS AI is designed with an immersive cinematic dark sci-fi UI
    MaterialTheme(
        colorScheme = JarvisDarkColorScheme,
        typography = Typography,
        content = content
    )
}
