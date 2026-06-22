package com.animia.mvp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = GreenPrimary,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = GreenAccent,
    background = GreenSurface,
    surface = CardBg,
    onSurface = TextPrimary,
    onBackground = TextPrimary
)

private val DarkColors = darkColorScheme(
    primary = GreenAccent,
    secondary = GreenPrimary,
    background = androidx.compose.ui.graphics.Color(0xFF0E1B12),
    surface = androidx.compose.ui.graphics.Color(0xFF152B1B)
)

@Composable
fun AnimIATheme(useDark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (useDark) DarkColors else LightColors,
        content = content
    )
}
