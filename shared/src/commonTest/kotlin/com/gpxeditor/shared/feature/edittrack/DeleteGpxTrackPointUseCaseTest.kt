package com.gpxeditor.shared.feature.edittrack

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DeleteGpxTrackPointUseCaseTest {
    @Test
    fun deletesPointByGlobalIndex() {
        val result = DeleteGpxTrackPointUseCase()(
            DeleteGpxTrackPointRequest(
                document = documentWithPoints(),
                pointIndex = 1,
            ),
        )

        val success = assertIs<DeleteGpxTrackPointResult.Success>(result)
        val names = success.document.tracks.single().segments.single().points.map { it.name }

        assertEquals(1, success.deletedPointIndex)
        assertEquals(listOf("p0", "p2"), names)
        assertEquals(2, success.summary.pointCount)
    }

    @Test
    fun returnsFailureForInvalidInput() {
        val result = DeleteGpxTrackPointUseCase()(
            DeleteGpxTrackPointRequest(
                document = documentWithPoints(),
                pointIndex = -1,
            ),
        )

        val failure = assertIs<DeleteGpxTrackPointResult.Failure>(result)
        assertEquals("Point index must be zero or greater.", failure.error.message)
    }
}
