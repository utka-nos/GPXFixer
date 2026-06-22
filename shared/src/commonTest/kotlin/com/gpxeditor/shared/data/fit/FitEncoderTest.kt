package com.gpxeditor.shared.data.fit

import com.gpxeditor.shared.domain.activity.ActivityDocument
import com.gpxeditor.shared.domain.activity.ActivityPoint
import com.gpxeditor.shared.domain.activity.ActivitySegment
import com.gpxeditor.shared.domain.activity.ActivityTrack
import com.gpxeditor.shared.domain.fit.FitDecodeResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class FitEncoderTest {
    @Test
    fun encodedDocumentRoundTripsThroughDecoder() {
        val document = ActivityDocument(
            tracks = listOf(
                ActivityTrack(
                    segments = listOf(
                        ActivitySegment(
                            points = listOf(
                                ActivityPoint(
                                    time = "2020-01-01T00:00:00Z",
                                    latitude = 41.7151,
                                    longitude = 44.8271,
                                    elevationMeters = 512.4,
                                    heartRateBpm = 142,
                                    powerWatts = 210,
                                    cadenceRpm = 88,
                                    distanceMeters = 0.0,
                                    speedMetersPerSecond = 5.5,
                                ),
                                ActivityPoint(
                                    time = "2020-01-01T00:00:01Z",
                                    latitude = 41.7152,
                                    longitude = 44.8272,
                                    elevationMeters = 513.0,
                                    heartRateBpm = 143,
                                    powerWatts = 215,
                                    cadenceRpm = 89,
                                    distanceMeters = 5.5,
                                    speedMetersPerSecond = 5.6,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val bytes = FitEncoder.encode(document)
        val result = FitActivityDecoder().decode(bytes)

        val success = assertIs<FitDecodeResult.Success>(result)
        val points = success.document.tracks.single().segments.single().points
        assertEquals(2, points.size)

        val first = points.first()
        assertEquals("2020-01-01T00:00:00Z", first.time)
        assertEquals(41.7151, first.latitude!!, absoluteTolerance = 1e-4)
        assertEquals(44.8271, first.longitude!!, absoluteTolerance = 1e-4)
        assertEquals(512.4, first.elevationMeters!!, absoluteTolerance = 0.2)
        assertEquals(142, first.heartRateBpm)
        assertEquals(210, first.powerWatts)
        assertEquals(88, first.cadenceRpm)
        assertEquals(5.5, first.speedMetersPerSecond!!, absoluteTolerance = 1e-3)

        val second = points.last()
        assertEquals("2020-01-01T00:00:01Z", second.time)
        assertEquals(5.5, second.distanceMeters!!, absoluteTolerance = 1e-2)
    }

    @Test
    fun missingFieldsStayNullAfterRoundTrip() {
        val document = ActivityDocument(
            tracks = listOf(
                ActivityTrack(
                    segments = listOf(
                        ActivitySegment(
                            points = listOf(
                                ActivityPoint(
                                    time = "2020-01-01T00:00:00Z",
                                    latitude = 10.0,
                                    longitude = 20.0,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val bytes = FitEncoder.encode(document)
        val result = FitActivityDecoder().decode(bytes)

        val success = assertIs<FitDecodeResult.Success>(result)
        val point = success.document.tracks.single().segments.single().points.single()
        assertEquals(10.0, point.latitude!!, absoluteTolerance = 1e-4)
        assertNull(point.heartRateBpm)
        assertNull(point.powerWatts)
        assertNull(point.elevationMeters)
    }
}
