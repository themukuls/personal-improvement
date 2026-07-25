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
        const val EXTRA_RITUAL = "ritual"
        const val EXTRA_COMMITMENT_ID = "commitment_id"
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
        }
    }
}
