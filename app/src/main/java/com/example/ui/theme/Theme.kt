package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = AmberPrimary,
    onPrimary = Color(0xFF1E1402),
    primaryContainer = AmberContainer,
    onPrimaryContainer = OnAmberContainer,
    secondary = AmberSecondary,
    onSecondary = Color.White,
    tertiary = BlueInfo,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    error = RedAlert,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = AmberSecondary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFEF3C7),
    onPrimaryContainer = Color(0xFF78350F),
    secondary = AmberPrimary,
    onSecondary = Color.Black,
    tertiary = BlueInfo,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightCardSurface,
    onSurfaceVariant = LightTextSecondary,
    error = RedAlert,
    onError = Color.White
)

@Composable
fun TrimTimeTheme(
    darkTheme: Boolean = true, // Default to sleek dark barber styling
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
