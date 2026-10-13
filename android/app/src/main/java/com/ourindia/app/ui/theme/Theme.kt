package com.ourindia.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val CivicShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

// Every surfaceContainer slot is set explicitly: lightColorScheme's defaults are the
// purple-tinted M3 baseline, which leaks into NavigationBar, sheets and chips otherwise.
private val CivicColorScheme = lightColorScheme(
    primary = CivicColors.Navy,
    onPrimary = CivicColors.OnPrimary,
    primaryContainer = Color(0xFFE3E8F0),
    onPrimaryContainer = CivicColors.Navy,
    secondary = CivicColors.Saffron,
    onSecondary = CivicColors.OnPrimary,
    secondaryContainer = Color(0xFFFCE3D2),
    onSecondaryContainer = Color(0xFF5A2300),
    tertiary = CivicColors.Teal,
    onTertiary = CivicColors.OnPrimary,
    tertiaryContainer = Color(0xFFD5EFEB),
    onTertiaryContainer = Color(0xFF0B3D37),
    error = CivicColors.CivicRed,
    onError = CivicColors.OnPrimary,
    background = CivicColors.Background,
    onBackground = CivicColors.OnBackground,
    surface = CivicColors.Background,
    onSurface = CivicColors.OnSurface,
    surfaceVariant = CivicColors.SurfaceVariant,
    onSurfaceVariant = CivicColors.TextSecondary,
    surfaceTint = Color.Transparent,
    outline = CivicColors.Outline,
    outlineVariant = CivicColors.Outline,
    surfaceBright = CivicColors.Surface,
    surfaceDim = Color(0xFFE9E5DC),
    surfaceContainerLowest = CivicColors.Surface,
    surfaceContainerLow = CivicColors.Surface,
    surfaceContainer = CivicColors.Surface,
    surfaceContainerHigh = Color(0xFFF3F0EA),
    surfaceContainerHighest = CivicColors.SurfaceVariant,
    inverseSurface = CivicColors.Navy,
    inverseOnSurface = Color(0xFFF1F3F6)
)

@Composable
fun OurIndiaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CivicColorScheme,
        typography = OurIndiaTypography,
        shapes = CivicShapes,
        content = content
    )
}
