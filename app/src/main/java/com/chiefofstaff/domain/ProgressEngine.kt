package com.chiefofstaff.domain

import com.chiefofstaff.core.Clock
import com.chiefofstaff.data.LifeRepository
import java.time.LocalDate
import java.time.temporal.IsoFields

/**
 * Turns the locally-persisted history — DayState (mood/energy per day), commitment verdicts, and the
 * weekly/period review Notes — into a single snapshot the Progress screen can render, plus the
 * once-a-week recap the app shows when a new week has started. Nothing here writes; it only reads
 * what the rituals and the day already saved (P4/P11), so it works offline and survives reinstall
 * along with the rest of the Room data.
 */
class ProgressEngine(
    private val repo: LifeRepository,
    private val clock: Clock,
    private val consistency: ConsistencyScore,
) {
    data class DayCell(
        val date: LocalDate,
        val dayLabel: String,      // "Mon"
        val done: Int,
        val mood: String?,         // key, e.g. "good"
        val energy: Int?,          // 1..5
        val isToday: Boolean,
    )

    data class ReviewEntry(
        val date: LocalDate,
        val kind: String,          // "Weekly", "Monthly", "Quarterly", "Annual"
        val text: String,
    )

    data class Snapshot(
        val consistencyPct: Int,
        val weekDone: Int,
        val weekResolved: Int,
        val activeDays: Int,       // days this week with any completion or check-in
        val week: List<DayCell>,   // Mon..today of the current ISO week
        val reviews: List<ReviewEntry>,
    )

    data class WeekRecap(
        val label: String,         // "Week of 14 Jul"
        val done: Int,
        val activeDays: Int,
        val consistencyPct: Int,
        val topMood: String?,      // most common mood key that week
    )

    data class DayRecap(
        val label: String,         // "Yesterday · Fri 25 Jul"
        val done: Int,
        val mood: String?,
        val energy: Int?,
    )

    suspend fun snapshot(): Snapshot {
        val today = clock.today()
        val monday = today.with(java.time.DayOfWeek.MONDAY)
        val days = mutableListOf<DayCell>()
        val stateByDate = repo.state.daysSince(monday.toString()).associateBy { it.date }

        var d = monday
        var weekDone = 0
        var active = 0
        while (!d.isAfter(today)) {
            val done = doneOn(d)
            val st = stateByDate[d.toString()]
            weekDone += done
            if (done > 0 || st?.mood != null || st?.energy != null) active++
            days += DayCell(
                date = d,
                dayLabel = d.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()),
                done = done,
                mood = st?.mood,
                energy = st?.energy,
                isToday = d == today,
            )
            d = d.plusDays(1)
        }
        val weekResolved = resolvedBetween(monday, today.plusDays(1))

        return Snapshot(
            consistencyPct = runCatching { consistency.asPercent() }.getOrDefault(0),
            weekDone = weekDone,
            weekResolved = weekResolved,
            activeDays = active,
            week = days,
            reviews = reviews(12),
        )
    }

    /** The saved weekly-audit / period-review narratives, newest first. */
    suspend fun reviews(limit: Int): List<ReviewEntry> {
        val zone = clock.zone()
        return repo.graph.recentNotes(200)
            .mapNotNull { note ->
                val kind = when {
                    "weekly_audit" in note.tags -> "Weekly"
                    "annual" in note.tags -> "Annual"
                    "quarterly" in note.tags -> "Quarterly"
                    "monthly" in note.tags -> "Monthly"
                    "review" in note.tags -> "Review"
                    else -> return@mapNotNull null
                }
                ReviewEntry(
                    date = note.createdAt.atZone(zone).toLocalDate(),
                    kind = kind,
                    text = note.text.removePrefix("Weekly change: ").trim(),
                )
            }
            .take(limit)
    }

    /**
     * The recap to show once when a new week has begun: the ISO week that just ended. Returns null if
     * that week had no activity at all (nothing worth interrupting the user for).
     */
    suspend fun lastWeekRecap(): WeekRecap? {
        val thisMonday = clock.today().with(java.time.DayOfWeek.MONDAY)
        val start = thisMonday.minusWeeks(1)
        val endExclusive = thisMonday
        val done = resolvedNarrow(start, endExclusive) // done count over the week
        val states = repo.state.daysSince(start.toString()).filter {
            val ld = runCatching { LocalDate.parse(it.date) }.getOrNull()
            ld != null && !ld.isBefore(start) && ld.isBefore(endExclusive)
        }
        val active = (0 until 7).count { i ->
            val day = start.plusDays(i.toLong())
            doneOn(day) > 0 || states.any { it.date == day.toString() && (it.mood != null || it.energy != null) }
        }
        if (done == 0 && active == 0) return null

        val moods = states.mapNotNull { it.mood }
        val topMood = moods.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        val label = "Week of ${start.dayOfMonth} ${start.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())}"
        return WeekRecap(
            label = label,
            done = done,
            activeDays = active,
            consistencyPct = runCatching { consistency.asPercent() }.getOrDefault(0),
            topMood = topMood,
        )
    }

    /**
     * The recap for the day just gone, shown once when the app first opens on a new day. Returns null
     * if yesterday had nothing to report (no completions and no check-in) — a fresh day just starts.
     */
    suspend fun lastDayRecap(): DayRecap? {
        val yesterday = clock.today().minusDays(1)
        val done = doneOn(yesterday)
        val st = repo.state.day(yesterday.toString())
        if (done == 0 && st?.mood == null && st?.energy == null) return null
        val label = "Yesterday · ${yesterday.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())} " +
            "${yesterday.dayOfMonth} ${yesterday.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())}"
        return DayRecap(label = label, done = done, mood = st?.mood, energy = st?.energy)
    }

    /** The current day key ("2026-07-26"), used to fire the day recap only once per new day. */
    fun currentDayKey(): String = clock.today().toString()

    /** A plain-text export of everything tracked, for the system share sheet (DOM-export). */
    suspend fun exportText(): String {
        val s = snapshot()
        return buildString {
            appendLine("Chief of Staff — progress export")
            appendLine("Generated ${clock.today()}")
            appendLine()
            appendLine("30-day consistency: ${s.consistencyPct}%")
            appendLine("This week: ${s.weekDone} done · ${s.activeDays} active day${if (s.activeDays == 1) "" else "s"}")
            appendLine()
            appendLine("Day by day (this week):")
            s.week.forEach { c ->
                appendLine("  ${c.dayLabel} ${c.date} — ${c.done} done${c.mood?.let { ", mood $it" } ?: ""}${c.energy?.let { ", energy $it/5" } ?: ""}")
            }
            if (s.reviews.isNotEmpty()) {
                appendLine()
                appendLine("Saved reviews:")
                s.reviews.forEach { r -> appendLine("  [${r.kind} · ${r.date}] ${r.text}") }
            }
        }
    }

    /** The ISO "YYYY-Www" key for the current week, used to fire the recap only once per new week. */
    fun currentWeekKey(): String {
        val today = clock.today()
        val week = today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        val year = today.get(IsoFields.WEEK_BASED_YEAR)
        return "%d-W%02d".format(year, week)
    }

    private suspend fun doneOn(day: LocalDate): Int {
        val zone = clock.zone()
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return repo.commitments.doneBetween(start, end)
    }

    private suspend fun resolvedBetween(startInclusive: LocalDate, endExclusive: LocalDate): Int {
        val zone = clock.zone()
        val start = startInclusive.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = endExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
        return repo.commitments.resolvedBetween(start, end)
    }

    private suspend fun resolvedNarrow(startInclusive: LocalDate, endExclusive: LocalDate): Int {
        val zone = clock.zone()
        val start = startInclusive.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = endExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
        return repo.commitments.doneBetween(start, end)
    }
}
