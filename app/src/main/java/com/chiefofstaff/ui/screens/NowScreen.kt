package com.chiefofstaff.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.InsertChartOutlined
import androidx.compose.material.icons.outlined.WbSunny
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
import com.chiefofstaff.ui.components.NeuButton
import com.chiefofstaff.ui.components.NeuButtonNeutral
import com.chiefofstaff.ui.components.NeuCard
import com.chiefofstaff.ui.components.SectionLabel
import com.chiefofstaff.ui.theme.Mono
import com.chiefofstaff.ui.theme.Palette
import com.chiefofstaff.ui.theme.glassSurface
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
    onSetMood: (String) -> Unit,
    onSetMode: (com.chiefofstaff.data.model.Mode) -> Unit,
    onToggleQuiet: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenProgress: () -> Unit,
    onRelationshipAnswer: (String, Int) -> Unit,
    onRelationshipSayMore: () -> Unit,
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
        GreetingHeader(
            greeting = state.greeting,
            name = state.name,
            mode = state.mode,
            quiet = state.quiet,
            celebrating = state.allDoneToday,
            onSetMode = onSetMode,
            onToggleQuiet = onToggleQuiet,
            onOpenProfile = onOpenProfile,
            onOpenCalendar = onOpenCalendar,
            onOpenProgress = onOpenProgress,
        )
        Spacer(Modifier.height(10.dp))
        Text(state.statusLine, style = Mono.Status, color = Palette.InkFaint)

        // RES-03 — re-entry after an absence takes over Now entirely: welcome back, just today.
        val showReentry = state.reentryDays >= 3 && !reentryDismissed
        if (showReentry) {
            Spacer(Modifier.height(16.dp))
            NeuCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SectionLabel("WELCOME BACK")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "It's been ${state.reentryDays} days. I kept things quiet — here's just today, nothing to catch up on.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Palette.InkMuted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    NeuButtonNeutral(text = "Thanks", onClick = { reentryDismissed = true })
                }
            }
        }

        // One compact check-in — mood + energy in a single card, only until both are set (not two).
        val needMood = state.wellbeing && state.moodToday == null
        val needEnergy = state.energyToday == null
        if (!showReentry && (needMood || needEnergy) && !state.loading) {
            Spacer(Modifier.height(16.dp))
            NeuCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    if (needMood) {
                        MoodTap(onSetMood)
                        if (needEnergy) Spacer(Modifier.height(16.dp))
                    }
                    if (needEnergy) EnergyTap(onSetEnergy)
                }
            }
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

        // ONE moment per day, never a stack — priority: the relationship ask, else what's looking
        // ahead, else the empathetic support line. Suppressed entirely during re-entry.
        if (!showReentry) {
            val prompt = state.relationship
            val ahead = state.anticipation
            val support = state.supportLine
            when {
                prompt != null -> {
                    Spacer(Modifier.height(18.dp))
                    RelationshipCard(
                        prompt = prompt,
                        onAnswer = { index -> onRelationshipAnswer(prompt.id, index) },
                        onSayMore = onRelationshipSayMore,
                    )
                }
                ahead != null -> {
                    Spacer(Modifier.height(18.dp))
                    AnticipationCard(ahead, onAddCommitment, onAnticipationRated)
                }
                !support.isNullOrBlank() -> {
                    Spacer(Modifier.height(16.dp))
                    SupportCard(support)
                }
            }
        }

        Spacer(Modifier.height(22.dp))
        SectionLabel("TODAY")
        Spacer(Modifier.height(12.dp))
        // A quiet single-column list of row-tiles (time · title · subline · check) — subtle, not
        // shouty grid cards, matching the reference "then today" list.
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
            WelcomeTile(name = state.name, onStart = onAddCommitment)
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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun RelationshipCard(
    prompt: com.chiefofstaff.domain.RelationshipEngine.Prompt,
    onAnswer: (Int) -> Unit,
    onSayMore: () -> Unit,
) {
    val isAsk = prompt.kind == com.chiefofstaff.domain.RelationshipEngine.Kind.ASK
    Box(modifier = Modifier.fillMaxWidth().glassSurface(cornerRadius = 22).padding(16.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(28.dp).clip(CircleShape).background(com.chiefofstaff.ui.theme.AvatarGradient),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(15.dp))
                }
                Spacer(Modifier.width(10.dp))
                SectionLabel(if (isAsk) "A QUESTION FOR YOU" else "SOMETHING I NOTICED")
            }
            Spacer(Modifier.height(12.dp))
            Text(prompt.text, style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
            Spacer(Modifier.height(14.dp))
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                prompt.answers.forEachIndexed { index, answer ->
                    Box(
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Palette.AccentSoft)
                            .clickable { onAnswer(index) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(answer.label, style = MaterialTheme.typography.labelLarge, color = Palette.Accent)
                    }
                }
            }
            if (prompt.sayMore) {
                GhostButton("Say more →", onClick = onSayMore)
            }
        }
    }
}

@Composable
private fun WelcomeTile(name: String, onStart: () -> Unit) {
    NeuCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onStart)) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Outlined.WbSunny,
                contentDescription = null,
                tint = Palette.Accent,
                modifier = Modifier.size(28.dp).padding(bottom = 12.dp),
            )
            Text(
                text = if (name.isBlank()) "Ready when you are" else "Ready when you are, $name",
                style = MaterialTheme.typography.titleLarge,
                color = Palette.Ink,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Nothing here yet — and that's fine. Hold the mic below and just tell me your first thing, or tap here to type it.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.InkMuted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
private fun SupportCard(line: String) {
    Box(modifier = Modifier.fillMaxWidth().glassSurface(cornerRadius = 22).padding(16.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier.size(34.dp).clip(CircleShape).background(Palette.AccentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.FavoriteBorder,
                    contentDescription = null,
                    tint = Palette.Accent,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                line,
                style = MaterialTheme.typography.bodyLarge,
                color = Palette.Ink,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MoodTap(onSetMood: (String) -> Unit) {
    Column {
        SectionLabel("HOW ARE YOU FEELING?")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            com.chiefofstaff.domain.EmotionalEngine.MOODS.forEach { (key, label, emoji) ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .neuSurface(cornerRadius = 16, elevation = 5.dp)
                        .clickable { onSetMood(key) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(emoji, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(2.dp))
                        Text(label, style = MaterialTheme.typography.bodySmall, color = Palette.InkFaint)
                    }
                }
            }
        }
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
private fun GreetingHeader(
    greeting: String,
    name: String,
    mode: com.chiefofstaff.data.model.Mode,
    quiet: Boolean,
    celebrating: Boolean,
    onSetMode: (com.chiefofstaff.data.model.Mode) -> Unit,
    onToggleQuiet: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenProgress: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Tappable mascot → the user's profile & options. Celebrates when the day's done.
        com.chiefofstaff.ui.components.ChiefMascot(
            state = if (celebrating) com.chiefofstaff.ui.components.MascotState.CELEBRATING
            else com.chiefofstaff.ui.components.MascotState.CALM,
            size = 46.dp,
            modifier = Modifier.clickable(onClick = onOpenProfile)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.WbSunny,
                    contentDescription = null,
                    tint = Palette.Accent,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "${greeting.ifBlank { "Hello" }}!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.InkMuted,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (name.isBlank()) "Welcome 👋" else "$name 👋",
                style = MaterialTheme.typography.headlineMedium,
                color = Palette.Ink,
            )
        }
        // Compact mode + quiet control, tucked into the header (freeing the body).
        ModeChipMenu(mode = mode, quiet = quiet, onSetMode = onSetMode, onToggleQuiet = onToggleQuiet)
        Spacer(Modifier.width(8.dp))
        // Progress → consistency, this week, saved reviews.
        HeaderIconButton(
            icon = Icons.Outlined.InsertChartOutlined,
            label = "Progress",
            onClick = onOpenProgress,
        )
        Spacer(Modifier.width(8.dp))
        // Calendar → month heatmap + day detail.
        HeaderIconButton(
            icon = Icons.Outlined.CalendarMonth,
            label = "Calendar",
            onClick = onOpenCalendar,
        )
    }
}

/** A round, sunken header action button — used for the calendar and progress entries. */
@Composable
private fun HeaderIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .neuSurface(cornerRadius = 20, elevation = 6.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = Palette.InkMuted, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ModeChipMenu(
    mode: com.chiefofstaff.data.model.Mode,
    quiet: Boolean,
    onSetMode: (com.chiefofstaff.data.model.Mode) -> Unit,
    onToggleQuiet: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = com.chiefofstaff.data.model.Mode.entries
    fun label(m: com.chiefofstaff.data.model.Mode) =
        m.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(if (quiet) Palette.AccentSoft else Palette.SurfaceSunken)
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = (if (quiet) "🔕 " else "") + label(mode),
                style = MaterialTheme.typography.labelLarge,
                color = if (quiet) Palette.Accent else Palette.Ink,
            )
            Text(" ▾", style = MaterialTheme.typography.labelLarge, color = Palette.InkFaint)
        }
        androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { m ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(label(m), color = if (m == mode) Palette.Accent else Palette.Ink) },
                    onClick = { onSetMode(m); expanded = false },
                )
            }
            androidx.compose.material3.HorizontalDivider()
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(if (quiet) "Quiet: on" else "Quiet: off", color = if (quiet) Palette.Accent else Palette.Ink) },
                onClick = { onToggleQuiet(); expanded = false },
            )
        }
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
