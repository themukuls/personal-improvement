package com.chiefofstaff.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The design ships a single light neumorphic scheme. OLED-black dark mode is a V1 item (UX-08)
 * and is intentionally out of scope for this V0 slice — we commit fully to the pastel light look
 * rather than half-building two themes.
 */
private val CoSColorScheme = lightColorScheme(
    primary = Palette.Accent,
    onPrimary = Color.White,
    background = Palette.Base,
    onBackground = Palette.Ink,
    surface = Palette.Surface,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.SurfaceSunken,
    onSurfaceVariant = Palette.InkMuted,
    outline = Palette.InkFaint,
)

@Composable
fun ChiefOfStaffTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CoSColorScheme,
        typography = CoSTypography,
        shapes = CoSShapes,
        content = content,
    )
}
