package com.classroom.simultranslate.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F6CBD),
    onPrimary = Color.White,
    secondary = Color(0xFF0E8A7D),
    onSecondary = Color.White,
    tertiary = Color(0xFFB26A00),
    background = Color(0xFFF7F9FB),
    surface = Color.White,
    surfaceVariant = Color(0xFFE8EEF4),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FC7FF),
    onPrimary = Color(0xFF002F57),
    secondary = Color(0xFF64D6C7),
    onSecondary = Color(0xFF003731),
    tertiary = Color(0xFFFFC56B),
    background = Color(0xFF101418),
    surface = Color(0xFF171C21),
    surfaceVariant = Color(0xFF242B32),
)

@Composable
fun SimulTranslateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}

