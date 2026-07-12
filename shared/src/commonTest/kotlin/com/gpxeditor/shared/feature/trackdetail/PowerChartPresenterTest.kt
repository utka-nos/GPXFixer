package com.gpxeditor.shared.feature.trackdetail

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PowerChartPresenterTest {
    @Test
    fun fullWindowSpansTheWholeSeries() {
        val samples = rideSamples(durationSeconds = 120, stepSeconds = 10)

        assertEquals(
            PowerChartWindow(startSeconds = 0, endSeconds = 120),
            PowerChartPresenter.fullWindow(samples),
        )
    }

    @Test
    fun fullWindowIsNullWithoutTwoDistinctTimestamps() {
        assertNull(PowerChartPresenter.fullWindow(emptyList()))
        assertNull(PowerChartPresenter.fullWindow(listOf(PowerChartSample(0, 200))))
        assertNull(
            PowerChartPresenter.fullWindow(
                listOf(PowerChartSample(5, 200), PowerChartSample(5, 220)),
            ),
        )
    }

    @Test
    fun shortWindowUsesMinuteSecondTicks() {
        val samples = rideSamples(durationSeconds = 300, stepSeconds = 5)

        val presentation = PowerChartPresenter.presentation(
            samples,
            PowerChartWindow(0, 300),
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
            PowerChartSample(0, 200),
            PowerChartSample(5 * 3_600, 210),
        )

        val presentation = PowerChartPresenter.presentation(
            samples,
            PowerChartWindow(0, 5 * 3_600),
        )

        assertEquals(
            listOf("0:00", "1:00", "2:00", "3:00", "4:00", "5:00"),
            presentation.timeTicks.map { it.label },
        )
    }

    @Test
    fun zoomedWindowBeyondAnHourKeepsSecondsWhenStepIsFine() {
        val samples = listOf(
            PowerChartSample(0, 200),
            PowerChartSample(2 * 3_600, 210),
        )

        val presentation = PowerChartPresenter.presentation(
            samples,
            PowerChartWindow(startSeconds = 3_600, endSeconds = 3_660),
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
            val presentation = PowerChartPresenter.presentation(
                listOf(PowerChartSample(0, 100), PowerChartSample(duration, 100)),
                PowerChartWindow(0, duration),
            )
            val count = presentation.timeTicks.size
            assertTrue(count in 2..7, "duration $duration produced $count ticks")
        }
    }

    @Test
    fun powerAxisStartsAtZeroWithPaddedRoundedMaximum() {
        val samples = listOf(
            PowerChartSample(0, 180),
            PowerChartSample(10, 487),
            PowerChartSample(20, 230),
        )

        val presentation = PowerChartPresenter.presentation(samples, PowerChartWindow(0, 20))

        assertEquals(0, presentation.powerTicks.first().powerWatts)
        assertTrue(presentation.axisMaxWatts > 487, "axis must leave headroom above the max")
        assertEquals(presentation.axisMaxWatts, presentation.powerTicks.last().powerWatts)
        val step = presentation.powerTicks[1].powerWatts - presentation.powerTicks[0].powerWatts
        assertEquals(0, presentation.axisMaxWatts % step)
        assertTrue(presentation.powerTicks.size in 3..7)
    }

    @Test
    fun summaryDescribesOnlyTheVisibleWindow() {
        val samples = listOf(
            PowerChartSample(0, 100),
            PowerChartSample(10, 200),
            PowerChartSample(20, 300),
            PowerChartSample(30, 900),
        )

        val presentation = PowerChartPresenter.presentation(samples, PowerChartWindow(0, 20))

        assertEquals(300, presentation.maxPowerWatts)
        assertEquals(200, presentation.averagePowerWatts)
    }

    @Test
    fun largeTimestampGapBreaksTheLine() {
        val samples = listOf(
            PowerChartSample(0, 200),
            PowerChartSample(30, 210),
            PowerChartSample(60, 220),
            // 10-minute pause.
            PowerChartSample(660, 230),
            PowerChartSample(690, 240),
        )

        val presentation = PowerChartPresenter.presentation(samples, PowerChartWindow(0, 690))

        assertEquals(2, presentation.segments.size)
        assertEquals(listOf<Long>(0, 30, 60), presentation.segments[0].map { it.elapsedSeconds })
        assertEquals(listOf<Long>(660, 690), presentation.segments[1].map { it.elapsedSeconds })
    }

    @Test
    fun gapAtOrBelowThresholdDoesNotBreakTheLine() {
        val samples = listOf(
            PowerChartSample(0, 200),
            PowerChartSample(PowerChartPresenter.GAP_THRESHOLD_SECONDS, 210),
            PowerChartSample(PowerChartPresenter.GAP_THRESHOLD_SECONDS + 30, 220),
        )

        val presentation = PowerChartPresenter.presentation(
            samples,
            PowerChartWindow(0, PowerChartPresenter.GAP_THRESHOLD_SECONDS + 30),
        )

        assertEquals(1, presentation.segments.size)
    }

    @Test
    fun duplicateTimestampsAreAveragedIntoOneSample() {
        val samples = listOf(
            PowerChartSample(0, 100),
            PowerChartSample(10, 200),
            PowerChartSample(10, 300),
            PowerChartSample(20, 400),
        )

        val presentation = PowerChartPresenter.presentation(samples, PowerChartWindow(0, 20))

        assertEquals(
            listOf(
                PowerChartSample(0, 100),
                PowerChartSample(10, 250),
                PowerChartSample(20, 400),
            ),
            presentation.segments.single(),
        )
        assertEquals(250, presentation.averagePowerWatts)
    }

    @Test
    fun downsamplingBoundsPointCountAndPreservesExtremes() {
        val samples = (0 until 10_000).map { index ->
            val power = when (index) {
                4_242 -> 950 // lone peak
                7_777 -> 5 // lone valley
                else -> 200 + (index % 40)
            }
            PowerChartSample(index.toLong(), power)
        }

        val presentation = PowerChartPresenter.presentation(
            samples,
            PowerChartWindow(0, 9_999),
            maxRenderPoints = 500,
        )

        val rendered = presentation.segments.single()
        assertTrue(rendered.size <= 500, "rendered ${rendered.size} points")
        assertTrue(rendered.any { it.powerWatts == 950 }, "peak must survive downsampling")
        assertTrue(rendered.any { it.powerWatts == 5 }, "valley must survive downsampling")
        assertEquals(0, rendered.first().elapsedSeconds)
        assertEquals(9_999, rendered.last().elapsedSeconds)
        assertEquals(950, presentation.maxPowerWatts)
    }

    @Test
    fun zoomedWindowKeepsEdgeNeighboursSoTheLineReachesTheViewport() {
        val samples = rideSamples(durationSeconds = 1_000, stepSeconds = 10)

        val presentation = PowerChartPresenter.presentation(
            samples,
            PowerChartWindow(startSeconds = 305, endSeconds = 505),
        )

        val rendered = presentation.segments.single()
        assertEquals(300, rendered.first().elapsedSeconds)
        assertEquals(510, rendered.last().elapsedSeconds)
    }

    @Test
    fun zoomInKeepsFocusAndRespectsMinimumWindow() {
        val samples = rideSamples(durationSeconds = 3_600, stepSeconds = 10)
        val full = PowerChartWindow(0, 3_600)

        val zoomed = PowerChartPresenter.zoomed(samples, full, factor = 2.0, focusSeconds = 1_800)
        assertEquals(1_800, zoomed.durationSeconds)
        assertEquals(900, zoomed.startSeconds)

        var window = full
        repeat(20) {
            window = PowerChartPresenter.zoomed(samples, window, factor = 2.0, focusSeconds = 1_800)
        }
        assertEquals(PowerChartPresenter.MIN_WINDOW_SECONDS, window.durationSeconds)
    }

    @Test
    fun zoomOutIsClampedToTheFullSeries() {
        val samples = rideSamples(durationSeconds = 600, stepSeconds = 10)
        val window = PowerChartWindow(100, 400)

        val zoomedOut = PowerChartPresenter.zoomed(samples, window, factor = 0.01, focusSeconds = 250)

        assertEquals(PowerChartWindow(0, 600), zoomedOut)
    }

    @Test
    fun panIsClampedToTheFullSeries() {
        val samples = rideSamples(durationSeconds = 600, stepSeconds = 10)
        val window = PowerChartWindow(100, 200)

        assertEquals(
            PowerChartWindow(150, 250),
            PowerChartPresenter.panned(samples, window, deltaSeconds = 50),
        )
        assertEquals(
            PowerChartWindow(500, 600),
            PowerChartPresenter.panned(samples, window, deltaSeconds = 10_000),
        )
        assertEquals(
            PowerChartWindow(0, 100),
            PowerChartPresenter.panned(samples, window, deltaSeconds = -10_000),
        )
    }

    @Test
    fun nearestSampleSnapsToTheClosestTimestamp() {
        val samples = listOf(
            PowerChartSample(0, 100),
            PowerChartSample(10, 200),
            PowerChartSample(30, 300),
        )

        assertEquals(PowerChartSample(0, 100), PowerChartPresenter.nearestSample(samples, -5))
        assertEquals(PowerChartSample(10, 200), PowerChartPresenter.nearestSample(samples, 14))
        assertEquals(PowerChartSample(30, 300), PowerChartPresenter.nearestSample(samples, 21))
        assertEquals(PowerChartSample(30, 300), PowerChartPresenter.nearestSample(samples, 999))
        assertNull(PowerChartPresenter.nearestSample(emptyList(), 0))
    }

    @Test
    fun formatsElapsedTimeWithAdaptiveUnits() {
        assertEquals("00:07", PowerChartPresenter.formatElapsed(7))
        assertEquals("05:30", PowerChartPresenter.formatElapsed(330))
        assertEquals("1:00:00", PowerChartPresenter.formatElapsed(3_600))
        assertEquals("12:34:56", PowerChartPresenter.formatElapsed(12 * 3_600 + 34 * 60 + 56))
    }

    private fun rideSamples(durationSeconds: Long, stepSeconds: Long): List<PowerChartSample> =
        (0..durationSeconds step stepSeconds).map { elapsed ->
            PowerChartSample(elapsed, 200 + (elapsed % 50).toInt())
        }
}
