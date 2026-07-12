package com.gpxeditor.shared.feature.trackdetail

/** Inclusive visible range of elapsed time on the power chart. */
data class PowerChartWindow(
    val startSeconds: Long,
    val endSeconds: Long,
) {
    val durationSeconds: Long get() = endSeconds - startSeconds
}

/** A labelled tick on the elapsed-time axis. */
data class PowerChartTimeTick(
    val elapsedSeconds: Long,
    val label: String,
)

/** A labelled tick on the power axis. */
data class PowerChartPowerTick(
    val powerWatts: Int,
    val label: String,
)

/**
 * Everything a platform chart needs to render one visible window of the power
 * series identically on Android and iOS: axis bounds, tick labels, the
 * range summary, and the gap-split, downsampled line segments.
 */
data class PowerChartPresentation(
    val window: PowerChartWindow,
    val timeTicks: List<PowerChartTimeTick>,
    val powerTicks: List<PowerChartPowerTick>,
    val axisMaxWatts: Int,
    val averagePowerWatts: Int?,
    val maxPowerWatts: Int?,
    val segments: List<List<PowerChartSample>>,
)

object PowerChartPresenter {
    /** A timestamp gap larger than this breaks the line instead of bridging it. */
    const val GAP_THRESHOLD_SECONDS: Long = 60L

    /** The chart never zooms in past this window span. */
    const val MIN_WINDOW_SECONDS: Long = 60L

    private val TIME_STEPS_SECONDS = listOf<Long>(
        1, 2, 5, 10, 15, 30,
        60, 120, 300, 600, 900, 1_800,
        3_600, 7_200, 10_800, 21_600, 43_200,
    )
    private val POWER_STEPS_WATTS = listOf(5, 10, 20, 25, 50, 100, 150, 200, 250, 500, 1_000)
    private const val TARGET_TIME_TICKS = 6
    private const val TARGET_POWER_TICKS = 5

    /**
     * The window spanning the whole series, or null when there is not enough
     * data to draw a chart (fewer than two distinct timestamps).
     */
    fun fullWindow(samples: List<PowerChartSample>): PowerChartWindow? {
        val collapsed = collapseDuplicates(samples)
        if (collapsed.size < 2 || collapsed.first().elapsedSeconds == collapsed.last().elapsedSeconds) {
            return null
        }
        return PowerChartWindow(collapsed.first().elapsedSeconds, collapsed.last().elapsedSeconds)
    }

    /**
     * Zooms [window] by [factor] (>1 zooms in) keeping the point at
     * [focusSeconds] fixed, clamped to the full series and [MIN_WINDOW_SECONDS].
     */
    fun zoomed(
        samples: List<PowerChartSample>,
        window: PowerChartWindow,
        factor: Double,
        focusSeconds: Long,
    ): PowerChartWindow {
        val full = fullWindow(samples) ?: return window
        if (factor <= 0.0) return window
        val minDuration = minOf(MIN_WINDOW_SECONDS, full.durationSeconds)
        val newDuration = (window.durationSeconds / factor).toLong()
            .coerceIn(minDuration, full.durationSeconds)
        val focusFraction = when {
            window.durationSeconds <= 0 -> 0.5
            else -> (focusSeconds - window.startSeconds).toDouble() / window.durationSeconds
        }.coerceIn(0.0, 1.0)
        val newStart = (focusSeconds - focusFraction * newDuration).toLong()
        return clampWindow(full, newStart, newDuration)
    }

    /** Shifts [window] by [deltaSeconds], clamped to the full series. */
    fun panned(
        samples: List<PowerChartSample>,
        window: PowerChartWindow,
        deltaSeconds: Long,
    ): PowerChartWindow {
        val full = fullWindow(samples) ?: return window
        return clampWindow(full, window.startSeconds + deltaSeconds, window.durationSeconds)
    }

    /**
     * Builds the render model for [window]. [maxRenderPoints] bounds the number
     * of points across all segments; downsampling keeps per-bucket minima and
     * maxima so peaks and valleys survive. The average/maximum summary is
     * computed from the original (non-downsampled) samples in the window.
     */
    fun presentation(
        samples: List<PowerChartSample>,
        window: PowerChartWindow,
        maxRenderPoints: Int = 600,
    ): PowerChartPresentation {
        val collapsed = collapseDuplicates(samples)
        val visible = collapsed.filter {
            it.elapsedSeconds >= window.startSeconds && it.elapsedSeconds <= window.endSeconds
        }
        val maxVisibleWatts = visible.maxOfOrNull { it.powerWatts }
        val averageVisibleWatts = when {
            visible.isEmpty() -> null
            else -> (visible.sumOf { it.powerWatts.toDouble() } / visible.size + 0.5).toInt()
        }

        val powerAxis = powerAxis(maxVisibleWatts ?: 0)
        val renderSegments = renderSegments(collapsed, window, maxRenderPoints)

        return PowerChartPresentation(
            window = window,
            timeTicks = timeTicks(window),
            powerTicks = powerAxis.second,
            axisMaxWatts = powerAxis.first,
            averagePowerWatts = averageVisibleWatts,
            maxPowerWatts = maxVisibleWatts,
            segments = renderSegments,
        )
    }

    /**
     * The sample nearest to [elapsedSeconds], for touch selection. Duplicate
     * timestamps are collapsed the same way as the rendered series so the
     * selected value matches the drawn line.
     */
    fun nearestSample(
        samples: List<PowerChartSample>,
        elapsedSeconds: Long,
    ): PowerChartSample? {
        val collapsed = collapseDuplicates(samples)
        if (collapsed.isEmpty()) return null
        var low = 0
        var high = collapsed.lastIndex
        while (low < high) {
            val mid = (low + high) / 2
            if (collapsed[mid].elapsedSeconds < elapsedSeconds) low = mid + 1 else high = mid
        }
        val after = collapsed[low]
        val before = collapsed.getOrNull(low - 1) ?: return after
        val beforeDistance = elapsedSeconds - before.elapsedSeconds
        val afterDistance = after.elapsedSeconds - elapsedSeconds
        return if (beforeDistance <= afterDistance) before else after
    }

    /**
     * Formats elapsed seconds as `MM:SS` under an hour and `H:MM:SS` above,
     * for tooltips and accessibility labels.
     */
    fun formatElapsed(elapsedSeconds: Long): String {
        val hours = elapsedSeconds / 3_600
        val minutes = (elapsedSeconds % 3_600) / 60
        val seconds = elapsedSeconds % 60
        return when {
            hours > 0 -> "$hours:${pad(minutes)}:${pad(seconds)}"
            else -> "${pad(minutes)}:${pad(seconds)}"
        }
    }

    private fun clampWindow(
        full: PowerChartWindow,
        proposedStart: Long,
        duration: Long,
    ): PowerChartWindow {
        val clampedDuration = duration.coerceIn(
            minOf(MIN_WINDOW_SECONDS, full.durationSeconds),
            full.durationSeconds,
        )
        val start = proposedStart.coerceIn(full.startSeconds, full.endSeconds - clampedDuration)
        return PowerChartWindow(start, start + clampedDuration)
    }

    /** Averages samples that share a timestamp so the line stays a function of time. */
    private fun collapseDuplicates(samples: List<PowerChartSample>): List<PowerChartSample> {
        if (samples.isEmpty()) return emptyList()
        val collapsed = ArrayList<PowerChartSample>(samples.size)
        var index = 0
        while (index < samples.size) {
            val elapsed = samples[index].elapsedSeconds
            var sum = 0.0
            var count = 0
            val segmentIndex = samples[index].segmentIndex
            while (
                index < samples.size &&
                samples[index].elapsedSeconds == elapsed &&
                samples[index].segmentIndex == segmentIndex
            ) {
                sum += samples[index].powerWatts
                count++
                index++
            }
            collapsed.add(PowerChartSample(elapsed, (sum / count + 0.5).toInt(), segmentIndex))
        }
        return collapsed
    }

    /** Splits the series where consecutive samples are further apart than the gap threshold. */
    private fun splitByGaps(samples: List<PowerChartSample>): List<List<PowerChartSample>> {
        if (samples.isEmpty()) return emptyList()
        val segments = mutableListOf<MutableList<PowerChartSample>>(mutableListOf(samples.first()))
        for (sampleIndex in 1 until samples.size) {
            val sample = samples[sampleIndex]
            val previous = samples[sampleIndex - 1]
            if (
                sample.segmentIndex != previous.segmentIndex ||
                sample.elapsedSeconds - previous.elapsedSeconds > GAP_THRESHOLD_SECONDS
            ) {
                segments.add(mutableListOf())
            }
            segments.last().add(sample)
        }
        return segments
    }

    private fun renderSegments(
        collapsed: List<PowerChartSample>,
        window: PowerChartWindow,
        maxRenderPoints: Int,
    ): List<List<PowerChartSample>> {
        val windowed = splitByGaps(collapsed).mapNotNull { segment ->
            val firstVisible = segment.indexOfFirst { it.elapsedSeconds >= window.startSeconds }
            if (firstVisible == -1) return@mapNotNull null
            val lastVisible = segment.indexOfLast { it.elapsedSeconds <= window.endSeconds }
            if (lastVisible < firstVisible) {
                // The window falls between two samples of this segment: keep the
                // bracketing pair so the line still crosses the viewport.
                return@mapNotNull when {
                    firstVisible > 0 -> listOf(segment[firstVisible - 1], segment[firstVisible])
                    else -> null
                }
            }
            // One extra sample on each side lets the line reach the viewport edges.
            segment.subList(
                (firstVisible - 1).coerceAtLeast(0),
                (lastVisible + 2).coerceAtMost(segment.size),
            ).toList()
        }.filter { it.size >= 2 }

        val totalPoints = windowed.sumOf { it.size }
        if (totalPoints <= maxRenderPoints) return windowed

        return windowed.map { segment ->
            val budget = (maxRenderPoints.toLong() * segment.size / totalPoints)
                .toInt()
                .coerceAtLeast(4)
            downsample(segment, budget)
        }
    }

    /**
     * Min-max downsampling: keeps the first and last sample and the extreme
     * values of each time bucket, so rendered peaks match the source data.
     */
    private fun downsample(segment: List<PowerChartSample>, budget: Int): List<PowerChartSample> {
        if (segment.size <= budget) return segment
        // First and last are always kept and each bucket contributes up to two
        // samples, so this bucket count keeps the result within the budget.
        val bucketCount = ((budget - 2) / 2).coerceAtLeast(1)
        val interior = segment.subList(1, segment.size - 1)
        val keptIndices = mutableSetOf(0, segment.size - 1)
        val bucketSize = (interior.size + bucketCount - 1) / bucketCount
        var bucketStart = 0
        while (bucketStart < interior.size) {
            val bucketEnd = (bucketStart + bucketSize).coerceAtMost(interior.size)
            var minIndex = bucketStart
            var maxIndex = bucketStart
            for (i in bucketStart until bucketEnd) {
                if (interior[i].powerWatts < interior[minIndex].powerWatts) minIndex = i
                if (interior[i].powerWatts > interior[maxIndex].powerWatts) maxIndex = i
            }
            keptIndices.add(minIndex + 1)
            keptIndices.add(maxIndex + 1)
            bucketStart = bucketEnd
        }
        return keptIndices.sorted().map(segment::get)
    }

    private fun timeTicks(window: PowerChartWindow): List<PowerChartTimeTick> {
        val duration = window.durationSeconds.coerceAtLeast(1)
        val step = TIME_STEPS_SECONDS.firstOrNull { duration / it <= TARGET_TIME_TICKS }
            ?: TIME_STEPS_SECONDS.last()
        val firstTick = ((window.startSeconds + step - 1) / step) * step
        val ticks = mutableListOf<PowerChartTimeTick>()
        var tick = firstTick
        while (tick <= window.endSeconds) {
            ticks.add(PowerChartTimeTick(tick, timeTickLabel(tick, window, step)))
            tick += step
        }
        return ticks
    }

    private fun timeTickLabel(elapsedSeconds: Long, window: PowerChartWindow, step: Long): String {
        val hours = elapsedSeconds / 3_600
        val minutes = (elapsedSeconds % 3_600) / 60
        val seconds = elapsedSeconds % 60
        return when {
            window.endSeconds < 3_600 -> "${pad(minutes)}:${pad(seconds)}"
            step >= 60 -> "$hours:${pad(minutes)}"
            else -> "$hours:${pad(minutes)}:${pad(seconds)}"
        }
    }

    /** Returns the axis maximum and the tick list for a zero-based watt axis. */
    private fun powerAxis(maxVisibleWatts: Int): Pair<Int, List<PowerChartPowerTick>> {
        val effectiveMax = maxVisibleWatts.coerceAtLeast(1)
        val rawStep = (effectiveMax + TARGET_POWER_TICKS - 1) / TARGET_POWER_TICKS
        val step = POWER_STEPS_WATTS.firstOrNull { it >= rawStep } ?: POWER_STEPS_WATTS.last()
        // Pad the axis above the series maximum so the line never touches the top.
        val paddedMax = effectiveMax + (effectiveMax / 20).coerceAtLeast(1)
        val axisMax = ((paddedMax + step - 1) / step) * step
        val ticks = (0..axisMax step step).map { PowerChartPowerTick(it, it.toString()) }
        return axisMax to ticks
    }

    private fun pad(value: Long): String = value.toString().padStart(2, '0')
}
