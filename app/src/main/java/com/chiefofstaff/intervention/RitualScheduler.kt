package com.chiefofstaff.intervention

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.chiefofstaff.core.AppLog
import com.chiefofstaff.core.Clock
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * INT-02/03 — exact ritual alarms. Uses AlarmManager.setExactAndAllowWhileIdle so the brief fires
 * precisely even in Doze (§5). Ritual times are fixed defaults in V0 (06:00 / 21:00 / 02:00); they
 * become adaptive in V2 (INT-12). Each alarm reschedules the next day's occurrence when it fires,
 * and BootReceiver re-arms everything after a reboot (§16.1).
 */
class RitualScheduler(
    private val context: Context,
    private val clock: Clock,
) {
    enum class Ritual(val hour: Int, val minute: Int, val dayOfWeek: java.time.DayOfWeek? = null) {
        MORNING_BRIEF(6, 0),
        MIDDAY_PULSE(13, 0),                               // ACC-09 conditional check-in
        EVENING_CLOSE(21, 0),
        NIGHTLY_BATCH(2, 0),
        WEEKLY_AUDIT(19, 0, java.time.DayOfWeek.SUNDAY),   // §9.1 Sun 19:00
    }

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun scheduleAll() {
        Ritual.entries.forEach { schedule(it) }
        AppLog.i("scheduler", "all rituals armed")
    }

    fun schedule(ritual: Ritual) {
        val triggerAt = nextOccurrence(LocalTime.of(ritual.hour, ritual.minute), ritual.dayOfWeek)
        val pi = ritualPendingIntent(ritual)
        // Guarded: on Android 12+ exact alarms require the (granted, sideloaded) permission.
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            // Degrade, never disappear (P11): an inexact alarm still fires, just not to the second.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            AppLog.w("scheduler", "exact alarms not permitted; using inexact for $ritual")
        }
    }

    /** INT-02 — a per-commitment exact alarm set by voice ("remind me at 4"). */
    fun scheduleCommitment(commitmentId: Long, atMillis: Long) {
        val intent = Intent(context, RitualAlarmReceiver::class.java).apply {
            action = RitualAlarmReceiver.ACTION_COMMITMENT_DUE
            putExtra(RitualAlarmReceiver.EXTRA_COMMITMENT_ID, commitmentId)
        }
        val pi = PendingIntent.getBroadcast(
            context, (commitmentId + 500_000).toInt(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        }
    }

    private fun ritualPendingIntent(ritual: Ritual): PendingIntent {
        val intent = Intent(context, RitualAlarmReceiver::class.java).apply {
            action = RitualAlarmReceiver.ACTION_RITUAL
            putExtra(RitualAlarmReceiver.EXTRA_RITUAL, ritual.name)
        }
        return PendingIntent.getBroadcast(
            context, ritual.ordinal, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun nextOccurrence(time: LocalTime, dayOfWeek: java.time.DayOfWeek? = null): Long {
        val now = ZonedDateTime.now(clock.zone())
        var next = now.withHour(time.hour).withMinute(time.minute).withSecond(0).withNano(0)
        if (dayOfWeek == null) {
            // Daily: next matching time today or tomorrow.
            if (!next.isAfter(now)) next = next.plusDays(1)
        } else {
            // Weekly: advance to the next matching day-of-week at the given time.
            while (next.dayOfWeek != dayOfWeek || !next.isAfter(now)) next = next.plusDays(1)
        }
        return next.toInstant().toEpochMilli()
    }

    private fun canScheduleExact(): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S)
            alarmManager.canScheduleExactAlarms() else true
}
