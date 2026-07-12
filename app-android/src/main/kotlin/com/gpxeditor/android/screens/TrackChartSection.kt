package com.gpxeditor.android.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gpxeditor.shared.feature.trackdetail.TrackChartSample

/** Inline metric-over-time chart card on the track detail screen. */
@Composable
fun TrackChartSection(
    title: String,
    unit: String,
    samples: List<TrackChartSample>,
    onOpenChart: (() -> Unit)? = null,
) {
    if (samples.size < 2) return
    val maxValue = samples.maxOf { it.value }
    val totalSeconds = samples.maxOf { it.elapsedSeconds }
    if (maxValue <= 0 || totalSeconds <= 0) return

    DetailSection(title = title) {
        val lineColor = MaterialTheme.colorScheme.primary
        val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val gridSteps = 4
                repeat(gridSteps + 1) { step ->
                    val y = size.height * step / gridSteps
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx(),
                    )
                }

                val linePath = Path()
                val areaPath = Path()
                samples.forEachIndexed { index, sample ->
                    val x = size.width * sample.elapsedSeconds / totalSeconds
                    val y = size.height * (1f - sample.value.toFloat() / maxValue)
                    if (index == 0) {
                        linePath.moveTo(x, y)
                        areaPath.moveTo(x, size.height)
                        areaPath.lineTo(x, y)
                    } else {
                        linePath.lineTo(x, y)
                        areaPath.lineTo(x, y)
                    }
                }
                areaPath.lineTo(size.width * samples.last().elapsedSeconds / totalSeconds, size.height)
                areaPath.close()

                drawPath(path = areaPath, color = lineColor.copy(alpha = 0.15f))
                drawPath(
                    path = linePath,
                    color = lineColor,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
            Text(
                text = "$maxValue $unit",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp),
            )
            Text(
                text = "0 $unit",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp),
            )
            if (onOpenChart != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(onClick = onOpenChart)
                        .semantics {
                            contentDescription = "Open full-screen ${title.lowercase()} chart"
                        },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatDuration(0),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatDuration(totalSeconds * 1_000),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
