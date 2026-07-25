package com.chiefofstaff.data

import androidx.room.TypeConverter
import java.time.Instant

/**
 * Room stores time as epoch-millis Longs and enums as their name Strings. Keeping the storage
 * form primitive keeps SQLite FTS and range queries simple, and makes the JSON export (SYS-11)
 * a direct column dump.
 */
class Converters {
    @TypeConverter fun instantToLong(value: Instant?): Long? = value?.toEpochMilli()
    @TypeConverter fun longToInstant(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }

    // Unit-separator (U+001F) delimited; safe against commas/newlines inside the values.
    @TypeConverter fun stringListToString(list: List<String>?): String? = list?.joinToString(SEP)
    @TypeConverter fun stringToStringList(value: String?): List<String> =
        value?.takeIf { it.isNotEmpty() }?.split(SEP) ?: emptyList()

    private companion object { const val SEP = "\u001F" }
}
