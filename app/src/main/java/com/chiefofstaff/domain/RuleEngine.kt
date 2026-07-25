package com.chiefofstaff.domain

import com.chiefofstaff.core.Clock
import com.chiefofstaff.data.LifeRepository
import com.chiefofstaff.data.entity.Commitment
import com.chiefofstaff.data.entity.RuleEntity
import com.chiefofstaff.data.model.Enforcement
import java.time.LocalTime

/**
 * DIR-03 / INT-09 — the rule engine. Rules are your constitution; the referee needs a rulebook.
 * Deterministic checks run before any token is spent (§4.1): hard rules become planning
 * constraints, and a plan that contradicts a hard rule is flagged so the "NOT TODAY" line can
 * explain the refusal ("Sprint retro invite declined — collides with your 14:00 deep-work rule").
 */
class RuleEngine(
    private val repo: LifeRepository,
    private val clock: Clock,
) {
    data class Violation(val rule: RuleEntity, val commitment: Commitment, val explanation: String)

    /** Check a set of proposed commitments against active hard rules. */
    suspend fun check(proposed: List<Commitment>): List<Violation> {
        val rules = repo.graph.activeRulesNow().filter { it.enforcement == Enforcement.HARD }
        if (rules.isEmpty()) return emptyList()
        val violations = mutableListOf<Violation>()
        for (c in proposed) {
            val due = c.dueAt ?: continue
            val at = LocalTime.ofInstant(due, clock.zone())
            for (r in rules) {
                val window = parseWindow(r.activeHours) ?: continue
                if (at >= window.first && at < window.second) {
                    violations += Violation(
                        rule = r,
                        commitment = c,
                        explanation = "${c.what} collides with your ${r.statement}",
                    )
                }
            }
        }
        return violations
    }

    private fun parseWindow(hours: String?): Pair<LocalTime, LocalTime>? {
        hours ?: return null
        val m = Regex("(\\d{1,2}):(\\d{2})\\s*-\\s*(\\d{1,2}):(\\d{2})").find(hours) ?: return null
        val (h1, m1, h2, m2) = m.destructured
        return LocalTime.of(h1.toInt(), m1.toInt()) to LocalTime.of(h2.toInt(), m2.toInt())
    }
}
