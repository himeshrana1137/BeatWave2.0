package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val BeatWaveDarkColorScheme = darkColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color(0xFF051A0C),
    primaryContainer = EmeraldDeep.copy(alpha = 0.25f),
    onPrimaryContainer = EmeraldPrimary,
    secondary = CyanAccent,
    onSecondary = Color(0xFF001F24),
    secondaryContainer = Color(0xFF0E2A33),
    onSecondaryContainer = CyanAccent,
    tertiary = AmberWarm,
    background = ObsidianBlack,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianBlack,
    surfaceContainerLow = SurfaceDark,
    surfaceContainer = SurfaceElevated,
    surfaceContainerHigh = SurfaceHighlight,
    surfaceContainerHighest = Color(0xFF2A2F40),
    outline = BorderSubtle,
    outlineVariant = Color(0xFF1F2330),
    error = CoralError,
    onError = Color(0xFF2B0007)
)

val BeatWaveShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun BeatWaveTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = BeatWaveDarkColorScheme,
        typography = Typography,
        shapes = BeatWaveShapes,
        content = content
    )
}
