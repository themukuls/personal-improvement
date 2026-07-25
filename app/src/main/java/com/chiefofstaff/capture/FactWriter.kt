package com.chiefofstaff.capture

import com.chiefofstaff.core.AppLog
import com.chiefofstaff.core.Clock
import com.chiefofstaff.data.LifeRepository
import com.chiefofstaff.data.entity.Commitment
import com.chiefofstaff.data.entity.Decision
import com.chiefofstaff.data.entity.Goal
import com.chiefofstaff.data.entity.Note
import com.chiefofstaff.data.entity.Observation
import com.chiefofstaff.data.entity.Person
import com.chiefofstaff.data.entity.RuleEntity
import com.chiefofstaff.data.entity.WaitingOn
import com.chiefofstaff.data.model.CommitmentState
import com.chiefofstaff.data.model.Domain
import com.chiefofstaff.data.model.EnergyCost
import com.chiefofstaff.data.model.Enforcement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalTime

/**
 * Turns one extracted fact object (kind + minimal fields) into a typed row, linked back to its
 * source capture. This is where derived facts land "alongside" the immutable capture (P5): the raw
 * text is never mutated, and a bad parse can be re-derived because the capture and its
 * parse_version are retained.
 */
class FactWriter(
    private val repo: LifeRepository,
    private val clock: Clock,
) {
    /** Returns true if the fact was written; false if it was too low-confidence / unknown kind. */
    suspend fun write(fact: JsonObject, sourceCaptureId: Long, confidenceFloor: Float = 0.35f): Boolean {
        val kind = fact.str("kind")?.lowercase() ?: return false
        val confidence = fact.num("confidence") ?: 0.5f
        if (confidence < confidenceFloor) return false
        val now = clock.now()

        return when (kind) {
            "commitment" -> {
                repo.commitments.insert(
                    Commitment(
                        what = fact.str("what") ?: return false,
                        dueAt = fact.str("due")?.let { parseTimeToday(it) },
                        domain = fact.domain(),
                        energyCost = fact.energy(),
                        state = CommitmentState.SCHEDULED,
                        sourceCaptureId = sourceCaptureId,
                        createdAt = now, updatedAt = now, lastTouchedAt = now,
                        contextNote = fact.str("context"),
                    )
                ); true
            }
            "waiting_on" -> {
                repo.commitments.insertWaiting(
                    WaitingOn(
                        what = fact.str("what") ?: return false,
                        who = fact.str("who") ?: "someone",
                        domain = fact.domain(Domain.WORK),
                        createdAt = now, lastTouchedAt = now,
                    )
                ); true
            }
            "observation" -> {
                repo.graph.insertObservation(
                    Observation(
                        metric = fact.str("metric") ?: return false,
                        value = fact.num("value")?.toDouble() ?: return false,
                        unit = fact.str("unit"),
                        observedAt = now, source = "capture", createdAt = now,
                    )
                ); true
            }
            "person" -> {
                // MEM-07 — light entity resolution: don't create a duplicate if the person (or an
                // alias) already exists.
                val name = fact.str("name") ?: return false
                if (repo.graph.findPerson(name) == null) {
                    repo.graph.insertPerson(Person(name = name, relationship = fact.str("relationship"), createdAt = now))
                }
                true
            }
            "decision" -> {
                repo.graph.insertDecision(
                    Decision(
                        question = fact.str("question") ?: return false,
                        chosen = fact.str("chosen") ?: "",
                        rationale = fact.str("rationale"),
                        createdAt = now,
                    )
                ); true
            }
            "rule" -> {
                repo.graph.insertRule(
                    RuleEntity(
                        statement = fact.str("statement") ?: return false,
                        domain = fact.domain(),
                        enforcement = if (fact.str("enforcement") == "hard") Enforcement.HARD else Enforcement.SOFT,
                        activeHours = fact.str("active_hours"),
                        createdAt = now,
                    )
                ); true
            }
            "goal" -> {
                repo.graph.insertGoal(
                    Goal(
                        title = fact.str("title") ?: return false,
                        horizon = fact.str("horizon") ?: "quarter",
                        domain = fact.domain(),
                        metric = fact.str("metric"),
                        target = fact.str("target"),
                        createdAt = now, lastTouchedAt = now,
                    )
                ); true
            }
            "note" -> {
                repo.graph.insertNote(Note(text = fact.str("text") ?: return false, createdAt = now)); true
            }
            else -> {
                AppLog.d("factwriter", "unhandled fact kind: $kind")
                false
            }
        }
    }

    // --- small JSON helpers ---
    private fun JsonObject.str(key: String): String? =
        this[key]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }?.takeIf { it.isNotBlank() && it != "null" }
    private fun JsonObject.num(key: String): Float? = str(key)?.toFloatOrNull()
    private fun JsonObject.domain(default: Domain = Domain.NONE): Domain =
        str("domain")?.let { runCatching { Domain.valueOf(it.uppercase()) }.getOrNull() } ?: default
    private fun JsonObject.energy(): EnergyCost =
        str("energy")?.let { runCatching { EnergyCost.valueOf(it.uppercase()) }.getOrNull() } ?: EnergyCost.MEDIUM

    /** Parse "16:00" / "4pm" into an Instant today; unknown formats yield null (a floating item). */
    private fun parseTimeToday(raw: String): java.time.Instant? {
        val t = parseLocalTime(raw) ?: return null
        return clock.today().atTime(t).atZone(clock.zone()).toInstant()
    }

    private fun parseLocalTime(raw: String): LocalTime? {
        val s = raw.trim().lowercase()
        Regex("^(\\d{1,2}):(\\d{2})").find(s)?.let {
            return LocalTime.of(it.groupValues[1].toInt(), it.groupValues[2].toInt())
        }
        Regex("^(\\d{1,2})\\s*(am|pm)").find(s)?.let {
            var h = it.groupValues[1].toInt() % 12
            if (it.groupValues[2] == "pm") h += 12
            return LocalTime.of(h, 0)
        }
        return null
    }
}
