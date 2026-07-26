package com.chiefofstaff.system

import android.content.Context
import com.chiefofstaff.core.Clock
import com.chiefofstaff.data.LifeRepository
import com.chiefofstaff.data.entity.DayState

/**
 * First-run setup. Deliberately seeds **no demo content** — a real user's day starts empty and is
 * filled by their own captures, not by a stranger's gym/lab/passport tasks shown "for the design".
 * All this does is make sure the singletons the referee and notification budget depend on exist
 * (the mode row and today's day-state). The Now screen shows a warm welcome tile until the first
 * real thing is captured. Runs once.
 */
class SeedData(
    private val context: Context,
    private val repo: LifeRepository,
    private val clock: Clock,
) {
    private val prefs get() = context.getSharedPreferences("cos_flags", Context.MODE_PRIVATE)

    suspend fun seedIfNeeded() {
        if (prefs.getBoolean("seeded", false)) return
        repo.mode()   // ensure the operating-mode singleton exists (normal)
        repo.state.upsertDay(DayState(date = DayState.keyFor(clock.today()), updatedAt = clock.now()))
        prefs.edit().putBoolean("seeded", true).apply()
    }
}
