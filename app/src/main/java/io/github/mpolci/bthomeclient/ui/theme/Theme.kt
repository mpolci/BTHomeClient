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
    tertiary = Color(0xFFD8C7A1),
    onTertiary = Color(0xFF3C3018),
    tertiaryContainer = Color(0xFF53462D),
    onTertiaryContainer = Color(0xFFF5E3BC),
    background = Color(0xFF111916),
    onBackground = Color(0xFFE0E9E1),
    surface = Color(0xFF111916),
    onSurface = Color(0xFFE0E9E1),
    surfaceVariant = Color(0xFF39483F),
    onSurfaceVariant = Color(0xFFB9C8BD),
    surfaceContainerLow = Color(0xFF1E2A25),
    surfaceContainer = Color(0xFF25312C),
    surfaceContainerHigh = Color(0xFF2D3933),
    surfaceContainerHighest = Color(0xFF35423A),
    surfaceContainerLowest = Color(0xFF0C1410),
    surfaceDim = Color(0xFF111916),
    surfaceBright = Color(0xFF344038),
    surfaceTint = Color(0xFFC4EED7),
    inverseSurface = Color(0xFFE0E9E1),
    inverseOnSurface = Color(0xFF2D3933),
    inversePrimary = Color(0xFF38664F),
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
    tertiary = Color(0xFF6B5B3F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF5E3BC),
    onTertiaryContainer = Color(0xFF251A04),
    background = Color(0xFFF5FAF6),
    onBackground = Color(0xFF19211C),
    surface = Color(0xFFF5FAF6),
    onSurface = Color(0xFF19211C),
    surfaceVariant = Color(0xFFDCE5DE),
    onSurfaceVariant = Color(0xFF46534A),
    surfaceContainerLow = Color(0xFFE7F0E9),
    surfaceContainer = Color(0xFFE1EAE3),
    surfaceContainerHigh = Color(0xFFDBE4DD),
    surfaceContainerHighest = Color(0xFFD5DED7),
    surfaceContainerLowest = Color.White,
    surfaceDim = Color(0xFFD5DED7),
    surfaceBright = Color(0xFFF5FAF6),
    surfaceTint = Color(0xFF38664F),
    inverseSurface = Color(0xFF2D3933),
    inverseOnSurface = Color(0xFFE7F0E9),
    inversePrimary = Color(0xFFC4EED7),
    outline = Color(0xFF6F8074),
    outlineVariant = Color(0xFFBDCEC1),
)

@Composable
fun BTHomeClientTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        content = content,
    )
}
