package com.gpxeditor.shared.feature.trackdetail

import com.gpxeditor.shared.domain.activity.ActivityDocument
import com.gpxeditor.shared.domain.activity.ActivityPoint
import com.gpxeditor.shared.domain.activity.ActivitySegment
import com.gpxeditor.shared.domain.activity.ActivityTrack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PowerChartCalculatorTest {
    @Test
    fun buildsElapsedTimeSeriesFromTimedPowerPoints() {
        val document = documentWithPoints(
            ActivityPoint(time = "2026-05-31T08:00:00Z", powerWatts = 200),
            ActivityPoint(time = "2026-05-31T08:00:05Z", powerWatts = 250),
            ActivityPoint(time = "2026-05-31T08:01:00Z", powerWatts = 180),
        )

        val samples = PowerChartCalculator.samplesFor(document)

        assertEquals(
            listOf(
                PowerChartSample(elapsedSeconds = 0, powerWatts = 200),
                PowerChartSample(elapsedSeconds = 5, powerWatts = 250),
                PowerChartSample(elapsedSeconds = 60, powerWatts = 180),
            ),
            samples,
        )
    }

    @Test
    fun skipsPointsWithoutPowerOrTime() {
        val document = documentWithPoints(
            ActivityPoint(time = "2026-05-31T08:00:00Z", powerWatts = 200),
            ActivityPoint(time = "2026-05-31T08:00:01Z"),
            ActivityPoint(powerWatts = 300),
            ActivityPoint(time = "not-a-timestamp", powerWatts = 300),
            ActivityPoint(time = "2026-05-31T08:00:04Z", powerWatts = 220),
        )

        val samples = PowerChartCalculator.samplesFor(document)

        assertEquals(
            listOf(
                PowerChartSample(elapsedSeconds = 0, powerWatts = 200),
                PowerChartSample(elapsedSeconds = 4, powerWatts = 220),
            ),
            samples,
        )
    }

    @Test
    fun spansSegmentsAndTracks() {
        val document = ActivityDocument(
            tracks = listOf(
                ActivityTrack(
                    segments = listOf(
                        ActivitySegment(
                            points = listOf(ActivityPoint(time = "2026-05-31T08:00:00Z", powerWatts = 100)),
                        ),
                        ActivitySegment(
                            points = listOf(ActivityPoint(time = "2026-05-31T08:00:10Z", powerWatts = 110)),
                        ),
                    ),
                ),
                ActivityTrack(
                    segments = listOf(
                        ActivitySegment(
                            points = listOf(ActivityPoint(time = "2026-05-31T08:00:20Z", powerWatts = 120)),
                        ),
                    ),
                ),
            ),
        )

        val samples = PowerChartCalculator.samplesFor(document)

        assertEquals(listOf(0L, 10L, 20L), samples.map { it.elapsedSeconds })
        assertEquals(listOf(100, 110, 120), samples.map { it.powerWatts })
    }

    @Test
    fun returnsEmptyListForSingleUsableSample() {
        val document = documentWithPoints(
            ActivityPoint(time = "2026-05-31T08:00:00Z", powerWatts = 200),
            ActivityPoint(time = "2026-05-31T08:00:01Z"),
        )

        assertTrue(PowerChartCalculator.samplesFor(document).isEmpty())
    }

    @Test
    fun returnsEmptyListWhenNoPowerData() {
        val document = documentWithPoints(
            ActivityPoint(time = "2026-05-31T08:00:00Z"),
            ActivityPoint(time = "2026-05-31T08:00:01Z"),
        )

        assertTrue(PowerChartCalculator.samplesFor(document).isEmpty())
    }

    private fun documentWithPoints(vararg points: ActivityPoint): ActivityDocument {
        return ActivityDocument(
            tracks = listOf(
                ActivityTrack(segments = listOf(ActivitySegment(points = points.toList()))),
            ),
        )
    }
}
