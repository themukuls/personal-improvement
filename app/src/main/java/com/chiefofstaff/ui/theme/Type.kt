package com.chiefofstaff.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * Two families, exactly as the design uses them:
 *  - a humanist sans for all content (system default here; drop in Nunito/Inter via res/font
 *    and swap [ContentFamily] without touching call sites),
 *  - a monospace for times, the "wait" marker and every ALL-CAPS section label
 *    (LOOKING AHEAD, TODAY, NOT TODAY, RIGHT NOW).
 */
val ContentFamily = FontFamily.SansSerif
val MonoFamily = FontFamily.Monospace

private val tightLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

val CoSTypography = Typography(
    // Big hero line — 1c "Finish the Q3 capacity plan before standup."
    displaySmall = TextStyle(
        fontFamily = ContentFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp,
        lineHeightStyle = tightLineHeight,
    ),
    // Screen title — "Tuesday, 28 July"
    headlineMedium = TextStyle(
        fontFamily = ContentFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.3).sp,
    ),
    // Card headline — anticipation copy, identity line
    titleLarge = TextStyle(
        fontFamily = ContentFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    // List item title — "Q3 capacity plan to Rakesh"
    titleMedium = TextStyle(
        fontFamily = ContentFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = ContentFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    // Context subline — "deferred once · predicted 15:20"
    bodyMedium = TextStyle(
        fontFamily = ContentFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    // Button / nav label
    labelLarge = TextStyle(
        fontFamily = ContentFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    // Mono section labels + times live here (see [Mono] below for the styled variant).
    labelSmall = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.5.sp,
    ),
)

/** Convenience mono styles used all over the Now screen. */
object Mono {
    val SectionLabel = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 2.sp,
    )
    val Time = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    )
    val Status = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp,
    )
}
