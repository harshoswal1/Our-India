package com.ourindia.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

// ── Dual Mode: Brutalist (sharp) vs Accessible (rounded) ───────────
enum class ThemeMode { BRUTALIST, ACCESSIBLE }

val LocalThemeMode = compositionLocalOf { ThemeMode.BRUTALIST }

private val BrutalistShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp)
)

private val AccessibleShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

private val CivicColorScheme = lightColorScheme(
    primary = CivicColors.Navy,
    onPrimary = CivicColors.OnPrimary,
    secondary = CivicColors.Saffron,
    onSecondary = CivicColors.OnPrimary,
    tertiary = CivicColors.Teal,
    onTertiary = CivicColors.OnPrimary,
    error = CivicColors.CivicRed,
    onError = CivicColors.OnPrimary,
    background = CivicColors.Background,
    onBackground = CivicColors.OnBackground,
    surface = CivicColors.Surface,
    onSurface = CivicColors.OnSurface,
    surfaceVariant = CivicColors.SurfaceVariant,
    onSurfaceVariant = CivicColors.TextSecondary,
    outline = CivicColors.Navy.copy(alpha = 0.2f)
)

@Composable
fun OurIndiaTheme(
    themeMode: ThemeMode = ThemeMode.BRUTALIST,
    content: @Composable () -> Unit
) {
    val shapes = when (themeMode) {
        ThemeMode.BRUTALIST -> BrutalistShapes
        ThemeMode.ACCESSIBLE -> AccessibleShapes
    }

    CompositionLocalProvider(LocalThemeMode provides themeMode) {
        MaterialTheme(
            colorScheme = CivicColorScheme,
            typography = OurIndiaTypography,
            shapes = shapes,
            content = content
        )
    }
}
