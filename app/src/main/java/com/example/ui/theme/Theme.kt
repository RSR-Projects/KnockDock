package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val KnockDockColorScheme = darkColorScheme(
    primary = CyanGlow,
    onPrimary = Color(0xFF001E2B),
    primaryContainer = Color(0xFF0C4A6E),
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = IndigoPulse,
    onSecondary = Color(0xFF1E1B4B),
    secondaryContainer = Color(0xFF312E81),
    onSecondaryContainer = Color(0xFFE0E7FF),
    tertiary = MintSuccess,
    onTertiary = Color(0xFF022C22),
    background = VoidBlack,
    onBackground = TextPrimary,
    surface = SlateGlassDark,
    onSurface = TextPrimary,
    surfaceVariant = SlateGlassCard,
    onSurfaceVariant = TextSecondary,
    error = RoseError,
    onError = Color.White
)

@Composable
fun KnockDockTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = KnockDockColorScheme,
        typography = Typography,
        content = content
    )
}
