package com.chiefofstaff.domain

import com.chiefofstaff.core.AppLog
import com.chiefofstaff.core.Clock
import com.chiefofstaff.data.LifeRepository
import com.chiefofstaff.data.model.CommitmentState
import com.chiefofstaff.data.model.Domain
import com.chiefofstaff.data.model.Verdict

/**
 * P10 / RES-02 / RES-04 — the anti-burden engine. The retention risk is not that the app does too
 * little; it is that it accumulates (§3.3). So the assistant must periodically propose its own
 * reduction — dropping goals with no activity, archiving dormant commitments — and offer to clear a
 * whole life domain's backlog in one action, without shame or reconstruction (domain bankruptcy).
 *
 * Detection is deterministic (§4.1); an LLM only ever rephrases the offer. Framing is neutral: these
 * are offers, never failures (RES-06).
 */
class ReductionEngine(
    private val repo: LifeRepository,
    private val clock: Clock,
    private val stateMachine: CommitmentStateMachine,
) {
    data class ReductionItem(val kind: Kind, val id: Long, val label: String) {
        enum class Kind { GOAL, COMMITMENT }
    }

    /** RES-04 — dormant commitments (45d) and goals with no activity (8 weeks). */
    suspend fun proposals(): List<ReductionItem> {
        val now = clock.now()
        val dormantCommitmentCutoff = now.minusSeconds(45L * 86_400).toEpochMilli()
        val staleGoalCutoff = now.minusSeconds(56L * 86_400).toEpochMilli()

        val commitmentItems = repo.commitments.dormantBefore(dormantCommitmentCutoff).map {
            ReductionItem(ReductionItem.Kind.COMMITMENT, it.id, it.what)
        }
        val goalItems = repo.graph.staleGoals(staleGoalCutoff).map {
            ReductionItem(ReductionItem.Kind.GOAL, it.id, it.title)
        }
        return commitmentItems + goalItems
    }

    /** Accept one reduction offer. Neutral: dropping is a valid, expected outcome (P4/RES-06). */
    suspend fun drop(item: ReductionItem) {
        when (item.kind) {
            ReductionItem.Kind.COMMITMENT -> stateMachine.applyVerdict(item.id, Verdict.DROPPED)
            ReductionItem.Kind.GOAL -> {
                val goals = repo.graph.staleGoals(Long.MAX_VALUE)
                goals.firstOrNull { it.id == item.id }?.let {
                    repo.graph.updateGoal(it.copy(status = "dropped", lastTouchedAt = clock.now()))
                }
            }
        }
        AppLog.i("reduce", "dropped ${item.kind} #${item.id}")
    }

    /**
     * RES-10 — the annual pruning proposal. Once a year the assistant looks at what has quietly
     * accumulated and offers to let it go: long-archived commitments, goals abandoned months ago,
     * references that expired long ago. Deterministic counts only — the offer is a summary, never an
     * automatic deletion (P10/RES-06). Returns neutral lines; empty if there's nothing to prune.
     */
    suspend fun annualPruningProposal(): List<String> {
        val now = clock.now()
        val out = mutableListOf<String>()
        val archived = repo.commitments.archivedBefore(now.minusSeconds(365L * 86_400).toEpochMilli()).size
        if (archived > 0) out += "$archived commitment(s) archived over a year ago"
        val staleGoals = repo.graph.staleGoals(now.minusSeconds(180L * 86_400).toEpochMilli()).size
        if (staleGoals > 0) out += "$staleGoals goal(s) untouched for 6+ months"
        val expired = repo.graph.expiredBefore(now.minusSeconds(365L * 86_400).toEpochMilli()).size
        if (expired > 0) out += "$expired reference(s) expired over a year ago"
        return out
    }

    /** RES-02 — how many open commitments each activated domain is carrying. */
    suspend fun openCountsByDomain(): Map<Domain, Int> =
        repo.commitments.openCommitmentsNow()
            .groupingBy { it.domain }
            .eachCount()
            .filterKeys { it != Domain.NONE }

    /** RES-02 — clear a whole domain's backlog in one action, archived (not deleted). */
    suspend fun declareBankruptcy(domain: Domain): Int {
        val open = repo.commitments.openCommitmentsNow().filter { it.domain == domain }
        val now = clock.now()
        open.forEach {
            repo.commitments.update(it.copy(state = CommitmentState.AUTO_ARCHIVED, updatedAt = now, lastTouchedAt = now))
        }
        AppLog.i("reduce", "domain bankruptcy for $domain cleared ${open.size}")
        return open.size
    }
}
