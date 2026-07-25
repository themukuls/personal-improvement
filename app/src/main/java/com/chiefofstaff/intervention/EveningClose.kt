package com.chiefofstaff.intervention

import com.chiefofstaff.core.AppLog
import com.chiefofstaff.core.Clock
import com.chiefofstaff.data.LifeRepository
import com.chiefofstaff.data.entity.Commitment
import com.chiefofstaff.data.model.Verdict
import com.chiefofstaff.domain.CommitmentStateMachine
import com.chiefofstaff.llm.LlmOrchestrator
import com.chiefofstaff.llm.TaskId
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * REV-01/02 — the evening close. Announces the ritual at 21:00 (essential notification), builds the
 * one-card-at-a-time stack the UI swipes through (§9.2), and can parse a free-form spoken recap into
 * verdicts (REV-02). The whole ritual is budgeted at 5 minutes for a 6-item day, and skipping is
 * penalty-free (RES-08) — silence on an item just leaves it for tomorrow, but the ritual itself
 * always offers a verdict path so nothing rots unresolved (P4).
 */
class EveningClose(
    private val repo: LifeRepository,
    private val clock: Clock,
    private val stateMachine: CommitmentStateMachine,
    private val orchestrator: LlmOrchestrator,
    private val notifier: Notifier,
) {
    /** The cards to swipe tonight: everything due through end of day without a verdict yet. */
    suspend fun cardStack(): List<Commitment> {
        val endOfDay = clock.today().atTime(23, 59).atZone(clock.zone()).toInstant()
        return repo.commitments.dueThrough(endOfDay.toEpochMilli())
    }

    /** Post the 21:00 prompt. Essential ritual — bypasses the discretionary budget (P2). */
    suspend fun announce() {
        val count = cardStack().size
        if (count == 0) {
            AppLog.i("close", "nothing due; skipping announce")
            return
        }
        notifier.post(
            channel = Channels.CLOSE,
            id = NotificationIds.EVENING_CLOSE,
            title = "Evening close",
            body = "A few minutes to close the day. $count to look at.",
            essential = true,
        )
    }

    /** ACC-03 / §9.2 — apply a single verdict (from a swipe or a one-word voice command). */
    suspend fun verdict(commitmentId: Long, verdict: Verdict, skipReason: String? = null) {
        stateMachine.applyVerdict(commitmentId, verdict, skipReason)
    }

    /**
     * REV-02 — parse a free-form recap ("did the gym, punted the plan to tomorrow") into verdicts.
     * Best-effort: matched items get their verdict, unmatched items are simply left for tomorrow.
     */
    suspend fun parseRecap(recap: String): Int {
        // Store the recap as an immutable capture first (P5) so nothing is lost even if parsing fails.
        repo.capture(com.chiefofstaff.data.model.CaptureSource.VOICE, recap)

        val result = orchestrator.run(TaskId.EVENING_PARSE, params = mapOf("recap" to recap))
        if (result !is LlmOrchestrator.TaskResult.Structured) return 0
        var applied = 0
        runCatching {
            result.obj["verdicts"]?.jsonArray?.forEach { el ->
                val obj = el.jsonObject
                val id = obj["commitment_id"]?.jsonPrimitive?.content?.toLongOrNull() ?: return@forEach
                val verdict = obj["verdict"]?.jsonPrimitive?.content
                    ?.let { runCatching { Verdict.valueOf(it.uppercase()) }.getOrNull() } ?: return@forEach
                stateMachine.applyVerdict(id, verdict)
                applied++
            }
        }
        AppLog.i("close", "recap applied $applied verdict(s)")
        return applied
    }
}
