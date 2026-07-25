package com.chiefofstaff.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chiefofstaff.AppContainer
import com.chiefofstaff.data.entity.Decision
import com.chiefofstaff.data.entity.Message
import com.chiefofstaff.data.entity.Session
import com.chiefofstaff.data.model.ConversationMode
import java.time.Duration
import com.chiefofstaff.llm.LlmOrchestrator
import com.chiefofstaff.llm.TaskId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ChatLine(val role: String, val text: String, val saved: Boolean = false)

data class TalkUiState(
    val lines: List<ChatLine> = emptyList(),
    val thinking: Boolean = false,
    val mode: ConversationMode = ConversationMode.THINK,
)

/**
 * Backs the Talk surface (§10). Full memory is assembled by the CONVERSE recipe. This adds the
 * conversation modes (§10.1) — Recall, Plan, Think, Challenge, Decide, Draft, Debrief — which change
 * how the assistant responds, and one-tap fact emission (CNV-04): any turn can be saved to memory,
 * where the extraction pipeline turns it into typed facts. Challenge mode is not optional politeness
 * (§10.2): it is told to surface contradictions with the user's own rules and past decisions.
 */
class TalkViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(TalkUiState())
    val state: StateFlow<TalkUiState> = _state
    private var sessionId: Long = -1

    fun setMode(mode: ConversationMode) {
        _state.value = _state.value.copy(mode = mode)
    }

    private suspend fun ensureSession(): Long {
        if (sessionId > 0) return sessionId
        sessionId = container.repo.conversation.insertSession(
            Session(startedAt = container.clock.now(), mode = _state.value.mode)
        )
        return sessionId
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val mode = _state.value.mode
        _state.value = _state.value.copy(
            lines = _state.value.lines + ChatLine("user", trimmed),
            thinking = true,
        )
        viewModelScope.launch {
            val sid = ensureSession()
            val now = container.clock.now()
            container.repo.conversation.insertMessage(Message(sessionId = sid, role = "user", content = trimmed, createdAt = now))

            val history = _state.value.lines.joinToString("\n") { "${it.role}: ${it.text}" }
            val framed = "Mode: ${mode.name}. ${directive(mode)}\nUser: $trimmed"
            val result = container.orchestrator.run(
                TaskId.CONVERSE,
                params = mapOf("history" to history, "message" to trimmed),
                extraUserContent = framed,
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

    /** CNV-04 — one-tap fact emission: run a line through the capture pipeline to derive typed facts. */
    fun saveToMemory(line: ChatLine) {
        viewModelScope.launch {
            container.captureManager.captureText(line.text)
            _state.value = _state.value.copy(
                lines = _state.value.lines.map { if (it === line) it.copy(saved = true) else it }
            )
        }
    }

    /**
     * CNV-09 / MEM-15 — write a Decision record from a Decide-mode turn. The question is the last
     * thing the user asked; the chosen path is the assistant's reply; a 30-day review is scheduled,
     * which the anticipation engine (ANT-10) will surface when it comes due — so a decision is
     * audited against its expected outcome rather than quietly forgotten.
     */
    fun saveAsDecision(line: ChatLine) {
        viewModelScope.launch {
            val question = _state.value.lines.lastOrNull { it.role == "user" }?.text.orEmpty()
            val now = container.clock.now()
            container.repo.graph.insertDecision(
                Decision(
                    question = question.ifBlank { "Decision" },
                    chosen = line.text.take(240),
                    reviewAt = now.plus(Duration.ofDays(30)),
                    createdAt = now,
                )
            )
            _state.value = _state.value.copy(
                lines = _state.value.lines.map { if (it === line) it.copy(saved = true) else it }
            )
        }
    }

    private fun directive(mode: ConversationMode): String = when (mode) {
        ConversationMode.RECALL -> "Answer from my own history and cite the capture."
        ConversationMode.PLAN -> "Work through scheduling and sequencing against my real constraints."
        ConversationMode.THINK -> "Think openly with me, with full context loaded."
        ConversationMode.CHALLENGE ->
            "Argue the other side. If this contradicts a stated rule or a past decision, say so and cite it."
        ConversationMode.DECIDE ->
            "Give options, criteria and past analogues, then a recommendation. This may become a Decision record."
        ConversationMode.DRAFT -> "Draft the message, email or agenda in my voice."
        ConversationMode.DEBRIEF -> "Extract what happened: what was committed, by whom."
    }
}
