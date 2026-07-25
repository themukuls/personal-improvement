package com.chiefofstaff.intervention

import com.chiefofstaff.capture.CalendarSync
import com.chiefofstaff.core.AppLog
import com.chiefofstaff.core.Clock
import com.chiefofstaff.data.LifeRepository
import com.chiefofstaff.llm.Fallback
import com.chiefofstaff.llm.LlmOrchestrator
import com.chiefofstaff.llm.TaskId
import kotlinx.serialization.json.jsonPrimitive

/**
 * INT-01 — the 06:00 morning brief. The single most important reliability guarantee in the app
 * (P11): it ALWAYS fires. It tries the LLM (MORNING_BRIEF) for a context-rich spoken brief, but if
 * the model or network is unavailable it speaks the deterministic fallback instead — calendar plus
 * open commitments by due date. Either way it speaks, posts one essential notification, and the day
 * has a plan. The notification bypasses the discretionary budget because it is a ritual (P2).
 */
class MorningBrief(
    private val repo: LifeRepository,
    private val clock: Clock,
    private val orchestrator: LlmOrchestrator,
    private val fallback: Fallback,
    private val tts: TtsSpeaker,
    private val notifier: Notifier,
    private val calendarSync: CalendarSync,
) {
    data class Result(val spoken: String, val statusLine: String, val usedFallback: Boolean)

    suspend fun deliver(speak: Boolean = true): Result {
        // Best-effort fresh calendar; failure here must not stop the brief.
        runCatching { calendarSync.sync() }

        val result = runCatching {
            orchestrator.run(
                TaskId.MORNING_BRIEF,
                params = mapOf("healthNote" to oneHealthNote()),
            )
        }.getOrNull()

        val (spoken, status, usedFallback) = when (result) {
            is LlmOrchestrator.TaskResult.Structured -> {
                val spokenText = result.obj["spoken"]?.jsonPrimitive?.content
                val statusText = result.obj["status_line"]?.jsonPrimitive?.content
                if (spokenText.isNullOrBlank()) fallbackTriple()
                else Triple(spokenText, statusText ?: defaultStatus(), false)
            }
            else -> fallbackTriple()
        }

        if (speak && tts.awaitReady()) tts.speak(spoken)
        notifier.post(
            channel = Channels.BRIEF,
            id = NotificationIds.MORNING_BRIEF,
            title = "Morning brief",
            body = spoken,
            essential = true,           // a ritual — bypasses the discretionary cap (P2, INT-04)
        )
        AppLog.i("brief", "delivered (fallback=$usedFallback)")
        return Result(spoken, status, usedFallback)
    }

    private suspend fun fallbackTriple(): Triple<String, String, Boolean> =
        Triple(fallback.deterministicBrief(), defaultStatus(), true)

    private suspend fun defaultStatus(): String {
        val mode = repo.mode().current.name.lowercase().replace('_', ' ')
        return "$mode mode"
    }

    /** One health note for the brief — the most recent observation, stated plainly. */
    private suspend fun oneHealthNote(): String {
        val sleep = repo.graph.observations("sleep_minutes", 1).firstOrNull()
        if (sleep != null) return "slept ${(sleep.value / 60).toInt()}h ${(sleep.value % 60).toInt()}m"
        val steps = repo.graph.observations("steps", 1).firstOrNull()
        if (steps != null) return "${steps.value.toInt()} steps yesterday"
        return ""
    }
}

/** Stable notification ids so re-posts replace rather than stack. */
object NotificationIds {
    const val MORNING_BRIEF = 1001
    const val EVENING_CLOSE = 1002
    const val SERVICE = 1003
    const val ANTICIPATION = 1004
    const val ARCHIVE = 1005
    const val SILENT_FAILURE = 1006
    const val WEEKLY_AUDIT = 1007
    const val MIDDAY_PULSE = 1008
}
