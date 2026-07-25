package com.chiefofstaff.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.chiefofstaff.data.model.Verdict
import com.chiefofstaff.ui.components.SectionLabel
import com.chiefofstaff.ui.theme.Mono
import com.chiefofstaff.ui.theme.Palette
import com.chiefofstaff.ui.theme.neuSurface
import com.chiefofstaff.ui.vm.CloseCard
import com.chiefofstaff.ui.vm.CloseUiState
import kotlin.math.abs

/**
 * The Close card stack (§3.2, §9.2, UX-04). One card at a time; swipe right = done, left = skipped,
 * up = defer, down = drop. Voice equivalents ("done"/"skipped"/"tomorrow"/"drop it") are handled by
 * the same verdict path. Neutral throughout — a missed item is reported as a fact, never a failure
 * (RES-06). When the stack empties, the day is simply closed; there is no score to celebrate or
 * defend.
 */
@Composable
fun CloseScreen(
    state: CloseUiState,
    onVerdict: (Verdict) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        SectionLabel("EVENING CLOSE")
        Spacer(Modifier.height(4.dp))
        Text(
            if (state.done) "Day closed." else "${state.cards.size - state.index} to look at.",
            style = MaterialTheme.typography.headlineMedium,
            color = Palette.Ink,
        )
        Spacer(Modifier.height(28.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when {
                state.done -> Text(
                    "Nothing left. Rest well.",
                    style = MaterialTheme.typography.titleLarge,
                    color = Palette.InkFaint,
                )
                else -> {
                    val card = state.cards[state.index]
                    SwipeCard(card = card, onVerdict = onVerdict)
                }
            }
        }

        if (!state.done) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                VerdictHint("← skip")
                VerdictHint("↑ tomorrow")
                VerdictHint("done →")
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SwipeCard(card: CloseCard, onVerdict: (Verdict) -> Unit) {
    val screenWidthPx = LocalConfiguration.current.screenWidthDp * 2.6f
    var offsetX by remember(card.id) { mutableFloatStateOf(0f) }
    var offsetY by remember(card.id) { mutableFloatStateOf(0f) }
    val animX by animateFloatAsState(offsetX, label = "x")
    val animY by animateFloatAsState(offsetY, label = "y")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .graphicsLayer {
                translationX = animX
                translationY = animY
                rotationZ = (animX / 60f).coerceIn(-8f, 8f)
            }
            .neuSurface(cornerRadius = 28, elevation = 14.dp)
            .pointerInput(card.id) {
                detectDragGestures(
                    onDragEnd = {
                        val threshold = screenWidthPx * 0.28f
                        when {
                            offsetX > threshold -> onVerdict(Verdict.DONE)
                            offsetX < -threshold -> onVerdict(Verdict.SKIPPED)
                            offsetY < -threshold -> onVerdict(Verdict.DEFERRED)
                            offsetY > threshold -> onVerdict(Verdict.DROPPED)
                            else -> { offsetX = 0f; offsetY = 0f }
                        }
                    },
                ) { change, drag ->
                    change.consume()
                    offsetX += drag.x
                    offsetY += drag.y
                }
            }
            .padding(24.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column {
            Text(card.time, style = Mono.Time, color = Palette.InkFaint)
            Spacer(Modifier.height(10.dp))
            Text(card.title, style = MaterialTheme.typography.displaySmall, color = Palette.Ink)
            if (!card.context.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(card.context, style = MaterialTheme.typography.bodyLarge, color = Palette.InkMuted)
            }
        }
    }
}

@Composable
private fun VerdictHint(text: String) {
    Text(text, style = Mono.Status, color = Palette.InkGhost)
}
