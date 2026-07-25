package com.chiefofstaff.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chiefofstaff.AppContainer
import com.chiefofstaff.data.entity.Commitment
import com.chiefofstaff.data.model.Verdict
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class CloseCard(val id: Long, val time: String, val title: String, val context: String?)

data class CloseUiState(
    val cards: List<CloseCard> = emptyList(),
    val index: Int = 0,
    val done: Boolean = false,
)

/**
 * Backs the Close card stack (§3.2, UX-04). One card at a time; swipe right = done, left = skipped,
 * up = defer, down = drop (§9.2). A skip asks one optional "why?" that is dismissible with one tap —
 * reasons are the most valuable data in the system and must never be mandatory (ACC-07).
 */
class CloseViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(CloseUiState())
    val state: StateFlow<CloseUiState> = _state

    init { load() }

    private fun load() {
        viewModelScope.launch {
            val zone = container.clock.zone()
            val cards = container.eveningClose.cardStack().map { it.toCard(zone) }
            _state.value = CloseUiState(cards = cards, index = 0, done = cards.isEmpty())
        }
    }

    fun verdict(verdict: Verdict, skipReason: String? = null) {
        val s = _state.value
        val card = s.cards.getOrNull(s.index) ?: return
        viewModelScope.launch {
            container.eveningClose.verdict(card.id, verdict, skipReason)
            val nextIndex = s.index + 1
            _state.value = s.copy(index = nextIndex, done = nextIndex >= s.cards.size)
        }
    }

    private fun Commitment.toCard(zone: java.time.ZoneId): CloseCard {
        val time = dueAt?.let {
            java.time.LocalTime.ofInstant(it, zone).let { t -> "%02d:%02d".format(t.hour, t.minute) }
        } ?: "wait"
        return CloseCard(id = id, time = time, title = what, context = contextNote)
    }
}
