package com.chiefofstaff.ui.vm

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chiefofstaff.AppContainer
import com.chiefofstaff.domain.ProgressEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProgressUiState(
    val loading: Boolean = true,
    val snapshot: ProgressEngine.Snapshot? = null,
)

/**
 * Backs the Progress overlay and the once-a-week recap pop-up. The recap fires at most once per ISO
 * week: the first time the app opens in a new week, it surfaces the week that just ended, then
 * records that week's key so it never nags again.
 */
class ProgressViewModel(private val container: AppContainer) : ViewModel() {
    private val engine = container.progressEngine
    private val prefs = container.appContext.getSharedPreferences("cos_progress", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(ProgressUiState())
    val state: StateFlow<ProgressUiState> = _state.asStateFlow()

    private val _pendingRecap = MutableStateFlow<ProgressEngine.WeekRecap?>(null)
    val pendingRecap: StateFlow<ProgressEngine.WeekRecap?> = _pendingRecap.asStateFlow()

    private val _pendingDay = MutableStateFlow<ProgressEngine.DayRecap?>(null)
    val pendingDay: StateFlow<ProgressEngine.DayRecap?> = _pendingDay.asStateFlow()

    init {
        refresh()
        evaluateRecaps()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = ProgressUiState(loading = false, snapshot = engine.snapshot())
        }
    }

    /**
     * Decide what to surface on open: the weekly recap takes priority on a week boundary, and when it
     * fires we mark today's daily recap as seen too, so the user never gets two pop-ups at once.
     */
    private fun evaluateRecaps() {
        viewModelScope.launch {
            var weeklyShowing = false
            val weekKey = engine.currentWeekKey()
            if (prefs.getString(KEY_LAST_WEEK, "") != weekKey) {
                val recap = engine.lastWeekRecap()
                if (recap != null) {
                    _pendingRecap.value = recap        // KEY_LAST_WEEK stored on dismiss
                    weeklyShowing = true
                } else {
                    prefs.edit().putString(KEY_LAST_WEEK, weekKey).apply()
                }
            }

            val dayKey = engine.currentDayKey()
            if (prefs.getString(KEY_LAST_DAY, "") != dayKey) {
                if (weeklyShowing) {
                    prefs.edit().putString(KEY_LAST_DAY, dayKey).apply()   // suppress the daily; weekly covers it
                } else {
                    val day = engine.lastDayRecap()
                    if (day != null) _pendingDay.value = day               // KEY_LAST_DAY stored on dismiss
                    else prefs.edit().putString(KEY_LAST_DAY, dayKey).apply()
                }
            }
        }
    }

    fun dismissRecap() {
        prefs.edit().putString(KEY_LAST_WEEK, engine.currentWeekKey()).apply()
        _pendingRecap.value = null
    }

    fun dismissDayRecap() {
        prefs.edit().putString(KEY_LAST_DAY, engine.currentDayKey()).apply()
        _pendingDay.value = null
    }

    /** Build the plain-text export off the main thread, then hand it back for the share sheet. */
    fun buildExport(onReady: (String) -> Unit) {
        viewModelScope.launch { onReady(engine.exportText()) }
    }

    private companion object {
        const val KEY_LAST_WEEK = "last_week_shown"
        const val KEY_LAST_DAY = "last_day_shown"
    }
}
