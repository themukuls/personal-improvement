package com.chiefofstaff.capture

import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.chiefofstaff.core.AppLog
import com.chiefofstaff.core.Clock
import com.chiefofstaff.data.LifeRepository
import com.chiefofstaff.data.entity.EventEntity
import java.time.Instant

/**
 * CAP-05 — calendar read sync. The spec's source of truth is Google Calendar (read in V0, write in
 * V2); this V0 slice reads the device calendar provider, which already aggregates the user's Google
 * account and works offline without an OAuth round-trip (§16.4 defers the OAuth decision to day 7).
 * Events are upserted by their external id so re-syncing every 15 minutes is idempotent.
 */
class CalendarSync(
    private val context: Context,
    private val repo: LifeRepository,
    private val clock: Clock,
) {
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /** Sync events from now through the next 14 days (the anticipation horizon, §11). */
    suspend fun sync(): Int {
        if (!hasPermission()) {
            AppLog.i("calendar", "no READ_CALENDAR grant; skipping sync")
            return 0
        }
        val from = clock.now().toEpochMilli()
        val to = clock.now().plusSeconds(14L * 86_400).toEpochMilli()

        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.EVENT_LOCATION,
        )
        val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
        val args = arrayOf(from.toString(), to.toString())

        var count = 0
        runCatching {
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI, projection, selection, args,
                "${CalendarContract.Events.DTSTART} ASC",
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(CalendarContract.Events._ID)
                val titleCol = cursor.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
                val startCol = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
                val endCol = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTEND)
                val locCol = cursor.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION)
                while (cursor.moveToNext()) {
                    val extId = cursor.getLong(idCol).toString()
                    val existing = repo.graph.eventByExternalId(extId)
                    repo.graph.upsertEvent(
                        EventEntity(
                            id = existing?.id ?: 0,
                            title = cursor.getString(titleCol) ?: "(untitled)",
                            start = Instant.ofEpochMilli(cursor.getLong(startCol)),
                            end = cursor.getLong(endCol).takeIf { it > 0 }?.let { Instant.ofEpochMilli(it) },
                            location = cursor.getString(locCol),
                            source = "device-calendar",
                            externalId = extId,
                            createdAt = existing?.createdAt ?: clock.now(),
                        )
                    )
                    count++
                }
            }
        }.onFailure { AppLog.w("calendar", "sync failed", it) }
        AppLog.i("calendar", "synced $count events")
        return count
    }
}
