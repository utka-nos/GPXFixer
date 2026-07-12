package com.gpxeditor.shared.feature.trackdetail

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrackChartPresenterTest {
    @Test
    fun fullWindowSpansTheWholeSeries() {
        val samples = rideSamples(durationSeconds = 120, stepSeconds = 10)

        assertEquals(
            TrackChartWindow(startSeconds = 0, endSeconds = 120),
            TrackChartPresenter.fullWindow(samples),
        )
    }

    @Test
    fun fullWindowIsNullWithoutTwoDistinctTimestamps() {
        assertNull(TrackChartPresenter.fullWindow(emptyList()))
        assertNull(TrackChartPresenter.fullWindow(listOf(TrackChartSample(0, 200))))
        assertNull(
            TrackChartPresenter.fullWindow(
                listOf(TrackChartSample(5, 200), TrackChartSample(5, 220)),
            ),
        )
    }

    @Test
    fun shortWindowUsesMinuteSecondTicks() {
        val samples = rideSamples(durationSeconds = 300, stepSeconds = 5)

        val presentation = TrackChartPresenter.presentation(
            samples,
            TrackChartWindow(0, 300),
        )

        assertEquals(
            listOf("00:00", "01:00", "02:00", "03:00", "04:00", "05:00"),
            presentation.timeTicks.map { it.label },
        )
        assertEquals(
            listOf<Long>(0, 60, 120, 180, 240, 300),
            presentation.timeTicks.map { it.elapsedSeconds },
        )
    }

    @Test
    fun longRideUsesHourMinuteTicks() {
        val samples = listOf(
            TrackChartSample(0, 200),
            TrackChartSample(5 * 3_600, 210),
        )

        val presentation = TrackChartPresenter.presentation(
            samples,
            TrackChartWindow(0, 5 * 3_600),
        )

        assertEquals(
            listOf("0:00", "1:00", "2:00", "3:00", "4:00", "5:00"),
            presentation.timeTicks.map { it.label },
        )
    }

    @Test
    fun zoomedWindowBeyondAnHourKeepsSecondsWhenStepIsFine() {
        val samples = listOf(
            TrackChartSample(0, 200),
            TrackChartSample(2 * 3_600, 210),
        )

        val presentation = TrackChartPresenter.presentation(
            samples,
            TrackChartWindow(startSeconds = 3_600, endSeconds = 3_660),
        )

        assertTrue(presentation.timeTicks.isNotEmpty())
        assertTrue(
            presentation.timeTicks.all { it.label.startsWith("1:") && it.label.count { c -> c == ':' } == 2 },
            "expected H:MM:SS labels, got ${presentation.timeTicks.map { it.label }}",
        )
    }

    @Test
    fun tickCountStaysReadable() {
        for (duration in listOf(60L, 313L, 3_599L, 3_600L, 12 * 3_600L, 30 * 3_600L)) {
            val presentation = TrackChartPresenter.presentation(
                listOf(TrackChartSample(0, 100), TrackChartSample(duration, 100)),
                TrackChartWindow(0, duration),
            )
            val count = presentation.timeTicks.size
            assertTrue(count in 2..7, "duration $duration produced $count ticks")
        }
    }

    @Test
    fun valueAxisStartsAtZeroWithPaddedRoundedMaximum() {
        val samples = listOf(
            TrackChartSample(0, 180),
            TrackChartSample(10, 487),
            TrackChartSample(20, 230),
        )

        val presentation = TrackChartPresenter.presentation(samples, TrackChartWindow(0, 20))

        assertEquals(0, presentation.valueTicks.first().value)
        assertTrue(presentation.axisMaxValue > 487, "axis must leave headroom above the max")
        assertEquals(presentation.axisMaxValue, presentation.valueTicks.last().value)
        val step = presentation.valueTicks[1].value - presentation.valueTicks[0].value
        assertEquals(0, presentation.axisMaxValue % step)
        assertTrue(presentation.valueTicks.size in 3..7)
    }

    @Test
    fun summaryDescribesOnlyTheVisibleWindow() {
        val samples = listOf(
            TrackChartSample(0, 100),
            TrackChartSample(10, 200),
            TrackChartSample(20, 300),
            TrackChartSample(30, 900),
        )

        val presentation = TrackChartPresenter.presentation(samples, TrackChartWindow(0, 20))

        assertEquals(300, presentation.maxValue)
        assertEquals(200, presentation.averageValue)
    }

    @Test
    fun largeTimestampGapBreaksTheLine() {
        val samples = listOf(
            TrackChartSample(0, 200),
            TrackChartSample(30, 210),
            TrackChartSample(60, 220),
            // 10-minute pause.
            TrackChartSample(660, 230),
            TrackChartSample(690, 240),
        )

        val presentation = TrackChartPresenter.presentation(samples, TrackChartWindow(0, 690))

        assertEquals(2, presentation.segments.size)
        assertEquals(listOf<Long>(0, 30, 60), presentation.segments[0].map { it.elapsedSeconds })
        assertEquals(listOf<Long>(660, 690), presentation.segments[1].map { it.elapsedSeconds })
    }

    @Test
    fun gapAtOrBelowThresholdDoesNotBreakTheLine() {
        val samples = listOf(
            TrackChartSample(0, 200),
            TrackChartSample(TrackChartPresenter.GAP_THRESHOLD_SECONDS, 210),
            TrackChartSample(TrackChartPresenter.GAP_THRESHOLD_SECONDS + 30, 220),
        )

        val presentation = TrackChartPresenter.presentation(
            samples,
            TrackChartWindow(0, TrackChartPresenter.GAP_THRESHOLD_SECONDS + 30),
        )

        assertEquals(1, presentation.segments.size)
    }

    @Test
    fun sourceSegmentBoundaryBreaksTheLineEvenWhenTimestampsAreClose() {
        val samples = listOf(
            TrackChartSample(0, 200, segmentIndex = 0),
            TrackChartSample(30, 210, segmentIndex = 0),
            TrackChartSample(31, 220, segmentIndex = 1),
            TrackChartSample(60, 230, segmentIndex = 1),
        )

        val presentation = TrackChartPresenter.presentation(samples, TrackChartWindow(0, 60))

        assertEquals(2, presentation.segments.size)
        assertEquals(listOf<Long>(0, 30), presentation.segments[0].map { it.elapsedSeconds })
        assertEquals(listOf<Long>(31, 60), presentation.segments[1].map { it.elapsedSeconds })
    }

    @Test
    fun duplicateTimestampAtSegmentBoundaryRemainsSplit() {
        val samples = listOf(
            TrackChartSample(0, 100, segmentIndex = 0),
            TrackChartSample(10, 200, segmentIndex = 0),
            TrackChartSample(10, 300, segmentIndex = 1),
            TrackChartSample(20, 400, segmentIndex = 1),
        )

        val presentation = TrackChartPresenter.presentation(samples, TrackChartWindow(0, 20))

        assertEquals(2, presentation.segments.size)
        assertEquals(listOf(100, 200), presentation.segments[0].map { it.value })
        assertEquals(listOf(300, 400), presentation.segments[1].map { it.value })
    }

    @Test
    fun duplicateTimestampsAreAveragedIntoOneSample() {
        val samples = listOf(
            TrackChartSample(0, 100),
            TrackChartSample(10, 200),
            TrackChartSample(10, 300),
            TrackChartSample(20, 400),
        )

        val presentation = TrackChartPresenter.presentation(samples, TrackChartWindow(0, 20))

        assertEquals(
            listOf(
                TrackChartSample(0, 100),
                TrackChartSample(10, 250),
                TrackChartSample(20, 400),
            ),
            presentation.segments.single(),
        )
        assertEquals(250, presentation.averageValue)
    }

    @Test
    fun downsamplingBoundsPointCountAndPreservesExtremes() {
        val samples = (0 until 10_000).map { index ->
            val value = when (index) {
                4_242 -> 950 // lone peak
                7_777 -> 5 // lone valley
                else -> 200 + (index % 40)
            }
            TrackChartSample(index.toLong(), value)
        }

        val presentation = TrackChartPresenter.presentation(
            samples,
            TrackChartWindow(0, 9_999),
            maxRenderPoints = 500,
        )

        val rendered = presentation.segments.single()
        assertTrue(rendered.size <= 500, "rendered ${rendered.size} points")
        assertTrue(rendered.any { it.value == 950 }, "peak must survive downsampling")
        assertTrue(rendered.any { it.value == 5 }, "valley must survive downsampling")
        assertEquals(0, rendered.first().elapsedSeconds)
        assertEquals(9_999, rendered.last().elapsedSeconds)
        assertEquals(950, presentation.maxValue)
    }

    @Test
    fun zoomedWindowKeepsEdgeNeighboursSoTheLineReachesTheViewport() {
        val samples = rideSamples(durationSeconds = 1_000, stepSeconds = 10)

        val presentation = TrackChartPresenter.presentation(
            samples,
            TrackChartWindow(startSeconds = 305, endSeconds = 505),
        )

        val rendered = presentation.segments.single()
        assertEquals(300, rendered.first().elapsedSeconds)
        assertEquals(510, rendered.last().elapsedSeconds)
    }

    @Test
    fun zoomInKeepsFocusAndRespectsMinimumWindow() {
        val samples = rideSamples(durationSeconds = 3_600, stepSeconds = 10)
        val full = TrackChartWindow(0, 3_600)

        val zoomed = TrackChartPresenter.zoomed(samples, full, factor = 2.0, focusSeconds = 1_800)
        assertEquals(1_800, zoomed.durationSeconds)
        assertEquals(900, zoomed.startSeconds)

        var window = full
        repeat(20) {
            window = TrackChartPresenter.zoomed(samples, window, factor = 2.0, focusSeconds = 1_800)
        }
        assertEquals(TrackChartPresenter.MIN_WINDOW_SECONDS, window.durationSeconds)
    }

    @Test
    fun zoomOutIsClampedToTheFullSeries() {
        val samples = rideSamples(durationSeconds = 600, stepSeconds = 10)
        val window = TrackChartWindow(100, 400)

        val zoomedOut = TrackChartPresenter.zoomed(samples, window, factor = 0.01, focusSeconds = 250)

        assertEquals(TrackChartWindow(0, 600), zoomedOut)
    }

    @Test
    fun panIsClampedToTheFullSeries() {
        val samples = rideSamples(durationSeconds = 600, stepSeconds = 10)
        val window = TrackChartWindow(100, 200)

        assertEquals(
            TrackChartWindow(150, 250),
            TrackChartPresenter.panned(samples, window, deltaSeconds = 50),
        )
        assertEquals(
            TrackChartWindow(500, 600),
            TrackChartPresenter.panned(samples, window, deltaSeconds = 10_000),
        )
        assertEquals(
            TrackChartWindow(0, 100),
            TrackChartPresenter.panned(samples, window, deltaSeconds = -10_000),
        )
    }

    @Test
    fun nearestSampleSnapsToTheClosestTimestamp() {
        val samples = listOf(
            TrackChartSample(0, 100),
            TrackChartSample(10, 200),
            TrackChartSample(30, 300),
        )

        assertEquals(TrackChartSample(0, 100), TrackChartPresenter.nearestSample(samples, -5))
        assertEquals(TrackChartSample(10, 200), TrackChartPresenter.nearestSample(samples, 14))
        assertEquals(TrackChartSample(30, 300), TrackChartPresenter.nearestSample(samples, 21))
        assertEquals(TrackChartSample(30, 300), TrackChartPresenter.nearestSample(samples, 999))
        assertNull(TrackChartPresenter.nearestSample(emptyList(), 0))
    }

    @Test
    fun formatsElapsedTimeWithAdaptiveUnits() {
        assertEquals("00:07", TrackChartPresenter.formatElapsed(7))
        assertEquals("05:30", TrackChartPresenter.formatElapsed(330))
        assertEquals("1:00:00", TrackChartPresenter.formatElapsed(3_600))
        assertEquals("12:34:56", TrackChartPresenter.formatElapsed(12 * 3_600 + 34 * 60 + 56))
    }

    private fun rideSamples(durationSeconds: Long, stepSeconds: Long): List<TrackChartSample> =
        (0..durationSeconds step stepSeconds).map { elapsed ->
            TrackChartSample(elapsed, 200 + (elapsed % 50).toInt())
        }
}
