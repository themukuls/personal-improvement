package com.chiefofstaff.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.chiefofstaff.data.model.Domain
import com.chiefofstaff.data.model.Mode
import com.chiefofstaff.domain.ReductionEngine
import com.chiefofstaff.ui.components.NeuCard
import com.chiefofstaff.ui.components.SectionLabel
import com.chiefofstaff.ui.theme.Mono
import com.chiefofstaff.ui.theme.Palette
import com.chiefofstaff.ui.theme.neuSurface
import com.chiefofstaff.ui.vm.LookSection
import com.chiefofstaff.ui.vm.LookUiState

/**
 * The Look screen (§3.2, UX-05) — "everything else". A search field over the FTS index at the top,
 * then four sections collapsed by default. This is where ~100 of the 150 features live; it is
 * visited perhaps twice a week and deliberately holds nothing that competes for daily attention.
 */
@Composable
fun LookScreen(
    state: LookUiState,
    onQuery: (String) -> Unit,
    onToggleSection: (LookSection) -> Unit,
    onSetQuiet: (Int?) -> Unit,
    onDrop: (ReductionEngine.ReductionItem) -> Unit,
    onBankruptcy: (Domain) -> Unit,
    onChase: (Long) -> Unit,
    onResolveWaiting: (Long) -> Unit,
    onLogContact: (Long) -> Unit,
    onSetMode: (Mode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        TextField(
            value = state.query,
            onValueChange = onQuery,
            placeholder = { Text("Search everything", color = Palette.InkGhost) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().neuSurface(cornerRadius = 22, elevation = 6.dp, pressed = true),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )

        if (state.results.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            SectionLabel("RESULTS")
            Spacer(Modifier.height(8.dp))
            state.results.forEach { r ->
                NeuCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Text(r, style = MaterialTheme.typography.bodyMedium, color = Palette.InkMuted)
                }
            }
        }

        // INT-08 — quiet mode: suppress proactive output while captures keep flowing.
        Spacer(Modifier.height(16.dp))
        NeuCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("QUIET MODE", modifier = Modifier.weight(1f))
                    Text(
                        if (state.quietActive) "on" else "off",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.quietActive) Palette.Accent else Palette.InkFaint,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuietChip("24h") { onSetQuiet(1) }
                    QuietChip("7 days") { onSetQuiet(7) }
                    QuietChip("Off") { onSetQuiet(null) }
                }
            }
        }

        // DIR-09 — operating mode; shapes the plan and notification posture.
        Spacer(Modifier.height(12.dp))
        NeuCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                SectionLabel("MODE")
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Mode.entries.forEach { mode ->
                        val active = mode == state.currentMode
                        Text(
                            text = mode.name.lowercase().replace('_', ' '),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (active) Palette.Ink else Palette.InkFaint,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .then(if (active) Modifier.neuSurface(cornerRadius = 14, elevation = 5.dp, pressed = true) else Modifier)
                                .clickable { onSetMode(mode) }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        CollapsedSection("DIRECTION", LookSection.DIRECTION, state, onToggleSection) {
            if (state.valueLines.isEmpty() && state.goalLines.isEmpty() && state.projectLines.isEmpty()) {
                Text("Your values, goals and projects form here.", style = MaterialTheme.typography.bodyMedium, color = Palette.InkFaint)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (state.valueLines.isNotEmpty()) {
                        SectionLabel("VALUES")
                        state.valueLines.forEach { Text(it, style = MaterialTheme.typography.bodyLarge, color = Palette.Ink) }
                    }
                    if (state.goalLines.isNotEmpty()) {
                        SectionLabel("GOALS")
                        state.goalLines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = Palette.InkMuted) }
                    }
                    if (state.projectLines.isNotEmpty()) {
                        SectionLabel("PROJECTS")
                        state.projectLines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = Palette.InkMuted) }
                    }
                }
            }
        }
        CollapsedSection("TIMELINE", LookSection.TIMELINE, state, onToggleSection) {
            if (state.timeline.isEmpty()) Text("No captures yet.", style = MaterialTheme.typography.bodyMedium, color = Palette.InkFaint)
            else state.timeline.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = Palette.InkMuted, modifier = Modifier.padding(vertical = 3.dp)) }
        }
        CollapsedSection("TRENDS", LookSection.TRENDS, state, onToggleSection) {
            val pct = state.consistencyPct
            val mae = state.predictionAccuracyHours
            if (pct == null && mae == null && state.healthLines.isEmpty()) {
                Text("Trends appear when there is data to trend.", style = MaterialTheme.typography.bodyMedium, color = Palette.InkFaint)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (pct != null) TrendLine("Consistency", "$pct% over 30 days")
                    if (mae != null) TrendLine("Self-accuracy", "estimates off by ~${"%.1f".format(mae)}h")
                    state.healthLines.forEach { TrendLine(it.substringBefore(":"), it.substringAfter(": ")) }
                    if (state.scorecard.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        SectionLabel("DOMAIN SCORECARD")
                        state.scorecard.forEach { TrendLine(it.substringBeforeLast(' '), it.substringAfterLast(' ')) }
                    }
                    state.tokensLine?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, style = Mono.Status, color = Palette.InkGhost)
                    }
                }
            }
        }
        CollapsedSection("DOMAINS", LookSection.DOMAINS, state, onToggleSection) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.domainCounts.isEmpty()) {
                    Text("Nothing open. Domains fill as you go.", style = MaterialTheme.typography.bodyMedium, color = Palette.InkFaint)
                }
                // RES-02 — per-domain backlog with one-action bankruptcy.
                state.domainCounts.forEach { (domain, count) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${domain.name.lowercase().replaceFirstChar { it.uppercase() }} — $count open",
                            style = MaterialTheme.typography.bodyMedium, color = Palette.InkMuted,
                            modifier = Modifier.weight(1f),
                        )
                        QuietChip("Clear backlog") { onBankruptcy(domain) }
                    }
                }
                // RES-04 — the assistant's own reduction proposals.
                if (state.proposals.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    SectionLabel("SUGGEST DROPPING")
                    state.proposals.forEach { item ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(item.label, style = MaterialTheme.typography.bodyMedium, color = Palette.InkMuted, modifier = Modifier.weight(1f))
                            QuietChip("Drop") { onDrop(item) }
                        }
                    }
                }
            }
        }
        CollapsedSection("WAITING ON", LookSection.WAITING, state, onToggleSection) {
            if (state.waiting.isEmpty()) {
                Text("Things others owe you show up here.", style = MaterialTheme.typography.bodyMedium, color = Palette.InkFaint)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.waiting.forEach { w ->
                        Column {
                            Text(w.what, style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
                            Text("from ${w.who}${if (w.context.isNotBlank()) " · ${w.context}" else ""}",
                                style = MaterialTheme.typography.bodyMedium, color = Palette.InkFaint)
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                QuietChip("Chase") { onChase(w.id) }
                                QuietChip("Got it") { onResolveWaiting(w.id) }
                            }
                        }
                    }
                }
            }
        }
        CollapsedSection("PEOPLE", LookSection.PEOPLE, state, onToggleSection) {
            if (state.people.isEmpty()) {
                Text("People appear once people exist.", style = MaterialTheme.typography.bodyMedium, color = Palette.InkFaint)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.people.forEach { p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(p.name, style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
                                if (p.sub.isNotBlank()) {
                                    Text(
                                        p.sub,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (p.overdue) Palette.Accent else Palette.InkFaint,
                                    )
                                }
                            }
                            QuietChip("Log contact") { onLogContact(p.id) }
                        }
                    }
                }
            }
        }
        CollapsedSection("DECISIONS", LookSection.DECISIONS, state, onToggleSection) {
            if (state.decisions.isEmpty()) {
                Text("Decisions you make in Decide mode land here, with a review date.", style = MaterialTheme.typography.bodyMedium, color = Palette.InkFaint)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.decisions.forEach { d ->
                        Column {
                            Text(d.question, style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
                            Text("→ ${d.chosen}", style = MaterialTheme.typography.bodyMedium, color = Palette.InkMuted)
                            Text(
                                if (d.hasOutcome) "reviewed" else d.reviewLabel,
                                style = Mono.Status,
                                color = Palette.InkFaint,
                            )
                        }
                    }
                }
            }
        }
        CollapsedSection("REFERENCE & EMERGENCY", LookSection.REFERENCES, state, onToggleSection) {
            if (state.references.isEmpty()) {
                Text("Photograph a document and its details land here, encrypted.", style = MaterialTheme.typography.bodyMedium, color = Palette.InkFaint)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.references.forEach { r ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(r.label, style = MaterialTheme.typography.titleMedium, color = if (r.emergency) Palette.Accent else Palette.Ink)
                                Text(r.value, style = MaterialTheme.typography.bodyLarge, color = Palette.InkMuted)
                            }
                            Text(r.sub, style = Mono.Status, color = Palette.InkFaint)
                        }
                    }
                }
            }
        }
        CollapsedSection("CONVERSATIONS", LookSection.CONVERSATIONS, state, onToggleSection) {
            if (state.sessions.isEmpty()) {
                Text("Past conversations are searchable here.", style = MaterialTheme.typography.bodyMedium, color = Palette.InkFaint)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.sessions.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = Palette.InkMuted) }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun QuietChip(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .neuSurface(cornerRadius = 14, elevation = 5.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = Palette.InkMuted)
    }
}

@Composable
private fun TrendLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Palette.InkMuted, modifier = Modifier.weight(1f))
        Text(value, style = Mono.Status, color = Palette.InkFaint)
    }
}

@Composable
private fun CollapsedSection(
    label: String,
    section: LookSection,
    state: LookUiState,
    onToggle: (LookSection) -> Unit,
    content: @Composable () -> Unit,
) {
    val expanded = state.expanded == section
    NeuCard(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onToggle(section) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionLabel(label, modifier = Modifier.weight(1f))
                Text(if (expanded) "–" else "+", style = MaterialTheme.typography.titleLarge, color = Palette.InkFaint)
            }
            if (expanded) {
                Spacer(Modifier.height(10.dp))
                content()
            }
        }
    }
}
