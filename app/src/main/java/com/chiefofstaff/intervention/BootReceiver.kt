package com.chiefofstaff.intervention

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.chiefofstaff.core.AppLog
import com.chiefofstaff.core.SystemClock

/**
 * §16.1 — OEM devices kill background work and drop alarms across reboots. Re-arm every ritual on
 * boot (and on app update) so the 06:00 brief survives an overnight restart. The foreground service
 * is also (re)started from the Application, but the alarms are the load-bearing part.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                AppLog.i("boot", "re-arming rituals after ${intent.action}")
                RitualScheduler(context, SystemClock()).scheduleAll()
                RitualForegroundService.start(context)
            }
        }
    }
}
