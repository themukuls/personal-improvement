package com.chiefofstaff.intervention

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.chiefofstaff.core.AppLog
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Fires the exact set of reminders the user asked for around a dated memory:
 *   - the day before, in the morning (08:00)
 *   - the day before, in the evening (20:00)
 *   - on the day, in the morning (08:00)
 *   - and, if the memory has a specific clock time, 30 minutes before it.
 *
 * Each is an exact alarm delivered to [RitualAlarmReceiver] (ACTION_MEMORY_REMINDER), which posts a
 * notification. Slots have deterministic request codes derived from the memory id, so re-scheduling
 * or deleting a memory cleanly replaces/cancels its alarms. Past times are skipped.
 */
class MemoryReminderScheduler(
    private val context: Context,
    private val zone: ZoneId,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    private companion object {
        const val SLOTS = 4
        const val BASE = 800_000            // keep request codes clear of ritual/commitment codes
        const val MORNING = 8               // 08:00
        const val EVENING = 20              // 20:00
    }

    /** (Re)schedule all reminders for a memory. Cancels any existing ones first. */
    fun schedule(memoryId: Long, text: String, remindAt: Instant, hasTime: Boolean) {
        cancel(memoryId)
        val now = Instant.now()
        val day = remindAt.atZone(zone).toLocalDate()

        val times = buildList {
            add(day.minusDays(1).atTime(LocalTime.of(MORNING, 0)).atZone(zone).toInstant())   // day before, AM
            add(day.minusDays(1).atTime(LocalTime.of(EVENING, 0)).atZone(zone).toInstant())   // day before, PM
            add(day.atTime(LocalTime.of(MORNING, 0)).atZone(zone).toInstant())                // day of, AM
            if (hasTime) add(remindAt.minusSeconds(30 * 60))                                   // 30 min before
            else add(null)                                                                     // keep slot count stable
        }

        times.forEachIndexed { slot, at ->
            if (at != null && at.isAfter(now)) setAlarm(memoryId, slot, text, at.toEpochMilli())
        }
        AppLog.i("memory", "scheduled reminders for #$memoryId (hasTime=$hasTime)")
    }

    fun cancel(memoryId: Long) {
        // A PendingIntent matches on request code + action + component (extras are ignored), so an
        // empty-text intent still cancels the scheduled alarm.
        for (slot in 0 until SLOTS) alarmManager.cancel(pendingIntent(memoryId, slot, ""))
    }

    private fun setAlarm(memoryId: Long, slot: Int, text: String, atMillis: Long) {
        val pi = pendingIntent(memoryId, slot, text)
        val exact = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()
        if (exact) alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        else alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
    }

    private fun pendingIntent(memoryId: Long, slot: Int, text: String): PendingIntent {
        val intent = Intent(context, RitualAlarmReceiver::class.java).apply {
            action = RitualAlarmReceiver.ACTION_MEMORY_REMINDER
            putExtra(RitualAlarmReceiver.EXTRA_MEMORY_ID, memoryId)
            putExtra(RitualAlarmReceiver.EXTRA_MEMORY_TEXT, text)
        }
        val code = BASE + (memoryId.toInt() * SLOTS) + slot
        return PendingIntent.getBroadcast(
            context, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
