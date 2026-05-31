package com.gpxeditor.shared.feature.trackdetail

import com.gpxeditor.shared.data.gpx.GpxParseError
import com.gpxeditor.shared.data.gpx.GpxParseResult
import com.gpxeditor.shared.data.gpx.GpxParser
import com.gpxeditor.shared.domain.gpx.GpxDocument
import com.gpxeditor.shared.domain.imported.ImportedTrack
import com.gpxeditor.shared.domain.imported.ports.GpxTrackFileStorage

class TrackDetailUseCase(
    private val fileStorage: GpxTrackFileStorage,
) {
    suspend operator fun invoke(track: ImportedTrack): TrackDetailResult {
        val content = fileStorage.read(track.storageKey)
        return when (val parseResult = GpxParser.parse(content)) {
            is GpxParseResult.Failure -> TrackDetailResult.Failure(parseResult.error)
            is GpxParseResult.Success -> TrackDetailResult.Success(
                TrackDetail(
                    importedTrack = track,
                    document = parseResult.document,
                    summary = TrackSummaryCalculator.summaryFor(parseResult.document),
                    segments = TrackSummaryCalculator.segmentSummariesFor(parseResult.document),
                    warnings = TrackSummaryCalculator.warningsFor(parseResult.document),
                ),
            )
        }
    }
}

data class TrackDetail(
    val importedTrack: ImportedTrack,
    val document: GpxDocument,
    val summary: TrackSummary,
    val segments: List<TrackSegmentSummary>,
    val warnings: List<String>,
)

data class TrackSummary(
    val trackCount: Int,
    val segmentCount: Int,
    val pointCount: Int,
    val distanceMeters: Double,
    val totalAscentMeters: Double?,
    val totalDescentMeters: Double?,
    val minElevationMeters: Double?,
    val maxElevationMeters: Double?,
    val startTime: String?,
    val endTime: String?,
    val startCoordinate: GpxCoordinate?,
    val endCoordinate: GpxCoordinate?,
)

data class TrackSegmentSummary(
    val index: Int,
    val pointCount: Int,
    val distanceMeters: Double,
    val startCoordinate: GpxCoordinate?,
    val endCoordinate: GpxCoordinate?,
    val startTime: String?,
    val endTime: String?,
)

data class GpxCoordinate(
    val latitude: Double,
    val longitude: Double,
)

sealed interface TrackDetailResult {
    data class Success(val detail: TrackDetail) : TrackDetailResult
    data class Failure(val error: GpxParseError) : TrackDetailResult
}
