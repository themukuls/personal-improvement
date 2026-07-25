package com.chiefofstaff.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Soft-UI ("neumorphism") surface treatment. Instead of Material elevation (one hard shadow),
 * we paint two blurred shadows — a light one up-left and a dark one down-right — so the surface
 * reads as gently extruded from the base, matching the PDF exactly.
 *
 * [pressed] inverts the offsets so a control looks pushed in (used for the active tab and the
 * hold-to-talk button while held).
 */
fun Modifier.neuSurface(
    cornerRadius: Int = 20,          // dp; kept as Int so call sites read cleanly (neuSurface(cornerRadius = 22))
    fill: Color = Palette.Surface,
    light: Color = Palette.ShadowLight,
    dark: Color = Palette.ShadowDark,
    elevation: Dp = 10.dp,
    pressed: Boolean = false,
): Modifier = this
    .drawBehind {
        val radiusPx = cornerRadius.dp.toPx()
        val blur = elevation.toPx()
        val offset = (elevation.toPx()) * 0.7f
        val dir = if (pressed) -1f else 1f

        drawIntoCanvas { canvas ->
            // Dark shadow, bottom-right.
            softShadow(canvas, dark, Offset(offset * dir, offset * dir), blur, radiusPx, size.width, size.height)
            // Light shadow, top-left.
            softShadow(canvas, light, Offset(-offset * dir, -offset * dir), blur, radiusPx, size.width, size.height)
        }
    }
    .background(color = fill, shape = RoundedCornerShape(cornerRadius.dp))

private fun softShadow(
    canvas: androidx.compose.ui.graphics.Canvas,
    color: Color,
    offset: Offset,
    blur: Float,
    radius: Float,
    width: Float,
    height: Float,
) {
    val paint = Paint()
    val frameworkPaint = paint.asFrameworkPaint()
    frameworkPaint.color = android.graphics.Color.TRANSPARENT
    frameworkPaint.setShadowLayer(blur, offset.x, offset.y, color.toArgb())
    canvas.drawRoundRect(
        left = 0f,
        top = 0f,
        right = width,
        bottom = height,
        radiusX = radius,
        radiusY = radius,
        paint = paint,
    )
}
