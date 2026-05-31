package com.gpxeditor.shared.domain.activity

data class ActivityDocument(
    val metadata: ActivityMetadata = ActivityMetadata(),
    val tracks: List<ActivityTrack> = emptyList(),
) {
    val pointCount: Int
        get() = tracks.sumOf { track ->
            track.segments.sumOf { segment -> segment.points.size }
        }
}

data class ActivityMetadata(
    val name: String? = null,
    val description: String? = null,
    val sport: String? = null,
    val startTime: String? = null,
    val source: String? = null,
)

data class ActivityTrack(
    val name: String? = null,
    val description: String? = null,
    val segments: List<ActivitySegment> = emptyList(),
) {
    val pointCount: Int
        get() = segments.sumOf { it.points.size }
}

data class ActivitySegment(
    val points: List<ActivityPoint> = emptyList(),
)

data class ActivityPoint(
    val time: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val elevationMeters: Double? = null,
    val name: String? = null,
    val description: String? = null,
    val heartRateBpm: Int? = null,
    val powerWatts: Int? = null,
    val cadenceRpm: Int? = null,
    val distanceMeters: Double? = null,
    val speedMetersPerSecond: Double? = null,
)
