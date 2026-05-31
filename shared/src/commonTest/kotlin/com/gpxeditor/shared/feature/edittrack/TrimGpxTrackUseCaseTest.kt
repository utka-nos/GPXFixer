package com.gpxeditor.shared.feature.edittrack

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TrimGpxTrackUseCaseTest {
    @Test
    fun trimsPointRangeAndKeepsSensorData() {
        val result = TrimGpxTrackUseCase()(
            TrimGpxTrackRequest(
                document = documentWithPoints(),
                startPointIndex = 1,
                endPointIndexInclusive = 2,
            ),
        )

        val success = assertIs<TrimGpxTrackResult.Success>(result)
        val points = success.document.tracks.single().segments.single().points

        assertEquals(1, success.removedPointCount)
        assertEquals(listOf("p1", "p2"), points.map { it.name })
        assertEquals(listOf(145, 150), points.map { it.heartRateBpm })
        assertEquals(listOf(220, 240), points.map { it.powerWatts })
        assertEquals(2, success.summary.pointCount)
    }

    @Test
    fun returnsFailureForInvalidInput() {
        val result = TrimGpxTrackUseCase()(
            TrimGpxTrackRequest(
                document = documentWithPoints(),
                startPointIndex = 2,
                endPointIndexInclusive = 1,
            ),
        )

        val failure = assertIs<TrimGpxTrackResult.Failure>(result)
        assertEquals("End point index must be greater than or equal to start point index.", failure.error.message)
    }
}
