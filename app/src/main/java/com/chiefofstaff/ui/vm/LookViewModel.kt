package com.chiefofstaff.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chiefofstaff.AppContainer
import com.chiefofstaff.data.entity.Capture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class LookSection { TIMELINE, TRENDS, DOMAINS, PEOPLE }

data class LookUiState(
    val query: String = "",
    val results: List<String> = emptyList(),
    val expanded: LookSection? = null,
    val timeline: List<String> = emptyList(),
)

/**
 * Backs the Look screen (§3.2, UX-05) — "everything else". A search field over the FTS index plus
 * four sections collapsed by default (Timeline, Trends, Domains, People). This screen holds ~100 of
 * the 150 features and is visited perhaps twice a week, so nothing here competes for attention on
 * the Now screen (P9: capabilities, not buttons).
 */
class LookViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(LookUiState())
    val state: StateFlow<LookUiState> = _state

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
        if (next == LookSection.TIMELINE) loadTimeline()
    }

    private fun loadTimeline() {
        viewModelScope.launch {
            // recent() is a Flow; take a snapshot for this one-shot view.
            val list = container.repo.captures.recent(30).first()
            _state.value = _state.value.copy(timeline = list.map { it.render() })
        }
    }

    private fun Capture.render(): String {
        val t = java.time.LocalDateTime.ofInstant(createdAt, container.clock.zone())
        return "%02d/%02d %02d:%02d  %s".format(t.dayOfMonth, t.monthValue, t.hour, t.minute, rawContent)
    }
}
