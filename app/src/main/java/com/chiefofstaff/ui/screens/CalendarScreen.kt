package com.chiefofstaff.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.chiefofstaff.AppContainer
import com.chiefofstaff.data.entity.EventEntity
import com.chiefofstaff.ui.components.CosTextField
import com.chiefofstaff.ui.components.GhostButton
import com.chiefofstaff.ui.components.NeuButton
import com.chiefofstaff.ui.components.NeuButtonNeutral
import com.chiefofstaff.ui.components.NeuCard
import com.chiefofstaff.ui.components.SectionLabel
import com.chiefofstaff.ui.theme.Mono
import com.chiefofstaff.ui.theme.Palette
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private data class CalItem(
    val id: Long,
    val time: String?,
    val startMillis: Long,
    val minutes: Int,
    val title: String,
    val isEvent: Boolean,
    val done: Boolean = false,
)

/** Editor state for adding or editing a calendar event. */
private data class EventDraft(
    val eventId: Long?,          // null = new
    val title: String,
    val start: LocalDateTime,
    val durationMin: Int,
)

/**
 * A month calendar with a load heatmap, and full event management: add via the + button, long-press
 * an event to edit or delete it. Editing lets you set the title, the start date & time, and pick a
 * duration (15 / 30 / 45 / 60 min) for that start. Reached from the header calendar icon.
 */
@Composable
fun CalendarScreen(container: AppContainer, onBack: () -> Unit) {
    val clock = container.clock
    val zone = clock.zone()
    val today = clock.today()
    val scope = rememberCoroutineScope()

    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var selected by remember { mutableStateOf(today) }
    var byDay by remember { mutableStateOf<Map<LocalDate, List<CalItem>>>(emptyMap()) }
    var refresh by remember { mutableIntStateOf(0) }
    var draft by remember { mutableStateOf<EventDraft?>(null) }
    var menuFor by remember { mutableStateOf<CalItem?>(null) }

    androidx.compose.runtime.LaunchedEffect(month, refresh) {
        val first = month.atDay(1)
        val fromMillis = first.atStartOfDay(zone).toInstant().toEpochMilli()
        val toMillis = first.plusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val map = mutableMapOf<LocalDate, MutableList<CalItem>>()
        container.repo.commitments.allCommitments().forEach { c ->
            val due = c.dueAt ?: return@forEach
            val d = due.atZone(zone).toLocalDate()
            if (YearMonth.from(d) == month) {
                val t = due.atZone(zone).toLocalTime()
                map.getOrPut(d) { mutableListOf() }.add(
                    CalItem(
                        id = c.id, time = "%02d:%02d".format(t.hour, t.minute), startMillis = due.toEpochMilli(),
                        minutes = 30, title = c.what, isEvent = false,
                        done = c.state == com.chiefofstaff.data.model.CommitmentState.DONE,
                    )
                )
            }
        }
        container.repo.graph.eventsBetween(fromMillis, toMillis).forEach { e ->
            val d = e.start.atZone(zone).toLocalDate()
            val t = e.start.atZone(zone).toLocalTime()
            val mins = e.end?.let { ((it.toEpochMilli() - e.start.toEpochMilli()) / 60_000L).toInt().coerceIn(0, 24 * 60) } ?: 60
            map.getOrPut(d) { mutableListOf() }.add(
                CalItem(id = e.id, time = "%02d:%02d".format(t.hour, t.minute), startMillis = e.start.toEpochMilli(),
                    minutes = mins, title = e.title, isEvent = true)
            )
        }
        byDay = map.mapValues { (_, v) -> v.sortedBy { it.time ?: "zz" } }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.Base)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 12.dp),
    ) {
        ScreenTopBar(title = "Calendar", onBack = onBack)
        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}",
                style = MaterialTheme.typography.titleLarge, color = Palette.Ink, modifier = Modifier.weight(1f),
            )
            NavArrow(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Previous month") { month = month.minusMonths(1) }
            Spacer(Modifier.width(4.dp))
            NavArrow(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Next month") { month = month.plusMonths(1) }
        }
        Spacer(Modifier.height(14.dp))

        Row(Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { d ->
                Text(d, style = Mono.SectionLabel, color = Palette.InkFaint, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(6.dp))

        val lead = (month.atDay(1).dayOfWeek.value + 6) % 7
        val cells = buildList<LocalDate?> {
            repeat(lead) { add(null) }
            for (day in 1..month.lengthOfMonth()) add(month.atDay(day))
            while (size % 7 != 0) add(null)
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    if (date == null) Spacer(Modifier.weight(1f).aspectRatio(1f))
                    else DayCell(
                        day = date.dayOfMonth, level = levelFor(byDay[date].orEmpty()),
                        isToday = date == today, isSelected = date == selected,
                        modifier = Modifier.weight(1f), onClick = { selected = date },
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        HeatLegend()

        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(
                "${selected.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())}, " +
                    "${selected.dayOfMonth} ${selected.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())}",
                modifier = Modifier.weight(1f),
            )
            AddEventButton { draft = EventDraft(null, "", selected.atTime(9, 0), 30) }
        }
        Spacer(Modifier.height(10.dp))

        val items = byDay[selected].orEmpty()
        if (items.isEmpty()) {
            NeuCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Nothing scheduled. Tap + to add an event, or say it in Talk.",
                    style = MaterialTheme.typography.bodyMedium, color = Palette.InkMuted,
                )
            }
        } else {
            items.forEach { item ->
                DayItemRow(item = item, onLongPress = { if (item.isEvent) menuFor = item })
                Spacer(Modifier.height(10.dp))
            }
            Text(
                "Long-press an event to edit or delete it.",
                style = MaterialTheme.typography.bodySmall, color = Palette.InkFaint,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
    }

    // Long-press action sheet.
    menuFor?.let { item ->
        EventActionDialog(
            title = item.title,
            onEdit = {
                draft = EventDraft(
                    eventId = item.id, title = item.title,
                    start = LocalDateTime.ofInstant(Instant.ofEpochMilli(item.startMillis), zone),
                    durationMin = item.minutes.coerceAtLeast(15),
                )
                menuFor = null
            },
            onDelete = { scope.launch { container.repo.graph.deleteEvent(item.id); refresh++ }; menuFor = null },
            onDismiss = { menuFor = null },
        )
    }

    // Add / edit editor.
    draft?.let { d ->
        EventEditorDialog(
            draft = d,
            onChange = { draft = it },
            onSave = {
                scope.launch {
                    val start = d.start.atZone(zone).toInstant()
                    container.repo.graph.upsertEvent(
                        EventEntity(
                            id = d.eventId ?: 0L,
                            title = d.title.ifBlank { "Event" },
                            start = start,
                            end = start.plusSeconds(d.durationMin * 60L),
                            source = "manual",
                            createdAt = clock.now(),
                        )
                    )
                    refresh++
                }
                selected = d.start.toLocalDate()
                draft = null
            },
            onDismiss = { draft = null },
        )
    }
}

@Composable
private fun AddEventButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(36.dp).clip(CircleShape).background(Palette.Accent).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.Add, contentDescription = "Add event", tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun EventActionDialog(title: String, onEdit: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        NeuCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NeuButton("Edit", onClick = onEdit, modifier = Modifier.weight(1f))
                    NeuButtonNeutral("Delete", onClick = onDelete, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    GhostButton("Cancel", onClick = onDismiss)
                }
            }
        }
    }
}

@Composable
private fun EventEditorDialog(
    draft: EventDraft,
    onChange: (EventDraft) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        NeuCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(
                    if (draft.eventId == null) "New event" else "Edit event",
                    style = MaterialTheme.typography.titleLarge, color = Palette.Ink,
                )
                Spacer(Modifier.height(16.dp))
                SectionLabel("TITLE")
                Spacer(Modifier.height(8.dp))
                CosTextField(
                    value = draft.title, onValueChange = { onChange(draft.copy(title = it)) },
                    placeholder = "e.g. Team sync", modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(16.dp))
                SectionLabel("STARTS")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PickerChip(
                        text = draft.start.toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM")),
                        modifier = Modifier.weight(1f),
                    ) {
                        val d = draft.start
                        DatePickerDialog(context, { _, y, m, day ->
                            onChange(draft.copy(start = LocalDate.of(y, m + 1, day).atTime(d.toLocalTime())))
                        }, d.year, d.monthValue - 1, d.dayOfMonth).show()
                    }
                    PickerChip(
                        text = "%02d:%02d".format(draft.start.hour, draft.start.minute),
                        modifier = Modifier.weight(1f),
                    ) {
                        val d = draft.start
                        TimePickerDialog(context, { _, h, min ->
                            onChange(draft.copy(start = d.toLocalDate().atTime(LocalTime.of(h, min))))
                        }, d.hour, d.minute, false).show()
                    }
                }

                Spacer(Modifier.height(16.dp))
                SectionLabel("FOR HOW LONG")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15 to "15 min", 30 to "30 min", 45 to "45 min", 60 to "1 hour").forEach { (mins, label) ->
                        DurationChip(label, selected = draft.durationMin == mins, modifier = Modifier.weight(1f)) {
                            onChange(draft.copy(durationMin = mins))
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                NeuButton(if (draft.eventId == null) "Add event" else "Save", onClick = onSave, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    GhostButton("Cancel", onClick = onDismiss)
                }
            }
        }
    }
}

@Composable
private fun PickerChip(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.SurfaceSunken)
            .border(1.dp, Palette.Hairline, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = Palette.Ink)
    }
}

@Composable
private fun DurationChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Palette.Accent else Palette.SurfaceSunken)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, style = MaterialTheme.typography.labelMedium,
            color = if (selected) Color.White else Palette.InkMuted,
        )
    }
}

@Composable
private fun NavArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(38.dp).clip(CircleShape).background(Palette.Surface).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = desc, tint = Palette.InkMuted, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun DayCell(
    day: Int, level: Int, isToday: Boolean, isSelected: Boolean,
    modifier: Modifier = Modifier, onClick: () -> Unit,
) {
    val bg = when (level) {
        0 -> Palette.Surface
        1 -> Palette.Accent.copy(alpha = 0.16f)
        2 -> Palette.Accent.copy(alpha = 0.40f)
        else -> Palette.Accent.copy(alpha = 0.75f)
    }
    val fg = if (level >= 3) Color.White else Palette.Ink
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .then(if (isSelected) Modifier.border(2.dp, Palette.Accent, RoundedCornerShape(12.dp)) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$day",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal),
            color = if (isToday && level < 3) Palette.Accent else fg,
        )
    }
}

@Composable
private fun HeatLegend() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Less", style = MaterialTheme.typography.bodySmall, color = Palette.InkFaint)
        Spacer(Modifier.width(6.dp))
        listOf(0, 1, 2, 3).forEach { lvl ->
            val c = when (lvl) {
                0 -> Palette.Surface
                1 -> Palette.Accent.copy(alpha = 0.16f)
                2 -> Palette.Accent.copy(alpha = 0.40f)
                else -> Palette.Accent.copy(alpha = 0.75f)
            }
            Box(Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).background(c).border(1.dp, Palette.Hairline, RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(4.dp))
        }
        Text("More", style = MaterialTheme.typography.bodySmall, color = Palette.InkFaint)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayItemRow(item: CalItem, onLongPress: () -> Unit) {
    NeuCard(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = onLongPress)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                item.time ?: "—",
                style = Mono.Time,
                color = if (item.isEvent) Palette.Accent else Palette.InkFaint,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (item.isEvent) Palette.AccentSoft else Palette.SurfaceSunken)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(item.title, style = MaterialTheme.typography.titleSmall, color = Palette.Ink, modifier = Modifier.weight(1f))
            Text(
                if (item.isEvent) "event" else if (item.done) "done" else "task",
                style = MaterialTheme.typography.bodySmall, color = Palette.InkFaint,
            )
        }
    }
}

/** Heatmap intensity for a day from its booked minutes (events) + number of commitments. */
private fun levelFor(items: List<CalItem>): Int {
    if (items.isEmpty()) return 0
    val bookedHours = items.sumOf { it.minutes } / 60f
    return when {
        bookedHours >= 4f || items.size >= 5 -> 3
        bookedHours >= 2f || items.size >= 3 -> 2
        else -> 1
    }
}
