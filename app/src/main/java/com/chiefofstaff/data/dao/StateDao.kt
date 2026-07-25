package com.chiefofstaff.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.chiefofstaff.data.entity.AnticipationItem
import com.chiefofstaff.data.entity.DayState
import com.chiefofstaff.data.entity.ModeState
import com.chiefofstaff.data.entity.NotificationLog
import com.chiefofstaff.data.entity.Prediction
import kotlinx.coroutines.flow.Flow

@Dao
interface StateDao {
    // Day state.
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertDay(d: DayState)
    @Query("SELECT * FROM day_state WHERE date = :date") suspend fun day(date: String): DayState?
    @Query("SELECT * FROM day_state WHERE date = :date") fun dayFlow(date: String): Flow<DayState?>

    // Mode (singleton).
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertMode(m: ModeState)
    @Query("SELECT * FROM mode_state WHERE id = 1") fun modeFlow(): Flow<ModeState?>
    @Query("SELECT * FROM mode_state WHERE id = 1") suspend fun mode(): ModeState?

    // Prediction ledger (ACC-05 / REV-09).
    @Insert suspend fun insertPrediction(p: Prediction): Long
    @Update suspend fun updatePrediction(p: Prediction)
    @Query("SELECT * FROM prediction WHERE subjectId = :subjectId AND resolvedAt IS NULL")
    suspend fun openPredictionsFor(subjectId: Long): List<Prediction>
    @Query("SELECT * FROM prediction WHERE resolvedAt IS NOT NULL AND resolvedAt >= :since")
    suspend fun resolvedSince(since: Long): List<Prediction>
    @Query("SELECT AVG(ABS(delta)) FROM prediction WHERE delta IS NOT NULL AND resolvedAt >= :since")
    suspend fun meanAbsErrorSince(since: Long): Float?

    // Anticipation (§11).
    @Insert suspend fun insertAnticipation(a: AnticipationItem): Long
    @Update suspend fun updateAnticipation(a: AnticipationItem)
    @Query("SELECT * FROM anticipation WHERE forDate = :date ORDER BY salience DESC LIMIT 1")
    suspend fun topAnticipationFor(date: String): AnticipationItem?
    @Query("SELECT * FROM anticipation WHERE forDate = :date ORDER BY salience DESC LIMIT 1")
    fun topAnticipationFlow(date: String): Flow<AnticipationItem?>

    // Notification budget ledger (INT-04).
    @Insert suspend fun logNotification(n: NotificationLog): Long
    @Query("SELECT COUNT(*) FROM notification_log WHERE postedDate = :date AND essential = 0")
    suspend fun discretionaryCountToday(date: String): Int

    @Query("SELECT COUNT(*) FROM notification_log WHERE postedDate = :date AND channel = :channel")
    suspend fun countChannelToday(date: String, channel: String): Int
}
