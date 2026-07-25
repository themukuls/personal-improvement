package com.chiefofstaff.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
 * The hollow circular check on each TODAY row. Tapping it is one gesture to a "done" verdict
 * (P6). Neumorphic: sunken ring when empty, gradient-filled with a check when done.
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
                if (checked) Modifier.background(AvatarGradient)
                else Modifier.neuSurface(cornerRadius = 13, elevation = 6.dp, pressed = true)
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

/** Raised pill button — "Add commitment". */
@Composable
fun NeuButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .neuSurface(cornerRadius = 16, elevation = 7.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
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
 * The primary control (UX-02, P6). Hold to talk; the ramble is captured (CAP-01) and parsed.
 * Rendered as a gradient pill that presses in while held. Sits at the right of the bottom nav.
 */
@Composable
fun HoldToTalkButton(
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
    modifier: Modifier = Modifier,
    onDoubleTap: () -> Unit = {},
    sizeDp: Int = 44,
) {
    var held by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val scale by animateFloatAsState(if (held) 0.92f else 1f, label = "hold-scale")
    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(AvatarGradient)
            .pointerInput(Unit) {
                detectTapGestures(
                    // Double-tap toggles a hands-free session (CNV-13); a plain hold is one capture.
                    // Double-tap leaves no held pointer, so it won't be undone by a release handler.
                    onDoubleTap = { onDoubleTap() },
                    onPress = {
                        held = true
                        onHoldStart()
                        tryAwaitRelease()
                        held = false
                        onHoldEnd()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Mic,
            contentDescription = "Hold to talk",
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
