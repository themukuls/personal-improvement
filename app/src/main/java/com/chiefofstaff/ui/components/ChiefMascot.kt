package com.chiefofstaff.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chiefofstaff.R

enum class MascotState {
    GREETING, THINKING, CELEBRATING, CALM
}

/**
 * "Chief" the mascot — not boxed in a circle, but a free-floating character that keeps busy: it
 * bobs, tilts its head as if glancing at its notes, and breathes gently, so it always reads as
 * alive and paying attention. A soft radial edge-fade dissolves the (opaque) image edges into the
 * app background — until transparent-PNG assets are dropped in, at which point the fade is harmless
 * and the character floats perfectly clean.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ChiefMascot(
    state: MascotState,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
) {
    val t = rememberInfiniteTransition(label = "mascot")
    val bob by t.animateFloat(
        -5f, 5f,
        infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob",
    )
    // A gentle head-tilt, like glancing down at a notepad and back up — the "noting" feel.
    val tilt by t.animateFloat(
        -2.5f, 3.5f,
        infiniteRepeatable(tween(2300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "tilt",
    )
    val breathe by t.animateFloat(
        0.985f, 1.02f,
        infiniteRepeatable(tween(1900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe",
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                translationY = bob
                rotationZ = tilt
                scaleX = breathe
                scaleY = breathe
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                fadeIn(tween(400)) togetherWith fadeOut(tween(400))
            },
            label = "mascot_state",
        ) { s ->
            val imageRes = when (s) {
                MascotState.GREETING -> R.drawable.chief_greeting
                MascotState.THINKING -> R.drawable.chief_thinking
                MascotState.CELEBRATING -> R.drawable.chief_celebrating
                MascotState.CALM -> R.drawable.chief_greeting
            }
            Image(
                painter = painterResource(id = imageRes),
                contentDescription = "Chief mascot",
                contentScale = ContentScale.Fit,   // whole character, not cropped into a circle
                modifier = Modifier
                    .fillMaxSize()
                    // Offscreen so the radial alpha-mask fades the opaque JPG edges to transparent.
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.radialGradient(
                                0.0f to Color.Black,
                                0.74f to Color.Black,
                                1.0f to Color.Transparent,
                                center = Offset(this.size.width / 2f, this.size.height / 2f),
                                radius = this.size.maxDimension * 0.62f,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            )
        }
    }
}
