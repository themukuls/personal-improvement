package com.chiefofstaff.capture

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import com.chiefofstaff.core.AppLog
import com.chiefofstaff.core.Clock
import com.chiefofstaff.data.LifeRepository
import com.chiefofstaff.data.entity.Observation

/**
 * CAP-09 — passive screen-time ingest (P1: know things you never told it). Reads today's total
 * foreground usage from UsageStatsManager and records it as an Observation the trends can use.
 * Requires the PACKAGE_USAGE_STATS special grant (a settings-page toggle; irrelevant restriction
 * when sideloading, §14.1); a no-op until granted, so the app never breaks without it (P11).
 */
class ScreenTimeSync(
    private val context: Context,
    private val repo: LifeRepository,
    private val clock: Clock,
) {
    fun hasPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    suspend fun sync(): Boolean {
        if (!hasPermission()) { AppLog.i("screentime", "no usage-stats grant; skipping"); return false }
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return false
        val start = clock.today().atStartOfDay(clock.zone()).toInstant().toEpochMilli()
        val now = clock.epochMillis()
        val totalMs = runCatching {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, now)
                .sumOf { it.totalTimeInForeground }
        }.getOrDefault(0L)
        if (totalMs <= 0L) return false
        repo.graph.insertObservation(
            Observation(metric = "screen_minutes", value = (totalMs / 60_000.0), unit = "min",
                observedAt = clock.now(), source = "usage_stats", createdAt = clock.now())
        )
        AppLog.i("screentime", "recorded ${totalMs / 60_000} min")
        return true
    }
}
