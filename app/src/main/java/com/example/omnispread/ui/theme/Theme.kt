package com.example.omnispread.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary          = AccentBlue,
    onPrimary        = TextPrimary,
    secondary        = AccentCyan,
    onSecondary      = BgPrimary,
    tertiary         = AccentGreen,
    background       = BgPrimary,
    onBackground     = TextPrimary,
    surface          = BgCard,
    onSurface        = TextPrimary,
    surfaceVariant   = BgSecondary,
    onSurfaceVariant = TextSecondary,
    outline          = Border,
    error            = AccentRed,
    onError          = TextPrimary,
)

@Composable
fun OmniSpreadTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography  = OmniSpreadTypography,
        content     = content,
    )
}
