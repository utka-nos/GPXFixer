package com.gpxeditor.shared.feature.trackdetail

import com.gpxeditor.shared.domain.activity.ActivityDocument
import com.gpxeditor.shared.domain.activity.ActivityPoint
import com.gpxeditor.shared.domain.activity.ActivitySegment
import com.gpxeditor.shared.domain.activity.ActivityTrack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrackChartCalculatorTest {
    @Test
    fun buildsElapsedTimeSeriesFromTimedPowerPoints() {
        val document = documentWithPoints(
            ActivityPoint(time = "2026-05-31T08:00:00Z", powerWatts = 200),
            ActivityPoint(time = "2026-05-31T08:00:05Z", powerWatts = 250),
            ActivityPoint(time = "2026-05-31T08:01:00Z", powerWatts = 180),
        )

        val samples = TrackChartCalculator.powerSamplesFor(document)

        assertEquals(
            listOf(
                TrackChartSample(elapsedSeconds = 0, value = 200),
                TrackChartSample(elapsedSeconds = 5, value = 250),
                TrackChartSample(elapsedSeconds = 60, value = 180),
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

        val samples = TrackChartCalculator.powerSamplesFor(document)

        assertEquals(
            listOf(
                TrackChartSample(elapsedSeconds = 0, value = 200),
                TrackChartSample(elapsedSeconds = 4, value = 220),
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

        val samples = TrackChartCalculator.powerSamplesFor(document)

        assertEquals(listOf(0L, 10L, 20L), samples.map { it.elapsedSeconds })
        assertEquals(listOf(100, 110, 120), samples.map { it.value })
        assertEquals(listOf(0, 1, 2), samples.map { it.segmentIndex })
    }

    @Test
    fun sortsSamplesByTimestamp() {
        val document = documentWithPoints(
            ActivityPoint(time = "2026-05-31T08:01:00Z", powerWatts = 180),
            ActivityPoint(time = "2026-05-31T08:00:00Z", powerWatts = 200),
            ActivityPoint(time = "2026-05-31T08:00:05Z", powerWatts = 250),
        )

        val samples = TrackChartCalculator.powerSamplesFor(document)

        assertEquals(
            listOf(
                TrackChartSample(elapsedSeconds = 0, value = 200),
                TrackChartSample(elapsedSeconds = 5, value = 250),
                TrackChartSample(elapsedSeconds = 60, value = 180),
            ),
            samples,
        )
    }

    @Test
    fun returnsEmptyListForSingleUsableSample() {
        val document = documentWithPoints(
            ActivityPoint(time = "2026-05-31T08:00:00Z", powerWatts = 200),
            ActivityPoint(time = "2026-05-31T08:00:01Z"),
        )

        assertTrue(TrackChartCalculator.powerSamplesFor(document).isEmpty())
    }

    @Test
    fun returnsEmptyListWhenNoPowerData() {
        val document = documentWithPoints(
            ActivityPoint(time = "2026-05-31T08:00:00Z"),
            ActivityPoint(time = "2026-05-31T08:00:01Z"),
        )

        assertTrue(TrackChartCalculator.powerSamplesFor(document).isEmpty())
    }

    @Test
    fun buildsElapsedTimeSeriesFromTimedHeartRatePoints() {
        val document = documentWithPoints(
            ActivityPoint(time = "2026-05-31T08:00:00Z", heartRateBpm = 120),
            ActivityPoint(time = "2026-05-31T08:00:05Z", heartRateBpm = 135),
            ActivityPoint(time = "2026-05-31T08:01:00Z", heartRateBpm = 150),
        )

        val samples = TrackChartCalculator.heartRateSamplesFor(document)

        assertEquals(
            listOf(
                TrackChartSample(elapsedSeconds = 0, value = 120),
                TrackChartSample(elapsedSeconds = 5, value = 135),
                TrackChartSample(elapsedSeconds = 60, value = 150),
            ),
            samples,
        )
    }

    @Test
    fun heartRateSeriesIgnoresPowerOnlyPoints() {
        val document = documentWithPoints(
            ActivityPoint(time = "2026-05-31T08:00:00Z", heartRateBpm = 120, powerWatts = 200),
            ActivityPoint(time = "2026-05-31T08:00:05Z", powerWatts = 250),
            ActivityPoint(time = "2026-05-31T08:00:10Z", heartRateBpm = 140),
        )

        val samples = TrackChartCalculator.heartRateSamplesFor(document)

        assertEquals(
            listOf(
                TrackChartSample(elapsedSeconds = 0, value = 120),
                TrackChartSample(elapsedSeconds = 10, value = 140),
            ),
            samples,
        )
    }

    @Test
    fun returnsEmptyListWhenNoHeartRateData() {
        val document = documentWithPoints(
            ActivityPoint(time = "2026-05-31T08:00:00Z", powerWatts = 200),
            ActivityPoint(time = "2026-05-31T08:00:01Z", powerWatts = 210),
        )

        assertTrue(TrackChartCalculator.heartRateSamplesFor(document).isEmpty())
    }

    private fun documentWithPoints(vararg points: ActivityPoint): ActivityDocument {
        return ActivityDocument(
            tracks = listOf(
                ActivityTrack(segments = listOf(ActivitySegment(points = points.toList()))),
            ),
        )
    }
}
