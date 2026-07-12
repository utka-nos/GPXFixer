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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.gpxeditor.shared.feature.trackdetail.PowerChartPresenter
import com.gpxeditor.shared.feature.trackdetail.PowerChartSample
import com.gpxeditor.shared.feature.trackdetail.PowerChartWindow
import kotlin.math.roundToLong

/**
 * Full-screen power-over-time chart. One finger scrubs a selection crosshair;
 * two fingers pinch to zoom and pan the visible time window.
 */
@Composable
fun PowerChartFullScreen(
    samples: List<PowerChartSample>,
    onBackClick: () -> Unit,
) {
    val fullWindow = remember(samples) { PowerChartPresenter.fullWindow(samples) }
    var window by remember(samples) { mutableStateOf(fullWindow) }
    var selectedSample by remember(samples) { mutableStateOf<PowerChartSample?>(null) }
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
                text = "Not enough power data to draw a chart.",
                style = MaterialTheme.typography.bodyMedium,
            )
            return@Column
        }

        val presentation = remember(samples, currentWindow, chartSize.width) {
            PowerChartPresenter.presentation(
                samples = samples,
                window = currentWindow,
                maxRenderPoints = (chartSize.width / 2).coerceAtLeast(300),
            )
        }

        Text(
            text = "Power, W",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = listOfNotNull(
                presentation.averagePowerWatts?.let { "Avg $it W" },
                presentation.maxPowerWatts?.let { "Max $it W" },
            ).joinToString(" · ") + " (visible range)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val chartDescription = buildString {
            append("Power chart from ")
            append(PowerChartPresenter.formatElapsed(currentWindow.startSeconds))
            append(" to ")
            append(PowerChartPresenter.formatElapsed(currentWindow.endSeconds))
            presentation.averagePowerWatts?.let { append(", average $it watts") }
            presentation.maxPowerWatts?.let { append(", maximum $it watts") }
            selectedSample?.let {
                append(". Selected point at ")
                append(PowerChartPresenter.formatElapsed(it.elapsedSeconds))
                append(", ")
                append(it.powerWatts)
                append(" watts")
            }
        }

        PowerChartCanvas(
            presentation = presentation,
            selectedSample = selectedSample,
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
                                    window = PowerChartPresenter.panned(
                                        samples = samples,
                                        window = PowerChartPresenter.zoomed(
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
    samples: List<PowerChartSample>,
    fullWindow: PowerChartWindow,
    window: PowerChartWindow,
    onWindowChange: (PowerChartWindow) -> Unit,
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
                            PowerChartPresenter.panned(samples, windowAtDragStart, deltaSeconds),
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

@Composable
private fun PowerChartCanvas(
    presentation: com.gpxeditor.shared.feature.trackdetail.PowerChartPresentation,
    selectedSample: PowerChartSample?,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall
        .copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val tooltipStyle = MaterialTheme.typography.labelMedium
        .copy(color = MaterialTheme.colorScheme.onPrimaryContainer)
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
    val tooltipBackground = MaterialTheme.colorScheme.primaryContainer

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val geometry = chartGeometry(IntSize(size.width.toInt(), size.height.toInt()))
            val window = presentation.window
            if (geometry.plotWidth <= 0f || geometry.plotHeight <= 0f) return@Canvas
            val axisMax = presentation.axisMaxWatts.coerceAtLeast(1)

            fun xOf(elapsedSeconds: Long): Float = geometry.secondsToX(window, elapsedSeconds)
            fun yOf(watts: Int): Float =
                geometry.plotBottom - geometry.plotHeight * watts / axisMax

            presentation.powerTicks.forEach { tick ->
                val y = yOf(tick.powerWatts)
                drawLine(
                    color = gridColor,
                    start = Offset(geometry.plotLeft, y),
                    end = Offset(geometry.plotRight, y),
                    strokeWidth = 1.dp.toPx(),
                )
                val layout = textMeasurer.measure(AnnotatedString(tick.label), labelStyle)
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        geometry.plotLeft - layout.size.width - 6.dp.toPx(),
                        y - layout.size.height / 2f,
                    ),
                )
            }

            val timeTicks = presentation.timeTicks
            val maxTimeLabelWidth = timeTicks.maxOfOrNull {
                textMeasurer.measure(AnnotatedString(it.label), labelStyle).size.width
            } ?: 0
            // With large font scales the full tick set can overlap: draw every
            // n-th label so neighbours keep at least half a label of spacing.
            val labelStride = when {
                timeTicks.size < 2 || maxTimeLabelWidth == 0 -> 1
                else -> {
                    val perTick = geometry.plotWidth / (timeTicks.size - 1)
                    ((maxTimeLabelWidth * 1.5f / perTick).toInt() + 1).coerceAtLeast(1)
                }
            }
            timeTicks.forEachIndexed { index, tick ->
                val x = xOf(tick.elapsedSeconds)
                drawLine(
                    color = gridColor,
                    start = Offset(x, geometry.plotTop),
                    end = Offset(x, geometry.plotBottom),
                    strokeWidth = 1.dp.toPx(),
                )
                if (index % labelStride == 0) {
                    val layout = textMeasurer.measure(AnnotatedString(tick.label), labelStyle)
                    drawText(
                        textLayoutResult = layout,
                        topLeft = Offset(
                            (x - layout.size.width / 2f)
                                .coerceIn(0f, size.width - layout.size.width),
                            geometry.plotBottom + 4.dp.toPx(),
                        ),
                    )
                }
            }

            presentation.segments.forEach { segment ->
                if (segment.size < 2) return@forEach
                val linePath = Path()
                val areaPath = Path()
                segment.forEachIndexed { index, sample ->
                    val x = xOf(sample.elapsedSeconds)
                    val y = yOf(sample.powerWatts)
                    if (index == 0) {
                        linePath.moveTo(x, y)
                        areaPath.moveTo(x, geometry.plotBottom)
                        areaPath.lineTo(x, y)
                    } else {
                        linePath.lineTo(x, y)
                        areaPath.lineTo(x, y)
                    }
                }
                areaPath.lineTo(xOf(segment.last().elapsedSeconds), geometry.plotBottom)
                areaPath.close()
                drawPath(path = areaPath, color = lineColor.copy(alpha = 0.15f))
                drawPath(path = linePath, color = lineColor, style = Stroke(width = 2.dp.toPx()))
            }

            val selected = selectedSample?.takeIf {
                it.elapsedSeconds in window.startSeconds..window.endSeconds
            }
            if (selected != null) {
                val x = xOf(selected.elapsedSeconds)
                val y = yOf(selected.powerWatts)
                drawLine(
                    color = lineColor,
                    start = Offset(x, geometry.plotTop),
                    end = Offset(x, geometry.plotBottom),
                    strokeWidth = 1.dp.toPx(),
                )
                drawCircle(color = lineColor, radius = 5.dp.toPx(), center = Offset(x, y))

                val tooltipText =
                    "${PowerChartPresenter.formatElapsed(selected.elapsedSeconds)} · " +
                        "${selected.powerWatts} W"
                val layout = textMeasurer.measure(AnnotatedString(tooltipText), tooltipStyle)
                val tooltipPadding = 6.dp.toPx()
                val tooltipWidth = layout.size.width + tooltipPadding * 2
                val tooltipLeft = (x - tooltipWidth / 2f)
                    .coerceIn(0f, size.width - tooltipWidth)
                drawRoundRect(
                    color = tooltipBackground,
                    topLeft = Offset(tooltipLeft, geometry.plotTop),
                    size = Size(tooltipWidth, layout.size.height + tooltipPadding * 2),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                )
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        tooltipLeft + tooltipPadding,
                        geometry.plotTop + tooltipPadding,
                    ),
                )
            }
        }
    }
}

/** Pixel geometry of the plot area inside the chart canvas. */
private data class ChartGeometry(
    val plotLeft: Float,
    val plotTop: Float,
    val plotRight: Float,
    val plotBottom: Float,
) {
    val plotWidth: Float get() = plotRight - plotLeft
    val plotHeight: Float get() = plotBottom - plotTop

    fun secondsToX(window: PowerChartWindow, elapsedSeconds: Long): Float {
        val duration = window.durationSeconds.coerceAtLeast(1)
        return plotLeft + plotWidth * (elapsedSeconds - window.startSeconds) / duration
    }

    fun xToSeconds(window: PowerChartWindow, x: Float): Long {
        if (plotWidth <= 0f) return window.startSeconds
        val fraction = ((x - plotLeft) / plotWidth).coerceIn(0f, 1f)
        return window.startSeconds + (fraction * window.durationSeconds).roundToLong()
    }
}

private fun chartGeometry(size: IntSize): ChartGeometry {
    val width = size.width.toFloat()
    val height = size.height.toFloat()
    return ChartGeometry(
        plotLeft = width * 0.12f,
        plotTop = height * 0.02f,
        plotRight = width * 0.99f,
        plotBottom = height * 0.92f,
    )
}

private fun sampleAt(
    samples: List<PowerChartSample>,
    window: PowerChartWindow,
    geometry: ChartGeometry,
    x: Float,
): PowerChartSample? {
    val sample = PowerChartPresenter.nearestSample(samples, geometry.xToSeconds(window, x))
    return sample?.takeIf { it.elapsedSeconds in window.startSeconds..window.endSeconds }
}

private fun distanceBetween(a: Offset, b: Offset): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return kotlin.math.sqrt(dx * dx + dy * dy)
}
