package com.gpxeditor.shared.domain.gpx

data class GpxDocument(
    val version: String = "1.1",
    val creator: String? = null,
    val metadata: GpxMetadata? = null,
    val tracks: List<GpxTrack> = emptyList(),
) {
    val pointCount: Int
        get() = tracks.sumOf { track ->
            track.segments.sumOf { segment -> segment.points.size }
        }
}

data class GpxMetadata(
    val name: String? = null,
    val description: String? = null,
    val time: String? = null,
)

data class GpxTrack(
    val name: String? = null,
    val description: String? = null,
    val segments: List<GpxTrackSegment> = emptyList(),
) {
    val pointCount: Int
        get() = segments.sumOf { it.points.size }
}

data class GpxTrackSegment(
    val points: List<GpxTrackPoint> = emptyList(),
)

data class GpxTrackPoint(
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double? = null,
    val time: String? = null,
    val name: String? = null,
    val description: String? = null,
)
