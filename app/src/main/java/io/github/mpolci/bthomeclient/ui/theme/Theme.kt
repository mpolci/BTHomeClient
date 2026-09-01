package io.github.mpolci.bthomeclient.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFB6CCFF),
    secondary = Color(0xFFC4C7D0),
    surface = Color(0xFF121318),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF315DA8),
    secondary = Color(0xFF5A5E68),
)

@Composable
fun BTHomeClientTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColorScheme else LightColorScheme,
        content = content,
    )
}
