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
