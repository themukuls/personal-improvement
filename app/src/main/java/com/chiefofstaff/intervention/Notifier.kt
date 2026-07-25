package com.chiefofstaff.intervention

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.chiefofstaff.MainActivity
import com.chiefofstaff.R
import com.chiefofstaff.core.AppLog
import com.chiefofstaff.data.model.Verdict

/**
 * Posts notifications through the [NotificationBudget]. Nothing in the app posts a notification any
 * other way, so the hard cap (INT-04) cannot be bypassed by accident. Copy is neutral (RES-06):
 * no counts, no red, no guilt language.
 */
class Notifier(
    private val context: Context,
    private val budget: NotificationBudget,
) {
    private val nm = NotificationManagerCompat.from(context)

    @Suppress("MissingPermission") // POST_NOTIFICATIONS is requested at first run; guarded by areNotificationsEnabled upstream.
    suspend fun post(
        channel: String,
        id: Int,
        title: String,
        body: String,
        essential: Boolean,
        actions: List<NotificationCompat.Action> = emptyList(),
    ): Boolean {
        when (val d = budget.mayPost(channel, essential)) {
            is NotificationBudget.Decision.Suppressed -> {
                AppLog.i("notif", "suppressed [$channel] $title — ${d.reason}")
                return false
            }
            NotificationBudget.Decision.Allowed -> Unit
        }

        val contentIntent = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
        actions.forEach { builder.addAction(it) }

        return runCatching {
            nm.notify(id, builder.build())
            budget.record(channel, title, essential)
            true
        }.getOrElse { AppLog.w("notif", "post failed", it); false }
    }

    /** ACC-04 — a verdict action button that resolves a commitment from the shade, one tap. */
    fun verdictAction(commitmentId: Long, verdict: Verdict, label: String): NotificationCompat.Action {
        val intent = Intent(context, VerdictActionReceiver::class.java).apply {
            action = VerdictActionReceiver.ACTION_VERDICT
            putExtra(VerdictActionReceiver.EXTRA_ID, commitmentId)
            putExtra(VerdictActionReceiver.EXTRA_VERDICT, verdict.name)
        }
        val pi = PendingIntent.getBroadcast(
            context, (commitmentId * 10 + verdict.ordinal).toInt(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Action.Builder(R.drawable.ic_mic, label, pi).build()
    }
}
