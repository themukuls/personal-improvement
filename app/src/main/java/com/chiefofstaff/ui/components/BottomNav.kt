package com.chiefofstaff.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chiefofstaff.ui.theme.Palette
import com.chiefofstaff.ui.theme.glassSurface

/** The four destinations (§3.2). Order is fixed; no badges, no counts (RES-05). */
enum class Destination(val label: String) {
    Now("Now"), Talk("Talk"), Close("Close"), Look("Look")
}

/**
 * Bottom bar: a floating white pill holding the four tabs — the active one shown as a solid violet
 * chip, the rest as quiet labels — and, separate to its right, the gradient hold-to-talk button.
 * The primary action is not a tab, it is voice.
 */
@Composable
fun BottomBar(
    current: Destination,
    onSelect: (Destination) -> Unit,
    listening: Boolean,
    onMicTap: () -> Unit,
    modifier: Modifier = Modifier,
    onSessionToggle: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()               // sit above the system gesture / nav bar
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .glassSurface(cornerRadius = 28)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Destination.entries.forEach { dest ->
                NavTab(
                    label = dest.label,
                    active = dest == current,
                    onClick = { onSelect(dest) },
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        MicButton(listening = listening, onTap = onMicTap, onDoubleTap = onSessionToggle)
    }
}

@Composable
private fun NavTab(label: String, active: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .then(if (active) Modifier.background(Palette.Accent) else Modifier)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() }
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = if (active) androidx.compose.ui.graphics.Color.White else Palette.InkFaint,
        )
    }
}
