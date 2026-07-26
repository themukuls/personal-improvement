package com.chiefofstaff.intervention

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.chiefofstaff.core.AppLog
import com.chiefofstaff.core.SystemClock
import com.chiefofstaff.intervention.work.CommitmentDueWorker
import com.chiefofstaff.intervention.work.EveningCloseWorker
import com.chiefofstaff.intervention.work.MiddayPulseWorker
import com.chiefofstaff.intervention.work.MorningBriefWorker
import com.chiefofstaff.intervention.work.NightlyBatchWorker
import com.chiefofstaff.intervention.work.WeeklyAuditWorker

/**
 * Receives ritual alarms and per-commitment due alarms. A receiver has only a few seconds, so it
 * hands the actual work to WorkManager (which survives OEM process killing, §16.1) and immediately
 * reschedules the next occurrence so the chain never breaks.
 */
class RitualAlarmReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_RITUAL = "com.chiefofstaff.RITUAL"
        const val ACTION_COMMITMENT_DUE = "com.chiefofstaff.COMMITMENT_DUE"
        const val ACTION_MEMORY_REMINDER = "com.chiefofstaff.MEMORY_REMINDER"
        const val EXTRA_RITUAL = "ritual"
        const val EXTRA_COMMITMENT_ID = "commitment_id"
        const val EXTRA_MEMORY_ID = "memory_id"
        const val EXTRA_MEMORY_TEXT = "memory_text"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val wm = WorkManager.getInstance(context)
        when (intent.action) {
            ACTION_RITUAL -> {
                val ritual = intent.getStringExtra(EXTRA_RITUAL)
                    ?.let { runCatching { RitualScheduler.Ritual.valueOf(it) }.getOrNull() } ?: return
                AppLog.i("alarm", "ritual fired: $ritual")
                when (ritual) {
                    RitualScheduler.Ritual.MORNING_BRIEF ->
                        wm.enqueue(OneTimeWorkRequestBuilder<MorningBriefWorker>().build())
                    RitualScheduler.Ritual.EVENING_CLOSE ->
                        wm.enqueue(OneTimeWorkRequestBuilder<EveningCloseWorker>().build())
                    RitualScheduler.Ritual.NIGHTLY_BATCH ->
                        wm.enqueue(OneTimeWorkRequestBuilder<NightlyBatchWorker>().build())
                    RitualScheduler.Ritual.WEEKLY_AUDIT ->
                        wm.enqueue(OneTimeWorkRequestBuilder<WeeklyAuditWorker>().build())
                    RitualScheduler.Ritual.MIDDAY_PULSE ->
                        wm.enqueue(OneTimeWorkRequestBuilder<MiddayPulseWorker>().build())
                }
                // Re-arm tomorrow's occurrence.
                RitualScheduler(context, SystemClock()).schedule(ritual)
            }
            ACTION_COMMITMENT_DUE -> {
                val id = intent.getLongExtra(EXTRA_COMMITMENT_ID, -1L)
                if (id > 0) {
                    wm.enqueue(
                        OneTimeWorkRequestBuilder<CommitmentDueWorker>()
                            .setInputData(workDataOf(CommitmentDueWorker.KEY_ID to id))
                            .build()
                    )
                }
            }
            ACTION_MEMORY_REMINDER -> {
                val text = intent.getStringExtra(EXTRA_MEMORY_TEXT).orEmpty()
                if (text.isNotBlank()) postMemoryReminder(context, intent.getLongExtra(EXTRA_MEMORY_ID, 0L), text)
            }
        }
    }

    /** Post a reminder notification directly (quick; the receiver has only a few seconds). */
    private fun postMemoryReminder(context: Context, memoryId: Long, text: String) {
        Channels.ensure(context)
        val tap = android.app.PendingIntent.getActivity(
            context, 0,
            Intent(context, com.chiefofstaff.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = androidx.core.app.NotificationCompat.Builder(context, Channels.RITUAL)
            .setSmallIcon(com.chiefofstaff.R.drawable.ic_mic)
            .setContentTitle("Reminder")
            .setContentText(text)
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(tap)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching {
            androidx.core.app.NotificationManagerCompat.from(context)
                .notify((700_000 + (memoryId % 100_000)).toInt(), notification)
        }.onFailure { AppLog.w("memory", "reminder notify failed", it) }
    }
}
