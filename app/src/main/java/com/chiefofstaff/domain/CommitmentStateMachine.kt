package com.chiefofstaff.domain

import com.chiefofstaff.core.Clock
import com.chiefofstaff.data.LifeRepository
import com.chiefofstaff.data.entity.Commitment
import com.chiefofstaff.data.model.CommitmentState
import com.chiefofstaff.data.model.Verdict

/**
 * §7.4 / ACC-01 — the commitment lifecycle, made concrete. This is the one place transitions
 * happen, so P4 ("every commitment gets a verdict; silence is never an outcome") is structurally
 * enforced: the only exits are DONE, SKIPPED, DROPPED, or AUTO_ARCHIVED, and deferral escalates
 * after three tries.
 *
 *   captured → scheduled → due → asked
 *                                 ├→ done
 *                                 ├→ skipped (reason optional)
 *                                 ├→ deferred (count++) → scheduled
 *                                 └→ [deferred ≥3] → escalated → dropped | recommitted
 *                                                                    ↓
 *                                          [untouched 45d] → auto-archived
 */
class CommitmentStateMachine(
    private val repo: LifeRepository,
    private val clock: Clock,
    private val ledger: PredictionLedger,
) {
    /** Escalation threshold from §7.4. */
    private val deferralEscalationLimit = 3

    suspend fun schedule(id: Long) = transition(id) { it.copy(state = CommitmentState.SCHEDULED) }
    suspend fun markDue(id: Long) = transition(id) { it.copy(state = CommitmentState.DUE) }
    suspend fun markAsked(id: Long) = transition(id) { it.copy(state = CommitmentState.ASKED) }

    /**
     * Apply a verdict from the Close card stack or a voice/notification action (§9.2, ACC-03/04).
     * A skip reason is always optional (ACC-07) — reasons are the most valuable data in the system
     * and must never be mandatory.
     */
    suspend fun applyVerdict(id: Long, verdict: Verdict, skipReason: String? = null) {
        val c = repo.commitments.byId(id) ?: return
        val now = clock.now()
        val updated = when (verdict) {
            Verdict.DONE -> c.copy(state = CommitmentState.DONE)
            Verdict.SKIPPED -> c.copy(state = CommitmentState.SKIPPED, skipReason = skipReason)
            Verdict.DROPPED -> c.copy(state = CommitmentState.DROPPED)
            Verdict.DEFERRED -> {
                val count = c.deferralCount + 1
                if (count >= deferralEscalationLimit) {
                    // Third deferral escalates rather than silently sliding again (§7.4).
                    c.copy(state = CommitmentState.ESCALATED, deferralCount = count)
                } else {
                    c.copy(state = CommitmentState.SCHEDULED, deferralCount = count)
                }
            }
        }.copy(updatedAt = now, lastTouchedAt = now)
        repo.commitments.update(updated)

        // INT-11 — snooze with a recorded consequence: a defer isn't free, it's written down.
        if (verdict == Verdict.DEFERRED) {
            repo.graph.insertNote(
                com.chiefofstaff.data.entity.Note(
                    text = "Snoozed \"${c.what}\" — now deferred ${updated.deferralCount}×" +
                        if (updated.state == CommitmentState.ESCALATED) " (escalated: recommit or drop)." else ".",
                    tags = listOf("snooze"),
                    createdAt = now,
                )
            )
        }

        // Close the loop on any open prediction about this commitment (§1.3, ACC-05).
        ledger.resolveForCommitment(updated, verdict)
    }

    /** Resolve an ESCALATED item: the user either recommits it or drops it (§7.4). */
    suspend fun resolveEscalation(id: Long, recommit: Boolean) = transition(id) {
        val now = clock.now()
        if (recommit) it.copy(state = CommitmentState.RECOMMITTED, deferralCount = 0, updatedAt = now, lastTouchedAt = now)
        else it.copy(state = CommitmentState.DROPPED, updatedAt = now, lastTouchedAt = now)
    }

    /** RES-01 — archive commitments untouched for 45 days. Returns the archived items to notify once. */
    suspend fun autoArchiveDormant(): List<Commitment> {
        val cutoff = clock.now().minusSeconds(45L * 86_400).toEpochMilli()
        val dormant = repo.commitments.dormantBefore(cutoff)
        val now = clock.now()
        dormant.forEach {
            repo.commitments.update(it.copy(state = CommitmentState.AUTO_ARCHIVED, updatedAt = now))
        }
        return dormant
    }

    private suspend inline fun transition(id: Long, mutate: (Commitment) -> Commitment) {
        val c = repo.commitments.byId(id) ?: return
        val now = clock.now()
        repo.commitments.update(mutate(c).copy(updatedAt = now, lastTouchedAt = now))
    }
}
