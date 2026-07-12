package com.gpxeditor.shared.feature.trackdetail

import com.gpxeditor.shared.core.time.isoToUnixSeconds
import com.gpxeditor.shared.domain.activity.ActivityDocument
import com.gpxeditor.shared.domain.activity.ActivityPoint

/** A single point of a metric-over-time track chart. */
data class TrackChartSample(
    val elapsedSeconds: Long,
    val value: Int,
    /** Identifies the source GPX segment so charts never connect segment boundaries. */
    val segmentIndex: Int = 0,
)

object TrackChartCalculator {
    /** The power-over-time series for a track, in watts. */
    fun powerSamplesFor(document: ActivityDocument): List<TrackChartSample> =
        samplesFor(document) { it.powerWatts }

    /**
     * Builds a metric-over-time series for a track: one sample per point that
     * carries both a metric value and a parsable timestamp, with elapsed time
     * measured from the earliest such point. Returns an empty list when the
     * track has fewer than two usable samples — not enough to draw a line.
     */
    private fun samplesFor(
        document: ActivityDocument,
        valueOf: (ActivityPoint) -> Int?,
    ): List<TrackChartSample> {
        val timedValues = document.tracks
            .flatMap { it.segments }
            .mapIndexed { segmentIndex, segment ->
                segment.points.mapNotNull { point ->
                    val value = valueOf(point) ?: return@mapNotNull null
                    val unixSeconds = point.time?.let(::isoToUnixSeconds) ?: return@mapNotNull null
                    TimedValue(unixSeconds, value, segmentIndex)
                }
            }
            .flatten()
            .sortedBy { it.unixSeconds }
        if (timedValues.size < 2) return emptyList()

        val startSeconds = timedValues.first().unixSeconds
        return timedValues.map { timedValue ->
            TrackChartSample(
                elapsedSeconds = timedValue.unixSeconds - startSeconds,
                value = timedValue.value,
                segmentIndex = timedValue.segmentIndex,
            )
        }
    }

    private data class TimedValue(
        val unixSeconds: Long,
        val value: Int,
        val segmentIndex: Int,
    )
}
