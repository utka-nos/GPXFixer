package com.gpxeditor.shared.feature.trackdetail

import com.gpxeditor.shared.core.geo.haversineMeters
import com.gpxeditor.shared.domain.activity.ActivityDocument
import com.gpxeditor.shared.domain.activity.ActivityPoint

object TrackSummaryCalculator {
    fun summaryFor(document: ActivityDocument): TrackSummary {
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
            startCoordinate = points.firstCoordinateOrNull(),
            endCoordinate = points.lastCoordinateOrNull(),
        )
    }

    fun segmentSummariesFor(document: ActivityDocument): List<TrackSegmentSummary> {
        return document.tracks
            .flatMap { it.segments }
            .mapIndexed { index, segment ->
                TrackSegmentSummary(
                    index = index + 1,
                    pointCount = segment.points.size,
                    distanceMeters = segmentDistanceMeters(segment.points),
                    startCoordinate = segment.points.firstCoordinateOrNull(),
                    endCoordinate = segment.points.lastCoordinateOrNull(),
                    startTime = segment.points.firstNotNullOfOrNull { it.time?.takeIf(String::isNotBlank) },
                    endTime = segment.points.asReversed().firstNotNullOfOrNull { it.time?.takeIf(String::isNotBlank) },
                )
            }
    }

    fun warningsFor(document: ActivityDocument): List<String> {
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

    private fun segmentDistanceMeters(points: List<ActivityPoint>): Double {
        return points.filter { it.latitude != null && it.longitude != null }.zipWithNext().sumOf { (from, to) ->
            haversineMeters(
                from.latitude ?: return@sumOf 0.0,
                from.longitude ?: return@sumOf 0.0,
                to.latitude ?: return@sumOf 0.0,
                to.longitude ?: return@sumOf 0.0,
            )
        }
    }

    private fun elevationGain(
        points: List<ActivityPoint>,
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

    private fun List<ActivityPoint>.firstCoordinateOrNull(): GpxCoordinate? {
        return firstNotNullOfOrNull { it.toCoordinateOrNull() }
    }

    private fun List<ActivityPoint>.lastCoordinateOrNull(): GpxCoordinate? {
        return asReversed().firstNotNullOfOrNull { it.toCoordinateOrNull() }
    }

    private fun ActivityPoint.toCoordinateOrNull(): GpxCoordinate? {
        val latitude = latitude ?: return null
        val longitude = longitude ?: return null
        return GpxCoordinate(latitude = latitude, longitude = longitude)
    }
}
