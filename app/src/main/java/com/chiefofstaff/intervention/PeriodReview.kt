package com.chiefofstaff.intervention

import com.chiefofstaff.core.AppLog
import com.chiefofstaff.core.Clock
import com.chiefofstaff.data.LifeRepository
import com.chiefofstaff.data.entity.Note
import com.chiefofstaff.domain.ConsistencyScore
import com.chiefofstaff.domain.HorizonEngine

/**
 * REV-07 monthly retro against goals · REV-10 quarterly direction review. Both are the same shape:
 * a neutral snapshot against goals (consistency, where the time went, what's drifting), delivered
 * as one essential notification and saved as a Note so the next period can compare. Triggered from
 * the nightly batch on month/quarter boundaries — no extra alarms, no scheduler changes.
 */
class PeriodReview(
    private val repo: LifeRepository,
    private val clock: Clock,
    private val consistency: ConsistencyScore,
    private val horizons: HorizonEngine,
    private val notifier: Notifier,
) {
    /** [period] is a human label like "Monthly" or "Quarterly". */
    suspend fun run(period: String) {
        val pct = consistency.asPercent()
        val attribution = horizons.weekAttribution()
        val drift = horizons.drift()
        val body = buildString {
            append("$period review: consistency $pct% over 30 days.")
            if (attribution.isNotEmpty()) append(" Effort: ${attribution.take(3).joinToString("; ")}.")
            if (drift.isNotEmpty()) append(" Drifting: ${drift.take(3).joinToString("; ")}.")
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
}
