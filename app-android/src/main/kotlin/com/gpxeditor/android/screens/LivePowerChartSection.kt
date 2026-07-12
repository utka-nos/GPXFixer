package com.gpxeditor.android.screens

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.gpxeditor.shared.feature.recordtrack.LiveChartWindow
import com.gpxeditor.shared.feature.trackdetail.TrackChartPresenter
import com.gpxeditor.shared.feature.trackdetail.TrackChartSample
import com.gpxeditor.shared.feature.trackdetail.TrackChartWindow
import kotlin.math.roundToLong

/**
 * Live power-over-time chart on the recording screen. Follows the newest
 * samples with a sliding window; dragging pans back over the ride, and the
 * Live button (or panning forward to the newest sample) resumes following.
 * Nothing is rendered until there is enough power data to draw a line.
 */
@Composable
fun LivePowerChartSection(
    samples: List<TrackChartSample>,
    modifier: Modifier = Modifier,
) {
    val liveWindow = LiveChartWindow.liveWindow(samples) ?: return
    var pannedWindow by remember { mutableStateOf<TrackChartWindow?>(null) }
    val window = pannedWindow ?: liveWindow
    var chartSize by remember { mutableStateOf(IntSize.Zero) }
    val currentSamples by rememberUpdatedState(samples)
    val currentWindow by rememberUpdatedState(window)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Power, W",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.weight(1f))
            if (pannedWindow != null) {
                TextButton(onClick = { pannedWindow = null }) {
                    Text("Live")
                }
            }
        }

        val presentation = remember(samples, window, chartSize.width) {
            TrackChartPresenter.presentation(
                samples = samples,
                window = window,
                maxRenderPoints = (chartSize.width / 2).coerceAtLeast(300),
            )
        }
        val chartDescription = buildString {
            append("Live power chart from ")
            append(TrackChartPresenter.formatElapsed(window.startSeconds))
            append(" to ")
            append(TrackChartPresenter.formatElapsed(window.endSeconds))
            append(". Drag to look back over the ride")
        }

        TrackChartCanvas(
            presentation = presentation,
            selectedSample = null,
            unit = "W",
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .onSizeChanged { chartSize = it }
                .semantics { contentDescription = chartDescription }
                .pointerInput(Unit) {
                    var windowAtDragStart = currentWindow
                    var draggedPx = 0f
                    detectHorizontalDragGestures(
                        onDragStart = {
                            windowAtDragStart = currentWindow
                            draggedPx = 0f
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            draggedPx += dragAmount
                            val geometry = chartGeometry(size)
                            if (geometry.plotWidth <= 0f) return@detectHorizontalDragGestures
                            val deltaSeconds =
                                (-draggedPx * windowAtDragStart.durationSeconds / geometry.plotWidth)
                                    .roundToLong()
                            val panned = LiveChartWindow.panned(
                                samples = currentSamples,
                                window = windowAtDragStart,
                                deltaSeconds = deltaSeconds,
                            )
                            pannedWindow = when {
                                LiveChartWindow.isAtLiveEdge(currentSamples, panned) -> null
                                else -> panned
                            }
                        },
                    )
                },
        )
    }
}
