package com.gpxeditor.android.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.gpxeditor.shared.feature.trackdetail.TrackChartPresenter
import com.gpxeditor.shared.feature.trackdetail.TrackChartSample
import com.gpxeditor.shared.feature.trackdetail.TrackChartWindow
import kotlin.math.roundToLong

/**
 * Full-screen metric-over-time chart. One finger scrubs a selection crosshair;
 * two fingers pinch to zoom and pan the visible time window.
 */
@Composable
fun TrackChartFullScreen(
    title: String,
    unit: String,
    unitLong: String,
    samples: List<TrackChartSample>,
    onBackClick: () -> Unit,
) {
    val fullWindow = remember(samples) { TrackChartPresenter.fullWindow(samples) }
    var window by remember(samples) { mutableStateOf(fullWindow) }
    var selectedSample by remember(samples) { mutableStateOf<TrackChartSample?>(null) }
    var chartSize by remember { mutableStateOf(IntSize.Zero) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = onBackClick) {
                Text("Back")
            }
            Spacer(modifier = Modifier.weight(1f))
            if (window != null && window != fullWindow) {
                TextButton(onClick = {
                    window = fullWindow
                    selectedSample = null
                }) {
                    Text("Reset zoom")
                }
            }
        }

        val currentWindow = window
        if (fullWindow == null || currentWindow == null) {
            Text(
                text = "Not enough ${title.lowercase()} data to draw a chart.",
                style = MaterialTheme.typography.bodyMedium,
            )
            return@Column
        }

        val presentation = remember(samples, currentWindow, chartSize.width) {
            TrackChartPresenter.presentation(
                samples = samples,
                window = currentWindow,
                maxRenderPoints = (chartSize.width / 2).coerceAtLeast(300),
            )
        }

        Text(
            text = "$title, $unit",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = listOfNotNull(
                presentation.averageValue?.let { "Avg $it $unit" },
                presentation.maxValue?.let { "Max $it $unit" },
            ).joinToString(" · ") + " (visible range)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val chartDescription = buildString {
            append("$title chart from ")
            append(TrackChartPresenter.formatElapsed(currentWindow.startSeconds))
            append(" to ")
            append(TrackChartPresenter.formatElapsed(currentWindow.endSeconds))
            presentation.averageValue?.let { append(", average $it $unitLong") }
            presentation.maxValue?.let { append(", maximum $it $unitLong") }
            selectedSample?.let {
                append(". Selected point at ")
                append(TrackChartPresenter.formatElapsed(it.elapsedSeconds))
                append(", ")
                append(it.value)
                append(" ")
                append(unitLong)
            }
        }

        TrackChartCanvas(
            presentation = presentation,
            selectedSample = selectedSample,
            unit = unit,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .onSizeChanged { chartSize = it }
                .semantics { contentDescription = chartDescription }
                .pointerInput(samples, fullWindow) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val geometry = chartGeometry(chartSize)
                        selectedSample = window?.let { activeWindow ->
                            sampleAt(samples, activeWindow, geometry, down.position.x)
                        }
                        var previousCentroidX: Float? = null
                        var previousDistance: Float? = null
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            val activeWindow = window ?: break
                            if (pressed.size == 1) {
                                previousCentroidX = null
                                previousDistance = null
                                selectedSample =
                                    sampleAt(samples, activeWindow, geometry, pressed[0].position.x)
                            } else {
                                selectedSample = null
                                val centroidX = pressed.map { it.position.x }.average().toFloat()
                                val distance = distanceBetween(
                                    pressed[0].position,
                                    pressed[1].position,
                                )
                                val lastCentroidX = previousCentroidX
                                val lastDistance = previousDistance
                                if (lastCentroidX != null && lastDistance != null && lastDistance > 0f) {
                                    val secondsPerPx =
                                        activeWindow.durationSeconds.toDouble() / geometry.plotWidth
                                    val panSeconds =
                                        (-(centroidX - lastCentroidX) * secondsPerPx).roundToLong()
                                    val focusSeconds = geometry.xToSeconds(activeWindow, centroidX)
                                    window = TrackChartPresenter.panned(
                                        samples = samples,
                                        window = TrackChartPresenter.zoomed(
                                            samples = samples,
                                            window = activeWindow,
                                            factor = (distance / lastDistance).toDouble(),
                                            focusSeconds = focusSeconds,
                                        ),
                                        deltaSeconds = panSeconds,
                                    )
                                }
                                previousCentroidX = centroidX
                                previousDistance = distance
                            }
                            pressed.forEach { it.consume() }
                        }
                    }
                },
        )

        ChartPanSlider(
            samples = samples,
            fullWindow = fullWindow,
            window = currentWindow,
            onWindowChange = { newWindow ->
                window = newWindow
                selectedSample = null
            },
        )
    }
}

/**
 * Scrollbar-style pan control under the chart: the track represents the whole
 * ride, the thumb the visible window. Dragging it pans the window at the
 * current zoom level.
 */
@Composable
private fun ChartPanSlider(
    samples: List<TrackChartSample>,
    fullWindow: TrackChartWindow,
    window: TrackChartWindow,
    onWindowChange: (TrackChartWindow) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentWindow by rememberUpdatedState(window)
    val currentOnWindowChange by rememberUpdatedState(onWindowChange)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val thumbColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(16.dp)
            .semantics { contentDescription = "Chart position. Drag to move the visible range" }
            .pointerInput(samples, fullWindow) {
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
                        val trackWidth = size.width
                        if (trackWidth <= 0) return@detectHorizontalDragGestures
                        val deltaSeconds =
                            (draggedPx / trackWidth * fullWindow.durationSeconds).roundToLong()
                        currentOnWindowChange(
                            TrackChartPresenter.panned(samples, windowAtDragStart, deltaSeconds),
                        )
                    },
                )
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cornerRadius = CornerRadius(size.height / 2f)
            drawRoundRect(color = trackColor, cornerRadius = cornerRadius)

            val fullDuration = fullWindow.durationSeconds.coerceAtLeast(1)
            val minThumbWidth = size.height * 2
            val thumbWidth = (size.width * window.durationSeconds / fullDuration)
                .coerceIn(minThumbWidth, size.width)
            val thumbLeft = (size.width * (window.startSeconds - fullWindow.startSeconds) / fullDuration)
                .coerceIn(0f, size.width - thumbWidth)
            drawRoundRect(
                color = thumbColor,
                topLeft = Offset(thumbLeft, 0f),
                size = Size(thumbWidth, size.height),
                cornerRadius = cornerRadius,
            )
        }
    }
}

private fun distanceBetween(a: Offset, b: Offset): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return kotlin.math.sqrt(dx * dx + dy * dy)
}
