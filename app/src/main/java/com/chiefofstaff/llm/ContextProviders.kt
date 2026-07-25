package com.chiefofstaff.llm

import com.chiefofstaff.core.Clock
import com.chiefofstaff.data.LifeRepository
import kotlinx.coroutines.flow.first
import java.time.LocalTime
import java.time.ZoneId

/** A trivial provider that echoes a single string param (raw capture, question, meal note…). */
class ParamProvider(override val key: String, private val paramName: String) : ContextProvider {
    override suspend fun render(params: Map<String, Any?>, budgetTokens: Int): String {
        val v = params[paramName]?.toString().orEmpty()
        return clampToBudget(v.lines(), budgetTokens)
    }
}

private fun hhmm(instant: java.time.Instant, zone: ZoneId): String =
    LocalTime.ofInstant(instant, zone).let { "%02d:%02d".format(it.hour, it.minute) }

/** Today's calendar as compact lines: "10:00 Standup @room". */
class CalendarProvider(private val repo: LifeRepository, private val clock: Clock) : ContextProvider {
    override val key = "today_calendar"
    override suspend fun render(params: Map<String, Any?>, budgetTokens: Int): String {
        val start = clock.today().atStartOfDay(clock.zone()).toInstant()
        val end = start.plusSeconds(86_400)
        val lines = repo.graph.eventsBetween(start.toEpochMilli(), end.toEpochMilli()).map { e ->
            "${hhmm(e.start, clock.zone())} ${e.title}" + (e.location?.let { " @$it" } ?: "")
        }
        return clampToBudget(lines, budgetTokens)
    }
}

/** Open commitments as: "16:00 Q3 capacity plan to Rakesh | domain=WORK defer=1". */
class OpenCommitmentsProvider(private val repo: LifeRepository, private val clock: Clock) : ContextProvider {
    override val key = "open_commitments"
    override suspend fun render(params: Map<String, Any?>, budgetTokens: Int): String {
        val list = repo.commitments.openCommitments().first()
            .filterNot { EgressPolicy.isLocalOnly(it.domain) }   // SYS-19 per-domain egress
        val lines = list.map { c ->
            val t = c.dueAt?.let { hhmm(it, clock.zone()) } ?: "wait"
            "$t ${c.what} | domain=${c.domain} energy=${c.energyCost} defer=${c.deferralCount}"
        }
        return clampToBudget(lines, budgetTokens)
    }
}

class DueCommitmentsProvider(private val repo: LifeRepository, private val clock: Clock) : ContextProvider {
    override val key = "due_commitments"
    override suspend fun render(params: Map<String, Any?>, budgetTokens: Int): String {
        val end = clock.today().atTime(23, 59).atZone(clock.zone()).toInstant()
        val lines = repo.commitments.dueThrough(end.toEpochMilli()).map { c ->
            "#${c.id} ${c.what} | state=${c.state}"
        }
        return clampToBudget(lines, budgetTokens)
    }
}

class ActiveRulesProvider(private val repo: LifeRepository) : ContextProvider {
    override val key = "active_rules"
    override suspend fun render(params: Map<String, Any?>, budgetTokens: Int): String {
        val lines = repo.graph.activeRulesNow().map { r ->
            "${r.enforcement} ${r.statement}" + (r.activeHours?.let { " [$it]" } ?: "") + " | ${r.domain}"
        }
        return clampToBudget(lines, budgetTokens)
    }
}

class CurrentStateProvider(private val repo: LifeRepository, private val clock: Clock) : ContextProvider {
    override val key = "current_state"
    override suspend fun render(params: Map<String, Any?>, budgetTokens: Int): String {
        val mode = repo.mode()
        val day = repo.today()
        val lines = listOf(
            "mode=${mode.current}",
            "energy=${day.energy ?: "?"}",
            "load=${day.loadScore ?: "?"}",
            "illness=${day.illnessFlag}",
        )
        return clampToBudget(lines, budgetTokens)
    }
}

class RecentObservationsProvider(private val repo: LifeRepository, private val clock: Clock) : ContextProvider {
    override val key = "recent_observations"
    override suspend fun render(params: Map<String, Any?>, budgetTokens: Int): String {
        val list = repo.graph.recentObservations(20).first()
        val lines = list.map { o -> "${o.metric}=${o.value}${o.unit ?: ""} @${hhmm(o.observedAt, clock.zone())}" }
        return clampToBudget(lines, budgetTokens)
    }
}

class YesterdayVerdictsProvider(private val repo: LifeRepository, private val clock: Clock) : ContextProvider {
    override val key = "yesterday_verdicts"
    override suspend fun render(params: Map<String, Any?>, budgetTokens: Int): String {
        val since = clock.today().minusDays(1).atStartOfDay(clock.zone()).toInstant().toEpochMilli()
        val done = repo.commitments.completedSince(since)
        val lines = done.map { "done: ${it.what}" }
        return clampToBudget(lines, budgetTokens)
    }
}

class PredictionAccuracyProvider(private val repo: LifeRepository, private val clock: Clock) : ContextProvider {
    override val key = "prediction_accuracy"
    override suspend fun render(params: Map<String, Any?>, budgetTokens: Int): String {
        val since = clock.today().minusDays(30).atStartOfDay(clock.zone()).toInstant().toEpochMilli()
        val mae = repo.state.meanAbsErrorSince(since) ?: return ""
        return clampToBudget(listOf("mean_abs_error_30d=${"%.2f".format(mae)}"), budgetTokens)
    }
}

class ActiveGoalsProvider(private val repo: LifeRepository) : ContextProvider {
    override val key = "active_goals"
    override suspend fun render(params: Map<String, Any?>, budgetTokens: Int): String {
        val list = repo.graph.activeGoals().first()
        val lines = list.map { g -> "${g.horizon}: ${g.title}" + (g.metric?.let { " ($it→${g.target})" } ?: "") }
        return clampToBudget(lines, budgetTokens)
    }
}

class TopAnticipationProvider(private val repo: LifeRepository, private val clock: Clock) : ContextProvider {
    override val key = "top_anticipation"
    override suspend fun render(params: Map<String, Any?>, budgetTokens: Int): String {
        val a = repo.state.topAnticipationFor(com.chiefofstaff.data.entity.DayState.keyFor(clock.today())) ?: return ""
        return clampToBudget(listOf("${a.kind}: ${a.headline} ${a.detail}"), budgetTokens)
    }
}

/** DIR-14 — recent decisions as analogues, so Decide-mode can reason against past choices. */
class RecentDecisionsProvider(private val repo: LifeRepository) : ContextProvider {
    override val key = "recent_decisions"
    override suspend fun render(params: Map<String, Any?>, budgetTokens: Int): String {
        val lines = repo.graph.recentDecisions(10).map { d ->
            "Q: ${d.question} → ${d.chosen}" + (d.actualOutcome?.let { " [outcome: $it]" } ?: "")
        }
        return clampToBudget(lines, budgetTokens)
    }
}

/** Factory assembling every provider for a repository. Providers not backed by the graph are
 *  simple [ParamProvider]s populated by the caller of the task. */
object ContextProviders {
    fun all(repo: LifeRepository, clock: Clock): List<ContextProvider> = listOf(
        CalendarProvider(repo, clock),
        OpenCommitmentsProvider(repo, clock),
        RecentDecisionsProvider(repo),
        DueCommitmentsProvider(repo, clock),
        ActiveRulesProvider(repo),
        CurrentStateProvider(repo, clock),
        RecentObservationsProvider(repo, clock),
        YesterdayVerdictsProvider(repo, clock),
        PredictionAccuracyProvider(repo, clock),
        ActiveGoalsProvider(repo),
        TopAnticipationProvider(repo, clock),
        // Param-backed blocks filled by whoever runs the task:
        ParamProvider("raw_capture", "raw"),
        ParamProvider("question", "question"),
        ParamProvider("free_recap", "recap"),
        ParamProvider("meal_note", "meal"),
        ParamProvider("decision_question", "question"),
        ParamProvider("draft_request", "request"),
        ParamProvider("meeting_transcript", "transcript"),
        ParamProvider("trend_series", "series"),
        ParamProvider("session_history", "history"),
        ParamProvider("today_plan", "plan"),
        ParamProvider("interview_sitting", "sitting"),
        ParamProvider("entity_mention", "mention"),
        ParamProvider("candidate_entities", "candidates"),
        ParamProvider("one_health_note", "healthNote"),
    )
}
