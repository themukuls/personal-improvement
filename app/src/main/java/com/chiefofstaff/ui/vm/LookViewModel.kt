package com.chiefofstaff.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chiefofstaff.AppContainer
import com.chiefofstaff.data.entity.Capture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Duration

enum class LookSection { TIMELINE, TRENDS, DOMAINS, PEOPLE }

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
)

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
            else -> Unit
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
