package com.chiefofstaff.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.chiefofstaff.data.model.Domain
import com.chiefofstaff.data.model.Enforcement
import java.time.Instant

/**
 * The life graph (§7.2). Value → Goal → Project → Commitment is the priority spine that makes
 * ranking non-arbitrary. The remaining types (Person, Interaction, Observation, Decision, Rule,
 * Reference, Event, Note) hang off it. Every type here is progressively disclosed (P9): a table
 * exists from day one but its screen real estate appears only when it holds data.
 */

/** §7.2 — root of the tree. 5–7 ranked statements of what you're optimising for. */
@Entity(tableName = "value_statement", indices = [Index("rank")])
data class ValueStatement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val statement: String,
    val rank: Int,
    val createdAt: Instant,
)

/** §7.2 — the layer that makes prioritisation non-arbitrary. */
@Entity(tableName = "goal", indices = [Index("horizon"), Index("domain"), Index("status")])
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val horizon: String,                 // year / quarter / month
    val domain: Domain,
    val metric: String? = null,
    val target: String? = null,
    val status: String = "active",
    val parentValueId: Long? = null,
    val createdAt: Instant,
    val lastTouchedAt: Instant,
)

/** §7.2 — container for multi-step work ("renovate the kitchen"). */
@Entity(tableName = "project", indices = [Index("domain"), Index("status")])
data class Project(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val outcome: String? = null,
    val deadline: Instant? = null,
    val domain: Domain,
    val parentGoalId: Long? = null,
    val status: String = "active",
    val createdAt: Instant,
    val lastTouchedAt: Instant,
)

/** §7.2 — your constitution. The referee (DIR-03/INT-09) reads these as planning constraints. */
@Entity(tableName = "rule", indices = [Index("domain"), Index("active")])
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val statement: String,
    val domain: Domain,
    val enforcement: Enforcement = Enforcement.SOFT,
    val activeHours: String? = null,     // e.g. "14:00-16:00" for a deep-work rule
    val active: Boolean = true,
    val createdAt: Instant,
)

/** §7.2 — the people layer (DOM-15). */
@Entity(tableName = "person", indices = [Index("name")])
data class Person(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val aliases: List<String> = emptyList(),
    val relationship: String? = null,
    val importance: Int = 3,
    val lastContact: Instant? = null,
    val cadenceTargetDays: Int? = null,  // expected contact cadence (neglect detection)
    val createdAt: Instant,
)

/** §7.2 — what actually happened with a person (DOM-15, MEM-12). */
@Entity(tableName = "interaction", indices = [Index("personId"), Index("whenAt")])
data class Interaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val channel: String? = null,
    val whenAt: Instant,
    val summary: String? = null,
    val commitmentsMade: String? = null,
    val commitmentsReceived: String? = null,
    val createdAt: Instant,
)

/** §7.2 — health and behaviour data (CAP-07, DOM-05). */
@Entity(tableName = "observation", indices = [Index("metric"), Index("observedAt")])
data class Observation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val metric: String,                  // steps, sleep_minutes, weight_kg, hr, spend_inr…
    val value: Double,
    val unit: String? = null,
    val observedAt: Instant,
    val source: String? = null,
    val createdAt: Instant,
)

/** §7.2 — enables auditing your own judgment (MEM-15, DIR-14). */
@Entity(tableName = "decision", indices = [Index("reviewAt")])
data class Decision(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val question: String,
    val chosen: String,
    val rationale: String? = null,
    val expectedOutcome: String? = null,
    val reviewAt: Instant? = null,
    val actualOutcome: String? = null,
    val createdAt: Instant,
)

/** §7.2 — the "knows everything" layer. Value is encrypted at the field level (FDN-07). */
@Entity(tableName = "reference_item", indices = [Index("type"), Index("expiresAt")])
data class ReferenceItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,                    // passport, policy, blood_group, emergency_contact…
    val label: String,
    val valueEncrypted: String,          // ciphertext; never leaves the device (§6.8)
    val expiresAt: Instant? = null,      // drives expiry watch (ANT-03)
    val createdAt: Instant,
)

/** §7.2 — time constraints (CAP-05). */
@Entity(tableName = "event", indices = [Index("start")])
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val start: Instant,
    val end: Instant? = null,
    val location: String? = null,
    val attendees: List<String> = emptyList(),
    val source: String = "calendar",
    val externalId: String? = null,
    val createdAt: Instant,
)

/** §7.2 — junk drawer. Keep it. */
@Entity(tableName = "note")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val tags: List<String> = emptyList(),
    val createdAt: Instant,
)

/**
 * DOM-16 — a recurring date attached to a person: a birthday, an anniversary, or a promised
 * follow-up. Stored year-agnostic as month/day so the anticipation scan can compute days-until the
 * next occurrence and nudge a few days ahead — the whole point is to never be the one who forgot.
 */
@Entity(tableName = "occasion", indices = [Index("month"), Index("day")])
data class Occasion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personName: String,
    val kind: String,          // birthday, anniversary, follow_up
    val month: Int,            // 1-12
    val day: Int,              // 1-31
    val note: String? = null,
    val createdAt: Instant,
)
