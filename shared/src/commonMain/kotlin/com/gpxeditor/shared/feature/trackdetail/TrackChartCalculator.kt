package com.gpxeditor.shared.feature.trackdetail

import com.gpxeditor.shared.core.geo.haversineMeters
import com.gpxeditor.shared.core.time.isoToUnixSeconds
import com.gpxeditor.shared.domain.activity.ActivityDocument
import com.gpxeditor.shared.domain.activity.ActivityPoint
import com.gpxeditor.shared.domain.activity.ActivitySegment

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

    /** The heart-rate-over-time series for a track, in beats per minute. */
    fun heartRateSamplesFor(document: ActivityDocument): List<TrackChartSample> =
        samplesFor(document) { it.heartRateBpm }

    /**
     * The speed-over-time series for a track, in kilometers per hour. Uses the
     * recorded speed where a point carries one, and otherwise derives speed
     * from the GPS distance and time to the previous located point of the
     * same segment.
     */
    fun speedSamplesFor(document: ActivityDocument): List<TrackChartSample> {
        val timedValues = document.tracks
            .flatMap { it.segments }
            .mapIndexed { segmentIndex, segment -> speedTimedValues(segment, segmentIndex) }
            .flatten()
            .sortedBy { it.unixSeconds }
        return toChartSamples(timedValues)
    }

    private fun speedTimedValues(segment: ActivitySegment, segmentIndex: Int): List<TimedValue> {
        val timedValues = mutableListOf<TimedValue>()
        var previousFix: GpsFix? = null
        for (point in segment.points) {
            val unixSeconds = point.time?.let(::isoToUnixSeconds) ?: continue
            val speedKmh = point.speedMetersPerSecond?.times(MPS_TO_KMH)
                ?: derivedSpeedKmh(previousFix, point, unixSeconds)
            if (point.latitude != null && point.longitude != null) {
                previousFix = GpsFix(unixSeconds, point.latitude, point.longitude)
            }
            if (speedKmh != null && speedKmh >= 0.0) {
                timedValues.add(TimedValue(unixSeconds, (speedKmh + 0.5).toInt(), segmentIndex))
            }
        }
        return timedValues
    }

    private fun derivedSpeedKmh(
        previousFix: GpsFix?,
        point: ActivityPoint,
        unixSeconds: Long,
    ): Double? {
        if (previousFix == null || point.latitude == null || point.longitude == null) return null
        val deltaSeconds = unixSeconds - previousFix.unixSeconds
        if (deltaSeconds <= 0) return null
        val meters = haversineMeters(
            previousFix.latitude,
            previousFix.longitude,
            point.latitude,
            point.longitude,
        )
        return meters / deltaSeconds * MPS_TO_KMH
    }

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
        return toChartSamples(timedValues)
    }

    private fun toChartSamples(timedValues: List<TimedValue>): List<TrackChartSample> {
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

    private const val MPS_TO_KMH = 3.6

    private data class TimedValue(
        val unixSeconds: Long,
        val value: Int,
        val segmentIndex: Int,
    )

    private data class GpsFix(
        val unixSeconds: Long,
        val latitude: Double,
        val longitude: Double,
    )
}
