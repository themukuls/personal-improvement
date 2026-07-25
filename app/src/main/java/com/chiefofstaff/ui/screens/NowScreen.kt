package com.chiefofstaff.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chiefofstaff.data.entity.AnticipationItem
import com.chiefofstaff.ui.components.GhostButton
import com.chiefofstaff.ui.components.GradientCard
import com.chiefofstaff.ui.components.GradientDot
import com.chiefofstaff.ui.components.NeuButton
import com.chiefofstaff.ui.components.NeuCard
import com.chiefofstaff.ui.components.SectionLabel
import com.chiefofstaff.ui.theme.Mono
import com.chiefofstaff.ui.theme.Palette
import com.chiefofstaff.ui.theme.neuSurface
import com.chiefofstaff.ui.vm.NowItem
import com.chiefofstaff.ui.vm.NowUiState
import com.chiefofstaff.ui.components.CommitmentRow

/**
 * The Now screen. Layout follows the recommendation on the design's own page: Direction 1a (Quiet
 * Ledger) as the base, with the RIGHT NOW line borrowed from 1c so the app answers "what do I do?"
 * before you read a list. At most six objects on screen (UX-01); no streak, badge, count or colour
 * pressure (RES-05) — those belong to the rejected 1b.
 */
@Composable
fun NowScreen(
    state: NowUiState,
    onToggle: (NowItem) -> Unit,
    onAddCommitment: () -> Unit,
    onAnticipationRated: (Boolean) -> Unit,
    onSetEnergy: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var reentryDismissed by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        TopRow()
        Spacer(Modifier.height(18.dp))

        Text(state.dateTitle, style = MaterialTheme.typography.headlineMedium, color = Palette.Ink)
        Spacer(Modifier.height(6.dp))
        Text(state.statusLine, style = Mono.Status, color = Palette.InkFaint)

        // RES-03 — re-entry after an absence: welcome back, no shame, no backlog dump.
        if (state.reentryDays >= 3 && !reentryDismissed) {
            Spacer(Modifier.height(16.dp))
            NeuCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    SectionLabel("WELCOME BACK")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "It's been ${state.reentryDays} days. I kept things quiet — here's just today, nothing to catch up on.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Palette.InkMuted,
                    )
                    Spacer(Modifier.height(10.dp))
                    GhostButton(text = "Thanks", onClick = { reentryDismissed = true })
                }
            }
        }

        // CAP-10 — one-gesture daily energy, shown only until it's set (progressive; P9).
        if (state.energyToday == null && !state.loading) {
            Spacer(Modifier.height(16.dp))
            EnergyTap(onSetEnergy)
        }

        // RIGHT NOW — the one borrowed idea from 1c.
        if (!state.rightNow.isNullOrBlank()) {
            Spacer(Modifier.height(20.dp))
            SectionLabel("RIGHT NOW")
            Spacer(Modifier.height(6.dp))
            Text(
                state.rightNow,
                style = MaterialTheme.typography.titleLarge,
                color = Palette.Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // LOOKING AHEAD — the single anticipation card (the only "intelligence" moment on Now).
        state.anticipation?.let { a ->
            Spacer(Modifier.height(18.dp))
            AnticipationCard(a, onAddCommitment, onAnticipationRated)
        }

        Spacer(Modifier.height(22.dp))
        SectionLabel("TODAY")
        Spacer(Modifier.height(10.dp))
        state.today.forEach { item ->
            CommitmentRow(
                time = item.time,
                title = item.title,
                context = item.context,
                checked = item.checked,
                onToggle = { onToggle(item) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            )
        }
        if (state.today.isEmpty() && !state.loading) {
            Text("Nothing set for today.", style = MaterialTheme.typography.bodyMedium, color = Palette.InkFaint)
        }

        // NOT TODAY — the "should-not" list makes the refusals visible.
        if (state.notToday.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            SectionLabel("NOT TODAY")
            Spacer(Modifier.height(10.dp))
            NeuCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    state.notToday.forEach {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = Palette.InkMuted, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun EnergyTap(onSetEnergy: (Int) -> Unit) {
    Column {
        SectionLabel("HOW'S YOUR ENERGY?")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            (1..5).forEach { level ->
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .neuSurface(cornerRadius = 17, elevation = 5.dp)
                        .clip(CircleShape)
                        .clickable { onSetEnergy(level) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$level", style = Mono.Time, color = Palette.InkMuted)
                }
            }
        }
    }
}

@Composable
private fun TopRow() {
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GradientDot(sizeDp = 34)
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .neuSurface(cornerRadius = 17, elevation = 6.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.History, contentDescription = "History", tint = Palette.InkMuted, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            GradientDot(sizeDp = 34)
        }
        // App wordmark, centered between the avatars.
        Text(
            text = "Chief of Staff",
            style = MaterialTheme.typography.labelLarge,
            color = Palette.InkMuted,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun AnticipationCard(
    a: AnticipationItem,
    onAddCommitment: () -> Unit,
    onRated: (Boolean) -> Unit,
) {
    GradientCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            SectionLabel("LOOKING AHEAD")
            Spacer(Modifier.height(8.dp))
            Text(a.headline, style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
            if (a.detail.isNotBlank()) {
                Text(a.detail, style = MaterialTheme.typography.bodyLarge, color = Palette.InkMuted, modifier = Modifier.padding(top = 2.dp))
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                NeuButton(text = "Add commitment", onClick = onAddCommitment)
                GhostButton(text = "Not useful", onClick = { onRated(false) })
            }
        }
    }
}
