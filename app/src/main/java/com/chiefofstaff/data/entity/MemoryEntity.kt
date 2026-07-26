package com.chiefofstaff.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * A dated memory the user asks the app to hold — "mom's birthday is 10 March", "renew insurance on
 * the 22nd", "call the clinic at 3pm Friday". Unlike a Note it carries a [remindAt] so the app can
 * proactively reminds the user before it happens (see MemoryReminderScheduler). [hasTime] tells the
 * scheduler whether a specific clock time was given (add a 30-min-before nudge) or just a date.
 */
@Entity(tableName = "memory", indices = [Index("remindAt")])
data class Memory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val remindAt: Instant? = null,
    val hasTime: Boolean = false,
    val createdAt: Instant,
)
