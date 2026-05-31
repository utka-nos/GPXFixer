package com.gpxeditor.shared.feature.trackdetail

import com.gpxeditor.shared.domain.gpx.GpxDocument
import com.gpxeditor.shared.domain.gpx.GpxTrackPoint
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object TrackSummaryCalculator {
    fun summaryFor(document: GpxDocument): TrackSummary {
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

    fun segmentSummariesFor(document: GpxDocument): List<TrackSegmentSummary> {
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

    fun warningsFor(document: GpxDocument): List<String> {
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
