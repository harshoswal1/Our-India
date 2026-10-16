package com.ourindia.ailawyer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object LegalColors {
    // Brand & Accents
    val Navy = Color(0xFF0F1E33)
    val NavyLight = Color(0xFF233554)
    val NavyAccent = Color(0xFF1E3A8A)
    val Saffron = Color(0xFFE85D04)
    val SaffronDeep = Color(0xFFB54A04)
    val SaffronContainer = Color(0xFFFDE8D7)

    val Civic = Color(0xFF1B6E4A)
    val CivicDeep = Color(0xFF12523A)
    val CivicContainer = Color(0xFFDDEFE5)

    val Crimson = Color(0xFFC62828)
    val CrimsonContainer = Color(0xFFF9DEDC)

    // Warm Paper & Cream Surfaces (Matching Our India Design System)
    val Background = Color(0xFFF7F5F0)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceVariant = Color(0xFFF0EDE6)
    val Outline = Color(0xFFE4DFD5)

    // Typography
    val OnPrimary = Color(0xFFFFFFFF)
    val OnBackground = Color(0xFF16202E)
    val OnSurface = Color(0xFF2A3341)
    val TextSecondary = Color(0xFF5B6472)
    val TextMuted = Color(0xFF8A919C)
}

private val LightColorScheme = lightColorScheme(
    primary = LegalColors.Saffron,
    onPrimary = Color.White,
    secondary = LegalColors.Civic,
    onSecondary = Color.White,
    background = LegalColors.Background,
    surface = LegalColors.Surface,
    surfaceVariant = LegalColors.SurfaceVariant,
    onBackground = LegalColors.OnBackground,
    onSurface = LegalColors.OnSurface,
    outline = LegalColors.Outline,
    error = LegalColors.Crimson
)

@Composable
fun AiLawyerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}
