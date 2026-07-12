package com.gpxeditor.shared.feature.recordtrack

import com.gpxeditor.shared.feature.trackdetail.TrackChartSample

/**
 * Accumulates a live metric-over-time series while a track is being recorded.
 *
 * Elapsed time is the recording's active time, so paused spans do not stretch
 * the chart. Samples landing in the same elapsed second are averaged into one
 * point, which keeps memory bounded by ride duration regardless of the
 * sensor's notification rate.
 */
class LiveMetricSeries {
    private val samples = mutableListOf<TrackChartSample>()
    private var lastSecondSum = 0.0
    private var lastSecondCount = 0

    /** Adds a sample. Samples older than the newest recorded second are ignored. */
    fun add(elapsedSeconds: Long, value: Int) {
        val last = samples.lastOrNull()
        when {
            last == null || elapsedSeconds > last.elapsedSeconds -> {
                lastSecondSum = value.toDouble()
                lastSecondCount = 1
                samples.add(TrackChartSample(elapsedSeconds = elapsedSeconds, value = value))
            }

            elapsedSeconds == last.elapsedSeconds -> {
                lastSecondSum += value
                lastSecondCount += 1
                samples[samples.lastIndex] =
                    last.copy(value = (lastSecondSum / lastSecondCount + 0.5).toInt())
            }
        }
    }

    /** Immutable snapshot of the series, ordered by elapsed time. */
    fun snapshot(): List<TrackChartSample> = samples.toList()

    fun clear() {
        samples.clear()
        lastSecondSum = 0.0
        lastSecondCount = 0
    }
}
