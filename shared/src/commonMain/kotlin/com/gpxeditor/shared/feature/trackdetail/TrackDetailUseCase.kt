package com.gpxeditor.shared.feature.trackdetail

import com.gpxeditor.shared.data.activity.ActivityDocumentJson
import com.gpxeditor.shared.data.activity.ActivityGpxMapper
import com.gpxeditor.shared.data.gpx.GpxParseError
import com.gpxeditor.shared.data.gpx.GpxParseResult
import com.gpxeditor.shared.data.gpx.GpxParser
import com.gpxeditor.shared.domain.activity.ActivityDocument
import com.gpxeditor.shared.domain.imported.ImportedTrack
import com.gpxeditor.shared.domain.imported.ports.ActivityTrackFileStorage

class TrackDetailUseCase(
    private val fileStorage: ActivityTrackFileStorage,
) {
    suspend operator fun invoke(track: ImportedTrack): TrackDetailResult {
        val content = fileStorage.read(track.storageKey)
        val document = when (val result = parseStoredDocument(content)) {
            is StoredDocumentParseResult.Failure -> return TrackDetailResult.Failure(result.error)
            is StoredDocumentParseResult.Success -> result.document
        }

        return TrackDetailResult.Success(
            TrackDetail(
                importedTrack = track,
                document = document,
                summary = TrackSummaryCalculator.summaryFor(document),
                segments = TrackSummaryCalculator.segmentSummariesFor(document),
                warnings = TrackSummaryCalculator.warningsFor(document),
                powerSamples = PowerChartCalculator.samplesFor(document),
            ),
        )
    }

    private fun parseStoredDocument(content: String): StoredDocumentParseResult {
        val trimmedContent = content.trimStart()
        if (trimmedContent.startsWith("<")) {
            return when (val parseResult = GpxParser.parse(content)) {
                is GpxParseResult.Failure -> StoredDocumentParseResult.Failure(parseResult.error)
                is GpxParseResult.Success -> StoredDocumentParseResult.Success(
                    ActivityGpxMapper.fromGpxDocument(parseResult.document),
                )
            }
        }

        return when (val parseResult = ActivityDocumentJson.parse(content)) {
            is com.gpxeditor.shared.data.activity.ActivityJsonParseResult.Failure -> {
                StoredDocumentParseResult.Failure(GpxParseError(parseResult.message))
            }
            is com.gpxeditor.shared.data.activity.ActivityJsonParseResult.Success -> {
                StoredDocumentParseResult.Success(parseResult.document)
            }
        }
    }
}

private sealed interface StoredDocumentParseResult {
    data class Success(val document: ActivityDocument) : StoredDocumentParseResult
    data class Failure(val error: GpxParseError) : StoredDocumentParseResult
}

data class TrackDetail(
    val importedTrack: ImportedTrack,
    val document: ActivityDocument,
    val summary: TrackSummary,
    val segments: List<TrackSegmentSummary>,
    val warnings: List<String>,
    val powerSamples: List<PowerChartSample> = emptyList(),
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
    val averagePowerWatts: Double?,
    val maxPowerWatts: Int?,
    val averageCadenceRpm: Double?,
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
