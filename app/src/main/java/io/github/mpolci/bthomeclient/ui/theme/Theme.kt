package io.github.mpolci.bthomeclient.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFC4EED7),
    onPrimary = Color(0xFF102F22),
    primaryContainer = Color(0xFF2C4B3D),
    onPrimaryContainer = Color(0xFFC4EED7),
    secondary = Color(0xFFB9C8BD),
    onSecondary = Color(0xFF25352B),
    secondaryContainer = Color(0xFF2C4B3D),
    onSecondaryContainer = Color(0xFFC4EED7),
    background = Color(0xFF111916),
    onBackground = Color(0xFFE0E9E1),
    surface = Color(0xFF111916),
    onSurface = Color(0xFFE0E9E1),
    surfaceVariant = Color(0xFF39483F),
    onSurfaceVariant = Color(0xFFB9C8BD),
    surfaceContainerLow = Color(0xFF1E2A25),
    surfaceContainer = Color(0xFF25312C),
    surfaceContainerHigh = Color(0xFF2D3933),
    outline = Color(0xFF899A8E),
    outlineVariant = Color(0xFF3E5145),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF38664F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEBDC),
    onPrimaryContainer = Color(0xFF102F22),
    secondary = Color(0xFF46534A),
    secondaryContainer = Color(0xFFCDEBDC),
    onSecondaryContainer = Color(0xFF102F22),
    background = Color(0xFFF5FAF6),
    onBackground = Color(0xFF19211C),
    surface = Color(0xFFF5FAF6),
    onSurface = Color(0xFF19211C),
    surfaceVariant = Color(0xFFDCE5DE),
    onSurfaceVariant = Color(0xFF46534A),
    surfaceContainerLow = Color(0xFFE7F0E9),
    surfaceContainer = Color(0xFFE1EAE3),
    surfaceContainerHigh = Color(0xFFDBE4DD),
    outline = Color(0xFF6F8074),
    outlineVariant = Color(0xFFBDCEC1),
)

@Composable
fun BTHomeClientTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColorScheme else LightColorScheme,
        content = content,
    )
}
