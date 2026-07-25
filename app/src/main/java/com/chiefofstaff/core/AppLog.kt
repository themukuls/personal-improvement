package com.chiefofstaff.core

import android.util.Log

/**
 * Thin logging wrapper. SYS-12 (silent-failure detection) taps warn/error to surface a
 * "the assistant went quiet" system notification when a ritual fails to run.
 */
object AppLog {
    private const val TAG = "CoS"
    var sink: ((level: Int, area: String, msg: String, t: Throwable?) -> Unit)? = null

    fun d(area: String, msg: String) = emit(Log.DEBUG, area, msg, null)
    fun i(area: String, msg: String) = emit(Log.INFO, area, msg, null)
    fun w(area: String, msg: String, t: Throwable? = null) = emit(Log.WARN, area, msg, t)
    fun e(area: String, msg: String, t: Throwable? = null) = emit(Log.ERROR, area, msg, t)

    private fun emit(level: Int, area: String, msg: String, t: Throwable?) {
        Log.println(level, TAG, "[$area] $msg" + (t?.let { " :: ${it.message}" } ?: ""))
        sink?.invoke(level, area, msg, t)
    }
}
