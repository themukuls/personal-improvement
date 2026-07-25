package com.chiefofstaff.domain

import com.chiefofstaff.core.AppLog
import com.chiefofstaff.core.Clock
import com.chiefofstaff.data.LifeRepository
import com.chiefofstaff.data.entity.Commitment
import com.chiefofstaff.llm.Fallback
import com.chiefofstaff.llm.LlmOrchestrator
import com.chiefofstaff.llm.TaskId
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * DIR-01/02/04 — turns memory into today's 3–6 items and the short "should not" list. The LLM
 * (GENERATE_PLAN) ranks against real constraints; if it can't run, the deterministic fallback
 * still produces a usable ordered plan (P11). Minimum-viable-day (DIR-04) collapses the plan to a
 * single item on low energy or high load — the plan reduces, it never disappears.
 */
class PlanGenerator(
    private val repo: LifeRepository,
    private val clock: Clock,
    private val orchestrator: LlmOrchestrator,
    private val fallback: Fallback,
    private val rules: RuleEngine,
    private val ledger: PredictionLedger,
) {
    data class Plan(
        val items: List<Commitment>,
        val notToday: List<String>,
        val minimumViableDay: Boolean,
        val offline: Boolean,
    )

    /** P7 — the plan proposes 3–6 items; DIR-04 reduces to 1 on a low-energy or high-load day. */
    suspend fun generate(maxItems: Int = 6): Plan {
        val day = repo.today()
        val mode = repo.mode().current
        val lowEnergy = (day.energy ?: 3) <= 2
        val highLoad = (day.loadScore ?: 0f) >= 0.8f
        // DIR-09 — sick/recovery modes reduce the day to a single item, like a low-energy day.
        val recoveryMode = mode == com.chiefofstaff.data.model.Mode.SICK || mode == com.chiefofstaff.data.model.Mode.RECOVERY
        val minimumViable = lowEnergy || highLoad || day.illnessFlag || recoveryMode
        val cap = if (minimumViable) 1 else maxItems

        val open = repo.commitments.openCommitments().first()
        val violations = rules.check(open)
        val notToday = violations.map { it.explanation }
        val excluded = violations.map { it.commitment.id }.toSet()

        val result = orchestrator.run(TaskId.GENERATE_PLAN)
        val ids: List<Long> = when (result) {
            is LlmOrchestrator.TaskResult.Structured -> runCatching {
                result.obj["items"]?.jsonArray?.mapNotNull {
                    it.jsonObject["commitment_id"]?.jsonPrimitive?.content?.toLongOrNull()
                } ?: emptyList()
            }.getOrDefault(emptyList())
            else -> emptyList()
        }

        val offline = result !is LlmOrchestrator.TaskResult.Structured
        val chosen: List<Commitment> = if (ids.isNotEmpty()) {
            ids.mapNotNull { id -> open.firstOrNull { it.id == id } }
        } else {
            // Fallback ordering: due first, then leverage-neutral recency.
            if (offline) AppLog.i("plan", "using deterministic plan (offline or unparsed)")
            open.filterNot { it.id in excluded }
                .sortedWith(compareBy({ it.dueAt == null }, { it.dueAt }))
        }

        val items = chosen.filterNot { it.id in excluded }.take(cap)
        return Plan(items = items, notToday = notToday, minimumViableDay = minimumViable, offline = offline)
    }

    /** DIR-02 — "What now?": one tap, one answer. The single highest-priority open item right now. */
    suspend fun whatNow(): Commitment? {
        val open = repo.commitments.openCommitments().first()
        val now = clock.now()
        // Prefer something due soon; otherwise the earliest-due open item.
        return open.minByOrNull { c ->
            val due = c.dueAt ?: return@minByOrNull Long.MAX_VALUE / 2
            kotlin.math.abs(due.toEpochMilli() - now.toEpochMilli())
        }
    }
}
