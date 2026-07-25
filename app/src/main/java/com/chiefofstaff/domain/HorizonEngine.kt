package com.chiefofstaff.domain

import com.chiefofstaff.core.Clock
import com.chiefofstaff.data.LifeRepository
import com.chiefofstaff.data.model.CommitmentState
import com.chiefofstaff.data.model.Domain
import kotlinx.coroutines.flow.first
import java.time.temporal.ChronoUnit

/**
 * DIR-10/11/12 + REV-06 — the horizon layer. Deterministic summaries of where you are against the
 * week, month and quarter: goals grouped by horizon, this week's committed load, where the week
 * actually went (time attribution), and drift — goals with no recent activity. The planner and the
 * weekly audit read these; nothing here spends a token.
 */
class HorizonEngine(
    private val repo: LifeRepository,
    private val clock: Clock,
) {
    /** DIR-12 — active goals grouped by horizon (year / quarter / month). */
    suspend fun goalsByHorizon(): Map<String, List<String>> =
        repo.graph.activeGoals().first()
            .groupBy { it.horizon.lowercase() }
            .mapValues { (_, goals) -> goals.map { g -> g.title + (g.metric?.let { " ($it→${g.target})" } ?: "") } }

    /** REV-06 — where the last 7 days went: resolved commitments by domain. */
    suspend fun weekAttribution(): List<String> {
        val since = clock.now().minus(7, ChronoUnit.DAYS).toEpochMilli()
        return repo.commitments.changedSince(since)
            .filter { it.state == CommitmentState.DONE }
            .groupingBy { it.domain }
            .eachCount()
            .filterKeys { it != Domain.NONE }
            .entries.sortedByDescending { it.value }
            .map { (d, n) -> "${d.name.lowercase().replaceFirstChar { c -> c.uppercase() }}: $n done" }
    }

    /** DIR-11 — drift: active goals untouched for 14+ days, with how long they've been quiet. */
    suspend fun drift(): List<String> {
        val now = clock.now()
        return repo.graph.activeGoals().first().mapNotNull { g ->
            val days = ChronoUnit.DAYS.between(g.lastTouchedAt, now)
            if (days < 14) null else "${g.title} — quiet ${days}d"
        }
    }

    /** DIR-11 — this week's committed load (items due in the next 7 days). */
    suspend fun weekLoad(): Int {
        val now = clock.now()
        val weekEnd = now.plus(7, ChronoUnit.DAYS)
        return repo.commitments.openCommitmentsNow().count { c ->
            val due = c.dueAt ?: return@count false
            !due.isBefore(now) && !due.isAfter(weekEnd)
        }
    }
}
