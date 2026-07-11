package com.gpxeditor.android.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gpxeditor.shared.feature.trackdetail.TrackSegmentSummary
import com.gpxeditor.shared.feature.trackdetail.TrackSummary

@Composable
fun SummarySection(
    title: String,
    importedAt: String?,
    summary: TrackSummary,
) {
    DetailSection(title = title) {
        importedAt?.let { DetailRow("Imported", it) }
        DetailRow("Tracks", summary.trackCount.toString())
        DetailRow("Segments", summary.segmentCount.toString())
        DetailRow("Points", summary.pointCount.toString())
        DetailRow("Distance", formatDistance(summary.distanceMeters))
        DetailRow("Elevation gain", formatElevation(summary.totalAscentMeters))
        DetailRow("Elevation loss", formatElevation(summary.totalDescentMeters))
        DetailRow("Elevation range",
            formatElevationRange(summary.minElevationMeters, summary.maxElevationMeters)
        )
        summary.averagePowerWatts?.let { DetailRow("Average power", "${it.toInt()} W") }
        summary.maxPowerWatts?.let { DetailRow("Maximum power", "$it W") }
        summary.averageCadenceRpm?.let { DetailRow("Average cadence", "${it.toInt()} rpm") }
        DetailRow("Time range", formatTimeRange(summary.startTime, summary.endTime))
        DetailRow("Start", formatCoordinate(summary.startCoordinate))
        DetailRow("Finish", formatCoordinate(summary.endCoordinate))
    }
}

@Composable
fun WarningsSection(warnings: List<String>) {
    if (warnings.isNotEmpty()) {
        DetailSection(title = "Warnings") {
            warnings.forEach { warning ->
                Text(
                    text = warning,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
fun TrackSegmentsSection(segments: List<TrackSegmentSummary>) {
    DetailSection(title = "Segments") {
        if (segments.isEmpty()) {
            Text(
                text = "No segments found.",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            segments.forEach { segment ->
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

@Composable
fun DetailSection(
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
fun DetailRow(
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
