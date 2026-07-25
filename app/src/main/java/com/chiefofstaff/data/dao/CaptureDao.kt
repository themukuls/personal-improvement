package com.chiefofstaff.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.chiefofstaff.data.entity.Capture
import com.chiefofstaff.data.entity.ReviewQueueItem
import kotlinx.coroutines.flow.Flow

@Dao
interface CaptureDao {
    @Insert suspend fun insert(capture: Capture): Long

    // The FTS index (capture_fts) is kept in sync automatically by SQLite triggers created in the
    // database callback (see CoSDatabase). Captures are immutable (P5), so in practice only the
    // AFTER INSERT trigger ever fires — no manual indexing call is needed here.

    @Query("SELECT * FROM capture WHERE parsed = 0 ORDER BY createdAt ASC")
    suspend fun unparsed(): List<Capture>

    @Query("UPDATE capture SET parsed = 1, parseVersion = :version, confidence = :confidence WHERE id = :id")
    suspend fun markParsed(id: Long, version: String, confidence: Float)

    @Query("SELECT * FROM capture ORDER BY createdAt DESC LIMIT :limit")
    fun recent(limit: Int = 100): Flow<List<Capture>>

    /**
     * MEM-03 — full-text recall over the immutable log. FTS4 MATCH; results ordered by recency
     * because relevance here is "when did I say this", not similarity ranking (§6.1).
     */
    @Query(
        """
        SELECT capture.* FROM capture
        JOIN capture_fts ON capture.id = capture_fts.rowid
        WHERE capture_fts MATCH :query
        ORDER BY capture.createdAt DESC
        LIMIT :limit
        """
    )
    suspend fun search(query: String, limit: Int = 50): List<Capture>

    @Query("SELECT * FROM capture WHERE id = :id")
    suspend fun byId(id: Long): Capture?

    /** SYS-11 — snapshot for the full JSON export. */
    @Query("SELECT * FROM capture ORDER BY createdAt DESC LIMIT :limit")
    suspend fun snapshot(limit: Int = 100_000): List<Capture>

    // --- Review queue (MEM-05) ---
    @Insert suspend fun enqueueReview(item: ReviewQueueItem): Long

    @Query("SELECT * FROM review_queue WHERE resolved = 0 ORDER BY createdAt ASC")
    fun openReviews(): Flow<List<ReviewQueueItem>>

    /** MEM-14 — snapshot of open review items for the nightly re-parse. */
    @Query("SELECT * FROM review_queue WHERE resolved = 0 ORDER BY createdAt ASC")
    suspend fun openReviewsNow(): List<ReviewQueueItem>

    @Query("UPDATE review_queue SET resolved = 1 WHERE id = :id")
    suspend fun resolveReview(id: Long)

    @Query("SELECT COUNT(*) FROM review_queue WHERE resolved = 0")
    fun openReviewCount(): Flow<Int>
}
