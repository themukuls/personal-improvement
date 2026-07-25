package com.chiefofstaff.data.entity

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import com.chiefofstaff.data.model.CaptureSource
import com.chiefofstaff.data.model.ReviewReason
import java.time.Instant

/**
 * §7.1 — the immutable capture. Raw input is never edited (P5); derived facts sit alongside in
 * their typed tables and reference [id]. A bad parse stays recoverable because the raw content
 * and the [parseVersion] that produced it are both retained (§6.4).
 */
@Entity(
    tableName = "capture",
    indices = [Index("createdAt"), Index("source")],
)
data class Capture(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: CaptureSource,
    val createdAt: Instant,
    val rawContent: String,
    val mediaPath: String? = null,
    val parseVersion: String? = null,   // which prompt_version parsed this (§6.4)
    val confidence: Float? = null,
    val parsed: Boolean = false,        // false = still queued for extraction
)

/**
 * MEM-03 — FTS5 mirror of capture text for free-form historical recall ("what did I say about…").
 * Room's @Fts4 maps to the SQLite FTS extension; content is kept in sync by the repository.
 */
@Fts4(contentEntity = Capture::class)
@Entity(tableName = "capture_fts")
data class CaptureFts(
    val rawContent: String,
)

/**
 * §7.3 / MEM-05 — low-confidence or ambiguous parses land here for one-gesture resolution.
 * Correcting from wherever the fact appears (P12, MEM-06) resolves the matching row.
 */
@Entity(tableName = "review_queue", indices = [Index("resolved")])
data class ReviewQueueItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val captureId: Long,
    val reason: ReviewReason,
    val detail: String? = null,
    val createdAt: Instant,
    val resolved: Boolean = false,
)
