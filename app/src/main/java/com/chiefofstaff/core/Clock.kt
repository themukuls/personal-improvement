package com.chiefofstaff.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Injectable clock. The prediction ledger (§1.3) and evening close compare "predicted at"
 * against "resolved at", so time must be substitutable in tests rather than read from the wall.
 */
interface Clock {
    fun now(): Instant
    fun zone(): ZoneId
    fun today(): LocalDate = LocalDate.ofInstant(now(), zone())
    fun localNow(): LocalDateTime = LocalDateTime.ofInstant(now(), zone())
    fun epochMillis(): Long = now().toEpochMilli()
}

class SystemClock(private val zone: ZoneId = ZoneId.systemDefault()) : Clock {
    override fun now(): Instant = Instant.now()
    override fun zone(): ZoneId = zone
}
