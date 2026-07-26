package com.chiefofstaff.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlin.math.PI
import kotlin.math.sin

/**
 * A liquid-glass listening orb: light at the top settling into blue water, with a wavy surface,
 * drifting caustics and rising bubbles. It's calm when idle and its currents quicken — bigger chop,
 * more bubbles, and outward ripples — as the user's voice ([amplitude], 0..1) rises. The time base is
 * driven per-frame and sped up by the voice, so "currents quicken" is literal, not just bigger.
 */
@Composable
fun ListeningOrb(amplitude: Float, modifier: Modifier = Modifier) {
    val amp by animateFloatAsState(amplitude.coerceIn(0f, 1f), tween(160), label = "amp")
    val ampState = rememberUpdatedState(amp)

    // A hand-rolled clock so the flow speed can scale with the voice (infinite transitions can't).
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) t += ((now - last) / 1_000_000_000f) * (0.5f + ampState.value * 1.7f)
                last = now
            }
        }
    }

    val glassTop = Color.White
    val glassLip = Color(0xFFEAF0FF)
    val waterTop = Color(0xFFBFD4F2)   // PastelSky
    val waterDeep = Color(0xFF9AA6EA)  // deeper periwinkle for the base

    Canvas(modifier) {
        val c = center
        val r = size.minDimension * 0.41f          // orb radius (leaves room for ripples)
        val phase = t

        // Soft outer glow.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFB9BEF3).copy(alpha = 0.35f), Color(0xFFB9BEF3).copy(alpha = 0f)),
                center = c, radius = r * 1.6f,
            ),
            radius = r * 1.6f, center = c,
        )

        val orb = Path().apply { addOval(Rect(c.x - r, c.y - r, c.x + r, c.y + r)) }
        clipPath(orb) {
            // Glass fill — white light up top fading toward the water.
            drawRect(
                brush = Brush.verticalGradient(listOf(glassTop, glassLip), startY = c.y - r, endY = c.y + r),
            )

            // Water body with a wavy surface that undulates and rises a touch with the voice.
            val surfaceY = c.y - r * (0.08f + amp * 0.06f)
            val waveAmp = r * (0.035f + amp * 0.075f)
            val water = Path().apply {
                moveTo(c.x - r, c.y + r)
                lineTo(c.x - r, surfaceY)
                var x = c.x - r
                val step = r * 0.06f
                while (x <= c.x + r) {
                    val y = surfaceY + sin((x - c.x) / r * PI.toFloat() * 2.2f + phase * 2.1f) * waveAmp
                    lineTo(x, y)
                    x += step
                }
                lineTo(c.x + r, c.y + r)
                close()
            }
            drawPath(water, brush = Brush.verticalGradient(listOf(waterTop, waterDeep), startY = surfaceY, endY = c.y + r))

            // Caustics — soft light bands slowly drifting across the water.
            for (i in 0..2) {
                val cy = surfaceY + r * (0.3f + i * 0.24f)
                val cx = c.x + sin(phase * 0.5f + i * 2.1f) * r * 0.45f
                drawOval(
                    color = Color.White.copy(alpha = 0.10f + amp * 0.05f),
                    topLeft = Offset(cx - r * 0.45f, cy - r * 0.045f),
                    size = Size(r * 0.9f, r * 0.09f),
                )
            }

            // Bubbles — more of them, brighter, as the voice rises.
            val bubbles = 3 + (amp * 4).toInt()
            for (i in 0 until bubbles) {
                val seed = i * 0.37f
                val prog = ((phase * 0.22f) + seed) % 1f
                val bx = c.x + sin(seed * 12f) * r * 0.55f
                val by = (c.y + r) - prog * (r * 1.7f)
                if (by > surfaceY) {
                    val br = r * (0.02f + (i % 3) * 0.012f)
                    drawCircle(Color.White.copy(alpha = (1f - prog) * 0.5f), br, Offset(bx, by))
                }
            }

            // Glassy top-left highlight.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0f)),
                    center = Offset(c.x - r * 0.32f, c.y - r * 0.38f), radius = r * 0.9f,
                ),
                radius = r, center = c,
            )
        }

        // Glass rim.
        drawCircle(color = Color.White.copy(alpha = 0.5f), radius = r, center = c, style = Stroke(width = r * 0.02f))

        // "You're talking" — outward ripples that grow with the voice.
        if (amp > 0.12f) {
            for (i in 0..1) {
                val p = ((phase * 0.6f) + i * 0.5f) % 1f
                drawCircle(
                    color = Color(0xFFB9BEF3).copy(alpha = (1f - p) * amp * 0.55f),
                    radius = r * (1f + p * 0.45f), center = c, style = Stroke(width = r * 0.02f),
                )
            }
        }
    }
}
