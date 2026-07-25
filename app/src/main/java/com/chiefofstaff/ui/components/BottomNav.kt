package com.chiefofstaff.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chiefofstaff.ui.theme.Palette
import com.chiefofstaff.ui.theme.neuSurface

/** The four destinations (§3.2). Order is fixed; no badges, no counts (RES-05). */
enum class Destination(val label: String) {
    Now("Now"), Talk("Talk"), Close("Close"), Look("Look")
}

/**
 * Bottom bar: a neumorphic pill holding the four text tabs, and — separate, to its right —
 * the gradient hold-to-talk button. This matches the design precisely: the primary action is
 * not a tab, it is voice.
 */
@Composable
fun BottomBar(
    current: Destination,
    onSelect: (Destination) -> Unit,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
    modifier: Modifier = Modifier,
    onSessionToggle: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .neuSurface(cornerRadius = 26, elevation = 9.dp)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Destination.entries.forEach { dest ->
                val active = dest == current
                Text(
                    text = dest.label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    ),
                    color = if (active) Palette.Ink else Palette.InkFaint,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(dest) }
                        .padding(vertical = 2.dp),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        HoldToTalkButton(onHoldStart = onHoldStart, onHoldEnd = onHoldEnd, onLongPress = onSessionToggle)
    }
}
