package com.chiefofstaff.domain

import com.chiefofstaff.core.Clock
import com.chiefofstaff.data.LifeRepository
import com.chiefofstaff.data.entity.AnticipationItem
import com.chiefofstaff.data.entity.DayState
import com.chiefofstaff.data.model.AnticipationKind
import com.chiefofstaff.data.model.Domain
import java.time.temporal.ChronoUnit

/**
 * §11 — the anticipation engine. Runs in the 02:00 batch and produces AT MOST ONE item per day
 * (ANT-02), the highest-salience look-ahead, delivered inside the morning brief and the Now
 * "LOOKING AHEAD" card. This is the highest-perceived-intelligence feature and the easiest to ruin
 * with volume, so the one-per-day cap is enforced structurally: the scans only ever persist the
 * single winner.
 *
 * The scans are deterministic (§4.1 — never spend a token on what a rule can answer). An LLM pass
 * can later rephrase the winner, but the detection is pure SQL + arithmetic.
 */
class AnticipationEngine(
    private val repo: LifeRepository,
    private val clock: Clock,
) {
    private data class Candidate(
        val kind: AnticipationKind,
        val headline: String,
        val detail: String,
        val salience: Float,
        val relatedId: Long? = null,
    )

    /** Run all scans, keep the single highest-salience candidate, persist it for today. */
    suspend fun runNightlyScan(): AnticipationItem? {
        val forDate = DayState.keyFor(clock.today())
        // Idempotent: if today's item already exists, don't churn it.
        repo.state.topAnticipationFor(forDate)?.let { return it }

        val candidates = buildList {
            addAll(expiryWatch())
            addAll(waitingDecay())
            addAll(decisionReview())
            addAll(neglectDetection())
            addAll(unpreparedDependency())
            addAll(overloadForecast())
            addAll(financialCalendar())
        }
        val winner = candidates.maxByOrNull { it.salience } ?: return null

        val item = AnticipationItem(
            kind = winner.kind,
            forDate = forDate,
            headline = winner.headline,
            detail = winner.detail,
            salience = winner.salience,
            relatedId = winner.relatedId,
            createdAt = clock.now(),
        )
        val id = repo.state.insertAnticipation(item)
        return item.copy(id = id)
    }

    // ANT-03 — expiry watch on documents/insurance/subscriptions; sooner = higher salience.
    private suspend fun expiryWatch(): List<Candidate> {
        val horizon = clock.now().plus(45, ChronoUnit.DAYS)
        return repo.graph.expiringBefore(horizon.toEpochMilli()).mapNotNull { ref ->
            val expires = ref.expiresAt ?: return@mapNotNull null
            val days = ChronoUnit.DAYS.between(clock.now(), expires).coerceAtLeast(0)
            Candidate(
                kind = AnticipationKind.EXPIRY,
                headline = "${ref.label} expires soon.",
                detail = "Renewal windows run long — start now if it matters.",
                salience = (1f - (days / 45f)).coerceIn(0f, 1f) * 0.9f,
                relatedId = ref.id,
            )
        }
    }

    // §11 waiting-on decay — something owed to you, overdue and unchased.
    private suspend fun waitingDecay(): List<Candidate> {
        return repo.commitments.overdueWaiting(clock.now().toEpochMilli()).map { w ->
            Candidate(
                kind = AnticipationKind.WAITING_DECAY,
                headline = "${w.what} from ${w.who} is overdue.",
                detail = "Promised earlier, unchased ${w.chaseCount} times. Worth a nudge.",
                salience = 0.7f + (w.chaseCount.coerceAtMost(3) * 0.05f),
                relatedId = w.id,
            )
        }
    }

    // ANT-10 — a Decision whose review_at has arrived.
    private suspend fun decisionReview(): List<Candidate> {
        return repo.graph.decisionsDueForReview(clock.now().toEpochMilli()).map { d ->
            Candidate(
                kind = AnticipationKind.DECISION_REVIEW,
                headline = "Time to review a decision: ${d.question}",
                detail = "You chose \"${d.chosen}\". Did it land the way you expected?",
                salience = 0.65f,
                relatedId = d.id,
            )
        }
    }

    // ANT-06 — a person past their expected contact cadence.
    private suspend fun neglectDetection(): List<Candidate> {
        val now = clock.now()
        return repo.graph.peopleWithCadence().mapNotNull { p ->
            val cadence = p.cadenceTargetDays ?: return@mapNotNull null
            val last = p.lastContact ?: return@mapNotNull null
            val overdueDays = ChronoUnit.DAYS.between(last, now) - cadence
            if (overdueDays <= 0) return@mapNotNull null
            Candidate(
                kind = AnticipationKind.NEGLECT,
                headline = "You haven't spoken to ${p.name} in a while.",
                detail = "Past your ${cadence}-day cadence by $overdueDays days.",
                salience = (0.5f + overdueDays / 60f).coerceAtMost(0.85f),
                relatedId = p.id,
            )
        }
    }

    // ANT-04 — an event in the next 14 days that reads like it needs prep, with none scheduled.
    private suspend fun unpreparedDependency(): List<Candidate> {
        val now = clock.now()
        val horizon = now.plus(14, ChronoUnit.DAYS)
        val events = repo.graph.eventsBetween(now.toEpochMilli(), horizon.toEpochMilli())
        val open = repo.commitments.openCommitmentsNow()
        val prepKeywords = listOf("flight", "travel", "trip", "meeting", "interview", "review", "demo", "launch")
        return events.mapNotNull { ev ->
            val title = ev.title.lowercase()
            if (prepKeywords.none { title.contains(it) }) return@mapNotNull null
            val firstWord = ev.title.substringBefore(' ').lowercase()
            val hasPrep = open.any { it.what.lowercase().contains(firstWord) || it.what.lowercase().contains("prep") }
            if (hasPrep) return@mapNotNull null
            val days = ChronoUnit.DAYS.between(now, ev.start).coerceAtLeast(0)
            Candidate(
                kind = AnticipationKind.HORIZON,
                headline = "${ev.title} is coming and nothing's prepped.",
                detail = "In $days day${if (days == 1L) "" else "s"} — worth a prep item.",
                salience = (0.8f - days / 28f).coerceIn(0.55f, 0.8f),
                relatedId = ev.id,
            )
        }
    }

    // ANT-05 — next week's committed load looks heavier than a normal week can hold.
    private suspend fun overloadForecast(): List<Candidate> {
        val now = clock.now()
        val weekEnd = now.plus(7, ChronoUnit.DAYS)
        val dueNextWeek = repo.commitments.openCommitmentsNow().count { c ->
            val due = c.dueAt ?: return@count false
            !due.isBefore(now) && !due.isAfter(weekEnd)
        }
        if (dueNextWeek < 12) return emptyList()
        return listOf(
            Candidate(
                kind = AnticipationKind.OVERLOAD,
                headline = "Next week is stacking up.",
                detail = "$dueNextWeek commitments land in seven days. Worth pruning before it hits.",
                salience = (0.7f + (dueNextWeek - 12) * 0.01f).coerceAtMost(0.9f),
            )
        )
    }

    // ANT-09 — a money deadline (bill / EMI / renewal) approaching in the next 14 days.
    private suspend fun financialCalendar(): List<Candidate> {
        val now = clock.now()
        val horizon = now.plus(14, ChronoUnit.DAYS)
        return repo.commitments.openCommitmentsNow().mapNotNull { c ->
            if (c.domain != Domain.MONEY) return@mapNotNull null
            val due = c.dueAt ?: return@mapNotNull null
            if (due.isAfter(horizon)) return@mapNotNull null
            val days = ChronoUnit.DAYS.between(now, due).coerceAtLeast(0)
            Candidate(
                kind = AnticipationKind.FINANCIAL,
                headline = "${c.what} is due " + if (days == 0L) "today." else "in $days day${if (days == 1L) "" else "s"}.",
                detail = "A money deadline is coming up.",
                salience = (0.78f + (1f - days / 14f) * 0.15f).coerceIn(0f, 0.93f),
                relatedId = c.id,
            )
        }
    }
}
