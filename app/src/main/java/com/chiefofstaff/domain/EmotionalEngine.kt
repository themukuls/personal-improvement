package com.chiefofstaff.domain

import com.chiefofstaff.core.Clock
import com.chiefofstaff.data.LifeRepository

/**
 * Emotional intelligence, encoded deterministically so it always works with no model and no network
 * (P11). It reads the day's *felt* state — mood, energy, illness, how much is on, recent
 * consistency — and returns one short, warm, non-judging line plus a tone the UI can colour with.
 *
 * The rule the whole app lives by (RES-06 / §15): never guilt, never a streak to defend. On a hard
 * day the intelligent thing is to ask for *less* and say so kindly — which is also why a low mood
 * feeds the minimum-viable-day reduction in [PlanGenerator], not just the copy here.
 */
class EmotionalEngine(
    private val repo: LifeRepository,
    private val clock: Clock,
    private val consistency: ConsistencyScore,
) {
    enum class Tone { STRUGGLING, TIRED, STEADY, ENERGISED, PROUD }

    data class Read(val tone: Tone, val line: String)

    suspend fun read(): Read {
        val day = repo.today()
        val energy = day.energy ?: 3
        val mood = moodScore(day.mood)          // 1..5, or null when not checked in
        val load = day.loadScore ?: 0f
        val ill = day.illnessFlag
        val open = repo.commitments.openCommitmentsNow().size
        val consist = runCatching { consistency.rolling30Day() }.getOrDefault(0f)

        val struggling = ill || energy <= 2 || (mood != null && mood <= 2)
        return when {
            struggling -> Read(Tone.STRUGGLING, struggleLine(ill))
            load >= 0.8f || open >= 6 -> Read(
                Tone.STEADY,
                "A lot on today. I'll protect your focus and hold the rest — you don't have to carry it all at once.",
            )
            energy >= 4 && (mood ?: 3) >= 4 && load < 0.5f -> Read(
                Tone.ENERGISED,
                "You've got room today. Good moment to get ahead on something that matters to you.",
            )
            consist >= 0.75f && (mood ?: 3) >= 3 -> Read(
                Tone.PROUD,
                "You've been steady lately — that's the whole game. Keep it easy today.",
            )
            else -> Read(Tone.STEADY, "Here's today. One thing at a time; I've got the rest.")
        }
    }

    private fun struggleLine(ill: Boolean): String =
        if (ill) {
            "Rest is the task today. I've cleared the deck to the one thing that truly can't wait."
        } else {
            "Rough one. I've trimmed today to what actually matters — a single step is enough, and that's not nothing."
        }

    private fun moodScore(mood: String?): Int? = when (mood) {
        "low" -> 1
        "meh" -> 2
        "ok" -> 3
        "good" -> 4
        "great" -> 5
        else -> null
    }

    companion object {
        /** The mood check-in options (key, label, emoji). The key is what lands in DayState.mood. */
        val MOODS = listOf(
            Triple("low", "Low", "😔"),
            Triple("meh", "Meh", "😕"),
            Triple("ok", "OK", "🙂"),
            Triple("good", "Good", "😄"),
            Triple("great", "Great", "🤩"),
        )

        /** Low-mood keys that should also collapse the day to one item (DIR-04 via emotion). */
        val LOW_MOODS = setOf("low")
    }
}
