package com.chiefofstaff.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.chiefofstaff.data.model.AnticipationKind
import com.chiefofstaff.data.model.Mode
import java.time.Instant
import java.time.LocalDate

/** §7.3 — one row per day. Energy/load drive the minimum-viable-day reduction (DIR-04, RES-07). */
@Entity(tableName = "day_state")
data class DayState(
    @PrimaryKey val date: String,        // ISO LocalDate as the natural key
    val energy: Int? = null,             // 1–5 (CAP-10)
    val mood: String? = null,
    val sleepQuality: Int? = null,
    val illnessFlag: Boolean = false,
    val loadScore: Float? = null,
    val updatedAt: Instant,
) {
    companion object { fun keyFor(d: LocalDate): String = d.toString() }
}

/** §7.3 — current operating mode. Single active row. Affects notification budget + plan shape. */
@Entity(tableName = "mode_state")
data class ModeState(
    @PrimaryKey val id: Int = 1,         // singleton
    val current: Mode = Mode.NORMAL,
    val since: Instant,
    /** Quiet mode (INT-08): suppress proactive output until this instant; captures still flow. */
    val quietUntil: Instant? = null,
)

/**
 * §1.3 / ACC-05 — the prediction ledger. Every "you'll finish by 4pm" is written here when made,
 * and the evening close writes back [actual] and [resolvedAt]. This is what lets the system
 * report on its own accuracy (REV-09) and stop planning your Tuesday evenings optimistically.
 */
@Entity(tableName = "prediction", indices = [Index("subjectId"), Index("resolvedAt")])
data class Prediction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val predictedAt: Instant,
    val subjectId: Long? = null,         // usually a commitment id
    val subjectKind: String,             // "commitment", "load", "trend"…
    val prediction: String,              // compact, e.g. "done_by=15:20"
    val confidence: Float,
    val actual: String? = null,
    val resolvedAt: Instant? = null,
    val delta: Float? = null,            // signed error once resolved
)

/**
 * §11 — the single daily anticipation item, produced by the 02:00 batch and surfaced inside the
 * morning brief and the Now "LOOKING AHEAD" card. Exactly one is shown per day (ANT-02); the
 * usefulness rating (ANT-11) tunes future scans.
 */
@Entity(tableName = "anticipation", indices = [Index("forDate"), Index("shown")])
data class AnticipationItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: AnticipationKind,
    val forDate: String,                 // ISO date this item is for
    val headline: String,                // "Passport expires 14 Sep."
    val detail: String,                  // "Bengaluru renewal is running four weeks."
    val salience: Float,                 // ranking score; highest wins the one daily slot
    val relatedId: Long? = null,
    val shown: Boolean = false,
    val ratedUseful: Boolean? = null,    // ANT-11
    val createdAt: Instant,
)

/**
 * INT-04 — the notification budget is enforced in code, not by policy. Every posted proactive
 * notification is logged here; the enforcer refuses to post once the daily cap (3 in V0) is hit.
 * Getting muted is unrecoverable (P3), so this table is load-bearing.
 */
@Entity(tableName = "notification_log", indices = [Index("postedDate"), Index("channel")])
data class NotificationLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channel: String,
    val postedAt: Instant,
    val postedDate: String,              // ISO date, for the daily count
    val title: String,
    val essential: Boolean = false,      // rituals bypass the discretionary cap
)
