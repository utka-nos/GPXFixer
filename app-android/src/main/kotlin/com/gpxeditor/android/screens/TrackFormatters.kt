package com.gpxeditor.android

import com.gpxeditor.shared.feature.trackdetail.GpxCoordinate
import java.util.Locale

fun formatDistance(meters: Double): String {
    return if (meters >= 1_000.0) {
        String.format(Locale.US, "%.2f km", meters / 1_000.0)
    } else {
        String.format(Locale.US, "%.0f m", meters)
    }
}

fun formatElevation(meters: Double?): String {
    return meters?.let { String.format(Locale.US, "%.0f m", it) } ?: "No data"
}

fun formatElevationRange(
    minMeters: Double?,
    maxMeters: Double?,
): String {
    if (minMeters == null || maxMeters == null) return "No data"
    return "${formatElevation(minMeters)} to ${formatElevation(maxMeters)}"
}

fun formatTimeRange(
    startTime: String?,
    endTime: String?,
): String {
    if (startTime == null && endTime == null) return "No data"
    if (startTime == endTime || endTime == null) return startTime ?: "No data"
    if (startTime == null) return endTime
    return "$startTime to $endTime"
}

fun formatCoordinate(coordinate: GpxCoordinate?): String {
    return coordinate?.let {
        String.format(Locale.US, "%.5f, %.5f", it.latitude, it.longitude)
    } ?: "No data"
}
