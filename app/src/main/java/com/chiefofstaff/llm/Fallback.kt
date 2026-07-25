package com.chiefofstaff.llm

import com.chiefofstaff.core.Clock
import com.chiefofstaff.data.LifeRepository
import kotlinx.coroutines.flow.first
import java.time.LocalTime

/**
 * §6.6 / SYS-07 / P11 — the deterministic fallback. When the network or provider is down, this
 * produces a usable morning brief with no LLM at all: calendar plus open commitments by due date.
 * "The 06:00 brief always fires." Reliability is the entire value proposition, so this path must
 * never itself depend on anything that can fail remotely.
 */
class Fallback(
    private val repo: LifeRepository,
    private val clock: Clock,
) {
    private fun hhmm(instant: java.time.Instant): String =
        LocalTime.ofInstant(instant, clock.zone()).let { "%02d:%02d".format(it.hour, it.minute) }

    /** A plain-text brief assembled purely from local structured data. */
    suspend fun deterministicBrief(): String {
        val date = clock.today()
        val startOfDay = date.atStartOfDay(clock.zone()).toInstant()
        val endOfDay = startOfDay.plusSeconds(86_400)

        val events = repo.graph.eventsBetween(startOfDay.toEpochMilli(), endOfDay.toEpochMilli())
        val commitments = repo.commitments.openCommitments().first()
            .sortedWith(compareBy({ it.dueAt == null }, { it.dueAt }))
            .take(6)

        return buildString {
            append("Good morning. ")
            val next = events.firstOrNull { it.start.isAfter(clock.now()) }
            if (next != null) append("Next up is ${next.title} at ${hhmm(next.start)}. ")
            if (commitments.isEmpty()) {
                append("Nothing is committed for today.")
            } else {
                append("Today: ")
                append(commitments.joinToString("; ") { c ->
                    val t = c.dueAt?.let { hhmm(it) } ?: "when you can"
                    "${c.what} ($t)"
                })
                append(".")
            }
            append(" (Offline — this is the plain brief; I'll add context when I'm back online.)")
        }
    }

    /** A minimal, always-valid plan when GENERATE_PLAN can't run (DIR-04 leans on this too). */
    suspend fun deterministicPlan(maxItems: Int): List<Long> =
        repo.commitments.openCommitments().first()
            .sortedWith(compareBy({ it.dueAt == null }, { it.dueAt }))
            .take(maxItems)
            .map { it.id }
}
