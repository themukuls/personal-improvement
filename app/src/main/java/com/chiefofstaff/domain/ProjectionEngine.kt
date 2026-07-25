package com.chiefofstaff.domain

import com.chiefofstaff.core.Clock
import com.chiefofstaff.data.LifeRepository
import java.time.temporal.ChronoUnit

/**
 * DIR-16 — scenario projection: "at this pace, where am I in N months?" Pure trend extrapolation
 * over the observations already on device (§4.1 — never spend a token on what arithmetic answers).
 * A metric with a clear recent slope is projected forward; a flat or too-sparse metric is skipped
 * rather than guessed. The framing is a pace check, not a prophecy — it says "on current pace",
 * because the point is to make a drifting trajectory visible early enough to change it.
 */
class ProjectionEngine(
    private val repo: LifeRepository,
    private val clock: Clock,
) {
    /** A projected metric: where it lands in [horizonDays] if the recent slope holds. */
    data class Projection(val metric: String, val current: Double, val projected: Double, val horizonDays: Int)

    private val tracked = listOf("weight_kg", "steps", "net_worth")

    /** Project every tracked metric that has a usable trend. */
    suspend fun projections(horizonDays: Int = 90): List<Projection> =
        tracked.mapNotNull { projectMetric(it, horizonDays) }

    /**
     * Fit a slope from the most recent readings (a coarse least-squares over day-offsets) and
     * extrapolate. Returns null when there aren't enough points, the timespan is zero, or the slope
     * is negligible — no trend, nothing worth projecting.
     */
    suspend fun projectMetric(metric: String, horizonDays: Int = 90): Projection? {
        val obs = repo.graph.observations(metric, 12).sortedBy { it.observedAt }  // chronological
        if (obs.size < 3) return null
        val t0 = obs.first().observedAt
        val xs = obs.map { ChronoUnit.DAYS.between(t0, it.observedAt).toDouble() }
        val ys = obs.map { it.value }
        if (xs.last() - xs.first() < 1.0) return null

        val n = xs.size
        val meanX = xs.average(); val meanY = ys.average()
        var num = 0.0; var den = 0.0
        for (i in 0 until n) { num += (xs[i] - meanX) * (ys[i] - meanY); den += (xs[i] - meanX) * (xs[i] - meanX) }
        if (den == 0.0) return null
        val slope = num / den
        val current = ys.last()
        val projected = current + slope * horizonDays
        // Ignore trends too small to matter (< 2% drift over the horizon).
        if (kotlin.math.abs(projected - current) < kotlin.math.abs(current) * 0.02) return null
        return Projection(metric, current, projected, horizonDays)
    }

    /** One neutral line per projection, months-framed. */
    suspend fun projectionLines(horizonDays: Int = 90): List<String> {
        val months = (horizonDays / 30).coerceAtLeast(1)
        return projections(horizonDays).map { p ->
            val name = p.metric.replace('_', ' ')
            val cur = fmt(p.metric, p.current); val proj = fmt(p.metric, p.projected)
            val dir = if (p.projected > p.current) "↑" else "↓"
            "On current pace, $name: $cur → $proj $dir in $months month${if (months == 1) "" else "s"}."
        }
    }

    private fun fmt(metric: String, v: Double): String = when (metric) {
        "net_worth" -> "₹${v.toLong()}"
        "steps" -> "${v.toLong()}/day"
        else -> "%.1f".format(v)
    }
}
