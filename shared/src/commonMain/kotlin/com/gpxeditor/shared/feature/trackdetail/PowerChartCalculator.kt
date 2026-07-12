package com.gpxeditor.shared.feature.trackdetail

import com.gpxeditor.shared.core.time.isoToUnixSeconds
import com.gpxeditor.shared.domain.activity.ActivityDocument

/** A single point of the power-over-time chart. */
data class PowerChartSample(
    val elapsedSeconds: Long,
    val powerWatts: Int,
    /** Identifies the source GPX segment so charts never connect segment boundaries. */
    val segmentIndex: Int = 0,
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
            .mapIndexed { segmentIndex, segment ->
                segment.points.mapNotNull { point ->
                    val power = point.powerWatts ?: return@mapNotNull null
                    val unixSeconds = point.time?.let(::isoToUnixSeconds) ?: return@mapNotNull null
                    TimedPower(unixSeconds, power, segmentIndex)
                }
            }
            .flatten()
            .sortedBy { it.unixSeconds }
        if (timedPowers.size < 2) return emptyList()

        val startSeconds = timedPowers.first().unixSeconds
        return timedPowers.map { timedPower ->
            PowerChartSample(
                elapsedSeconds = timedPower.unixSeconds - startSeconds,
                powerWatts = timedPower.powerWatts,
                segmentIndex = timedPower.segmentIndex,
            )
        }
    }

    private data class TimedPower(
        val unixSeconds: Long,
        val powerWatts: Int,
        val segmentIndex: Int,
    )
}
