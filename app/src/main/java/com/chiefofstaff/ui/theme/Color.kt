package com.chiefofstaff.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Flat, subtle palette: clean white cards on a near-white page with a single soft shadow, and a
 * clear violet accent for the primary action and active states. This replaces the earlier
 * neumorphic ("3D" extruded) treatment — the surfaces are now defined by one soft shadow and a
 * faint hairline, not dual light/dark shadows. Colour still never encodes pressure (RES-05: no
 * red); the pastel gradient stays decorative, reserved for the hero / anticipation card and avatars.
 */
object Palette {
    // --- Flat surfaces (light) ---
    val Base = Color(0xFFF7F8FC)        // page / screen background (near-white)
    val Surface = Color(0xFFFFFFFF)     // card face
    val SurfaceSunken = Color(0xFFF0F1F7) // input fields / pressed & recessed states
    val Hairline = Color(0xFFECEEF4)    // 1px card border for white-on-white definition

    // Kept for API compatibility with neuSurface(); now just the soft drop-shadow tint.
    val ShadowLight = Color(0xFFFFFFFF)
    val ShadowDark = Color(0xFFC9D0E0)  // soft cool-grey shadow, applied at low alpha

    // --- Ink ---
    val Ink = Color(0xFF2E3140)         // primary text (dry, near-black slate)
    val InkMuted = Color(0xFF6B7180)    // secondary / context sublines
    val InkFaint = Color(0xFF9AA0B0)    // mono section labels, times, hints
    val InkGhost = Color(0xFFB9BFCC)    // disabled / placeholder

    // --- Decorative pastel stops (never a status signal) ---
    val PastelLavender = Color(0xFFCBC7F5)
    val PastelPink = Color(0xFFEBC5E8)
    val PastelSky = Color(0xFFBFD4F2)
    val PastelMint = Color(0xFFC3E7DA)
    val PastelPeriwinkle = Color(0xFFB9BEF3)

    // The violet accent — primary buttons, selected day/tab, checks, focus. From the reference design.
    val Accent = Color(0xFF7C5CFF)
    val AccentSoft = Color(0xFFECE8FF)  // tinted fill behind active chips / soft accent surfaces

    // Verdict tints for the Close card stack — muted, never alarming (P4, RES-06).
    val DoneTint = Color(0xFFBFE3D2)
    val SkipTint = Color(0xFFE7D2D8)
    val DeferTint = Color(0xFFDBD6EF)
    val DropTint = Color(0xFFDDE0E8)
}

/** The decorative gradient, lavender → pink → sky → mint. Used on ≤ a handful of surfaces. */
val DecorativeGradient: Brush
    get() = Brush.linearGradient(
        colors = listOf(
            Palette.PastelLavender,
            Palette.PastelPink,
            Palette.PastelSky,
            Palette.PastelMint,
        ),
    )

/** Softer avatar / control gradient. */
val AvatarGradient: Brush
    get() = Brush.linearGradient(
        colors = listOf(Palette.PastelSky, Palette.PastelPeriwinkle),
    )
