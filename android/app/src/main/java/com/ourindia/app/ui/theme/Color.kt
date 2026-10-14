package com.ourindia.app.ui.theme

import androidx.compose.ui.graphics.Color

// ── Civic palette: navy ink on warm paper, saffron as the single accent ──
object CivicColors {
    // Brand
    val Navy = Color(0xFF0F1E33)
    val Saffron = Color(0xFFE85D04)
    // Saffron/teal are 3.3:1 on white — fine for fills and large type, too light
    // for small text. The Deep variants clear 4.5:1 and are what body copy uses.
    val SaffronDeep = Color(0xFFB54A04)
    val Teal = Color(0xFF2A9D8F)
    val TealDeep = Color(0xFF1F7468)
    val CivicRed = Color(0xFFC62828)

    // Surfaces
    val Background = Color(0xFFF7F5F0)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceVariant = Color(0xFFF0EDE6)
    val Outline = Color(0xFFE4DFD5)
    val Water = Color(0xFFDDE7EE)

    // Text
    val OnPrimary = Color(0xFFFFFFFF)
    val OnBackground = Color(0xFF16202E)
    val OnSurface = Color(0xFF2A3341)
    val TextSecondary = Color(0xFF5B6472)
    val TextMuted = Color(0xFF8A919C)

    // NagrikVoice: the civic section keeps its own accent (India green) on the shared paper,
    // so the two halves of the app are recognisable at a glance without clashing.
    val Civic = Color(0xFF1B6E4A)
    val CivicDeep = Color(0xFF12523A)
    val CivicContainer = Color(0xFFDDEFE5)
    val Amber = Color(0xFFB7791F)
    val AmberContainer = Color(0xFFFBEBD0)
    val Danger = Color(0xFFB3261E)
    val DangerContainer = Color(0xFFF9DEDC)
    val Violet = Color(0xFF5B4BA6)
    val VioletContainer = Color(0xFFE6E1F7)

    // Party colors
    val BJP = Color(0xFFFF6B00)
    val INC = Color(0xFF0FA3D8)
    val AAP = Color(0xFF0077B6)
    val NCP = Color(0xFF005F73)
    val TMC = Color(0xFF2A9D8F)
    val Others = Color(0xFF6B7280)
}
