package com.chiefofstaff.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chiefofstaff.AppContainer
import com.chiefofstaff.data.entity.Message
import com.chiefofstaff.data.entity.Session
import com.chiefofstaff.data.model.ConversationMode
import com.chiefofstaff.llm.LlmOrchestrator
import com.chiefofstaff.llm.TaskId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ChatLine(val role: String, val text: String)

data class TalkUiState(
    val lines: List<ChatLine> = emptyList(),
    val thinking: Boolean = false,
)

/**
 * Backs the Talk surface (§10, UX-03) — the layer that makes this a secretary, not a tracker. Full
 * memory is assembled by the CONVERSE task's context recipe; the surface is empty by default (no
 * suggested-prompt clutter, §3.2). Any turn can emit facts with one confirming tap (CNV-04); that
 * confirmation flow is surfaced by the screen when the model proposes a fact.
 */
class TalkViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(TalkUiState())
    val state: StateFlow<TalkUiState> = _state
    private var sessionId: Long = -1

    private suspend fun ensureSession(): Long {
        if (sessionId > 0) return sessionId
        sessionId = container.repo.conversation.insertSession(
            Session(startedAt = container.clock.now(), mode = ConversationMode.THINK)
        )
        return sessionId
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        _state.value = _state.value.copy(
            lines = _state.value.lines + ChatLine("user", trimmed),
            thinking = true,
        )
        viewModelScope.launch {
            val sid = ensureSession()
            val now = container.clock.now()
            container.repo.conversation.insertMessage(Message(sessionId = sid, role = "user", content = trimmed, createdAt = now))

            val history = _state.value.lines.joinToString("\n") { "${it.role}: ${it.text}" }
            val result = container.orchestrator.run(
                TaskId.CONVERSE,
                params = mapOf("history" to history, "message" to trimmed),
                extraUserContent = trimmed,
            )
            val reply = when (result) {
                is LlmOrchestrator.TaskResult.Text -> result.text
                is LlmOrchestrator.TaskResult.Structured -> result.rawText
                is LlmOrchestrator.TaskResult.Failed ->
                    "I can't reach my reasoning right now — but I saved what you said, and nothing is lost."
            }
            container.repo.conversation.insertMessage(
                Message(sessionId = sid, role = "assistant", content = reply, createdAt = container.clock.now())
            )
            _state.value = _state.value.copy(
                lines = _state.value.lines + ChatLine("assistant", reply),
                thinking = false,
            )
        }
    }
}
