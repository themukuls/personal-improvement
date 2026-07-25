package com.chiefofstaff.domain

import com.chiefofstaff.core.Clock
import com.chiefofstaff.data.LifeRepository
import com.chiefofstaff.data.entity.Commitment
import com.chiefofstaff.data.model.CommitmentState
import com.chiefofstaff.data.model.Domain
import com.chiefofstaff.data.model.EnergyCost
import java.time.Duration
import java.time.LocalTime

/**
 * Direction/resilience insights computed deterministically from the current graph (§4.1):
 *  - DIR-08 conflict detection: commitments that will collide before they do,
 *  - DIR-05 overcommit warning + RES-07 load score: refuse to plan a full day when load is high,
 *  - MEM-08 open-loop detection: items that have quietly stalled.
 *
 * All neutral, factual, and cheap — no tokens spent on what arithmetic can answer.
 */
class InsightEngine(
    private val repo: LifeRepository,
    private val clock: Clock,
) {
    /** DIR-08 — pairs of timed commitments whose due times land within 30 minutes of each other. */
    suspend fun conflicts(): List<String> {
        val timed = repo.commitments.openCommitmentsNow()
            .filter { it.dueAt != null }
            .sortedBy { it.dueAt }
        val out = mutableListOf<String>()
        for (i in 0 until timed.size - 1) {
            val a = timed[i]; val b = timed[i + 1]
            val gap = Duration.between(a.dueAt, b.dueAt).abs()
            if (gap <= Duration.ofMinutes(30)) {
                out += "${a.what} and ${b.what} collide — both around ${hhmm(a.dueAt!!)}."
            }
        }
        return out
    }

    /**
     * RES-07 — a 0..1 load score for today from the number and energy cost of open, timed items.
     * Persisted to DayState so the planner (minimum-viable-day) and the Now warning can read it.
     */
    suspend fun computeAndStoreDayLoad(): Float {
        val today = repo.today()
        val open = repo.commitments.openCommitmentsNow()
        val weighted = open.sumOf { weight(it) }
        val score = (weighted / 12.0).coerceIn(0.0, 1.0).toFloat()   // ~12 weight-units = a full day
        repo.state.upsertDay(today.copy(loadScore = score, updatedAt = clock.now()))
        return score
    }

    /** DIR-05 — a neutral overcommit warning when today's load is high. Null when it isn't. */
    suspend fun overcommitWarning(): String? {
        val score = computeAndStoreDayLoad()
        if (score < 0.8f) return null
        val count = repo.commitments.openCommitmentsNow().size
        return "Today is heavy ($count open). Consider moving one or two — a full day at this load rarely holds."
    }

    /** MEM-08 — quietly stalled loops: deferred twice or more, or owed-to-you and overdue. */
    suspend fun openLoops(): List<String> {
        val stalled = repo.commitments.openCommitmentsNow()
            .filter { it.deferralCount >= 2 }
            .map { "${it.what} — deferred ${it.deferralCount}×, still open." }
        val overdueWaiting = repo.commitments.overdueWaiting(clock.now().toEpochMilli())
            .map { "${it.what} from ${it.who} — overdue and open." }
        return stalled + overdueWaiting
    }

    /**
     * ACC-12 — sensor contradiction: a movement commitment marked done today, but the step count
     * says otherwise. Surfaced quietly (a flag, never an accusation) — the sensor might be wrong too.
     */
    suspend fun sensorContradictions(): List<String> {
        val since = clock.today().atStartOfDay(clock.zone()).toInstant().toEpochMilli()
        val movementWords = listOf("gym", "walk", "run", "steps", "workout", "jog")
        val claimed = repo.commitments.changedSince(since)
            .filter { it.state == CommitmentState.DONE && it.domain == Domain.HEALTH }
            .filter { c -> movementWords.any { c.what.lowercase().contains(it) } }
        if (claimed.isEmpty()) return emptyList()
        val steps = repo.graph.observations("steps", 1).firstOrNull()?.value ?: return emptyList()
        if (steps >= 2000) return emptyList()
        return claimed.map { "\"${it.what}\" is marked done, but only ${steps.toInt()} steps are logged today." }
    }

    private fun weight(c: Commitment): Double {
        val base = when (c.energyCost) {
            EnergyCost.LOW -> 1.0
            EnergyCost.MEDIUM -> 2.0
            EnergyCost.HIGH -> 3.5
        }
        return if (c.dueAt != null) base else base * 0.5   // untimed items weigh less
    }

    private fun hhmm(instant: java.time.Instant): String =
        LocalTime.ofInstant(instant, clock.zone()).let { "%02d:%02d".format(it.hour, it.minute) }
}
