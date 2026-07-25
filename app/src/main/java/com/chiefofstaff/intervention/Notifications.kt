package com.chiefofstaff.intervention

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.chiefofstaff.R
import com.chiefofstaff.core.AppLog
import com.chiefofstaff.core.Clock
import com.chiefofstaff.data.LifeRepository
import com.chiefofstaff.data.entity.DayState
import com.chiefofstaff.data.entity.NotificationLog

/** INT-05 — separate, individually mutable channels. Getting one muted must not mute the rest. */
object Channels {
    const val BRIEF = "brief"
    const val RITUAL = "ritual"
    const val CLOSE = "close"
    const val SERVICE = "service"
    const val SYSTEM = "system"

    fun ensure(context: Context) {
        val mgr = context.getSystemService(NotificationManager::class.java)
        val defs = listOf(
            Triple(BRIEF, R.string.channel_brief, NotificationManager.IMPORTANCE_HIGH),
            Triple(RITUAL, R.string.channel_ritual, NotificationManager.IMPORTANCE_DEFAULT),
            Triple(CLOSE, R.string.channel_close, NotificationManager.IMPORTANCE_HIGH),
            Triple(SERVICE, R.string.channel_service, NotificationManager.IMPORTANCE_MIN),
            Triple(SYSTEM, R.string.channel_system, NotificationManager.IMPORTANCE_LOW),
        )
        defs.forEach { (id, nameRes, importance) ->
            mgr.createNotificationChannel(
                NotificationChannel(id, context.getString(nameRes), importance)
            )
        }
    }
}

/**
 * INT-04 — the notification budget, enforced in code. 3 discretionary notifications per day in V0.
 * Rituals (the morning brief, the evening close) are [essential] and bypass the cap; everything
 * else is discretionary and refused once the cap is hit. This is load-bearing: getting muted is
 * unrecoverable (P3), so the app would rather stay silent than risk it. Absolute DND is respected
 * (INT-07) and quiet mode (INT-08) suppresses all proactive output while captures keep flowing.
 */
class NotificationBudget(
    private val context: Context,
    private val repo: LifeRepository,
    private val clock: Clock,
) {
    companion object { const val V0_DAILY_CAP = 3 }

    sealed interface Decision {
        data object Allowed : Decision
        data class Suppressed(val reason: String) : Decision
    }

    suspend fun mayPost(channel: String, essential: Boolean): Decision {
        // Quiet mode (INT-08): suppress proactive output, essential or not, while captures flow.
        val mode = repo.mode()
        mode.quietUntil?.let { until ->
            if (clock.now().isBefore(until)) return Decision.Suppressed("quiet mode until $until")
        }

        // Absolute DND respect (INT-07): never override the user's Do Not Disturb.
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE && !essential) {
            return Decision.Suppressed("DND total silence")
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled() && !essential) {
            return Decision.Suppressed("notifications disabled")
        }

        if (essential) return Decision.Allowed

        val date = DayState.keyFor(clock.today())
        val used = repo.state.discretionaryCountToday(date)
        return if (used >= V0_DAILY_CAP) Decision.Suppressed("daily budget spent ($used/$V0_DAILY_CAP)")
        else Decision.Allowed
    }

    /** Record a posted notification against the budget ledger. */
    suspend fun record(channel: String, title: String, essential: Boolean) {
        repo.state.logNotification(
            NotificationLog(
                channel = channel,
                postedAt = clock.now(),
                postedDate = DayState.keyFor(clock.today()),
                title = title,
                essential = essential,
            )
        )
        AppLog.d("notif", "posted [$channel] essential=$essential: $title")
    }
}
