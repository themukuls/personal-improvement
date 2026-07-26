package com.chiefofstaff.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Glassmorphism helpers, kept deliberately restrained so the app reads premium, not gaudy.
 *
 * "Frosted glass" only reads when something colourful sits behind it, so [GlassBackdrop] lays a few
 * faint, blurred pastel blobs on the near-white base; [glassSurface] then renders a translucent
 * panel with a light top edge over them. On Android 12+ the blobs are genuinely blurred
 * (RenderEffect); on older devices `Modifier.blur` is a no-op and they degrade to soft gradients —
 * still clean, just less dreamy.
 */
fun Modifier.glassSurface(
    cornerRadius: Int = 22,
    tint: Color = Color.White,
    alpha: Float = 0.62f,
    elevation: Dp = 14.dp,
): Modifier = this
    .drawBehind {
        val radiusPx = cornerRadius.dp.toPx()
        val blur = elevation.toPx() * 0.9f
        val dy = elevation.toPx() * 0.4f
        drawIntoCanvas { canvas ->
            val paint = Paint()
            val fw = paint.asFrameworkPaint()
            fw.color = android.graphics.Color.TRANSPARENT
            fw.setShadowLayer(blur, 0f, dy, Palette.ShadowDark.copy(alpha = 0.5f).toArgb())
            canvas.drawRoundRect(0f, 0f, size.width, size.height, radiusPx, radiusPx, paint)
        }
    }
    .clip(RoundedCornerShape(cornerRadius.dp))
    .background(tint.copy(alpha = alpha), RoundedCornerShape(cornerRadius.dp))
    // A soft top-left sheen fading out — the tell-tale glass highlight.
    .background(
        brush = Brush.linearGradient(
            colors = listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.0f)),
        ),
        shape = RoundedCornerShape(cornerRadius.dp),
    )
    .border(1.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(cornerRadius.dp))

/**
 * The app background: the near-white base with a handful of faint, blurred pastel blobs that give
 * the glass surfaces something to refract. Draw it behind the main content (a transparent Scaffold).
 */
@Composable
fun GlassBackdrop(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().background(Palette.Base)) {
        Blob(Palette.PastelLavender, 300, alpha = 0.40f, align = Alignment.TopStart, dx = (-60).dp, dy = (-40).dp)
        Blob(Palette.PastelSky, 260, alpha = 0.34f, align = Alignment.TopEnd, dx = 50.dp, dy = 10.dp)
        Blob(Palette.PastelMint, 280, alpha = 0.28f, align = Alignment.BottomEnd, dx = 40.dp, dy = 60.dp)
        Blob(Palette.PastelPink, 240, alpha = 0.24f, align = Alignment.BottomStart, dx = (-50).dp, dy = 40.dp)
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.Blob(
    color: Color,
    sizeDp: Int,
    alpha: Float,
    align: Alignment,
    dx: Dp,
    dy: Dp,
) {
    Box(
        modifier = Modifier
            .align(align)
            .offset(x = dx, y = dy)
            .size(sizeDp.dp)
            .blur(70.dp)                       // real frosted blur on API 31+, no-op below
            .clip(RoundedCornerShape(sizeDp.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)),
                ),
            ),
    )
}
