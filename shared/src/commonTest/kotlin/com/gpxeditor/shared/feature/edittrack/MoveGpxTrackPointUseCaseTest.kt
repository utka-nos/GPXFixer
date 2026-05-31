package com.gpxeditor.shared.feature.edittrack

import com.gpxeditor.shared.domain.activity.ActivityDocument
import com.gpxeditor.shared.domain.activity.ActivityPoint
import com.gpxeditor.shared.domain.activity.ActivitySegment
import com.gpxeditor.shared.domain.activity.ActivityTrack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MoveGpxTrackPointUseCaseTest {
    @Test
    fun movesPointByGlobalIndexAndKeepsSensorData() {
        val result = MoveGpxTrackPointUseCase()(
            MoveGpxTrackPointRequest(
                document = documentWithPoints(),
                pointIndex = 1,
                latitude = 41.7151,
                longitude = 44.8271,
            ),
        )

        val success = assertIs<MoveGpxTrackPointResult.Success>(result)
        val movedPoint = success.document.tracks.single().segments.single().points[1]

        assertEquals(1, success.movedPointIndex)
        assertEquals(41.7151, movedPoint.latitude)
        assertEquals(44.8271, movedPoint.longitude)
        assertEquals(145, movedPoint.heartRateBpm)
        assertEquals(220, movedPoint.powerWatts)
        assertEquals(3, success.summary.pointCount)
    }

    @Test
    fun returnsFailureForInvalidInput() {
        val result = MoveGpxTrackPointUseCase()(
            MoveGpxTrackPointRequest(
                document = documentWithPoints(),
                pointIndex = 3,
                latitude = 0.0,
                longitude = 0.0,
            ),
        )

        val failure = assertIs<MoveGpxTrackPointResult.Failure>(result)
        assertEquals("Point index is outside the activity document point range.", failure.error.message)
    }
}

internal fun documentWithPoints(): ActivityDocument {
    return ActivityDocument(
        tracks = listOf(
            ActivityTrack(
                name = "Ride",
                segments = listOf(
                    ActivitySegment(
                        points = listOf(
                            point("p0", longitude = 44.0, heartRate = 140, power = 200),
                            point("p1", longitude = 44.001, heartRate = 145, power = 220),
                            point("p2", longitude = 44.002, heartRate = 150, power = 240),
                        ),
                    ),
                ),
            ),
        ),
    )
}

internal fun point(
    name: String,
    longitude: Double,
    heartRate: Int,
    power: Int,
): ActivityPoint {
    return ActivityPoint(
        latitude = 41.0,
        longitude = longitude,
        elevationMeters = 500.0,
        time = "2026-05-31T08:00:00Z",
        name = name,
        heartRateBpm = heartRate,
        powerWatts = power,
    )
}
