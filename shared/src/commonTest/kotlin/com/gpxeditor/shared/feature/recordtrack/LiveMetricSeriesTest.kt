package com.gpxeditor.shared.feature.recordtrack

import com.gpxeditor.shared.feature.trackdetail.TrackChartSample
import kotlin.test.Test
import kotlin.test.assertEquals

class LiveMetricSeriesTest {
    @Test
    fun keepsSamplesOrderedByElapsedTime() {
        val series = LiveMetricSeries()

        series.add(elapsedSeconds = 0, value = 200)
        series.add(elapsedSeconds = 1, value = 210)
        series.add(elapsedSeconds = 3, value = 220)

        assertEquals(
            listOf(
                TrackChartSample(0, 200),
                TrackChartSample(1, 210),
                TrackChartSample(3, 220),
            ),
            series.snapshot(),
        )
    }

    @Test
    fun averagesSamplesLandingInTheSameSecond() {
        val series = LiveMetricSeries()

        series.add(elapsedSeconds = 5, value = 100)
        series.add(elapsedSeconds = 5, value = 200)
        series.add(elapsedSeconds = 5, value = 201)

        assertEquals(listOf(TrackChartSample(5, 167)), series.snapshot())
    }

    @Test
    fun ignoresSamplesOlderThanTheNewestSecond() {
        val series = LiveMetricSeries()

        series.add(elapsedSeconds = 10, value = 200)
        series.add(elapsedSeconds = 4, value = 999)

        assertEquals(listOf(TrackChartSample(10, 200)), series.snapshot())
    }

    @Test
    fun snapshotIsNotAffectedByLaterSamples() {
        val series = LiveMetricSeries()
        series.add(elapsedSeconds = 0, value = 200)

        val snapshot = series.snapshot()
        series.add(elapsedSeconds = 1, value = 210)

        assertEquals(listOf(TrackChartSample(0, 200)), snapshot)
    }

    @Test
    fun clearResetsTheSeriesAndItsAveraging() {
        val series = LiveMetricSeries()
        series.add(elapsedSeconds = 0, value = 100)
        series.add(elapsedSeconds = 0, value = 200)

        series.clear()
        series.add(elapsedSeconds = 0, value = 300)
        series.add(elapsedSeconds = 0, value = 400)

        assertEquals(listOf(TrackChartSample(0, 350)), series.snapshot())
    }
}
