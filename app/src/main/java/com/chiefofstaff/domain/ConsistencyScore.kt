package com.chiefofstaff.domain

import com.chiefofstaff.core.Clock
import com.chiefofstaff.data.LifeRepository

/**
 * ACC-10 — a rolling 30-day consistency percentage, NOT a streak. The design is explicit that a
 * streak with a reset threat ("miss tonight and the run resets to zero") is exactly what kills the
 * product on the day you break one (§15, Direction 1b is the anti-example). This number is stated
 * as a fact — "consistency 78% over 30 days" — never as a score to defend, and there is nothing to
 * "break": a bad day lowers a rolling average slightly and recovers on its own.
 */
class ConsistencyScore(
    private val repo: LifeRepository,
    private val clock: Clock,
) {
    /** Fraction of commitments that reached ANY verdict (done/skipped/dropped) over 30 days. */
    suspend fun rolling30Day(): Float {
        val since = clock.today().minusDays(30).atStartOfDay(clock.zone()).toInstant().toEpochMilli()
        val resolved = repo.commitments.resolvedCountSince(since)
        val done = repo.commitments.doneCountSince(since)
        // Consistency here means "closed the loop", weighted toward completion but crediting any
        // honest verdict — reaching a verdict is the behaviour we care about (P4), not only "done".
        if (resolved == 0) return 0f
        return ((done + resolved).toFloat() / (2f * resolved)).coerceIn(0f, 1f)
    }

    suspend fun asPercent(): Int = (rolling30Day() * 100).toInt()
}
