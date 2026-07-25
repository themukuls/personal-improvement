package com.chiefofstaff.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.chiefofstaff.data.model.CommitmentState
import com.chiefofstaff.data.model.Domain
import com.chiefofstaff.data.model.EnergyCost
import com.chiefofstaff.data.model.LoopState
import java.time.Instant

/**
 * §7.2 — the atomic unit of doing. Drives the Now list and the Close card stack. The state
 * machine (§7.4, ACC-01) is enforced in the domain layer; this table only stores the current
 * [state], the [deferralCount] that triggers escalation, and the timestamps the prediction
 * ledger and auto-archive read.
 */
@Entity(
    tableName = "commitment",
    indices = [Index("state"), Index("dueAt"), Index("domain"), Index("parentProjectId")],
)
data class Commitment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val what: String,
    val dueAt: Instant? = null,
    val energyCost: EnergyCost = EnergyCost.MEDIUM,
    val blockedBy: Long? = null,
    val domain: Domain = Domain.NONE,
    val parentProjectId: Long? = null,
    val state: CommitmentState = CommitmentState.CAPTURED,
    val deferralCount: Int = 0,
    val skipReason: String? = null,      // ACC-07, always optional
    val sourceCaptureId: Long? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val lastTouchedAt: Instant,          // drives 45-day auto-archive (RES-01)
    /** Context sublines the design shows, e.g. "skipped twice last week", "deferred once". */
    val contextNote: String? = null,
)

/**
 * §7.2 — what others owe you. "Half of working life." Feeds the waiting-on register (MEM-11)
 * and the waiting-on decay anticipation scan (§11).
 */
@Entity(tableName = "waiting_on", indices = [Index("state"), Index("expectedBy")])
data class WaitingOn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val what: String,
    val who: String,
    val personId: Long? = null,
    val promisedAt: Instant? = null,
    val expectedBy: Instant? = null,
    val chaseCount: Int = 0,
    val state: LoopState = LoopState.OPEN,
    val domain: Domain = Domain.WORK,
    val createdAt: Instant,
    val lastTouchedAt: Instant,
)
