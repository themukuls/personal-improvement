package com.chiefofstaff.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chiefofstaff.AppContainer
import com.chiefofstaff.data.entity.Capture
import com.chiefofstaff.data.model.CommitmentState
import com.chiefofstaff.data.model.Domain
import com.chiefofstaff.data.model.Mode
import com.chiefofstaff.domain.ReductionEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Duration

enum class LookSection { DIRECTION, HORIZONS, TIMELINE, TRENDS, DOMAINS, WAITING, PEOPLE, DECISIONS, REFERENCES, CONVERSATIONS }

data class LookUiState(
    val query: String = "",
    val results: List<String> = emptyList(),
    val expanded: LookSection? = null,
    val timeline: List<String> = emptyList(),
    // REV-04 / REV-09 — surfaced facts, never scores to defend.
    val consistencyPct: Int? = null,
    val predictionAccuracyHours: Float? = null,
    val healthLines: List<String> = emptyList(),
    // INT-08 — quiet mode state.
    val quietActive: Boolean = false,
    // DIR-09 — operating mode.
    val currentMode: Mode = Mode.NORMAL,
    // RES-02/04 — anti-burden.
    val proposals: List<ReductionEngine.ReductionItem> = emptyList(),
    val domainCounts: Map<Domain, Int> = emptyMap(),
    // MEM-15 — decision archive.
    val decisions: List<DecisionRow> = emptyList(),
    // MEM-11 — waiting-on register.
    val waiting: List<WaitingRow> = emptyList(),
    // DOM-15 / MEM-09 — people layer.
    val people: List<PeopleRow> = emptyList(),
    // FDN-07/08 — reference vault + emergency card.
    val references: List<ReferenceRow> = emptyList(),
    // FDN-02/03 — value → goal → project tree.
    val valueLines: List<String> = emptyList(),
    val goalLines: List<String> = emptyList(),
    val projectLines: List<String> = emptyList(),
    // REV-05 domain scorecard · SYS-17 token dashboard · CNV-12 session history.
    val scorecard: List<String> = emptyList(),
    val tokensLine: String? = null,
    val sessions: List<String> = emptyList(),
    val accuracyReport: String? = null,
    // DIR-10/11/12 + REV-06 horizons.
    val horizonGoals: List<String> = emptyList(),
    val weekAttribution: List<String> = emptyList(),
    val drift: List<String> = emptyList(),
    val weekLoad: Int = 0,
)

data class ReferenceRow(val label: String, val value: String, val sub: String, val emergency: Boolean)

data class PeopleRow(val id: Long, val name: String, val sub: String, val overdue: Boolean)

data class DecisionRow(val id: Long, val question: String, val chosen: String, val reviewLabel: String, val hasOutcome: Boolean)
data class WaitingRow(val id: Long, val what: String, val who: String, val context: String)

/**
 * Backs the Look screen (§3.2, UX-05). Search over the FTS index plus four collapsed sections. Now
 * that health data and a prediction ledger exist, the Trends section shows real facts: rolling
 * consistency (ACC-10), self-accuracy (REV-09), and recent health observations (REV-04). It also
 * hosts the quiet-mode control (INT-08) — suppress proactive output for a day or a week while
 * captures keep flowing.
 */
class LookViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(LookUiState())
    val state: StateFlow<LookUiState> = _state

    init { refreshQuiet() }

    fun onQuery(q: String) {
        _state.value = _state.value.copy(query = q)
        viewModelScope.launch {
            if (q.isBlank()) {
                _state.value = _state.value.copy(results = emptyList())
            } else {
                val hits = container.repo.searchCaptures(q).map { it.render() }
                _state.value = _state.value.copy(results = hits)
            }
        }
    }

    fun toggle(section: LookSection) {
        val next = if (_state.value.expanded == section) null else section
        _state.value = _state.value.copy(expanded = next)
        when (next) {
            LookSection.TIMELINE -> loadTimeline()
            LookSection.TRENDS -> loadTrends()
            LookSection.DOMAINS -> loadReduction()
            LookSection.DECISIONS -> loadDecisions()
            LookSection.WAITING -> loadWaiting()
            LookSection.PEOPLE -> loadPeople()
            LookSection.REFERENCES -> loadReferences()
            LookSection.DIRECTION -> loadDirection()
            LookSection.CONVERSATIONS -> loadSessions()
            LookSection.HORIZONS -> loadHorizons()
            else -> Unit
        }
    }

    /** DIR-10/11/12 + REV-06 — horizon summary. */
    private fun loadHorizons() {
        viewModelScope.launch {
            val byHorizon = container.horizonEngine.goalsByHorizon()
            val order = listOf("year", "quarter", "month")
            val goalLines = order.flatMap { h ->
                byHorizon[h]?.map { "${h.replaceFirstChar { c -> c.uppercase() }}: $it" } ?: emptyList()
            }
            _state.value = _state.value.copy(
                horizonGoals = goalLines,
                weekAttribution = container.horizonEngine.weekAttribution(),
                drift = container.horizonEngine.drift(),
                weekLoad = container.horizonEngine.weekLoad(),
            )
        }
    }

    /** REV-08 — record that a decision has been reviewed (outcome noted). */
    fun recordDecisionOutcome(id: Long) {
        viewModelScope.launch { container.repo.recordDecisionOutcome(id, "reviewed"); loadDecisions() }
    }

    /** FDN-02/03 — the value → goal → project spine that makes prioritisation non-arbitrary. */
    private fun loadDirection() {
        viewModelScope.launch {
            val values = container.repo.graph.values().first()
                .sortedBy { it.rank }.map { "${it.rank}. ${it.statement}" }
            val goals = container.repo.graph.activeGoals().first().map { g ->
                buildString {
                    append("${g.horizon}: ${g.title}")
                    g.metric?.let { append(" (").append(it); g.target?.let { t -> append(" → ").append(t) }; append(")") }
                }
            }
            val projects = container.repo.graph.activeProjects().first().map { p ->
                p.outcome?.let { "${p.title} → $it" } ?: p.title
            }
            _state.value = _state.value.copy(valueLines = values, goalLines = goals, projectLines = projects)
        }
    }

    /**
     * FDN-07/08 — the reference vault and emergency card. Emergency-relevant types (blood group,
     * allergies, emergency contacts) are flagged so the UI can lift them to the top. In this local-
     * storage build the value is stored in the clear on-device; the field is named for the spec's
     * intended per-field encryption.
     */
    private fun loadReferences() {
        viewModelScope.launch {
            val zone = container.clock.zone()
            val emergencyTypes = setOf("blood_group", "allergy", "emergency_contact")
            val rows = container.repo.graph.references().first().map { r ->
                val sub = r.expiresAt?.let {
                    val d = java.time.LocalDate.ofInstant(it, zone)
                    "expires ${d.dayOfMonth}/${d.monthValue}/${d.year}"
                } ?: r.type.replace('_', ' ')
                ReferenceRow(r.label, r.valueEncrypted, sub, r.type in emergencyTypes)
            }.sortedByDescending { it.emergency }
            _state.value = _state.value.copy(references = rows)
        }
    }

    /** DOM-15 / MEM-09 — people with cadence status; overdue = past the target contact interval. */
    private fun loadPeople() {
        viewModelScope.launch {
            val now = container.clock.now()
            val rows = container.repo.people().first().map { p ->
                val cadence = p.cadenceTargetDays
                val last = p.lastContact
                val days = last?.let { java.time.Duration.between(it, now).toDays() }
                val overdue = cadence != null && days != null && days > cadence
                // MEM-12 — most recent interaction summary, inline.
                val lastInteraction = container.repo.graph.interactionsFor(p.id).first().firstOrNull()?.summary
                val sub = buildString {
                    p.relationship?.let { append(it) }
                    if (days != null) { if (isNotEmpty()) append(" · "); append("last $days d ago") }
                    if (cadence != null) { if (isNotEmpty()) append(" · "); append("every $cadence d") }
                    if (!lastInteraction.isNullOrBlank()) { if (isNotEmpty()) append(" · "); append(lastInteraction) }
                }
                PeopleRow(p.id, p.name, sub, overdue)
            }
            _state.value = _state.value.copy(people = rows)
        }
    }

    fun logContact(id: Long) {
        viewModelScope.launch { container.repo.logContact(id); loadPeople() }
    }

    /** MEM-11 — load the waiting-on register (what others owe you). */
    private fun loadWaiting() {
        viewModelScope.launch {
            val zone = container.clock.zone()
            val rows = container.repo.openWaiting().first().map { w ->
                val ctx = buildString {
                    w.expectedBy?.let {
                        val d = java.time.LocalDate.ofInstant(it, zone)
                        append("expected ${d.dayOfMonth}/${d.monthValue}")
                    }
                    if (w.chaseCount > 0) { if (isNotEmpty()) append(" · "); append("chased ${w.chaseCount}×") }
                }
                WaitingRow(w.id, w.what, w.who, ctx)
            }
            _state.value = _state.value.copy(waiting = rows)
        }
    }

    /** ACC-11 — chase (nudge) an outstanding item, or mark it resolved. */
    fun chase(id: Long) {
        viewModelScope.launch { container.repo.chaseWaiting(id); loadWaiting() }
    }

    fun resolveWaiting(id: Long) {
        viewModelScope.launch { container.repo.resolveWaiting(id); loadWaiting() }
    }

    /** MEM-15 — the decision archive with review dates and outcomes. */
    private fun loadDecisions() {
        viewModelScope.launch {
            val zone = container.clock.zone()
            val rows = container.repo.graph.recentDecisions(20).map { d ->
                val review = d.reviewAt?.let {
                    val date = java.time.LocalDate.ofInstant(it, zone)
                    "review ${date.dayOfMonth}/${date.monthValue}"
                } ?: ""
                DecisionRow(d.id, d.question, d.chosen, review, d.actualOutcome != null)
            }
            _state.value = _state.value.copy(decisions = rows)
        }
    }

    /** RES-02/04 — load the domain backlog counts and the reduction proposals. */
    private fun loadReduction() {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                domainCounts = container.reductionEngine.openCountsByDomain(),
                proposals = container.reductionEngine.proposals(),
            )
        }
    }

    fun drop(item: ReductionEngine.ReductionItem) {
        viewModelScope.launch {
            container.reductionEngine.drop(item)
            loadReduction()
        }
    }

    fun declareBankruptcy(domain: Domain) {
        viewModelScope.launch {
            container.reductionEngine.declareBankruptcy(domain)
            loadReduction()
        }
    }

    /** ACC-14 — bulk-reschedule everything due today to tomorrow. */
    fun rescheduleTomorrow() {
        viewModelScope.launch { container.repo.rescheduleTodayToTomorrow(); loadReduction() }
    }

    /** INT-08 — enable quiet mode for [days] days (24h or 7d), or clear it. */
    fun setQuiet(days: Int?) {
        viewModelScope.launch {
            val until = days?.let { container.clock.now().plus(Duration.ofDays(it.toLong())) }
            container.repo.setQuietUntil(until)
            refreshQuiet()
        }
    }

    private fun refreshQuiet() {
        viewModelScope.launch {
            val mode = container.repo.mode()
            val active = mode.quietUntil != null && container.clock.now().isBefore(mode.quietUntil)
            _state.value = _state.value.copy(quietActive = active, currentMode = mode.current)
        }
    }

    /** DIR-09 — switch operating mode; affects plan shape (sick/recovery reduce the day). */
    fun setMode(mode: Mode) {
        viewModelScope.launch { container.repo.setMode(mode); refreshQuiet() }
    }

    private fun loadTrends() {
        viewModelScope.launch {
            val pct = container.consistencyScore.asPercent()
            val mae = container.ledger.meanAbsErrorLast30Days()
            val health = buildList {
                container.repo.graph.observations("steps", 1).firstOrNull()?.let { add("Steps: ${it.value.toInt()}") }
                container.repo.graph.observations("sleep_minutes", 1).firstOrNull()?.let {
                    add("Sleep: ${(it.value / 60).toInt()}h ${(it.value % 60).toInt()}m")
                }
                container.repo.graph.observations("weight_kg", 1).firstOrNull()?.let { add("Weight: ${"%.1f".format(it.value)} kg") }
                container.repo.graph.observations("screen_minutes", 1).firstOrNull()?.let { add("Screen: ${(it.value / 60).toInt()}h ${(it.value % 60).toInt()}m") }
            }
            // REV-05 — per-domain done/total over 30 days.
            val since = container.clock.today().minusDays(30).atStartOfDay(container.clock.zone()).toInstant().toEpochMilli()
            val resolved = container.repo.commitments.changedSince(since)
                .filter { it.state == CommitmentState.DONE || it.state == CommitmentState.SKIPPED || it.state == CommitmentState.DROPPED }
            val scorecard = resolved.groupBy { it.domain }.filterKeys { it != Domain.NONE }.map { (d, list) ->
                val done = list.count { it.state == CommitmentState.DONE }
                "${d.name.lowercase().replaceFirstChar { c -> c.uppercase() }} $done/${list.size}"
            }
            // SYS-17 — token spend today.
            val tokens = "tokens today: ${container.costTracker.todayInputTokens()} in / ${container.costTracker.todayOutputTokens()} out"
            _state.value = _state.value.copy(
                consistencyPct = pct, predictionAccuracyHours = mae, healthLines = health,
                scorecard = scorecard, tokensLine = tokens,
                accuracyReport = container.ledger.accuracyReport(),
            )
        }
    }

    /** CNV-12 — searchable session history (recent conversations). */
    private fun loadSessions() {
        viewModelScope.launch {
            val zone = container.clock.zone()
            val rows = container.repo.conversation.sessions().first().take(30).map { s ->
                val t = java.time.LocalDateTime.ofInstant(s.startedAt, zone)
                "%02d/%02d %02d:%02d · %s".format(t.dayOfMonth, t.monthValue, t.hour, t.minute, s.mode.name.lowercase())
            }
            _state.value = _state.value.copy(sessions = rows)
        }
    }

    private fun loadTimeline() {
        viewModelScope.launch {
            val list = container.repo.captures.recent(30).first()
            _state.value = _state.value.copy(timeline = list.map { it.render() })
        }
    }

    private fun Capture.render(): String {
        val t = java.time.LocalDateTime.ofInstant(createdAt, container.clock.zone())
        return "%02d/%02d %02d:%02d  %s".format(t.dayOfMonth, t.monthValue, t.hour, t.minute, rawContent)
    }
}
