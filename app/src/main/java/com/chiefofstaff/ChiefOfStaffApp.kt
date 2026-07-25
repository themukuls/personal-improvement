package com.chiefofstaff

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.chiefofstaff.core.AppLog
import com.chiefofstaff.intervention.Channels
import com.chiefofstaff.intervention.work.CalendarSyncWorker
import com.chiefofstaff.system.SeedData
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * App entry. Holds the [AppContainer] (the whole dependency graph) and does the one-time setup the
 * rituals depend on: notification channels, first-run seed, exact ritual alarms, and the 15-minute
 * calendar sync. The foreground service and alarm re-arming also happen here on cold start; boot is
 * handled separately by BootReceiver (§16.1).
 */
class ChiefOfStaffApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        Channels.ensure(this)

        container.appScope.launch {
            runCatching { SeedData(this@ChiefOfStaffApp, container.repo, container.clock).seedIfNeeded() }
                .onFailure { AppLog.e("app", "seed failed", it) }
        }

        // Arm the daily rituals (idempotent) and the periodic calendar sync (CAP-05).
        runCatching { container.ritualScheduler.scheduleAll() }
        schedulePeriodicSync()

        AppLog.i("app", "container ready")
    }

    private fun schedulePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<CalendarSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "calendar-sync", ExistingPeriodicWorkPolicy.KEEP, request,
        )
    }
}
