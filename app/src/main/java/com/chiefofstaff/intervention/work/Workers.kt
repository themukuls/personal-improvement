package com.chiefofstaff.intervention.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.chiefofstaff.ChiefOfStaffApp
import com.chiefofstaff.core.AppLog
import com.chiefofstaff.data.model.Verdict

/** Base worker exposing the app container. Workers are instantiated by name (see proguard rules). */
abstract class ContainerWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    protected val container get() = (applicationContext as ChiefOfStaffApp).container
}

/** INT-01 — deliver the morning brief. Always fires (P11) via the deterministic fallback path. */
class MorningBriefWorker(c: Context, p: WorkerParameters) : ContainerWorker(c, p) {
    override suspend fun doWork(): Result = runCatching {
        container.morningBrief.deliver()
        Result.success()
    }.getOrElse { AppLog.e("worker", "morning brief failed", it); Result.retry() }
}

/** REV-01 — prepare and announce the evening close (the card stack is built lazily by the UI). */
class EveningCloseWorker(c: Context, p: WorkerParameters) : ContainerWorker(c, p) {
    override suspend fun doWork(): Result = runCatching {
        container.eveningClose.announce()
        Result.success()
    }.getOrElse { AppLog.e("worker", "evening close failed", it); Result.retry() }
}

/**
 * §4.1 — the 02:00 batch: drain unparsed captures, run the anticipation scan, auto-archive dormant
 * commitments, refresh calendar, and back up. Deferred/non-urgent work is intentionally pooled here
 * to keep daytime battery and cost low.
 */
class NightlyBatchWorker(c: Context, p: WorkerParameters) : ContainerWorker(c, p) {
    override suspend fun doWork(): Result = runCatching {
        with(container) {
            runCatching { healthConnectSync.sync() }   // CAP-07 passive health pull
            extractionPipeline.processBacklog()
            anticipationEngine.runNightlyScan()
            val archived = stateMachine.autoArchiveDormant()
            if (archived.isNotEmpty()) {
                // RES-01 — one notification listing what was archived, no shame.
                notifier.post(
                    channel = com.chiefofstaff.intervention.Channels.RITUAL,
                    id = com.chiefofstaff.intervention.NotificationIds.ARCHIVE,
                    title = "Tidied up",
                    body = "Archived ${archived.size} item(s) untouched for 45 days: " +
                        archived.take(5).joinToString("; ") { it.what },
                    essential = false,
                )
            }
            runCatching { calendarSync.sync() }
            backup.runNightlyBackup()
        }
        Result.success()
    }.getOrElse { AppLog.e("worker", "nightly batch failed", it); Result.retry() }
}

/** INT-02 — a commitment came due: mark it asked and post a one-tap verdict notification. */
class CommitmentDueWorker(c: Context, p: WorkerParameters) : ContainerWorker(c, p) {
    companion object { const val KEY_ID = "id" }
    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_ID, -1L)
        if (id <= 0) return Result.failure()
        return runCatching {
            container.stateMachine.markAsked(id)
            val c = container.repo.commitments.byId(id)
            if (c != null) {
                container.notifier.post(
                    channel = com.chiefofstaff.intervention.Channels.RITUAL,
                    id = (1_100_000 + id).toInt(),
                    title = c.what,
                    body = "Due now.",
                    essential = false,
                    actions = listOf(
                        container.notifier.verdictAction(id, Verdict.DONE, "Done"),
                        container.notifier.verdictAction(id, Verdict.DEFERRED, "Later"),
                    ),
                )
            }
            Result.success()
        }.getOrElse { Result.retry() }
    }
}

/** REV-03 — the Sunday 19:00 weekly audit. */
class WeeklyAuditWorker(c: Context, p: WorkerParameters) : ContainerWorker(c, p) {
    override suspend fun doWork(): Result = runCatching {
        container.weeklyAudit.run()
        Result.success()
    }.getOrElse { AppLog.e("worker", "weekly audit failed", it); Result.retry() }
}

/** CAP-05 — periodic 15-minute calendar sync. */
class CalendarSyncWorker(c: Context, p: WorkerParameters) : ContainerWorker(c, p) {
    override suspend fun doWork(): Result = runCatching {
        container.calendarSync.sync()
        Result.success()
    }.getOrElse { Result.retry() }
}
