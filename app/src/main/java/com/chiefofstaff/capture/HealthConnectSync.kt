package com.chiefofstaff.capture

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.chiefofstaff.core.AppLog
import com.chiefofstaff.core.Clock
import com.chiefofstaff.data.LifeRepository
import com.chiefofstaff.data.entity.Observation
import java.time.Duration
import java.time.Instant

/**
 * CAP-07 / DOM-01,02,05 — passive health capture via Health Connect. This is P1 made literal: steps,
 * sleep, weight and heart rate arrive from whatever wearable the user owns, with zero typing. Each
 * reading is written as an immutable [Observation]; the morning brief's one health note and the
 * Look → Trends section read from these rows. Health data is a candidate for local-only egress
 * control (§6.8), so nothing here is ever assembled into an LLM request without passing redaction.
 *
 * Requires the runtime Health Connect grants (see [permissions]); if the SDK isn't installed or the
 * grants are absent, [sync] is a no-op — the app degrades, it does not break (P11).
 */
class HealthConnectSync(
    private val context: Context,
    private val repo: LifeRepository,
    private val clock: Clock,
) {
    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
    )

    fun isAvailable(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    private fun client(): HealthConnectClient? =
        if (isAvailable()) HealthConnectClient.getOrCreate(context) else null

    suspend fun hasAllPermissions(): Boolean {
        val client = client() ?: return false
        val granted = client.permissionController.getGrantedPermissions()
        return granted.containsAll(permissions)
    }

    /** Pull the last 24h of readings into Observation rows. Idempotent enough for a 02:00 batch. */
    suspend fun sync(): Int {
        val client = client() ?: run { AppLog.i("health", "Health Connect unavailable"); return 0 }
        if (!hasAllPermissions()) { AppLog.i("health", "health grants missing; skipping"); return 0 }

        val now = clock.now()
        val since = now.minus(Duration.ofDays(1))
        val range = TimeRangeFilter.between(since, now)
        var written = 0

        runCatching {
            // Steps — sum the day's records.
            val steps = client.readRecords(ReadRecordsRequest(StepsRecord::class, range))
                .records.sumOf { it.count }
            if (steps > 0) written += record("steps", steps.toDouble(), "count", now)

            // Sleep — total minutes of the most recent session.
            client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, range))
                .records.maxByOrNull { it.endTime }?.let { s ->
                    val minutes = Duration.between(s.startTime, s.endTime).toMinutes()
                    written += record("sleep_minutes", minutes.toDouble(), "min", s.endTime)
                }

            // Weight — latest.
            client.readRecords(ReadRecordsRequest(WeightRecord::class, range))
                .records.maxByOrNull { it.time }?.let { w ->
                    written += record("weight_kg", w.weight.inKilograms, "kg", w.time)
                }

            // Heart rate — latest sample bpm.
            client.readRecords(ReadRecordsRequest(HeartRateRecord::class, range))
                .records.flatMap { it.samples }.maxByOrNull { it.time }?.let { hr ->
                    written += record("hr_bpm", hr.beatsPerMinute.toDouble(), "bpm", hr.time)
                }
        }.onFailure { AppLog.w("health", "sync failed", it) }

        AppLog.i("health", "wrote $written health observation(s)")
        return written
    }

    private suspend fun record(metric: String, value: Double, unit: String, at: Instant): Int {
        repo.graph.insertObservation(
            Observation(metric = metric, value = value, unit = unit, observedAt = at,
                source = "health_connect", createdAt = clock.now())
        )
        return 1
    }
}
