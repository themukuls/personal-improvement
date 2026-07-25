package com.chiefofstaff.data

import com.chiefofstaff.core.Clock
import com.chiefofstaff.data.entity.Capture
import com.chiefofstaff.data.entity.Commitment
import com.chiefofstaff.data.entity.DayState
import com.chiefofstaff.data.entity.ModeState
import com.chiefofstaff.data.model.CaptureSource
import com.chiefofstaff.data.model.Mode
import kotlinx.coroutines.flow.Flow

/**
 * The single façade the rest of the app talks to. It hides Room behind intention-level methods
 * and enforces the two invariants that matter most here:
 *   - captures are immutable and always FTS-indexed on write (P5, MEM-01/03),
 *   - the mode singleton always exists so the referee and notification budget have a value.
 *
 * Higher-level behaviour (state machine transitions, plan generation, anticipation) lives in the
 * domain layer and calls back into this repository; kept apart so the data layer stays dumb.
 */
class LifeRepository(
    private val db: CoSDatabase,
    private val clock: Clock,
) {
    val captures = db.captureDao()
    val commitments = db.commitmentDao()
    val graph = db.graphDao()
    val state = db.stateDao()
    val conversation = db.conversationDao()

    // --- Capture (immutable, always indexed) ---
    suspend fun capture(source: CaptureSource, raw: String, mediaPath: String? = null): Long {
        val now = clock.now()
        // The AFTER INSERT trigger on `capture` mirrors the row into the FTS index automatically.
        return captures.insert(
            Capture(source = source, createdAt = now, rawContent = raw, mediaPath = mediaPath)
        )
    }

    fun recentCaptures(limit: Int = 100): Flow<List<Capture>> = captures.recent(limit)
    suspend fun searchCaptures(query: String): List<Capture> = captures.search(sanitizeFts(query))

    // --- Now-screen reactive reads ---
    fun openCommitments(): Flow<List<Commitment>> = commitments.openCommitments()

    // --- Mode + day state ---
    fun modeFlow(): Flow<ModeState?> = state.modeFlow()

    suspend fun mode(): ModeState {
        state.mode()?.let { return it }
        val fresh = ModeState(current = Mode.NORMAL, since = clock.now())
        state.upsertMode(fresh)
        return fresh
    }

    suspend fun setMode(mode: Mode) {
        state.upsertMode(mode().copy(current = mode, since = clock.now()))
    }

    /** Quiet mode (INT-08): suppress proactive output until [until]; captures still flow. */
    suspend fun setQuietUntil(until: java.time.Instant?) {
        state.upsertMode(mode().copy(quietUntil = until))
    }

    suspend fun today(): DayState {
        val key = DayState.keyFor(clock.today())
        return state.day(key) ?: DayState(date = key, updatedAt = clock.now()).also { state.upsertDay(it) }
    }

    fun todayFlow(): Flow<DayState?> = state.dayFlow(DayState.keyFor(clock.today()))

    suspend fun setEnergy(energy: Int) {
        val d = today()
        state.upsertDay(d.copy(energy = energy.coerceIn(1, 5), updatedAt = clock.now()))
    }

    /** FTS MATCH is picky about punctuation; keep alnum tokens and OR them for forgiving recall. */
    private fun sanitizeFts(query: String): String =
        query.split(Regex("\\s+"))
            .map { it.filter(Char::isLetterOrDigit) }
            .filter { it.isNotBlank() }
            .joinToString(" OR ") { "$it*" }
            .ifBlank { "\"\"" }
}
