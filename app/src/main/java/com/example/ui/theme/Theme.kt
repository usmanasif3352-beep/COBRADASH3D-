package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val CobraDarkColorScheme = darkColorScheme(
    primary = EmeraldCore,
    onPrimary = VoidObsidian,
    primaryContainer = EmeraldDeep,
    onPrimaryContainer = EmeraldGlow,
    secondary = CyberCyan,
    onSecondary = VoidObsidian,
    secondaryContainer = CardSurfaceElevated,
    onSecondaryContainer = CyberCyan,
    tertiary = SolarGold,
    onTertiary = VoidObsidian,
    background = VoidObsidian,
    onBackground = TextPrimary,
    surface = ArenaSlate,
    onSurface = TextPrimary,
    surfaceVariant = CardSurface,
    onSurfaceVariant = TextSecondary,
    outline = BorderEmerald,
    error = CrimsonDanger,
    onError = TextPrimary
)

private val CobraShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp)
)

@Composable
fun CobraDash3DTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CobraDarkColorScheme,
        typography = CobraTypography,
        shapes = CobraShapes,
        content = content
    )
}
