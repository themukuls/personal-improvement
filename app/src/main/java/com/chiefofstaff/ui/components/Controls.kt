package com.chiefofstaff.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import com.chiefofstaff.ui.theme.AvatarGradient
import com.chiefofstaff.ui.theme.Mono
import com.chiefofstaff.ui.theme.Palette
import com.chiefofstaff.ui.theme.neuSurface

/**
 * The circular check on each TODAY tile. Tapping it is one gesture to a "done" verdict (P6).
 * Flat: a thin outlined ring when empty, a solid accent circle with a white check when done.
 */
@Composable
fun NeuCheckbox(
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(26.dp)
            .clip(CircleShape)
            .then(
                if (checked) Modifier.background(Palette.Accent)
                else Modifier.border(1.5.dp, Palette.InkGhost, CircleShape)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Checkbox,
                onClick = onToggle,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Done",
                tint = Color.White,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

/** Primary pill button — solid violet accent, white label. The one emphasised action on a surface. */
@Composable
fun NeuButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.Accent)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = androidx.compose.material3.MaterialTheme.typography.labelLarge, color = Color.White)
    }
}

/** Neutral pill button — flat white card with a soft shadow, ink label. Secondary to [NeuButton]. */
@Composable
fun NeuButtonNeutral(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .neuSurface(cornerRadius = 16, elevation = 6.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = androidx.compose.material3.MaterialTheme.typography.labelLarge, color = Palette.Ink)
    }
}

/** Ghost text button — "Not useful". No box, just muted text. */
@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
        color = Palette.InkFaint,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

/**
 * The primary control (UX-02, P6). Tap to talk: one tap starts listening, the recogniser finishes on
 * its own when you stop speaking, and the transcript is captured (CAP-01) and sent to the assistant.
 * Tap again while listening to cancel. Double-tap starts a hands-free session (CNV-13). It pulses
 * while active so it's obvious it's live. Sits at the right of the bottom nav.
 */
@Composable
fun MicButton(
    listening: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    onDoubleTap: () -> Unit = {},
    sizeDp: Int = 44,
) {
    val scale by animateFloatAsState(if (listening) 1.12f else 1f, label = "mic-scale")
    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(AvatarGradient)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { onDoubleTap() },
                    onTap = { onTap() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (listening) Icons.Filled.Stop else Icons.Filled.Mic,
            contentDescription = if (listening) "Stop listening" else "Tap to talk",
            tint = Color.White,
            modifier = Modifier.size((sizeDp * 0.42f).dp),
        )
    }
}

/** Small mono status chip used e.g. "consistency 78% over 30 days" (a fact, not a score). */
@Composable
fun MonoStat(text: String, modifier: Modifier = Modifier) {
    Text(text = text, style = Mono.Status, color = Palette.InkFaint, modifier = modifier)
}
