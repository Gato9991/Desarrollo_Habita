package com.habita.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = HabitaForestGreen,
    onPrimary = HabitaSurface,
    secondary = HabitaSoftMint,
    onSecondary = HabitaForestDark,
    background = HabitaBackground,
    onBackground = HabitaTextPrimary,
    surface = HabitaSurface,
    onSurface = HabitaTextPrimary,
    surfaceVariant = HabitaLightGreenBg,
    onSurfaceVariant = HabitaTextSecondary
)

@Composable
fun HabitaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
