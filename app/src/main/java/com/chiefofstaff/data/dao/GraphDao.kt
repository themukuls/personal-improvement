package com.chiefofstaff.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.chiefofstaff.data.entity.Decision
import com.chiefofstaff.data.entity.EventEntity
import com.chiefofstaff.data.entity.Goal
import com.chiefofstaff.data.entity.Interaction
import com.chiefofstaff.data.entity.Note
import com.chiefofstaff.data.entity.Occasion
import com.chiefofstaff.data.entity.Person
import com.chiefofstaff.data.entity.Project
import com.chiefofstaff.data.entity.Observation
import com.chiefofstaff.data.entity.ReferenceItem
import com.chiefofstaff.data.entity.RuleEntity
import com.chiefofstaff.data.entity.ValueStatement
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface GraphDao {
    // Values / goals / projects — the priority spine.
    @Insert suspend fun insertValue(v: ValueStatement): Long
    @Query("SELECT * FROM value_statement ORDER BY rank ASC") fun values(): Flow<List<ValueStatement>>

    @Insert suspend fun insertGoal(g: Goal): Long
    @Update suspend fun updateGoal(g: Goal)
    @Query("SELECT * FROM goal WHERE status = 'active' ORDER BY horizon") fun activeGoals(): Flow<List<Goal>>
    @Query("SELECT * FROM goal WHERE lastTouchedAt < :cutoff AND status = 'active'")
    suspend fun staleGoals(cutoff: Long): List<Goal>

    @Insert suspend fun insertProject(p: Project): Long
    @Query("SELECT * FROM project WHERE status = 'active'") fun activeProjects(): Flow<List<Project>>

    // Rules — the constitution / referee input.
    @Insert suspend fun insertRule(r: RuleEntity): Long
    @Update suspend fun updateRule(r: RuleEntity)
    @Query("SELECT * FROM rule WHERE active = 1") fun activeRules(): Flow<List<RuleEntity>>
    @Query("SELECT * FROM rule WHERE active = 1") suspend fun activeRulesNow(): List<RuleEntity>

    // People + interactions.
    @Insert suspend fun insertPerson(p: Person): Long
    @Update suspend fun updatePerson(p: Person)
    @Query("SELECT * FROM person ORDER BY importance DESC") fun people(): Flow<List<Person>>
    @Query("SELECT * FROM person WHERE name = :name OR aliases LIKE '%' || :name || '%' LIMIT 1")
    suspend fun findPerson(name: String): Person?
    @Query("SELECT * FROM person WHERE cadenceTargetDays IS NOT NULL")
    suspend fun peopleWithCadence(): List<Person>
    @Query("SELECT * FROM person WHERE id = :id")
    suspend fun personById(id: Long): Person?

    @Insert suspend fun insertInteraction(i: Interaction): Long
    @Query("SELECT * FROM interaction WHERE personId = :personId ORDER BY whenAt DESC")
    fun interactionsFor(personId: Long): Flow<List<Interaction>>

    // Observations (health/behaviour).
    @Insert suspend fun insertObservation(o: Observation): Long
    @Query("SELECT * FROM observation WHERE metric = :metric ORDER BY observedAt DESC LIMIT :limit")
    suspend fun observations(metric: String, limit: Int = 60): List<Observation>
    @Query("SELECT * FROM observation WHERE metric = :metric AND observedAt >= :since ORDER BY observedAt DESC")
    suspend fun observationsSince(metric: String, since: Long): List<Observation>
    @Query("SELECT * FROM observation ORDER BY observedAt DESC LIMIT :limit")
    fun recentObservations(limit: Int = 30): Flow<List<Observation>>

    // Decisions.
    @Insert suspend fun insertDecision(d: Decision): Long
    @Update suspend fun updateDecision(d: Decision)
    @Query("SELECT * FROM decision WHERE reviewAt IS NOT NULL AND reviewAt <= :now AND actualOutcome IS NULL")
    suspend fun decisionsDueForReview(now: Long): List<Decision>
    @Query("SELECT * FROM decision ORDER BY createdAt DESC") fun decisions(): Flow<List<Decision>>
    @Query("SELECT * FROM decision ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recentDecisions(limit: Int = 20): List<Decision>
    @Query("SELECT * FROM decision WHERE id = :id") suspend fun decisionById(id: Long): Decision?
    /** REV-08 — decisions past 90 days with no recorded outcome, for the decision audit. */
    @Query("SELECT * FROM decision WHERE actualOutcome IS NULL AND createdAt <= :cutoff ORDER BY createdAt ASC")
    suspend fun decisionsOlderThan(cutoff: Long): List<Decision>

    // Reference vault (encrypted values).
    @Insert suspend fun insertReference(r: ReferenceItem): Long
    @Query("SELECT * FROM reference_item ORDER BY type") fun references(): Flow<List<ReferenceItem>>
    @Query("SELECT * FROM reference_item WHERE expiresAt IS NOT NULL AND expiresAt <= :horizon")
    suspend fun expiringBefore(horizon: Long): List<ReferenceItem>
    /** RES-10 — references that expired before a cutoff, for the annual pruning proposal. */
    @Query("SELECT * FROM reference_item WHERE expiresAt IS NOT NULL AND expiresAt < :cutoff")
    suspend fun expiredBefore(cutoff: Long): List<ReferenceItem>

    // Events (calendar).
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertEvent(e: EventEntity): Long
    @Query("SELECT * FROM event WHERE start BETWEEN :from AND :to ORDER BY start ASC")
    suspend fun eventsBetween(from: Long, to: Long): List<EventEntity>
    @Query("SELECT * FROM event WHERE externalId = :externalId LIMIT 1")
    suspend fun eventByExternalId(externalId: String): EventEntity?
    @Query("DELETE FROM event WHERE id = :id") suspend fun deleteEvent(id: Long)

    // Notes.
    @Insert suspend fun insertNote(n: Note): Long
    @Query("SELECT * FROM note ORDER BY createdAt DESC") fun notes(): Flow<List<Note>>

    // Occasions (DOM-16 — recurring dates per person).
    @Insert suspend fun insertOccasion(o: Occasion): Long
    @Query("SELECT * FROM occasion ORDER BY month, day") suspend fun allOccasions(): List<Occasion>
    @Query("SELECT * FROM occasion WHERE personName = :name AND kind = :kind LIMIT 1")
    suspend fun findOccasion(name: String, kind: String): Occasion?
}
