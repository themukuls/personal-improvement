package com.chiefofstaff.capture

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.chiefofstaff.core.AppLog
import com.chiefofstaff.core.Clock
import com.chiefofstaff.data.LifeRepository
import com.chiefofstaff.data.entity.Commitment
import com.chiefofstaff.data.entity.EventEntity
import com.chiefofstaff.data.entity.Note
import com.chiefofstaff.data.model.CaptureSource
import com.chiefofstaff.data.model.CommitmentState
import com.chiefofstaff.data.model.Domain
import com.chiefofstaff.domain.EmotionalEngine
import com.chiefofstaff.domain.PlanGenerator
import com.chiefofstaff.intervention.RitualScheduler
import com.chiefofstaff.llm.LlmOrchestrator
import com.chiefofstaff.llm.TaskId
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Turns a spoken sentence into a real action. The LLM (VOICE_COMMAND) classifies the utterance into
 * one structured intent — create a task, add a calendar event, set a reminder/alarm, give a brief,
 * save a note, or just chat — and this executor performs it against the life graph and the alarm
 * scheduler, then hands back a short confirmation to show and (optionally) speak.
 *
 * A deterministic fallback handles the basics if the model is unreachable (P11), and every utterance
 * is stored as an immutable capture first so nothing is ever lost (P5).
 */
class VoiceCommandProcessor(
    private val appContext: Context,
    private val repo: LifeRepository,
    private val clock: Clock,
    private val orchestrator: LlmOrchestrator,
    private val ritualScheduler: RitualScheduler,
    private val planGenerator: PlanGenerator,
    private val emotionalEngine: EmotionalEngine,
) {
    data class Result(val reply: String)

    suspend fun handle(raw: String): Result {
        val text = raw.trim()
        if (text.isEmpty()) return Result("I didn't catch that — try again?")

        // P5 — keep the raw utterance, immutable and indexed, before doing anything else.
        runCatching { repo.capture(CaptureSource.VOICE, text) }

        val iso = LocalDateTime.ofInstant(clock.now(), clock.zone())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"))
        val obj = runCatching {
            (orchestrator.run(TaskId.VOICE_COMMAND, extraUserContent = "Now: $iso\nUser said: $text")
                as? LlmOrchestrator.TaskResult.Structured)?.obj
        }.getOrNull()

        return if (obj != null) execute(obj, text) else deterministic(text)
    }

    private suspend fun execute(obj: JsonObject, raw: String): Result {
        fun str(k: String): String? = (obj[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        val intent = str("intent")?.lowercase() ?: "chat"
        val title = str("title") ?: raw
        val domain = runCatching { Domain.valueOf((str("domain") ?: "NONE").uppercase()) }.getOrDefault(Domain.NONE)
        val (whenAt, hasTime) = parseWhen(str("datetime"))
        val durationMin = (obj["durationMinutes"] as? JsonPrimitive)?.intOrNull ?: 0
        val reply = str("reply")
        val now = clock.now()

        return when (intent) {
            "alarm" -> {
                // A real clock alarm in the user's default Clock app.
                if (whenAt != null && hasTime) {
                    val ldt = LocalDateTime.ofInstant(whenAt, clock.zone())
                    if (setSystemAlarm(ldt.hour, ldt.minute, title.takeIf { it != raw } ?: "Alarm")) {
                        Result(reply ?: "Alarm set for ${fmt(whenAt)} in your Clock.")
                    } else {
                        // No clock app took it — fall back to an in-app reminder so nothing is lost.
                        reminder(title, whenAt, hasTime, domain, now, "I couldn't reach your Clock app, so I set an in-app reminder")
                    }
                } else {
                    Result("What time should the alarm be? Try \"set an alarm for 7am.\"")
                }
            }
            "timer" -> {
                val mins = if (durationMin > 0) durationMin else parseDurationMinutes(raw)
                if (mins > 0 && setSystemTimer(mins, title.takeIf { it != raw } ?: "Timer")) {
                    Result(reply ?: "Timer set for $mins minute${if (mins == 1) "" else "s"}.")
                } else {
                    Result("How long should the timer be? Try \"set a timer for 10 minutes.\"")
                }
            }
            "create_event" -> {
                val start = whenAt ?: now
                val end = start.plusSeconds((if (durationMin > 0) durationMin else 60) * 60L)
                repo.graph.upsertEvent(EventEntity(title = title, start = start, end = end, source = "voice", createdAt = now))
                Result(reply ?: "Added \"$title\" to your calendar${whenAt?.let { " for ${fmt(it)}" } ?: ""}.")
            }
            "set_reminder", "create_task" -> reminder(title, whenAt, hasTime, domain, now, override = reply)
            "note" -> {
                repo.graph.insertNote(Note(text = title, tags = listOf("voice"), createdAt = now))
                Result(reply ?: "Noted — I'll remember that.")
            }
            "brief" -> Result(buildBrief())
            else -> chat(raw)
        }
    }

    /** In-app reminder: a scheduled commitment plus an exact-alarm notification (INT-02). */
    private suspend fun reminder(
        title: String,
        whenAt: Instant?,
        hasTime: Boolean,
        domain: Domain,
        now: Instant,
        override: String? = null,
        prefix: String? = null,
    ): Result {
        val id = repo.commitments.insert(
            Commitment(
                what = title, dueAt = whenAt, domain = domain, state = CommitmentState.SCHEDULED,
                createdAt = now, updatedAt = now, lastTouchedAt = now,
            )
        )
        if (whenAt != null && hasTime && whenAt.isAfter(now)) {
            runCatching { ritualScheduler.scheduleCommitment(id, whenAt.toEpochMilli()) }
                .onFailure { AppLog.w("voice", "reminder alarm schedule failed", it) }
        }
        if (override != null) return Result(override)
        val base = "\"$title\"${whenAt?.let { " for ${fmt(it)}" } ?: " added to today"}"
        return Result(prefix?.let { "$it — $base." } ?: "Got it — $base.")
    }

    /** Create a real alarm in the device's default Clock app. Returns false if no Clock app took it. */
    private fun setSystemAlarm(hour: Int, minute: Int, label: String): Boolean {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)   // set it silently, stay in our app
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            appContext.startActivity(intent); true
        } catch (e: ActivityNotFoundException) {
            AppLog.w("voice", "no clock app for SET_ALARM", e); false
        } catch (e: Exception) {
            AppLog.w("voice", "SET_ALARM failed", e); false
        }
    }

    /** Start a countdown timer in the default Clock app. */
    private fun setSystemTimer(minutes: Int, label: String): Boolean {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            appContext.startActivity(intent); true
        } catch (e: Exception) {
            AppLog.w("voice", "SET_TIMER failed", e); false
        }
    }

    /** Pull a rough minute count out of "10 minutes", "an hour and a half", "90 min". */
    private fun parseDurationMinutes(text: String): Int {
        val hours = Regex("(\\d+)\\s*(h|hr|hour)").find(text.lowercase())?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val mins = Regex("(\\d+)\\s*(m|min|minute)").find(text.lowercase())?.groupValues?.get(1)?.toIntOrNull() ?: 0
        return hours * 60 + mins
    }

    /** Open-ended talk: run the full conversation task and return its reply. */
    private suspend fun chat(text: String): Result {
        val r = runCatching {
            orchestrator.run(TaskId.CONVERSE, params = mapOf("message" to text), extraUserContent = "User: $text")
        }.getOrNull()
        val reply = when (r) {
            is LlmOrchestrator.TaskResult.Text -> r.text
            is LlmOrchestrator.TaskResult.Structured -> r.rawText
            else -> "I've saved that. I'll reason over it fully once my connection is back."
        }
        return Result(reply)
    }

    private suspend fun buildBrief(): String {
        val plan = runCatching { planGenerator.generate() }.getOrNull()
        val support = runCatching { emotionalEngine.read().line }.getOrNull()
        return buildString {
            support?.let { append(it).append("\n\n") }
            if (plan == null || plan.items.isEmpty()) {
                append("Nothing scheduled yet today. Tell me your first thing and I'll take it from there.")
            } else {
                append("Here's today:")
                plan.items.forEach { c ->
                    append("\n• ").append(c.dueAt?.let { fmt(it) } ?: "anytime").append(" — ").append(c.what)
                }
                if (plan.notToday.isNotEmpty()) append("\n\nNot today: ").append(plan.notToday.joinToString("; "))
            }
        }.trim()
    }

    /**
     * Keyword fallback when the model can't be reached — and it now genuinely acts: it parses the
     * time itself and sets a real alarm / timer / reminder, so "set an alarm for 5am" works even
     * offline (P11), not just "added to a list".
     */
    private suspend fun deterministic(text: String): Result {
        val t = " ${text.lowercase()} "
        val now = clock.now()
        val hm = parseClockTime(text)
        return when {
            "alarm" in t || "wake me" in t || "wake up" in t -> when {
                hm != null && setSystemAlarm(hm.first, hm.second, "Alarm") ->
                    Result("Alarm set for ${fmtHM(hm)} in your Clock.")
                hm != null -> reminder(
                    cleanTitle(text), whenFrom(text, hm, now), true, Domain.NONE, now,
                    prefix = "I couldn't reach your Clock app, so I set an in-app reminder",
                )
                else -> Result("What time should the alarm be? Try \"set an alarm for 7am.\"")
            }
            "timer" in t || "countdown" in t -> {
                val mins = parseDurationMinutes(text)
                if (mins > 0 && setSystemTimer(mins, "Timer")) {
                    Result("Timer set for $mins minute${if (mins == 1) "" else "s"}.")
                } else {
                    Result("How long should the timer be? Try \"set a timer for 10 minutes.\"")
                }
            }
            "brief" in t || "my day" in t || "what's on" in t || "today look" in t || "how's my day" in t ->
                Result(buildBrief())
            "remind" in t || "reminder" in t || " task " in t || text.trimStart().lowercase().startsWith("add ") -> {
                val whenAt = hm?.let { whenFrom(text, it, now) }
                reminder(cleanTitle(text), whenAt, whenAt != null, Domain.NONE, now)
            }
            else -> {
                repo.graph.insertNote(Note(text = text, tags = listOf("voice"), createdAt = now))
                Result("Saved that as a note — I couldn't reach my reasoning just now.")
            }
        }
    }

    /** Pull a clock time out of "5am", "5:00 a.m.", "at 7", "17:00", "7 pm". Returns (hour, minute). */
    private fun parseClockTime(text: String): Pair<Int, Int>? {
        val m = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(a\\.?m\\.?|p\\.?m\\.?)?", RegexOption.IGNORE_CASE)
            .findAll(text)
            .firstOrNull { it.groupValues[1].toIntOrNull() in 0..23 } ?: return null
        var hour = m.groupValues[1].toIntOrNull() ?: return null
        val minute = m.groupValues[2].toIntOrNull() ?: 0
        when (m.groupValues[3].lowercase().replace(".", "")) {
            "pm" -> if (hour < 12) hour += 12
            "am" -> if (hour == 12) hour = 0
        }
        return if (hour in 0..23 && minute in 0..59) hour to minute else null
    }

    /** Build an Instant for a parsed time, honouring "tomorrow"/"tonight" and rolling past times forward. */
    private fun whenFrom(text: String, hm: Pair<Int, Int>, now: Instant): Instant {
        val lower = text.lowercase()
        var day = clock.today()
        if ("tomorrow" in lower) day = day.plusDays(1)
        var inst = day.atTime(hm.first, hm.second).atZone(clock.zone()).toInstant()
        if ("tomorrow" !in lower && inst.isBefore(now)) inst = inst.plusSeconds(86_400)
        return inst
    }

    private fun fmtHM(hm: Pair<Int, Int>): String = "%02d:%02d".format(hm.first, hm.second)

    private fun cleanTitle(text: String): String =
        text.replace(
            Regex("(?i)^(please\\s+)?(remind me to|remind me|set a reminder to|set an alarm to|add a task to|add task|add|remember to|remember|note|create)\\s+"),
            "",
        ).trim().ifBlank { text }.replaceFirstChar { it.uppercase() }

    private fun parseWhen(s: String?): Pair<Instant?, Boolean> {
        if (s.isNullOrBlank()) return null to false
        val zone = clock.zone()
        return try {
            if (s.contains('T')) {
                LocalDateTime.parse(s).atZone(zone).toInstant() to true
            } else {
                LocalDate.parse(s).atTime(9, 0).atZone(zone).toInstant() to false
            }
        } catch (e: Exception) {
            null to false
        }
    }

    private fun fmt(i: Instant): String {
        val ldt = LocalDateTime.ofInstant(i, clock.zone())
        val day = when (ldt.toLocalDate()) {
            clock.today() -> "today"
            clock.today().plusDays(1) -> "tomorrow"
            else -> ldt.format(DateTimeFormatter.ofPattern("EEE d MMM"))
        }
        return "$day ${"%02d:%02d".format(ldt.hour, ldt.minute)}"
    }
}
