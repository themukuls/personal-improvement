package com.chiefofstaff.domain

import com.chiefofstaff.core.Clock
import com.chiefofstaff.data.LifeRepository
import kotlinx.coroutines.flow.first
import java.time.Duration

/**
 * The two-way relationship loop — what turns the app from a tool into a presence. Each day it either
 * **tells** the user something it noticed from their own data, or **asks** one thing to learn them
 * better, with quick-tap answers that write straight back into memory. One prompt a day, dismissable,
 * never naggy — and it respects the same no-guilt rule as everything else (§15, RES-06).
 *
 * Deterministic so it always works offline (P11); an LLM can later phrase these more warmly, but the
 * *decision of what to surface* stays here, grounded in the life graph.
 */
class RelationshipEngine(
    private val repo: LifeRepository,
    private val clock: Clock,
    private val consistency: ConsistencyScore,
) {
    enum class Kind { TELL, ASK }

    sealed interface Act {
        data object Ack : Act
        data class Note(val text: String) : Act
        data class LogContact(val personId: Long) : Act
        data class SetPronouns(val value: String) : Act
    }

    data class Answer(val label: String, val act: Act)

    data class Prompt(
        val id: String,
        val kind: Kind,
        val text: String,
        val answers: List<Answer>,
        /** ASK prompts that deserve a real answer offer "Say more" → opens Talk. */
        val sayMore: Boolean = false,
    )

    /**
     * Pick today's single prompt. Ordered by how much it shows the app *knows* the person; falls back
     * to a rotating get-to-know question so the daily beat never goes silent.
     */
    suspend fun todaysPrompt(pronounsKnown: Boolean): Prompt {
        val now = clock.now()

        // 1) A neglected relationship — the strongest "it actually knows me" moment.
        neglectedPerson()?.let { (name, id, days) ->
            return Prompt(
                id = "neglect-$id",
                kind = Kind.ASK,
                text = "It's been $days days since you connected with $name. Want to close that gap?",
                answers = listOf(
                    Answer("Remind me", Act.Note("Reach out to $name")),
                    Answer("Did recently", Act.LogContact(id)),
                    Answer("Not now", Act.Ack),
                ),
            )
        }

        // 2) Something you're still owed.
        repo.commitments.overdueWaiting(now.toEpochMilli()).firstOrNull()?.let { w ->
            return Prompt(
                id = "waiting-${w.id}",
                kind = Kind.ASK,
                text = "You're still waiting on \"${w.what}\" from ${w.who}. Want to chase it?",
                answers = listOf(
                    Answer("Chase it", Act.Note("Chase ${w.who}: ${w.what}")),
                    Answer("Let it go", Act.Note("Let go of waiting: ${w.what}")),
                    Answer("Not now", Act.Ack),
                ),
            )
        }

        // 3) A gentle gap in what I know about you.
        if (!pronounsKnown) {
            return Prompt(
                id = "pronouns",
                kind = Kind.ASK,
                text = "Quick one so I get it right — how should I refer to you?",
                answers = listOf(
                    Answer("She / her", Act.SetPronouns("she")),
                    Answer("He / him", Act.SetPronouns("he")),
                    Answer("They / them", Act.SetPronouns("they")),
                ),
            )
        }

        // 4) A proud reflection when you've been steady (a fact, never a streak).
        val consist = runCatching { consistency.rolling30Day() }.getOrDefault(0f)
        if (consist >= 0.75f) {
            return Prompt(
                id = "proud-${clock.today()}",
                kind = Kind.TELL,
                text = "Something I noticed: you've been closing the loop on most of what you take on. Quietly, that's a lot.",
                answers = listOf(Answer("Thanks", Act.Ack)),
            )
        }

        // 5) Otherwise a get-to-know question — rotated by the day so it deepens the model over time.
        val bank = listOf(
            "What's one thing you keep meaning to do but never seem to schedule?",
            "When you picture next week going well — what happened?",
            "What usually derails your day?",
            "Who's someone you'd like to keep closer to?",
            "What's a small win from today I should know about?",
        )
        val q = bank[(clock.today().toEpochDay() % bank.size).toInt()]
        return Prompt(
            id = "bank-${clock.today()}",
            kind = Kind.ASK,
            text = q,
            answers = listOf(Answer("Maybe later", Act.Ack)),
            sayMore = true,
        )
    }

    private suspend fun neglectedPerson(): Triple<String, Long, Long>? {
        val people = repo.people().first()
        val now = clock.now()
        return people.mapNotNull { p ->
            val last = p.lastContact ?: return@mapNotNull null
            val cadence = p.cadenceTargetDays ?: return@mapNotNull null
            val days = Duration.between(last, now).toDays()
            if (days > cadence) Triple(p.name, p.id, days) else null
        }.maxByOrNull { it.third }
    }
}
