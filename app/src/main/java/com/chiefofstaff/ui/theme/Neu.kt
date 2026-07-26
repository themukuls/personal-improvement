package com.chiefofstaff.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Flat surface treatment. The app used to paint two blurred shadows (a light one up-left, a dark
 * one down-right) for a neumorphic "extruded" look; that read as heavy and didn't hold up, so this
 * is now a single, soft, downward drop shadow plus a faint hairline — clean cards on white, matching
 * the reference design.
 *
 * The signature is unchanged so every existing call site keeps compiling. [pressed] no longer
 * inverts a 3D effect; it renders a recessed field instead (a faint sunken fill, no drop shadow),
 * used for text inputs and active toggle chips.
 */
fun Modifier.neuSurface(
    cornerRadius: Int = 20,          // dp; kept as Int so call sites read cleanly (neuSurface(cornerRadius = 22))
    fill: Color = Palette.Surface,
    @Suppress("UNUSED_PARAMETER") light: Color = Palette.ShadowLight,
    dark: Color = Palette.ShadowDark,
    elevation: Dp = 10.dp,
    pressed: Boolean = false,
): Modifier = if (pressed) {
    // Recessed field: no shadow, a faint sunken fill and a hairline so it reads as inset.
    this
        .background(color = Palette.SurfaceSunken, shape = RoundedCornerShape(cornerRadius.dp))
        .border(1.dp, Palette.Hairline, RoundedCornerShape(cornerRadius.dp))
} else {
    // Raised card: one soft, subtle drop shadow beneath, then the fill, then a hairline edge.
    this
        .drawBehind {
            val radiusPx = cornerRadius.dp.toPx()
            // A gentle shadow: modest blur, a small downward offset, low alpha so it stays subtle.
            val blur = (elevation.toPx()) * 0.9f
            val dy = (elevation.toPx()) * 0.35f
            drawIntoCanvas { canvas ->
                val paint = Paint()
                val fw = paint.asFrameworkPaint()
                fw.color = android.graphics.Color.TRANSPARENT
                fw.setShadowLayer(blur, 0f, dy, dark.copy(alpha = 0.55f).toArgb())
                canvas.drawRoundRect(0f, 0f, size.width, size.height, radiusPx, radiusPx, paint)
            }
        }
        .background(color = fill, shape = RoundedCornerShape(cornerRadius.dp))
        .border(1.dp, Palette.Hairline, RoundedCornerShape(cornerRadius.dp))
}
