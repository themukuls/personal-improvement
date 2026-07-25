package com.chiefofstaff.capture

import com.chiefofstaff.core.AppLog
import com.chiefofstaff.core.Clock
import com.chiefofstaff.data.LifeRepository
import com.chiefofstaff.data.entity.Commitment
import com.chiefofstaff.data.entity.Decision
import com.chiefofstaff.data.entity.Note
import com.chiefofstaff.data.model.CommitmentState
import com.chiefofstaff.data.model.Domain
import com.chiefofstaff.data.model.EnergyCost
import com.chiefofstaff.llm.LlmOrchestrator
import com.chiefofstaff.llm.TaskId
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * CAP-17 + DOM-08 — turn a meeting into a summary, action items and decisions. The transcript comes
 * from the on-device continuous recognition (CNV-13), so it's rough; the LLM (SUMMARISE_MEETING)
 * infers intent and extracts what was committed and decided. Action items become real commitments,
 * decisions become decision records, and the summary is saved as a Note.
 *
 * P11 — if the model can't run, the raw transcript is still kept as a Note, so a meeting is never
 * lost to a missing network. The transcript itself already lives in immutable captures (P5).
 */
class MeetingSummariser(
    private val repo: LifeRepository,
    private val clock: Clock,
    private val orchestrator: LlmOrchestrator,
) {
    data class Result(val summary: String, val actionItems: Int, val decisions: Int, val usedFallback: Boolean)

    /** Summarise a full meeting transcript. Returns null for an empty transcript. */
    suspend fun summarise(transcript: String): Result? {
        val text = transcript.trim()
        if (text.isBlank()) return null
        val now = clock.now()

        val result = runCatching {
            orchestrator.run(TaskId.SUMMARISE_MEETING, params = mapOf("transcript" to text))
        }.getOrNull()

        if (result !is LlmOrchestrator.TaskResult.Structured) {
            // Fallback: keep the transcript so nothing is lost.
            repo.graph.insertNote(Note(text = "Meeting (unsummarised):\n$text", tags = listOf("meeting"), createdAt = now))
            AppLog.i("meeting", "stored raw transcript (fallback)")
            return Result(text.take(120), 0, 0, usedFallback = true)
        }

        val obj = result.obj
        val summary = obj["summary"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: text.take(120)
        repo.graph.insertNote(Note(text = "Meeting: $summary", tags = listOf("meeting", "summary"), createdAt = now))

        var actions = 0
        obj["action_items"]?.jsonArray?.forEach { el ->
            val item = runCatching { el.jsonObject }.getOrNull() ?: return@forEach
            val what = item["what"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: return@forEach
            val who = item["who"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() && it != "null" }
            repo.commitments.insert(
                Commitment(
                    what = if (who != null) "$what (for $who)" else what,
                    domain = Domain.WORK,
                    energyCost = EnergyCost.MEDIUM,
                    state = CommitmentState.SCHEDULED,
                    contextNote = "from meeting",
                    createdAt = now, updatedAt = now, lastTouchedAt = now,
                )
            )
            actions++
        }

        var decisions = 0
        obj["decisions"]?.jsonArray?.forEach { el ->
            val d = runCatching { el.jsonPrimitive.content }.getOrNull()?.takeIf { it.isNotBlank() } ?: return@forEach
            repo.graph.insertDecision(
                Decision(question = "Decided in meeting", chosen = d, rationale = null, createdAt = now)
            )
            decisions++
        }

        AppLog.i("meeting", "summarised: $actions action item(s), $decisions decision(s)")
        return Result(summary, actions, decisions, usedFallback = false)
    }
}
