package com.gpxeditor.shared.feature.edittrack

import com.gpxeditor.shared.domain.gpx.GpxDocument
import com.gpxeditor.shared.domain.gpx.GpxTrack
import com.gpxeditor.shared.feature.trackdetail.TrackSummary
import com.gpxeditor.shared.feature.trackdetail.TrackSummaryCalculator

class MoveGpxTrackPointUseCase {
    operator fun invoke(request: MoveGpxTrackPointRequest): MoveGpxTrackPointResult {
        val totalPointCount = request.document.pointCount
        val validationError = validationErrorFor(request, totalPointCount)

        if (validationError != null) {
            return MoveGpxTrackPointResult.Failure(validationError)
        }

        val updatedDocument = request.document.copy(
            tracks = movePointAt(
                tracks = request.document.tracks,
                pointIndexToMove = request.pointIndex,
                latitude = request.latitude,
                longitude = request.longitude,
            ),
        )

        return MoveGpxTrackPointResult.Success(
            document = updatedDocument,
            summary = TrackSummaryCalculator.summaryFor(updatedDocument),
            movedPointIndex = request.pointIndex,
        )
    }

    private fun validationErrorFor(
        request: MoveGpxTrackPointRequest,
        totalPointCount: Int,
    ): MoveGpxTrackPointError? {
        return when {
            totalPointCount == 0 -> MoveGpxTrackPointError("Cannot move a point in an empty GPX document.")
            request.pointIndex < 0 -> MoveGpxTrackPointError("Point index must be zero or greater.")
            request.pointIndex >= totalPointCount -> MoveGpxTrackPointError("Point index is outside the GPX document point range.")
            request.latitude !in -90.0..90.0 -> MoveGpxTrackPointError("Latitude must be between -90 and 90 degrees.")
            request.longitude !in -180.0..180.0 -> MoveGpxTrackPointError("Longitude must be between -180 and 180 degrees.")
            else -> null
        }
    }

    private fun movePointAt(
        tracks: List<GpxTrack>,
        pointIndexToMove: Int,
        latitude: Double,
        longitude: Double,
    ): List<GpxTrack> {
        var currentPointIndex = 0

        return tracks.map { track ->
            track.copy(
                segments = track.segments.map { segment ->
                    segment.copy(
                        points = segment.points.map { point ->
                            val pointIndex = currentPointIndex
                            currentPointIndex += 1

                            if (pointIndex == pointIndexToMove) {
                                point.copy(
                                    latitude = latitude,
                                    longitude = longitude,
                                )
                            } else {
                                point
                            }
                        },
                    )
                },
            )
        }
    }
}

data class MoveGpxTrackPointRequest(
    val document: GpxDocument,
    val pointIndex: Int,
    val latitude: Double,
    val longitude: Double,
)

sealed interface MoveGpxTrackPointResult {
    data class Success(
        val document: GpxDocument,
        val summary: TrackSummary,
        val movedPointIndex: Int,
    ) : MoveGpxTrackPointResult

    data class Failure(
        val error: MoveGpxTrackPointError,
    ) : MoveGpxTrackPointResult
}

data class MoveGpxTrackPointError(
    val message: String,
)
