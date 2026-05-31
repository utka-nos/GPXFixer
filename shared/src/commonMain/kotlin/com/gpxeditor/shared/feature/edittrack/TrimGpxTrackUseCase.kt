package com.gpxeditor.shared.feature.edittrack

import com.gpxeditor.shared.domain.gpx.GpxDocument
import com.gpxeditor.shared.domain.gpx.GpxTrack
import com.gpxeditor.shared.domain.gpx.GpxTrackPoint
import com.gpxeditor.shared.domain.gpx.GpxTrackSegment
import com.gpxeditor.shared.feature.trackdetail.TrackSummary
import com.gpxeditor.shared.feature.trackdetail.TrackSummaryCalculator

class TrimGpxTrackUseCase {
    operator fun invoke(request: TrimGpxTrackRequest): TrimGpxTrackResult {
        val totalPointCount = request.document.pointCount
        val validationError = validationErrorFor(request, totalPointCount)

        if (validationError != null) {
            return TrimGpxTrackResult.Failure(validationError)
        }

        val trimmedDocument = request.document.copy(
            tracks = trimTracks(
                tracks = request.document.tracks,
                startPointIndex = request.startPointIndex,
                endPointIndexInclusive = request.endPointIndexInclusive,
            ),
        )

        return TrimGpxTrackResult.Success(
            document = trimmedDocument,
            summary = TrackSummaryCalculator.summaryFor(trimmedDocument),
            removedPointCount = totalPointCount - trimmedDocument.pointCount,
        )
    }

    private fun validationErrorFor(
        request: TrimGpxTrackRequest,
        totalPointCount: Int,
    ): TrimGpxTrackError? {
        return when {
            totalPointCount == 0 -> TrimGpxTrackError("Cannot trim GPX document without track points.")
            request.startPointIndex < 0 -> TrimGpxTrackError("Start point index must be zero or greater.")
            request.endPointIndexInclusive < request.startPointIndex -> {
                TrimGpxTrackError("End point index must be greater than or equal to start point index.")
            }
            request.endPointIndexInclusive >= totalPointCount -> {
                TrimGpxTrackError("End point index is outside the GPX document point range.")
            }
            else -> null
        }
    }

    private fun trimTracks(
        tracks: List<GpxTrack>,
        startPointIndex: Int,
        endPointIndexInclusive: Int,
    ): List<GpxTrack> {
        var currentPointIndex = 0

        return tracks.mapNotNull { track ->
            val trimmedSegments = track.segments.mapNotNull { segment ->
                val trimmedPoints = segment.points.filter {
                    val pointIndex = currentPointIndex
                    currentPointIndex += 1
                    pointIndex in startPointIndex..endPointIndexInclusive
                }

                segment.withPointsOrNull(trimmedPoints)
            }

            if (trimmedSegments.isEmpty()) {
                null
            } else {
                track.copy(segments = trimmedSegments)
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

data class TrimGpxTrackRequest(
    val document: GpxDocument,
    val startPointIndex: Int,
    val endPointIndexInclusive: Int,
)

sealed interface TrimGpxTrackResult {
    data class Success(
        val document: GpxDocument,
        val summary: TrackSummary,
        val removedPointCount: Int,
    ) : TrimGpxTrackResult

    data class Failure(
        val error: TrimGpxTrackError,
    ) : TrimGpxTrackResult
}

data class TrimGpxTrackError(
    val message: String,
)
