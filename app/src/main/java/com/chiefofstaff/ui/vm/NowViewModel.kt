package com.chiefofstaff.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chiefofstaff.AppContainer
import com.chiefofstaff.data.entity.AnticipationItem
import com.chiefofstaff.data.entity.Commitment
import com.chiefofstaff.data.entity.DayState
import com.chiefofstaff.data.model.CommitmentState
import com.chiefofstaff.data.model.Verdict
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

data class NowItem(
    val id: Long,
    val time: String,
    val title: String,
    val context: String?,
    val checked: Boolean,
)

data class NowUiState(
    val dateTitle: String = "",
    val statusLine: String = "",
    val rightNow: String? = null,
    val anticipation: AnticipationItem? = null,
    val today: List<NowItem> = emptyList(),
    val notToday: List<String> = emptyList(),
    val energyToday: Int? = null,
    val reentryDays: Int = 0,
    val loading: Boolean = true,
)

/**
 * Backs the Now screen — the home surface (§3.2, UX-01). It never shows more than the design does:
 * the date, one status line, the RIGHT NOW answer, the anticipation card, 3–6 TODAY items and the
 * NOT TODAY refusals. No badges, no counts on icons, no streak (RES-05).
 */
class NowViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.repo
    private val clock = container.clock

    val state: StateFlow<NowUiState> = combine(
        repo.openCommitments(),
        repo.modeFlow(),
        repo.state.topAnticipationFlow(DayState.keyFor(clock.today())),
        repo.todayFlow(),
    ) { commitments, mode, anticipation, day ->
        val zone = clock.zone()
        val today = clock.today()
        val dateTitle = "${today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())}, " +
            "${today.dayOfMonth} ${today.month.getDisplayName(TextStyle.FULL, Locale.getDefault())}"

        // A rule-flagged item belongs only in NOT TODAY, never also in the TODAY list.
        val violations = container.ruleEngine.check(commitments)
        val excluded = violations.map { it.commitment.id }.toSet()
        val rows = commitments.filterNot { it.id in excluded }.map { c -> c.toRow(zone) }
        val rightNow = container.planGenerator.whatNow()?.what

        // Next calendar event for the status line.
        val start = today.atStartOfDay(zone).toInstant()
        val events = repo.graph.eventsBetween(start.toEpochMilli(), start.plusSeconds(86_400).toEpochMilli())
        val next = events.firstOrNull { it.start.isAfter(clock.now()) }
        val modeLabel = (mode?.current?.name ?: "NORMAL").lowercase().replace('_', ' ')
        val status = buildString {
            append("$modeLabel mode · ${rows.size} item${if (rows.size == 1) "" else "s"}")
            next?.let { append(" · next ${hhmm(it.start, zone)} ${it.title.lowercase()}") }
        }

        NowUiState(
            dateTitle = dateTitle,
            statusLine = status,
            rightNow = rightNow,
            anticipation = anticipation,
            today = rows,
            notToday = violations.map { it.explanation },
            energyToday = day?.energy,
            reentryDays = container.daysAway,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NowUiState())

    fun toggleDone(item: NowItem) {
        viewModelScope.launch {
            val verdict = if (item.checked) return@launch else Verdict.DONE
            container.stateMachine.applyVerdict(item.id, verdict)
        }
    }

    /** CAP-10 — one-gesture daily energy (1–5). Feeds minimum-viable-day + energy-aware scheduling. */
    fun setEnergy(level: Int) {
        viewModelScope.launch { repo.setEnergy(level) }
    }

    fun dismissAnticipation(useful: Boolean) {
        viewModelScope.launch {
            val a = state.value.anticipation ?: return@launch
            repo.state.updateAnticipation(a.copy(shown = true, ratedUseful = useful))
        }
    }

    private fun Commitment.toRow(zone: java.time.ZoneId): NowItem = NowItem(
        id = id,
        time = dueAt?.let { hhmm(it, zone) } ?: "wait",
        title = what,
        context = contextNote,
        checked = state == CommitmentState.DONE,
    )

    private fun hhmm(instant: java.time.Instant, zone: java.time.ZoneId): String =
        LocalTime.ofInstant(instant, zone).let { "%02d:%02d".format(it.hour, it.minute) }
}
