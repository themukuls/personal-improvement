package com.chiefofstaff.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chiefofstaff.AppContainer
import com.chiefofstaff.data.entity.AnticipationItem
import com.chiefofstaff.data.entity.Commitment
import com.chiefofstaff.data.entity.DayState
import com.chiefofstaff.data.model.CommitmentState
import com.chiefofstaff.data.model.Verdict
import kotlinx.coroutines.flow.MutableStateFlow
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
    val greeting: String = "",
    val name: String = "",
    val dateTitle: String = "",
    val statusLine: String = "",
    val rightNow: String? = null,
    val anticipation: AnticipationItem? = null,
    val today: List<NowItem> = emptyList(),
    val notToday: List<String> = emptyList(),
    val energyToday: Int? = null,
    val moodToday: String? = null,
    val supportLine: String? = null,
    val wellbeing: Boolean = true,
    val relationship: com.chiefofstaff.domain.RelationshipEngine.Prompt? = null,
    val mode: com.chiefofstaff.data.model.Mode = com.chiefofstaff.data.model.Mode.NORMAL,
    val quiet: Boolean = false,
    val modeSetToday: Boolean = false,
    val allDoneToday: Boolean = false,
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
    private val profile = com.chiefofstaff.system.UserProfile(container.appContext)
    private val relPrefs = container.appContext.getSharedPreferences("cos_relationship", android.content.Context.MODE_PRIVATE)

    /** Bumped when the daily relationship prompt is answered, so the combined state recomputes. */
    private val relationshipTrigger = MutableStateFlow(0)

    val state: StateFlow<NowUiState> = combine(
        repo.openCommitments(),
        repo.modeFlow(),
        repo.state.topAnticipationFlow(DayState.keyFor(clock.today())),
        repo.todayFlow(),
        relationshipTrigger,
    ) { commitments, mode, anticipation, day, _ ->
        val zone = clock.zone()
        val today = clock.today()
        val dateTitle = "${today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())}, " +
            "${today.dayOfMonth} ${today.month.getDisplayName(TextStyle.FULL, Locale.getDefault())}"

        // A rule-flagged item belongs only in NOT TODAY, never also in the TODAY list.
        val violations = container.ruleEngine.check(commitments)
        val excluded = violations.map { it.commitment.id }.toSet()
        val rows = commitments.filterNot { it.id in excluded }.map { c -> c.toRow(zone) }
        val rightNow = container.planGenerator.whatNow()?.what

        // DIR-08 conflicts + DIR-05 overcommit warning join the NOT TODAY / refusals line.
        val conflicts = container.insightEngine.conflicts()
        val overcommit = container.insightEngine.overcommitWarning()

        // Next calendar event for the status line.
        val start = today.atStartOfDay(zone).toInstant()
        val events = repo.graph.eventsBetween(start.toEpochMilli(), start.plusSeconds(86_400).toEpochMilli())
        val next = events.firstOrNull { it.start.isAfter(clock.now()) }
        val modeLabel = (mode?.current?.name ?: "NORMAL").lowercase().replace('_', ' ')
        val status = buildString {
            append("$modeLabel mode · ${rows.size} item${if (rows.size == 1) "" else "s"}")
            next?.let { append(" · next ${hhmm(it.start, zone)} ${it.title.lowercase()}") }
        }

        // Celebrate when the list is clear *and* something was actually completed today.
        val startOfToday = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val doneToday = repo.commitments.doneCountSince(startOfToday)
        val allDoneToday = rows.isEmpty() && doneToday > 0

        // Emotional intelligence: a warm, non-guilt read of the day, tuned by the user's chosen
        // support style from onboarding (deterministic; always available).
        val support = container.emotionalEngine.read(firm = profile.nudgeStyle == "firm")

        // The daily "tell & ask" prompt — one per day, suppressed once answered.
        val answeredToday = relPrefs.getString("answered_date", "") == clock.today().toString()
        val relationship = if (answeredToday) null else
            runCatching { container.relationshipEngine.todaysPrompt(profile.pronouns.isNotBlank()) }.getOrNull()

        NowUiState(
            greeting = greetingFor(LocalTime.ofInstant(clock.now(), zone).hour),
            name = profile.name,
            dateTitle = dateTitle,
            statusLine = status,
            rightNow = rightNow,
            anticipation = anticipation,
            today = rows,
            notToday = violations.map { it.explanation } + conflicts + listOfNotNull(overcommit),
            energyToday = day?.energy,
            moodToday = day?.mood,
            supportLine = support.line,
            wellbeing = profile.wellbeingCheckins,
            relationship = relationship,
            mode = mode?.current ?: com.chiefofstaff.data.model.Mode.NORMAL,
            quiet = mode?.quietUntil?.isAfter(clock.now()) == true,
            modeSetToday = mode?.since?.isAfter(today.atStartOfDay(zone).toInstant()) == true,
            allDoneToday = allDoneToday,
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

    /** Daily mood check-in. Feeds the empathetic support line and the minimum-viable-day reduction. */
    fun setMood(mood: String) {
        viewModelScope.launch { repo.setMood(mood) }
    }

    /** §7.3 — set the operating mode for the day; affects the plan shape and notification budget. */
    fun setMode(mode: com.chiefofstaff.data.model.Mode) {
        viewModelScope.launch { repo.setMode(mode) }
    }

    /** INT-08 — quiet mode: suppress proactive output for the rest of today; captures still flow. */
    fun toggleQuiet() {
        viewModelScope.launch {
            val until = if (state.value.quiet) null
            else clock.today().atTime(23, 59).atZone(clock.zone()).toInstant()
            repo.setQuietUntil(until)
        }
    }

    /** Answer today's relationship prompt: apply its effect to memory, then retire it for the day. */
    fun answerRelationship(promptId: String, answerIndex: Int) {
        viewModelScope.launch {
            val prompt = state.value.relationship?.takeIf { it.id == promptId } ?: return@launch
            val answer = prompt.answers.getOrNull(answerIndex) ?: return@launch
            when (val act = answer.act) {
                is com.chiefofstaff.domain.RelationshipEngine.Act.Ack -> Unit
                is com.chiefofstaff.domain.RelationshipEngine.Act.Note ->
                    repo.graph.insertNote(
                        com.chiefofstaff.data.entity.Note(text = act.text, tags = listOf("checkin"), createdAt = clock.now())
                    )
                is com.chiefofstaff.domain.RelationshipEngine.Act.LogContact -> repo.logContact(act.personId)
                is com.chiefofstaff.domain.RelationshipEngine.Act.SetPronouns -> profile.pronouns = act.value
            }
            retireRelationship()
        }
    }

    /** "Say more" opens Talk (handled by the screen); retire the prompt here so it doesn't nag again. */
    fun markRelationshipAnswered() = retireRelationship()

    private fun retireRelationship() {
        relPrefs.edit().putString("answered_date", clock.today().toString()).apply()
        relationshipTrigger.value += 1
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

    /** Time-of-day greeting for the header. */
    private fun greetingFor(hour: Int): String = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Hello"
    }
}
