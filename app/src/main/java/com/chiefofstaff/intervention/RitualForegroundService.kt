package com.chiefofstaff.intervention

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import com.chiefofstaff.R
import com.chiefofstaff.core.AppLog

/**
 * SYS-08 / §16.1 — the foreground service. Its only job is to hold a live process so the ritual
 * alarm chain and WorkManager survive aggressive OEM background killing. It carries the required
 * visible ongoing notification on a MIN-importance channel so it stays unobtrusive (RES-05: no
 * counts, no red). It does no work itself — the rituals run as WorkManager jobs.
 */
class RitualForegroundService : LifecycleService() {

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startForegroundCompat()
        return START_STICKY
    }

    private fun startForegroundCompat() {
        val notification: Notification = NotificationCompat.Builder(this, Channels.SERVICE)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle("Chief of Staff is keeping watch")
            .setContentText("Rituals and reminders stay reliable.")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this, NotificationIds.SERVICE, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NotificationIds.SERVICE, notification)
        }
        AppLog.i("service", "foreground service running")
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, RitualForegroundService::class.java)
            context.startForegroundService(intent)
        }
    }
}
