package com.chiefofstaff.intervention

import com.chiefofstaff.core.AppLog
import com.chiefofstaff.core.Clock
import com.chiefofstaff.data.LifeRepository
import com.chiefofstaff.data.entity.Note
import com.chiefofstaff.domain.ConsistencyScore
import com.chiefofstaff.domain.HorizonEngine
import com.chiefofstaff.domain.ProjectionEngine
import com.chiefofstaff.domain.ReductionEngine

/**
 * REV-07 monthly retro · REV-10 quarterly direction review · REV-11 annual synthesis. All the same
 * shape: a neutral snapshot against goals — consistency, where the effort went, what's drifting —
 * delivered as one essential notification and saved as a Note so the next period can compare.
 *
 * The longer the period, the more it looks forward as well as back: the quarterly and annual reviews
 * add scenario projections (DIR-16, "on current pace…") and the net-worth position (DOM-14), and the
 * annual one also carries the pruning proposal (RES-10 — a year is when accumulation is worth
 * clearing). Triggered from the nightly batch on period boundaries; no extra alarms.
 */
class PeriodReview(
    private val repo: LifeRepository,
    private val clock: Clock,
    private val consistency: ConsistencyScore,
    private val horizons: HorizonEngine,
    private val projections: ProjectionEngine,
    private val reduction: ReductionEngine,
    private val notifier: Notifier,
) {
    /** [period] is a human label: "Monthly", "Quarterly", or "Annual". */
    suspend fun run(period: String) {
        val annual = period == "Annual"
        val forward = annual || period == "Quarterly"

        val pct = consistency.asPercent()
        val attribution = horizons.weekAttribution()
        val drift = horizons.drift()
        val projLines = if (forward) runCatching { projections.projectionLines(if (annual) 180 else 90) }.getOrDefault(emptyList()) else emptyList()
        val netWorth = if (forward) netWorthLine() else null
        val pruneLines = if (annual) runCatching { reduction.annualPruningProposal() }.getOrDefault(emptyList()) else emptyList()

        val body = buildString {
            append("$period review: consistency $pct% over 30 days.")
            if (attribution.isNotEmpty()) append(" Effort: ${attribution.take(3).joinToString("; ")}.")
            if (drift.isNotEmpty()) append(" Drifting: ${drift.take(3).joinToString("; ")}.")
            if (netWorth != null) append("\n").append(netWorth)
            if (projLines.isNotEmpty()) append("\n").append(projLines.joinToString("\n"))
            if (pruneLines.isNotEmpty()) append("\nCould let go: ${pruneLines.joinToString("; ")}.")
        }
        notifier.post(
            channel = Channels.RITUAL,
            id = NotificationIds.PERIOD_REVIEW,
            title = "$period review",
            body = body,
            essential = true,   // a ritual (§9.1)
        )
        repo.graph.insertNote(Note(text = body, tags = listOf(period.lowercase(), "review"), createdAt = clock.now()))
        AppLog.i("review", "$period review delivered")
    }

    /** DOM-14 — latest net-worth snapshot with the change since the prior reading, if any. */
    private suspend fun netWorthLine(): String? {
        val obs = repo.graph.observations("net_worth", 2)
        val latest = obs.firstOrNull() ?: return null
        val prior = obs.getOrNull(1)
        val delta = prior?.let { latest.value - it.value }
        val deltaTxt = delta?.let {
            val sign = if (it >= 0) "+" else "-"
            " ($sign₹${kotlin.math.abs(it).toLong()} since last)"
        } ?: ""
        return "Net worth: ₹${latest.value.toLong()}$deltaTxt."
    }
}
