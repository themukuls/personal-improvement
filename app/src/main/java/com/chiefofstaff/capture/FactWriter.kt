package com.chiefofstaff.capture

import com.chiefofstaff.core.AppLog
import com.chiefofstaff.core.Clock
import com.chiefofstaff.data.LifeRepository
import com.chiefofstaff.data.entity.Commitment
import com.chiefofstaff.data.entity.Decision
import com.chiefofstaff.data.entity.Goal
import com.chiefofstaff.data.entity.EventEntity
import com.chiefofstaff.data.entity.Interaction
import com.chiefofstaff.data.entity.Note
import com.chiefofstaff.data.entity.Observation
import com.chiefofstaff.data.entity.Occasion
import com.chiefofstaff.data.entity.Person
import com.chiefofstaff.data.entity.Project
import com.chiefofstaff.data.entity.ReferenceItem
import com.chiefofstaff.data.entity.RuleEntity
import com.chiefofstaff.data.entity.ValueStatement
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
                        domain = fact.domain(fact.str("what")),
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
                        domain = fact.domain(fact.str("what"), Domain.WORK),
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
                        domain = fact.domain(fact.str("statement")),
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
                        domain = fact.domain(fact.str("title")),
                        metric = fact.str("metric"),
                        target = fact.str("target"),
                        createdAt = now, lastTouchedAt = now,
                    )
                ); true
            }
            "reference" -> {
                // DOM-18 — a document/fact worth keeping. An expiry date activates expiry watch (ANT-03).
                repo.graph.insertReference(
                    ReferenceItem(
                        type = fact.str("type") ?: "note",
                        label = fact.str("label") ?: fact.str("what") ?: return false,
                        valueEncrypted = fact.str("value") ?: "",
                        expiresAt = fact.str("expires")?.let { parseDate(it) },
                        createdAt = now,
                    )
                ); true
            }
            "project" -> {
                // DOM-22 — a personal project with its own outcome and optional deadline.
                repo.graph.insertProject(
                    Project(
                        title = fact.str("title") ?: fact.str("what") ?: return false,
                        outcome = fact.str("outcome"),
                        deadline = fact.str("deadline")?.let { parseDate(it) },
                        domain = fact.domain(fact.str("title") ?: fact.str("what"), Domain.PROJECTS),
                        createdAt = now, lastTouchedAt = now,
                    )
                ); true
            }
            "event" -> {
                // DOM-20/CAP-05 — a dated commitment on the calendar (trip, appointment, booking).
                val start = fact.str("start")?.let { parseDateTime(it) }
                    ?: fact.str("when")?.let { parseDateTime(it) } ?: return false
                repo.graph.upsertEvent(
                    EventEntity(
                        title = fact.str("title") ?: fact.str("what") ?: return false,
                        start = start,
                        location = fact.str("location"),
                        source = "capture",
                        createdAt = now,
                    )
                ); true
            }
            "value" -> {
                repo.graph.insertValue(
                    ValueStatement(
                        statement = fact.str("statement") ?: fact.str("text") ?: return false,
                        rank = fact.num("rank")?.toInt() ?: 99,
                        createdAt = now,
                    )
                ); true
            }
            "interaction" -> {
                // DOM-15 — a logged contact with a person; links to (or creates) the person.
                val who = fact.str("who") ?: fact.str("name") ?: return false
                val person = repo.graph.findPerson(who)
                    ?: Person(name = who, createdAt = now).let { it.copy(id = repo.graph.insertPerson(it)) }
                repo.graph.insertInteraction(
                    Interaction(
                        personId = person.id,
                        channel = fact.str("channel"),
                        whenAt = now,
                        summary = fact.str("summary") ?: fact.str("what"),
                        createdAt = now,
                    )
                ); true
            }
            "occasion" -> {
                // DOM-16 — a recurring date for a person (birthday/anniversary/follow_up).
                val who = fact.str("who") ?: fact.str("name") ?: return false
                val md = fact.str("date")?.let { parseMonthDay(it) } ?: return false
                val occKind = fact.str("kind")?.lowercase()?.takeIf {
                    it in setOf("birthday", "anniversary", "follow_up")
                } ?: "birthday"
                if (repo.graph.findOccasion(who, occKind) == null) {
                    repo.graph.insertOccasion(
                        Occasion(
                            personName = who, kind = occKind,
                            month = md.first, day = md.second,
                            note = fact.str("note"), createdAt = now,
                        )
                    )
                }
                true
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
    /**
     * The model's explicit domain tag wins; otherwise infer one from the fact's text so the capture
     * lands in the right domain even offline (DOM-05 activation, P11). Only [Domain.NONE] gaps fall
     * through to the classifier — an explicit tag is always honoured.
     */
    private fun JsonObject.domain(text: String?, default: Domain = Domain.NONE): Domain {
        str("domain")?.let { runCatching { Domain.valueOf(it.uppercase()) }.getOrNull() }
            ?.takeIf { it != Domain.NONE }
            ?.let { return it }
        return DomainClassifier.classify(text, default)
    }
    private fun JsonObject.energy(): EnergyCost =
        str("energy")?.let { runCatching { EnergyCost.valueOf(it.uppercase()) }.getOrNull() } ?: EnergyCost.MEDIUM

    /** Parse "16:00" / "4pm" into an Instant today; unknown formats yield null (a floating item). */
    private fun parseTimeToday(raw: String): java.time.Instant? {
        val t = parseLocalTime(raw) ?: return null
        return clock.today().atTime(t).atZone(clock.zone()).toInstant()
    }

    /** Parse "2026-03-10", "03-10", or "March 10" / "10 March" into (month, day); else null. */
    private fun parseMonthDay(raw: String): Pair<Int, Int>? {
        val s = raw.trim().lowercase()
        Regex("""(\d{4})-(\d{1,2})-(\d{1,2})""").find(s)?.let {
            return it.groupValues[2].toInt() to it.groupValues[3].toInt()
        }
        Regex("""^(\d{1,2})-(\d{1,2})$""").find(s)?.let {
            return it.groupValues[1].toInt() to it.groupValues[2].toInt()
        }
        val months = listOf(
            "january", "february", "march", "april", "may", "june",
            "july", "august", "september", "october", "november", "december",
        )
        val monthIdx = months.indexOfFirst { s.contains(it.take(3)) }
        if (monthIdx >= 0) {
            Regex("""(\d{1,2})""").find(s)?.let { return (monthIdx + 1) to it.groupValues[1].toInt() }
        }
        return null
    }

    /** Parse an ISO date ("2026-08-15") into an Instant at start of day; unknown formats yield null. */
    private fun parseDate(raw: String): java.time.Instant? {
        val d = runCatching { java.time.LocalDate.parse(raw.trim().take(10)) }.getOrNull() ?: return null
        return d.atStartOfDay(clock.zone()).toInstant()
    }

    /** Parse "2026-08-15" or "2026-08-15 09:30" / "2026-08-15T09:30" into an Instant. */
    private fun parseDateTime(raw: String): java.time.Instant? {
        val s = raw.trim().replace('T', ' ')
        val datePart = s.take(10)
        val date = runCatching { java.time.LocalDate.parse(datePart) }.getOrNull() ?: return null
        val time = s.drop(10).trim().let { if (it.isBlank()) null else parseLocalTime(it) } ?: LocalTime.of(9, 0)
        return date.atTime(time).atZone(clock.zone()).toInstant()
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
