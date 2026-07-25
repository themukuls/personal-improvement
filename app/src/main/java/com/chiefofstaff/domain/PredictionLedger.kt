package com.chiefofstaff.domain

import com.chiefofstaff.core.Clock
import com.chiefofstaff.data.LifeRepository
import com.chiefofstaff.data.entity.Commitment
import com.chiefofstaff.data.entity.Prediction
import com.chiefofstaff.data.model.Verdict
import java.time.Duration
import java.time.LocalTime

/**
 * §1.3 / ACC-05 — the prediction ledger, the thing that makes "self-aware" buildable. When the
 * planner says "you'll finish this by 4pm", that is written here. When the evening close resolves
 * the item, the miss is recorded with a signed delta. Over weeks this yields a system that knows
 * it is bad at estimating Tuesday evenings and stops planning them optimistically (REV-09).
 */
class PredictionLedger(
    private val repo: LifeRepository,
    private val clock: Clock,
) {
    /** Record a "done by HH:mm" prediction for a commitment at plan time. */
    suspend fun predictCompletion(commitment: Commitment, byTime: LocalTime, confidence: Float) {
        repo.state.insertPrediction(
            Prediction(
                predictedAt = clock.now(),
                subjectId = commitment.id,
                subjectKind = "commitment",
                prediction = "done_by=%02d:%02d".format(byTime.hour, byTime.minute),
                confidence = confidence.coerceIn(0f, 1f),
            )
        )
    }

    /**
     * Resolve every open prediction about a commitment when its verdict lands. Delta is expressed
     * in hours of error for time predictions: negative = earlier than predicted, positive = later
     * or missed. A skipped/dropped item resolves its prediction as a miss.
     */
    suspend fun resolveForCommitment(commitment: Commitment, verdict: Verdict) {
        val open = repo.state.openPredictionsFor(commitment.id)
        if (open.isEmpty()) return
        val now = clock.now()
        val nowLocal = LocalTime.ofInstant(now, clock.zone())

        open.forEach { p ->
            val actual: String
            val delta: Float
            when (verdict) {
                Verdict.DONE -> {
                    val predicted = parsePredictedTime(p.prediction)
                    actual = "done_at=%02d:%02d".format(nowLocal.hour, nowLocal.minute)
                    delta = predicted?.let {
                        Duration.between(it, nowLocal).toMinutes() / 60f
                    } ?: 0f
                }
                Verdict.SKIPPED, Verdict.DROPPED -> {
                    actual = "not_done"
                    delta = 1f          // full miss, counts against optimism
                }
                Verdict.DEFERRED -> return@forEach   // not resolved yet; still open
            }
            repo.state.updatePrediction(p.copy(actual = actual, resolvedAt = now, delta = delta))
        }
    }

    /** REV-09 — mean absolute error over the last 30 days, surfaced in the self-accuracy report. */
    suspend fun meanAbsErrorLast30Days(): Float? {
        val since = clock.today().minusDays(30).atStartOfDay(clock.zone()).toInstant().toEpochMilli()
        return repo.state.meanAbsErrorSince(since)
    }

    /**
     * ACC-13 / REV-09 — the self-accuracy report. Keeps the system's fallibility visible (§16.10):
     * how many predictions resolved, how often it was within an hour, and the average miss. Null
     * when there isn't enough resolved history yet.
     */
    suspend fun accuracyReport(): String? {
        val since = clock.today().minusDays(30).atStartOfDay(clock.zone()).toInstant().toEpochMilli()
        val resolved = repo.state.resolvedSince(since).filter { it.delta != null }
        if (resolved.isEmpty()) return null
        val n = resolved.size
        val within = resolved.count { kotlin.math.abs(it.delta!!) <= 1f }
        val mae = resolved.map { kotlin.math.abs(it.delta!!) }.average()
        return "$n resolved · ${within * 100 / n}% within 1h · avg miss ${"%.1f".format(mae)}h"
    }

    private fun parsePredictedTime(prediction: String): LocalTime? =
        Regex("done_by=(\\d{2}):(\\d{2})").find(prediction)?.let {
            LocalTime.of(it.groupValues[1].toInt(), it.groupValues[2].toInt())
        }
}
