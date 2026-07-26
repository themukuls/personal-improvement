package com.chiefofstaff.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.chiefofstaff.domain.EmotionalEngine
import com.chiefofstaff.domain.ProgressEngine
import com.chiefofstaff.ui.components.ChiefMascot
import com.chiefofstaff.ui.components.MascotState
import com.chiefofstaff.ui.components.NeuButton
import com.chiefofstaff.ui.components.NeuButtonNeutral
import com.chiefofstaff.ui.components.NeuCard
import com.chiefofstaff.ui.components.SectionLabel
import com.chiefofstaff.ui.theme.Palette
import com.chiefofstaff.ui.vm.ProgressUiState

/**
 * The Progress overlay. It answers "how am I actually doing?" from the data the app already keeps —
 * a rolling consistency figure (never a streak, RES-05), this week day-by-day, and the saved weekly
 * and period reviews. Everything shown here is local and travels with the backup.
 */
@Composable
fun ProgressScreen(
    state: ProgressUiState,
    onExport: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 12.dp),
    ) {
        ScreenTopBar(title = "Progress", onBack = onBack)
        Spacer(Modifier.height(14.dp))

        val snap = state.snapshot
        if (state.loading || snap == null) {
            Spacer(Modifier.height(60.dp))
            Text(
                "Gathering your week…",
                style = MaterialTheme.typography.bodyLarge,
                color = Palette.InkFaint,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            return@Column
        }

        HeadlineCard(snap)
        Spacer(Modifier.height(20.dp))

        SectionLabel("THIS WEEK")
        Spacer(Modifier.height(10.dp))
        WeekStrip(snap.week)
        Spacer(Modifier.height(8.dp))
        Text(
            "${snap.weekDone} done · ${snap.activeDays} active day${if (snap.activeDays == 1) "" else "s"} this week.",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.InkMuted,
        )
        Spacer(Modifier.height(24.dp))

        SectionLabel("REVIEWS")
        Spacer(Modifier.height(10.dp))
        if (snap.reviews.isEmpty()) {
            NeuCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Your first weekly review lands Sunday evening — a plain read of the week and one small thing to try next. Monthly and longer reviews build up here over time.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.InkMuted,
                )
            }
        } else {
            snap.reviews.forEach { r ->
                ReviewCard(r)
                Spacer(Modifier.height(10.dp))
            }
        }

        Spacer(Modifier.height(18.dp))
        NeuButtonNeutral(text = "Export progress", onClick = onExport, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        Text(
            "Everything here is saved on your device and included in the automatic backup.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.InkFaint,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HeadlineCard(snap: ProgressEngine.Snapshot) {
    NeuCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text("CONSISTENCY", style = MaterialTheme.typography.labelMedium, color = Palette.InkFaint)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${snap.consistencyPct}",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = Palette.Accent,
                )
                Text(
                    "%",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Palette.Accent,
                    modifier = Modifier.padding(bottom = 6.dp, start = 2.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "over the last 30 days — a rolling average, not a streak. A slow day just dips it a little, and it recovers on its own.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.InkMuted,
            )
        }
    }
}

@Composable
private fun WeekStrip(week: List<ProgressEngine.DayCell>) {
    val maxDone = (week.maxOfOrNull { it.done } ?: 0).coerceAtLeast(1)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        week.forEach { cell -> DayColumn(cell, maxDone) }
    }
}

@Composable
private fun DayColumn(cell: ProgressEngine.DayCell, maxDone: Int) {
    val emoji = EmotionalEngine.MOODS.firstOrNull { it.first == cell.mood }?.third
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(38.dp)) {
        // Count above the bar (only when there's something to show).
        Text(
            text = if (cell.done > 0) "${cell.done}" else "",
            style = MaterialTheme.typography.labelSmall,
            color = Palette.InkMuted,
        )
        Spacer(Modifier.height(4.dp))
        // Bar height scales with completions; empty days show a faint stub so the day still reads.
        val barHeight = if (cell.done == 0) 6.dp else (12 + (cell.done.toFloat() / maxDone) * 56).dp
        Box(
            modifier = Modifier
                .width(14.dp)
                .height(barHeight)
                .clip(RoundedCornerShape(7.dp))
                .background(if (cell.done > 0) Palette.Accent else Palette.SurfaceSunken),
        )
        Spacer(Modifier.height(6.dp))
        Text(text = emoji ?: "·", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(2.dp))
        // Today's label is emphasised so the strip has a clear "you are here".
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(if (cell.isToday) Palette.Accent else androidx.compose.ui.graphics.Color.Transparent)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(
                text = cell.dayLabel,
                style = MaterialTheme.typography.labelSmall,
                color = if (cell.isToday) androidx.compose.ui.graphics.Color.White else Palette.InkFaint,
            )
        }
    }
}

/**
 * The weekly recap pop-up — shown once when the app first opens in a new week (driven by
 * [com.chiefofstaff.ui.vm.ProgressViewModel]). A warm, non-judging read of the week just gone, with
 * a way straight into the fuller Progress view.
 */
@Composable
fun WeeklyRecapDialog(
    recap: ProgressEngine.WeekRecap,
    onViewProgress: () -> Unit,
    onDismiss: () -> Unit,
) {
    val moodEmoji = EmotionalEngine.MOODS.firstOrNull { it.first == recap.topMood }?.third
    Dialog(onDismissRequest = onDismiss) {
        NeuCard(modifier = Modifier.fillMaxWidth()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                ChiefMascot(state = MascotState.CELEBRATING, size = 54.dp)
                Spacer(Modifier.height(10.dp))
                Text("Last week", style = MaterialTheme.typography.labelMedium, color = Palette.InkFaint)
                Spacer(Modifier.height(2.dp))
                Text(
                    recap.label,
                    style = MaterialTheme.typography.titleLarge,
                    color = Palette.Ink,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    buildString {
                        append("You closed ${recap.done} thing${if (recap.done == 1) "" else "s"} across ${recap.activeDays} day${if (recap.activeDays == 1) "" else "s"}")
                        if (moodEmoji != null) append(", mostly feeling $moodEmoji")
                        append(".")
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = Palette.InkMuted,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Consistency ${recap.consistencyPct}% over 30 days — steady is the whole game.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.InkFaint,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                NeuButton(text = "See my progress", onClick = onViewProgress, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                NeuButtonNeutral(text = "Not now", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * The day-close recap — shown once when the app first opens on a new day. A brief, kind read of
 * yesterday; a quiet day is stated plainly with no guilt (RES-06).
 */
@Composable
fun DailyRecapDialog(
    recap: ProgressEngine.DayRecap,
    onViewProgress: () -> Unit,
    onDismiss: () -> Unit,
) {
    val moodEmoji = EmotionalEngine.MOODS.firstOrNull { it.first == recap.mood }?.third
    Dialog(onDismissRequest = onDismiss) {
        NeuCard(modifier = Modifier.fillMaxWidth()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                ChiefMascot(
                    state = if (recap.done > 0) MascotState.CELEBRATING else MascotState.CALM,
                    size = 54.dp,
                )
                Spacer(Modifier.height(10.dp))
                Text(recap.label, style = MaterialTheme.typography.labelMedium, color = Palette.InkFaint)
                Spacer(Modifier.height(8.dp))
                Text(
                    buildString {
                        if (recap.done > 0) {
                            append("You closed ${recap.done} thing${if (recap.done == 1) "" else "s"} yesterday")
                        } else {
                            append("Yesterday was a quiet one")
                        }
                        if (moodEmoji != null) append(" · $moodEmoji")
                        append(".")
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.Ink,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (recap.done > 0) "A fresh day now — one thing at a time."
                    else "A fresh day now — no debt carried over.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.InkMuted,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(18.dp))
                NeuButton(text = "See my week", onClick = onViewProgress, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                NeuButtonNeutral(text = "Start the day", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ReviewCard(r: ProgressEngine.ReviewEntry) {
    NeuCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Palette.Accent.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(r.kind, style = MaterialTheme.typography.labelSmall, color = Palette.Accent)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "${r.date.dayOfMonth} ${r.date.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.InkFaint,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(r.text, style = MaterialTheme.typography.bodyMedium, color = Palette.Ink)
        }
    }
}
