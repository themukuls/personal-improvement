package com.chiefofstaff.system

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Everything the first-run onboarding (and only it) needs to reason about permissions: which
 * runtime grants to ask for, whether each special-access toggle is on, and the exact Settings
 * intent that drops the user on the right switch.
 *
 * The app is sideload-only (P8) and requests restricted grants a Play release would reject — so we
 * never assume they exist. Every capture/ritual surface degrades gracefully without them (P11);
 * this is purely the guided path for a user who wants to turn them on.
 *
 * minSdk is 29, so the API guards below only bracket things that genuinely arrived later
 * (exact-alarm consent on API 31+).
 */
object Permissions {

    private const val FLAGS = "cos_flags"
    private const val KEY_ONBOARDED = "onboarded"

    // --- First-run gate ---
    fun isOnboarded(ctx: Context): Boolean =
        ctx.getSharedPreferences(FLAGS, Context.MODE_PRIVATE).getBoolean(KEY_ONBOARDED, false)

    fun markOnboarded(ctx: Context) =
        ctx.getSharedPreferences(FLAGS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ONBOARDED, true).apply()

    // --- Runtime permissions (the standard system popup) ---
    fun runtimeWanted(): Array<String> = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        add(Manifest.permission.CAMERA)
        add(Manifest.permission.READ_CALENDAR)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    fun granted(ctx: Context, perm: String): Boolean =
        ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED

    fun allRuntimeGranted(ctx: Context): Boolean = runtimeWanted().all { granted(ctx, it) }

    // --- Special-access states (each lives behind its own Settings screen, not a popup) ---

    /** CAP-06 — notification listener; the only way to read commitments out of message notifications. */
    fun notificationListenerEnabled(ctx: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    /** Usage access (PACKAGE_USAGE_STATS) — an AppOps grant, checked via AppOpsManager. */
    fun usageAccessGranted(ctx: Context): Boolean {
        val appOps = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Exact-alarm consent (§16.1). Auto-granted below API 31; user-toggled on 31+. */
    fun exactAlarmAllowed(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    /** Battery optimisation off — the foreground watchdog (SYS-08) survives OEM killing. */
    fun batteryUnrestricted(ctx: Context): Boolean {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    // --- Intents to the exact Settings panel for each special grant ---

    fun notificationListenerSettings(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    fun usageAccessSettings(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    fun exactAlarmSettings(ctx: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${ctx.packageName}".toUri())
        } else {
            appDetails(ctx)
        }

    fun batterySettings(ctx: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${ctx.packageName}".toUri())

    /** Fallback: the app's own details page, from which every toggle is reachable. */
    fun appDetails(ctx: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${ctx.packageName}".toUri())

    private fun String.toUri(): Uri = Uri.parse(this)
}
