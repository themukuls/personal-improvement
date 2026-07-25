package com.chiefofstaff.system

import com.chiefofstaff.core.AppLog
import com.chiefofstaff.core.Clock
import com.chiefofstaff.data.LifeRepository
import com.chiefofstaff.data.entity.DayState
import com.chiefofstaff.intervention.Channels
import com.chiefofstaff.intervention.NotificationIds
import com.chiefofstaff.intervention.Notifier

/**
 * SYS-12 — silent-failure detection. The worst failure mode is the app going quiet without an
 * error: the 06:00 brief simply not firing on an OEM device that killed the process (§16.1). This
 * checks that the brief actually posted today, and if it didn't (past the expected hour), posts one
 * essential system alert so a silent miss becomes a visible one.
 */
class SilentFailureDetector(
    private val repo: LifeRepository,
    private val clock: Clock,
    private val notifier: Notifier,
) {
    suspend fun checkMorningBriefFired(expectedHour: Int = 6) {
        if (clock.localNow().hour < expectedHour + 1) return   // too early to judge

        val date = DayState.keyFor(clock.today())
        val briefsToday = repo.state.countChannelToday(date, Channels.BRIEF)
        if (briefsToday > 0) return                            // it fired; all good

        AppLog.w("watchdog", "morning brief did not fire today")
        notifier.post(
            channel = Channels.SYSTEM,
            id = NotificationIds.SILENT_FAILURE,
            title = "I went quiet this morning",
            body = "The morning brief didn't fire — likely a battery or background restriction. " +
                "Tap to re-check settings so this stays reliable.",
            essential = true,
        )
    }
}
