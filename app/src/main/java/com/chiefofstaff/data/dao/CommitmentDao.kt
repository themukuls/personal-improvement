package com.chiefofstaff.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.chiefofstaff.data.entity.Commitment
import com.chiefofstaff.data.entity.WaitingOn
import kotlinx.coroutines.flow.Flow

@Dao
interface CommitmentDao {
    @Insert suspend fun insert(c: Commitment): Long
    @Update suspend fun update(c: Commitment)

    @Query("SELECT * FROM commitment WHERE id = :id")
    suspend fun byId(id: Long): Commitment?

    /** SYS-11 — snapshot for the full JSON export. */
    @Query("SELECT * FROM commitment ORDER BY createdAt DESC")
    suspend fun allCommitments(): List<Commitment>

    /** The Now list: today's open items in due order. Terminal-state items drop off automatically. */
    @Query(
        """
        SELECT * FROM commitment
        WHERE state IN ('CAPTURED','SCHEDULED','DUE','ASKED','DEFERRED','RECOMMITTED')
        ORDER BY CASE WHEN dueAt IS NULL THEN 1 ELSE 0 END, dueAt ASC
        """
    )
    fun openCommitments(): Flow<List<Commitment>>

    /** Snapshot of open commitments (for reduction/bankruptcy grouping). */
    @Query(
        """
        SELECT * FROM commitment
        WHERE state IN ('CAPTURED','SCHEDULED','DUE','ASKED','DEFERRED','RECOMMITTED')
        ORDER BY domain
        """
    )
    suspend fun openCommitmentsNow(): List<Commitment>

    /** The Close card stack: everything due today that hasn't reached a verdict yet. */
    @Query(
        """
        SELECT * FROM commitment
        WHERE state IN ('SCHEDULED','DUE','ASKED','RECOMMITTED')
          AND dueAt IS NOT NULL AND dueAt <= :endOfDay
        ORDER BY dueAt ASC
        """
    )
    suspend fun dueThrough(endOfDay: Long): List<Commitment>

    @Query("SELECT * FROM commitment WHERE state = 'DONE' AND updatedAt >= :since")
    suspend fun completedSince(since: Long): List<Commitment>

    /** RES-01 — auto-archive candidates: untouched for 45 days and not already terminal. */
    @Query(
        """
        SELECT * FROM commitment
        WHERE lastTouchedAt < :cutoff
          AND state NOT IN ('DONE','DROPPED','AUTO_ARCHIVED')
        """
    )
    suspend fun dormantBefore(cutoff: Long): List<Commitment>

    @Query("SELECT COUNT(*) FROM commitment WHERE state = 'DONE' AND updatedAt >= :since")
    suspend fun doneCountSince(since: Long): Int

    /** REV-05 — commitments touched in a window, for the per-domain scorecard. */
    @Query("SELECT * FROM commitment WHERE updatedAt >= :since")
    suspend fun changedSince(since: Long): List<Commitment>

    @Query("SELECT COUNT(*) FROM commitment WHERE state IN ('DONE','SKIPPED','DROPPED') AND updatedAt >= :since")
    suspend fun resolvedCountSince(since: Long): Int

    // --- Waiting-on register (MEM-11) ---
    @Insert suspend fun insertWaiting(w: WaitingOn): Long
    @Update suspend fun updateWaiting(w: WaitingOn)

    @Query("SELECT * FROM waiting_on WHERE state IN ('OPEN','CHASED') ORDER BY expectedBy ASC")
    fun openWaiting(): Flow<List<WaitingOn>>

    @Query("SELECT * FROM waiting_on WHERE state IN ('OPEN','CHASED') AND expectedBy < :now")
    suspend fun overdueWaiting(now: Long): List<WaitingOn>

    @Query("SELECT * FROM waiting_on WHERE id = :id")
    suspend fun waitingById(id: Long): WaitingOn?
}
