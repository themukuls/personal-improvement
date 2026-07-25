package com.chiefofstaff.system

import android.content.Context
import com.chiefofstaff.core.Clock
import com.chiefofstaff.data.LifeRepository
import com.chiefofstaff.data.entity.AnticipationItem
import com.chiefofstaff.data.entity.Commitment
import com.chiefofstaff.data.entity.DayState
import com.chiefofstaff.data.entity.EventEntity
import com.chiefofstaff.data.entity.ReferenceItem
import com.chiefofstaff.data.entity.RuleEntity
import com.chiefofstaff.data.model.AnticipationKind
import com.chiefofstaff.data.model.CommitmentState
import com.chiefofstaff.data.model.Domain
import com.chiefofstaff.data.model.EnergyCost
import com.chiefofstaff.data.model.Enforcement
import java.time.LocalTime

/**
 * First-run seed that reproduces the exact content shown in the "visual directions" design so a
 * fresh install opens on the Now screen the reviewer already recognises. This stands in for the
 * life-intake interview (FDN-01), which populates the same tables for real. Runs once.
 */
class SeedData(
    private val context: Context,
    private val repo: LifeRepository,
    private val clock: Clock,
) {
    private val prefs get() = context.getSharedPreferences("cos_flags", Context.MODE_PRIVATE)

    suspend fun seedIfNeeded() {
        if (prefs.getBoolean("seeded", false)) return
        val now = clock.now()
        val zone = clock.zone()
        fun at(h: Int, m: Int) = clock.today().atTime(LocalTime.of(h, m)).atZone(zone).toInstant()

        // Mode + day state (normal, energy unset → full plan, not minimum-viable).
        repo.mode()
        repo.state.upsertDay(DayState(date = DayState.keyFor(clock.today()), updatedAt = now))

        // The hard rule the NOT TODAY refusal cites.
        repo.graph.insertRule(
            RuleEntity(
                statement = "14:00 deep-work block",
                domain = Domain.WORK,
                enforcement = Enforcement.HARD,
                activeHours = "14:00-16:00",
                createdAt = now,
            )
        )

        // Today's calendar (status line: "next 10:00 standup").
        repo.graph.upsertEvent(
            EventEntity(title = "Standup", start = at(10, 0), end = at(10, 15), source = "seed", externalId = "seed-standup", createdAt = now)
        )

        // The three TODAY items, verbatim from Direction 1a.
        repo.commitments.insert(
            Commitment(
                what = "Gym — legs", dueAt = at(7, 0), domain = Domain.HEALTH, energyCost = EnergyCost.HIGH,
                state = CommitmentState.SCHEDULED, contextNote = "skipped twice last week",
                createdAt = now, updatedAt = now, lastTouchedAt = now,
            )
        )
        repo.commitments.insert(
            Commitment(
                what = "Q3 capacity plan to Rakesh", dueAt = at(16, 0), domain = Domain.WORK, energyCost = EnergyCost.HIGH,
                state = CommitmentState.SCHEDULED, deferralCount = 1, contextNote = "deferred once · predicted 15:20",
                createdAt = now, updatedAt = now, lastTouchedAt = now,
            )
        )
        repo.commitments.insert(
            Commitment(
                what = "Lab — lipid panel result", dueAt = null, domain = Domain.HEALTH,
                state = CommitmentState.SCHEDULED, contextNote = "promised Friday · unchased 4 days",
                createdAt = now, updatedAt = now, lastTouchedAt = now,
            )
        )
        // The item that collides with the deep-work rule → surfaces only in NOT TODAY.
        repo.commitments.insert(
            Commitment(
                what = "Sprint retro invite", dueAt = at(14, 30), domain = Domain.WORK,
                state = CommitmentState.SCHEDULED, contextNote = "collides with deep-work",
                createdAt = now, updatedAt = now, lastTouchedAt = now,
            )
        )

        // The single anticipation card (LOOKING AHEAD).
        repo.state.insertAnticipation(
            AnticipationItem(
                kind = AnticipationKind.EXPIRY,
                forDate = DayState.keyFor(clock.today()),
                headline = "Passport expires 14 Sep.",
                detail = "Bengaluru renewal is running four weeks.",
                salience = 0.9f,
                createdAt = now,
            )
        )
        // The reference behind it (value sealed; the vault is never stored in plaintext, §6.8).
        repo.graph.insertReference(
            ReferenceItem(type = "passport", label = "Passport", valueEncrypted = "«sealed»",
                expiresAt = clock.today().withMonth(9).withDayOfMonth(14).atStartOfDay(zone).toInstant(), createdAt = now)
        )

        prefs.edit().putBoolean("seeded", true).apply()
    }
}
