package com.gpxeditor.android.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.gpxeditor.shared.feature.trackdetail.TrackChartPresentation
import com.gpxeditor.shared.feature.trackdetail.TrackChartPresenter
import com.gpxeditor.shared.feature.trackdetail.TrackChartSample
import com.gpxeditor.shared.feature.trackdetail.TrackChartWindow
import kotlin.math.roundToLong

/**
 * Canvas rendering one visible window of a metric-over-time series: value and
 * time grid with labels, the gap-split line segments with an area fill, and an
 * optional selection crosshair with a tooltip.
 */
@Composable
internal fun TrackChartCanvas(
    presentation: TrackChartPresentation,
    selectedSample: TrackChartSample?,
    unit: String,
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
            val axisMax = presentation.axisMaxValue.coerceAtLeast(1)

            fun xOf(elapsedSeconds: Long): Float = geometry.secondsToX(window, elapsedSeconds)
            fun yOf(value: Int): Float =
                geometry.plotBottom - geometry.plotHeight * value / axisMax

            presentation.valueTicks.forEach { tick ->
                val y = yOf(tick.value)
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
                    val y = yOf(sample.value)
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
                val y = yOf(selected.value)
                drawLine(
                    color = lineColor,
                    start = Offset(x, geometry.plotTop),
                    end = Offset(x, geometry.plotBottom),
                    strokeWidth = 1.dp.toPx(),
                )
                drawCircle(color = lineColor, radius = 5.dp.toPx(), center = Offset(x, y))

                val tooltipText =
                    "${TrackChartPresenter.formatElapsed(selected.elapsedSeconds)} · " +
                        "${selected.value} $unit"
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
internal data class ChartGeometry(
    val plotLeft: Float,
    val plotTop: Float,
    val plotRight: Float,
    val plotBottom: Float,
) {
    val plotWidth: Float get() = plotRight - plotLeft
    val plotHeight: Float get() = plotBottom - plotTop

    fun secondsToX(window: TrackChartWindow, elapsedSeconds: Long): Float {
        val duration = window.durationSeconds.coerceAtLeast(1)
        return plotLeft + plotWidth * (elapsedSeconds - window.startSeconds) / duration
    }

    fun xToSeconds(window: TrackChartWindow, x: Float): Long {
        if (plotWidth <= 0f) return window.startSeconds
        val fraction = ((x - plotLeft) / plotWidth).coerceIn(0f, 1f)
        return window.startSeconds + (fraction * window.durationSeconds).roundToLong()
    }
}

internal fun chartGeometry(size: IntSize): ChartGeometry {
    val width = size.width.toFloat()
    val height = size.height.toFloat()
    return ChartGeometry(
        plotLeft = width * 0.12f,
        plotTop = height * 0.02f,
        plotRight = width * 0.99f,
        plotBottom = height * 0.92f,
    )
}

internal fun sampleAt(
    samples: List<TrackChartSample>,
    window: TrackChartWindow,
    geometry: ChartGeometry,
    x: Float,
): TrackChartSample? {
    val sample = TrackChartPresenter.nearestSample(samples, geometry.xToSeconds(window, x))
    return sample?.takeIf { it.elapsedSeconds in window.startSeconds..window.endSeconds }
}
