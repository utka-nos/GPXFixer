package com.gpxeditor.shared.feature.edittrack

import com.gpxeditor.shared.domain.gpx.GpxDocument
import com.gpxeditor.shared.domain.gpx.GpxTrack
import com.gpxeditor.shared.domain.gpx.GpxTrackPoint
import com.gpxeditor.shared.domain.gpx.GpxTrackSegment
import com.gpxeditor.shared.feature.trackdetail.TrackSummary
import com.gpxeditor.shared.feature.trackdetail.TrackSummaryCalculator

class DeleteGpxTrackPointUseCase {
    operator fun invoke(request: DeleteGpxTrackPointRequest): DeleteGpxTrackPointResult {
        val totalPointCount = request.document.pointCount
        val validationError = validationErrorFor(request.pointIndex, totalPointCount)

        if (validationError != null) {
            return DeleteGpxTrackPointResult.Failure(validationError)
        }

        val updatedDocument = request.document.copy(
            tracks = deletePointAt(
                tracks = request.document.tracks,
                pointIndexToDelete = request.pointIndex,
            ),
        )

        return DeleteGpxTrackPointResult.Success(
            document = updatedDocument,
            summary = TrackSummaryCalculator.summaryFor(updatedDocument),
            deletedPointIndex = request.pointIndex,
        )
    }

    private fun validationErrorFor(
        pointIndex: Int,
        totalPointCount: Int,
    ): DeleteGpxTrackPointError? {
        return when {
            totalPointCount == 0 -> DeleteGpxTrackPointError("Cannot delete a point from an empty GPX document.")
            pointIndex < 0 -> DeleteGpxTrackPointError("Point index must be zero or greater.")
            pointIndex >= totalPointCount -> DeleteGpxTrackPointError("Point index is outside the GPX document point range.")
            else -> null
        }
    }

    private fun deletePointAt(
        tracks: List<GpxTrack>,
        pointIndexToDelete: Int,
    ): List<GpxTrack> {
        var currentPointIndex = 0

        return tracks.mapNotNull { track ->
            val updatedSegments = track.segments.mapNotNull { segment ->
                val updatedPoints = segment.points.filter {
                    val pointIndex = currentPointIndex
                    currentPointIndex += 1
                    pointIndex != pointIndexToDelete
                }

                segment.withPointsOrNull(updatedPoints)
            }

            if (updatedSegments.isEmpty()) {
                null
            } else {
                track.copy(segments = updatedSegments)
            }
        }
    }

    private fun GpxTrackSegment.withPointsOrNull(points: List<GpxTrackPoint>): GpxTrackSegment? {
        return if (points.isEmpty()) {
            null
        } else {
            copy(points = points)
        }
    }
}

data class DeleteGpxTrackPointRequest(
    val document: GpxDocument,
    val pointIndex: Int,
)

sealed interface DeleteGpxTrackPointResult {
    data class Success(
        val document: GpxDocument,
        val summary: TrackSummary,
        val deletedPointIndex: Int,
    ) : DeleteGpxTrackPointResult

    data class Failure(
        val error: DeleteGpxTrackPointError,
    ) : DeleteGpxTrackPointResult
}

data class DeleteGpxTrackPointError(
    val message: String,
)
