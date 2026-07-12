package com.gpxeditor.shared.feature.trackdetail

import com.gpxeditor.shared.core.time.isoToUnixSeconds
import com.gpxeditor.shared.domain.activity.ActivityDocument

/** A single point of the power-over-time chart. */
data class PowerChartSample(
    val elapsedSeconds: Long,
    val powerWatts: Int,
)

object PowerChartCalculator {
    /**
     * Builds the power-over-time series for a track: one sample per point that
     * carries both a power value and a parsable timestamp, with elapsed time
     * measured from the earliest such point. Returns an empty list when the
     * track has fewer than two usable samples — not enough to draw a line.
     */
    fun samplesFor(document: ActivityDocument): List<PowerChartSample> {
        val timedPowers = document.tracks
            .flatMap { it.segments }
            .flatMap { it.points }
            .mapNotNull { point ->
                val power = point.powerWatts ?: return@mapNotNull null
                val unixSeconds = point.time?.let(::isoToUnixSeconds) ?: return@mapNotNull null
                unixSeconds to power
            }
            .sortedBy { it.first }
        if (timedPowers.size < 2) return emptyList()

        val startSeconds = timedPowers.first().first
        return timedPowers.map { (unixSeconds, power) ->
            PowerChartSample(
                elapsedSeconds = unixSeconds - startSeconds,
                powerWatts = power,
            )
        }
    }
}
