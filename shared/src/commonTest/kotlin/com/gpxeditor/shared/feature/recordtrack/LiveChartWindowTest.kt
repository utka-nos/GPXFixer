package com.gpxeditor.shared.feature.recordtrack

import com.gpxeditor.shared.feature.trackdetail.TrackChartSample
import com.gpxeditor.shared.feature.trackdetail.TrackChartWindow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LiveChartWindowTest {
    @Test
    fun liveWindowIsNullWithoutTwoDistinctTimestamps() {
        assertNull(LiveChartWindow.liveWindow(emptyList()))
        assertNull(LiveChartWindow.liveWindow(listOf(TrackChartSample(0, 200))))
        assertNull(
            LiveChartWindow.liveWindow(
                listOf(TrackChartSample(3, 200), TrackChartSample(3, 210)),
            ),
        )
    }

    @Test
    fun liveWindowKeepsTheFixedScaleDuringTheFirstMinute() {
        val samples = rideSamples(durationSeconds = 20)

        assertEquals(
            TrackChartWindow(startSeconds = 0, endSeconds = 60),
            LiveChartWindow.liveWindow(samples),
        )
    }

    @Test
    fun liveWindowStartsAtZeroWhenPowerSamplesBeginPartwayThroughTheFirstMinute() {
        val samples = listOf(
            TrackChartSample(elapsedSeconds = 30, value = 200),
            TrackChartSample(elapsedSeconds = 40, value = 210),
        )

        assertEquals(
            TrackChartWindow(startSeconds = 0, endSeconds = 60),
            LiveChartWindow.liveWindow(samples),
        )
    }

    @Test
    fun liveWindowShowsTheLastSpanOfALongerRide() {
        val samples = rideSamples(durationSeconds = 1_000)

        assertEquals(
            TrackChartWindow(startSeconds = 940, endSeconds = 1_000),
            LiveChartWindow.liveWindow(samples),
        )
    }

    @Test
    fun pannedWindowIsClampedToTheRecordedRide() {
        val samples = rideSamples(durationSeconds = 1_000)
        val window = LiveChartWindow.liveWindow(samples)!!

        val panned = LiveChartWindow.panned(samples, window, deltaSeconds = -10_000)

        assertEquals(TrackChartWindow(startSeconds = 0, endSeconds = 60), panned)
    }

    @Test
    fun windowFollowingTheNewestSampleIsAtTheLiveEdge() {
        val samples = rideSamples(durationSeconds = 1_000)
        val window = LiveChartWindow.liveWindow(samples)!!

        assertTrue(LiveChartWindow.isAtLiveEdge(samples, window))
    }

    @Test
    fun windowPannedBackLeavesTheLiveEdgeAndPanningForwardReturnsToIt() {
        val samples = rideSamples(durationSeconds = 1_000)
        val window = LiveChartWindow.liveWindow(samples)!!

        val pannedBack = LiveChartWindow.panned(samples, window, deltaSeconds = -100)
        assertFalse(LiveChartWindow.isAtLiveEdge(samples, pannedBack))

        val pannedForward = LiveChartWindow.panned(samples, pannedBack, deltaSeconds = 500)
        assertTrue(LiveChartWindow.isAtLiveEdge(samples, pannedForward))
    }

    private fun rideSamples(durationSeconds: Long): List<TrackChartSample> {
        return (0..durationSeconds).map { TrackChartSample(it, 200) }
    }
}
