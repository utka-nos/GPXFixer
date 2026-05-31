package com.gpxeditor.shared.domain.fit

data class FitActivityDocument(
    val metadata: FitActivityMetadata = FitActivityMetadata(),
    val tracks: List<FitActivityTrack> = emptyList(),
) {
    val pointCount: Int
        get() = tracks.sumOf { track ->
            track.segments.sumOf { segment -> segment.points.size }
        }
}

data class FitActivityMetadata(
    val name: String? = null,
    val sport: String? = null,
    val startTime: String? = null,
    val source: String? = null,
)

data class FitActivityTrack(
    val name: String? = null,
    val segments: List<FitActivitySegment> = emptyList(),
) {
    val pointCount: Int
        get() = segments.sumOf { it.points.size }
}

data class FitActivitySegment(
    val points: List<FitActivityPoint> = emptyList(),
)

data class FitActivityPoint(
    val time: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val elevationMeters: Double? = null,
    val heartRateBpm: Int? = null,
    val powerWatts: Int? = null,
    val cadenceRpm: Int? = null,
    val distanceMeters: Double? = null,
    val speedMetersPerSecond: Double? = null,
)
