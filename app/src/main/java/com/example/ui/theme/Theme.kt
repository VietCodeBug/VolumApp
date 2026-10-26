package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = SkyCobalt400,
    onPrimary = Slate950,
    primaryContainer = SkyCobalt900,
    onPrimaryContainer = SkyCobalt100,
    secondary = Emerald400,
    onSecondary = Slate950,
    secondaryContainer = Emerald900,
    onSecondaryContainer = Color(0xFFD1FAE5),
    tertiary = Amber400,
    onTertiary = Slate950,
    error = CoralRed400,
    onError = Slate950,
    background = Slate900,
    onBackground = Slate50,
    surface = Slate900,
    onSurface = Slate50,
    surfaceVariant = Slate800,
    onSurfaceVariant = Slate200,
    outline = Slate700
)

private val LightColorScheme = lightColorScheme(
    primary = SkyCobalt600,
    onPrimary = Color.White,
    primaryContainer = SkyCobalt100,
    onPrimaryContainer = SkyCobalt900,
    secondary = Emerald500,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = Emerald900,
    tertiary = Amber500,
    onTertiary = Color.White,
    error = CoralRed500,
    onError = Color.White,
    background = Slate50,
    onBackground = Slate900,
    surface = Color.White,
    onSurface = Slate900,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate700,
    outline = Slate400
)

@Composable
fun FloatingVolumeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
