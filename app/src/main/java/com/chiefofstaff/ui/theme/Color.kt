package com.chiefofstaff.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Palette sampled directly from the "visual directions" PDF (Direction 1a — Quiet Ledger).
 * The language is soft pastel neumorphism on a single near-white surface. Colour never
 * encodes status (RES-05: no red, no colour-coded pressure) — the pastel gradient is
 * decorative only, reserved for the anticipation card, the 1c hero window, avatars and the
 * hold-to-talk control.
 */
object Palette {
    // --- Neumorphic surface (light) ---
    val Base = Color(0xFFEEF1F6)        // page / screen background
    val Surface = Color(0xFFF0F3FA)     // raised card face
    val SurfaceSunken = Color(0xFFE7EBF2)

    // Dual soft shadows that make the neumorphism read as extruded, not flat.
    val ShadowLight = Color(0xFFFFFFFF) // top-left highlight
    val ShadowDark = Color(0xFFD1D9E6)  // bottom-right shadow

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

    // A single soft accent used sparingly for the active nav label / focus ring.
    val Accent = Color(0xFF8C93F2)

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
