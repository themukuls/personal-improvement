package com.chiefofstaff.intervention

import com.chiefofstaff.core.AppLog
import com.chiefofstaff.core.Clock
import com.chiefofstaff.data.LifeRepository
import com.chiefofstaff.data.model.Verdict

/**
 * ACC-09 — the midday pulse. 15 seconds, one question, and only when it earns the interruption:
 * it fires solely if a morning commitment is due-but-unresolved (at risk). Discretionary, so it
 * counts against the hard notification budget (INT-04) and simply doesn't post when there's nothing
 * worth asking — most days it stays silent.
 */
class MiddayPulse(
    private val repo: LifeRepository,
    private val clock: Clock,
    private val notifier: Notifier,
) {
    suspend fun maybePost() {
        val now = clock.now()
        val atRisk = repo.commitments.openCommitmentsNow()
            .firstOrNull { c -> c.dueAt?.isBefore(now) == true } ?: run {
                AppLog.i("pulse", "nothing at risk; staying silent")
                return
            }
        notifier.post(
            channel = Channels.RITUAL,
            id = NotificationIds.MIDDAY_PULSE,
            title = "Midday check",
            body = "Still on track for ${atRisk.what}?",
            essential = false,
            actions = listOf(
                notifier.verdictAction(atRisk.id, Verdict.DONE, "Done"),
                notifier.verdictAction(atRisk.id, Verdict.DEFERRED, "Later"),
            ),
        )
    }
}
