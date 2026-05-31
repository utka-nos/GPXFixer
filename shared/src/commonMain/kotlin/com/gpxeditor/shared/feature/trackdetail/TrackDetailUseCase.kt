package com.gpxeditor.shared.feature.trackdetail

import com.gpxeditor.shared.data.gpx.GpxParseError
import com.gpxeditor.shared.data.gpx.GpxParseResult
import com.gpxeditor.shared.data.gpx.GpxParser
import com.gpxeditor.shared.domain.gpx.GpxDocument
import com.gpxeditor.shared.domain.gpx.GpxTrackPoint
import com.gpxeditor.shared.domain.imported.ImportedTrack
import com.gpxeditor.shared.domain.imported.ports.GpxTrackFileStorage
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

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
                    summary = summaryFor(parseResult.document),
                    segments = segmentSummariesFor(parseResult.document),
                    warnings = warningsFor(parseResult.document),
                ),
            )
        }
    }

    private fun summaryFor(document: GpxDocument): TrackSummary {
        val segments = document.tracks.flatMap { it.segments }
        val points = segments.flatMap { it.points }
        val elevations = points.mapNotNull { it.elevationMeters }
        val times = points.mapNotNull { it.time?.takeIf(String::isNotBlank) }

        return TrackSummary(
            trackCount = document.tracks.size,
            segmentCount = segments.size,
            pointCount = points.size,
            distanceMeters = segments.sumOf { segmentDistanceMeters(it.points) },
            totalAscentMeters = elevationGain(points, onlyPositive = true),
            totalDescentMeters = elevationGain(points, onlyPositive = false),
            minElevationMeters = elevations.minOrNull(),
            maxElevationMeters = elevations.maxOrNull(),
            startTime = times.firstOrNull(),
            endTime = times.lastOrNull(),
            startCoordinate = points.firstOrNull()?.toCoordinate(),
            endCoordinate = points.lastOrNull()?.toCoordinate(),
        )
    }

    private fun segmentSummariesFor(document: GpxDocument): List<TrackSegmentSummary> {
        return document.tracks
            .flatMap { it.segments }
            .mapIndexed { index, segment ->
                TrackSegmentSummary(
                    index = index + 1,
                    pointCount = segment.points.size,
                    distanceMeters = segmentDistanceMeters(segment.points),
                    startCoordinate = segment.points.firstOrNull()?.toCoordinate(),
                    endCoordinate = segment.points.lastOrNull()?.toCoordinate(),
                    startTime = segment.points.firstNotNullOfOrNull { it.time?.takeIf(String::isNotBlank) },
                    endTime = segment.points.asReversed().firstNotNullOfOrNull { it.time?.takeIf(String::isNotBlank) },
                )
            }
    }

    private fun warningsFor(document: GpxDocument): List<String> {
        val segments = document.tracks.flatMap { it.segments }
        val points = segments.flatMap { it.points }
        val warnings = mutableListOf<String>()

        if (document.tracks.isEmpty()) {
            warnings += "No tracks found in GPX file."
        }
        if (points.isEmpty()) {
            warnings += "No track points found."
        }
        if (segments.any { it.points.isEmpty() }) {
            warnings += "Some segments are empty."
        }
        if (points.isNotEmpty() && points.none { it.elevationMeters != null }) {
            warnings += "No elevation data found."
        }
        if (points.isNotEmpty() && points.none { !it.time.isNullOrBlank() }) {
            warnings += "No timestamp data found."
        }

        return warnings
    }

    private fun segmentDistanceMeters(points: List<GpxTrackPoint>): Double {
        return points.zipWithNext().sumOf { (from, to) ->
            haversineMeters(
                from.latitude,
                from.longitude,
                to.latitude,
                to.longitude,
            )
        }
    }

    private fun elevationGain(
        points: List<GpxTrackPoint>,
        onlyPositive: Boolean,
    ): Double? {
        if (points.none { it.elevationMeters != null }) return null

        return points.zipWithNext().sumOf { (from, to) ->
            val fromElevation = from.elevationMeters ?: return@sumOf 0.0
            val toElevation = to.elevationMeters ?: return@sumOf 0.0
            val delta = toElevation - fromElevation

            when {
                onlyPositive && delta > 0.0 -> delta
                !onlyPositive && delta < 0.0 -> -delta
                else -> 0.0
            }
        }
    }

    private fun haversineMeters(
        fromLatitude: Double,
        fromLongitude: Double,
        toLatitude: Double,
        toLongitude: Double,
    ): Double {
        val earthRadiusMeters = 6_371_000.0
        val fromLatitudeRadians = fromLatitude.toRadians()
        val toLatitudeRadians = toLatitude.toRadians()
        val latitudeDelta = (toLatitude - fromLatitude).toRadians()
        val longitudeDelta = (toLongitude - fromLongitude).toRadians()

        val a = sin(latitudeDelta / 2.0) * sin(latitudeDelta / 2.0) +
            cos(fromLatitudeRadians) * cos(toLatitudeRadians) *
            sin(longitudeDelta / 2.0) * sin(longitudeDelta / 2.0)
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))

        return earthRadiusMeters * c
    }

    private fun Double.toRadians(): Double = this * PI / 180.0

    private fun GpxTrackPoint.toCoordinate(): GpxCoordinate {
        return GpxCoordinate(latitude = latitude, longitude = longitude)
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
