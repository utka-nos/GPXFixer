package com.gpxeditor.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gpxeditor.shared.feature.trackdetail.GpxCoordinate
import com.gpxeditor.shared.feature.trackdetail.TrackDetail
import java.util.Locale

@Composable
fun TrackDetailScreen(
    detail: TrackDetail,
    onBackClick: () -> Unit,
) {
    val summary = detail.summary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Button(onClick = onBackClick) {
            Text("Back")
        }

        Text(
            text = detail.importedTrack.displayName,
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = detail.importedTrack.originalFileName,
            style = MaterialTheme.typography.bodyMedium,
        )

        DetailSection(title = "Summary") {
            DetailRow("Imported", detail.importedTrack.importedAt)
            DetailRow("Tracks", summary.trackCount.toString())
            DetailRow("Segments", summary.segmentCount.toString())
            DetailRow("Points", summary.pointCount.toString())
            DetailRow("Distance", formatDistance(summary.distanceMeters))
            DetailRow("Elevation gain", formatElevation(summary.totalAscentMeters))
            DetailRow("Elevation loss", formatElevation(summary.totalDescentMeters))
            DetailRow("Elevation range", formatElevationRange(summary.minElevationMeters, summary.maxElevationMeters))
            DetailRow("Time range", formatTimeRange(summary.startTime, summary.endTime))
            DetailRow("Start", formatCoordinate(summary.startCoordinate))
            DetailRow("Finish", formatCoordinate(summary.endCoordinate))
        }

        if (detail.warnings.isNotEmpty()) {
            DetailSection(title = "Warnings") {
                detail.warnings.forEach { warning ->
                    Text(
                        text = warning,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        DetailSection(title = "Segments") {
            if (detail.segments.isEmpty()) {
                Text(
                    text = "No segments found.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                detail.segments.forEach { segment ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Segment ${segment.index}",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = "${segment.pointCount} points / ${formatDistance(segment.distanceMeters)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = formatTimeRange(segment.startTime, segment.endTime),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.small,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            content()
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun formatDistance(meters: Double): String {
    return if (meters >= 1_000.0) {
        String.format(Locale.US, "%.2f km", meters / 1_000.0)
    } else {
        String.format(Locale.US, "%.0f m", meters)
    }
}

private fun formatElevation(meters: Double?): String {
    return meters?.let { String.format(Locale.US, "%.0f m", it) } ?: "No data"
}

private fun formatElevationRange(
    minMeters: Double?,
    maxMeters: Double?,
): String {
    if (minMeters == null || maxMeters == null) return "No data"
    return "${formatElevation(minMeters)} to ${formatElevation(maxMeters)}"
}

private fun formatTimeRange(
    startTime: String?,
    endTime: String?,
): String {
    if (startTime == null && endTime == null) return "No data"
    if (startTime == endTime || endTime == null) return startTime ?: "No data"
    if (startTime == null) return endTime
    return "$startTime to $endTime"
}

private fun formatCoordinate(coordinate: GpxCoordinate?): String {
    return coordinate?.let {
        String.format(Locale.US, "%.5f, %.5f", it.latitude, it.longitude)
    } ?: "No data"
}
