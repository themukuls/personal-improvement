package com.chiefofstaff.intervention

import com.chiefofstaff.core.AppLog
import com.chiefofstaff.core.Clock
import com.chiefofstaff.data.LifeRepository
import com.chiefofstaff.data.entity.Note
import com.chiefofstaff.domain.ConsistencyScore
import com.chiefofstaff.domain.PredictionLedger
import com.chiefofstaff.llm.LlmOrchestrator
import com.chiefofstaff.llm.TaskId
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * REV-03 — the weekly audit. Sunday 19:00: planned vs actual, trends, and exactly one specific
 * change to try next week. It leans on the same machinery as the other rituals — the LLM
 * (WEEKLY_AUDIT) for the narrative, the deterministic layers for the numbers — and it degrades to a
 * plain factual summary if the model can't run (P11). The one change it proposes is stored as a
 * Note so the next audit can see whether it stuck.
 */
class WeeklyAudit(
    private val repo: LifeRepository,
    private val clock: Clock,
    private val orchestrator: LlmOrchestrator,
    private val consistency: ConsistencyScore,
    private val ledger: PredictionLedger,
    private val notifier: Notifier,
) {
    data class Result(val summary: String, val oneChange: String?, val usedFallback: Boolean)

    suspend fun run(): Result {
        val consistencyPct = consistency.asPercent()
        val mae = ledger.meanAbsErrorLast30Days()

        val result = runCatching { orchestrator.run(TaskId.WEEKLY_AUDIT) }.getOrNull()
        val (summary, oneChange, usedFallback) = when (result) {
            is LlmOrchestrator.TaskResult.Structured -> {
                val s = result.obj["summary"]?.jsonPrimitive?.content
                val c = result.obj["one_change"]?.jsonPrimitive?.content
                if (s.isNullOrBlank()) fallback(consistencyPct, mae)
                else Triple(s, c, false)
            }
            else -> fallback(consistencyPct, mae)
        }

        // Persist the proposed change so next week can check whether it held.
        if (!oneChange.isNullOrBlank()) {
            repo.graph.insertNote(Note(text = "Weekly change: $oneChange", tags = listOf("weekly_audit"), createdAt = clock.now()))
        }

        notifier.post(
            channel = Channels.RITUAL,
            id = NotificationIds.WEEKLY_AUDIT,
            title = "Weekly audit",
            body = oneChange?.let { "$summary\n\nOne change: $it" } ?: summary,
            essential = true,   // a ritual (§9.1); bypasses the discretionary budget
        )
        AppLog.i("audit", "weekly audit delivered (fallback=$usedFallback)")
        return Result(summary, oneChange, usedFallback)
    }

    private fun fallback(consistencyPct: Int, mae: Float?): Triple<String, String?, Boolean> {
        val accuracy = mae?.let { ", estimates off by ~${"%.1f".format(it)}h on average" } ?: ""
        return Triple(
            "This week: consistency $consistencyPct% over 30 days$accuracy.",
            null,
            true,
        )
    }
}
