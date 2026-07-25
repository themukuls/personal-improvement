package com.chiefofstaff.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chiefofstaff.AppContainer
import com.chiefofstaff.data.entity.Capture
import com.chiefofstaff.data.model.Domain
import com.chiefofstaff.domain.ReductionEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Duration

enum class LookSection { TIMELINE, TRENDS, DOMAINS, WAITING, PEOPLE, DECISIONS }

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
    // RES-02/04 — anti-burden.
    val proposals: List<ReductionEngine.ReductionItem> = emptyList(),
    val domainCounts: Map<Domain, Int> = emptyMap(),
    // MEM-15 — decision archive.
    val decisions: List<DecisionRow> = emptyList(),
    // MEM-11 — waiting-on register.
    val waiting: List<WaitingRow> = emptyList(),
    // DOM-15 / MEM-09 — people layer.
    val people: List<PeopleRow> = emptyList(),
)

data class PeopleRow(val id: Long, val name: String, val sub: String, val overdue: Boolean)

data class DecisionRow(val question: String, val chosen: String, val reviewLabel: String, val hasOutcome: Boolean)
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
            else -> Unit
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
                val sub = buildString {
                    p.relationship?.let { append(it) }
                    if (days != null) { if (isNotEmpty()) append(" · "); append("last $days d ago") }
                    if (cadence != null) { if (isNotEmpty()) append(" · "); append("every $cadence d") }
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
                DecisionRow(d.question, d.chosen, review, d.actualOutcome != null)
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
            val until = container.repo.mode().quietUntil
            val active = until != null && container.clock.now().isBefore(until)
            _state.value = _state.value.copy(quietActive = active)
        }
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
            }
            _state.value = _state.value.copy(consistencyPct = pct, predictionAccuracyHours = mae, healthLines = health)
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
