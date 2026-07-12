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

    @Test
    fun buildsSpeedSeriesFromRecordedSpeeds() {
        val document = documentWithPoints(
            ActivityPoint(time = "2026-05-31T08:00:00Z", speedMetersPerSecond = 10.0),
            ActivityPoint(time = "2026-05-31T08:00:05Z", speedMetersPerSecond = 5.0),
            ActivityPoint(time = "2026-05-31T08:01:00Z", speedMetersPerSecond = 2.5),
        )

        val samples = TrackChartCalculator.speedSamplesFor(document)

        assertEquals(
            listOf(
                TrackChartSample(elapsedSeconds = 0, value = 36),
                TrackChartSample(elapsedSeconds = 5, value = 18),
                TrackChartSample(elapsedSeconds = 60, value = 9),
            ),
            samples,
        )
    }

    @Test
    fun derivesSpeedFromGpsPointsWhenNotRecorded() {
        // Consecutive points 0.001 degrees of latitude (~111.2 m) apart every
        // 10 seconds: ~11.12 m/s, which rounds to 40 km/h.
        val document = documentWithPoints(
            ActivityPoint(time = "2026-05-31T08:00:00Z", latitude = 55.000, longitude = 37.0),
            ActivityPoint(time = "2026-05-31T08:00:10Z", latitude = 55.001, longitude = 37.0),
            ActivityPoint(time = "2026-05-31T08:00:20Z", latitude = 55.002, longitude = 37.0),
        )

        val samples = TrackChartCalculator.speedSamplesFor(document)

        assertEquals(
            listOf(
                TrackChartSample(elapsedSeconds = 0, value = 40),
                TrackChartSample(elapsedSeconds = 10, value = 40),
            ),
            samples,
        )
    }

    @Test
    fun prefersRecordedSpeedOverDerivedSpeed() {
        val document = documentWithPoints(
            ActivityPoint(time = "2026-05-31T08:00:00Z", latitude = 55.000, longitude = 37.0),
            ActivityPoint(
                time = "2026-05-31T08:00:10Z",
                latitude = 55.001,
                longitude = 37.0,
                speedMetersPerSecond = 5.0,
            ),
            ActivityPoint(time = "2026-05-31T08:00:20Z", latitude = 55.002, longitude = 37.0),
        )

        val samples = TrackChartCalculator.speedSamplesFor(document)

        assertEquals(
            listOf(
                TrackChartSample(elapsedSeconds = 0, value = 18),
                TrackChartSample(elapsedSeconds = 10, value = 40),
            ),
            samples,
        )
    }

    @Test
    fun doesNotDeriveSpeedAcrossSegments() {
        val document = ActivityDocument(
            tracks = listOf(
                ActivityTrack(
                    segments = listOf(
                        ActivitySegment(
                            points = listOf(
                                ActivityPoint(time = "2026-05-31T08:00:00Z", latitude = 55.000, longitude = 37.0),
                                ActivityPoint(time = "2026-05-31T08:00:10Z", latitude = 55.001, longitude = 37.0),
                            ),
                        ),
                        ActivitySegment(
                            points = listOf(
                                ActivityPoint(time = "2026-05-31T08:10:00Z", latitude = 55.100, longitude = 37.0),
                                ActivityPoint(time = "2026-05-31T08:10:10Z", latitude = 55.101, longitude = 37.0),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val samples = TrackChartCalculator.speedSamplesFor(document)

        assertEquals(
            listOf(
                TrackChartSample(elapsedSeconds = 0, value = 40, segmentIndex = 0),
                TrackChartSample(elapsedSeconds = 600, value = 40, segmentIndex = 1),
            ),
            samples,
        )
    }

    @Test
    fun skipsDerivedSpeedWhenTimestampsDoNotAdvance() {
        // The second point repeats the first timestamp, so only the third
        // point yields a derived speed — one sample is not enough for a chart.
        val document = documentWithPoints(
            ActivityPoint(time = "2026-05-31T08:00:00Z", latitude = 55.000, longitude = 37.0),
            ActivityPoint(time = "2026-05-31T08:00:00Z", latitude = 55.001, longitude = 37.0),
            ActivityPoint(time = "2026-05-31T08:00:10Z", latitude = 55.002, longitude = 37.0),
        )

        assertTrue(TrackChartCalculator.speedSamplesFor(document).isEmpty())
    }

    @Test
    fun returnsEmptyListWhenNoSpeedOrGpsData() {
        val document = documentWithPoints(
            ActivityPoint(time = "2026-05-31T08:00:00Z", powerWatts = 200),
            ActivityPoint(time = "2026-05-31T08:00:01Z", powerWatts = 210),
        )

        assertTrue(TrackChartCalculator.speedSamplesFor(document).isEmpty())
    }

    private fun documentWithPoints(vararg points: ActivityPoint): ActivityDocument {
        return ActivityDocument(
            tracks = listOf(
                ActivityTrack(segments = listOf(ActivitySegment(points = points.toList()))),
            ),
        )
    }
}
