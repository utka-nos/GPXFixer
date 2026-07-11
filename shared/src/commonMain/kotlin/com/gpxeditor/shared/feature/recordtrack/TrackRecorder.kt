package com.gpxeditor.shared.feature.recordtrack

import com.gpxeditor.shared.core.geo.haversineMeters
import com.gpxeditor.shared.core.time.unixSecondsToIso
import com.gpxeditor.shared.domain.activity.ActivityDocument
import com.gpxeditor.shared.domain.activity.ActivityMetadata
import com.gpxeditor.shared.domain.activity.ActivityPoint
import com.gpxeditor.shared.domain.activity.ActivitySegment
import com.gpxeditor.shared.domain.activity.ActivityTrack
import com.gpxeditor.shared.data.ble.PowerSample
import kotlin.math.roundToInt

/**
 * Records incoming [LocationSample]s into an [ActivityDocument].
 *
 * Pure state machine without platform dependencies: callers provide the
 * current time explicitly, so behaviour is fully deterministic in tests.
 * Each pause/resume cycle starts a new segment, and the gap between them
 * does not count towards distance or elapsed time.
 */
class TrackRecorder(
    private val config: TrackRecorderConfig = TrackRecorderConfig(),
) {
    var state: RecordingState = RecordingState.IDLE
        private set

    private val segments = mutableListOf<MutableList<ActivityPoint>>()
    private var lastAcceptedSample: LocationSample? = null
    private var distanceMeters = 0.0
    private var currentSpeedMetersPerSecond: Double? = null
    private var completedActiveMillis = 0L
    private var activeSpanStartMillis: Long? = null
    private var pointCount = 0
    private var latestPower: TimedPowerSample? = null

    fun start(atEpochMillis: Long) {
        check(state == RecordingState.IDLE) { "Recording can only be started from the idle state." }
        state = RecordingState.RECORDING
        activeSpanStartMillis = atEpochMillis
        segments += mutableListOf<ActivityPoint>()
    }

    fun pause(atEpochMillis: Long) {
        check(state == RecordingState.RECORDING) { "Recording can only be paused while recording." }
        state = RecordingState.PAUSED
        closeActiveSpan(atEpochMillis)
        lastAcceptedSample = null
        currentSpeedMetersPerSecond = null
    }

    fun resume(atEpochMillis: Long) {
        check(state == RecordingState.PAUSED) { "Recording can only be resumed while paused." }
        state = RecordingState.RECORDING
        activeSpanStartMillis = atEpochMillis
        segments += mutableListOf<ActivityPoint>()
    }

    fun stop(atEpochMillis: Long, name: String? = null): RecordedActivity {
        check(state == RecordingState.RECORDING || state == RecordingState.PAUSED) {
            "Recording can only be stopped while recording or paused."
        }
        closeActiveSpan(atEpochMillis)
        state = RecordingState.STOPPED

        return RecordedActivity(
            document = buildDocument(name),
            stats = stats(atEpochMillis),
        )
    }

    /** Feeds a GPS fix into the recording. Returns true if the sample became a track point. */
    fun onLocation(sample: LocationSample): Boolean {
        if (state != RecordingState.RECORDING) return false
        if (!sample.isAcceptable()) return false

        val previous = lastAcceptedSample
        if (previous != null) {
            if (sample.epochMillis <= previous.epochMillis) return false

            val stepMeters = haversineMeters(
                previous.latitude,
                previous.longitude,
                sample.latitude,
                sample.longitude,
            )
            distanceMeters += stepMeters
            currentSpeedMetersPerSecond = sample.speedMetersPerSecond
                ?: (stepMeters / ((sample.epochMillis - previous.epochMillis) / 1000.0))
        } else {
            currentSpeedMetersPerSecond = sample.speedMetersPerSecond
        }

        segments.last() += sample.toActivityPoint(powerAt(sample.epochMillis))
        lastAcceptedSample = sample
        pointCount += 1
        return true
    }

    fun onPower(sample: PowerSample, atEpochMillis: Long) {
        latestPower = TimedPowerSample(sample, atEpochMillis)
    }

    fun stats(nowEpochMillis: Long): RecordingStats {
        val runningSpanMillis = activeSpanStartMillis
            ?.let { (nowEpochMillis - it).coerceAtLeast(0L) }
            ?: 0L

        return RecordingStats(
            state = state,
            elapsedMillis = completedActiveMillis + runningSpanMillis,
            distanceMeters = distanceMeters,
            currentSpeedMetersPerSecond = currentSpeedMetersPerSecond,
            pointCount = pointCount,
            currentPowerWatts = powerAt(nowEpochMillis)?.watts,
            currentCadenceRpm = powerAt(nowEpochMillis)?.cadenceRpm?.roundToInt(),
        )
    }

    private fun closeActiveSpan(atEpochMillis: Long) {
        val spanStart = activeSpanStartMillis ?: return
        completedActiveMillis += (atEpochMillis - spanStart).coerceAtLeast(0L)
        activeSpanStartMillis = null
    }

    private fun LocationSample.isAcceptable(): Boolean {
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return false

        val accuracy = horizontalAccuracyMeters
        return accuracy == null || accuracy <= config.maxHorizontalAccuracyMeters
    }

    private fun powerAt(epochMillis: Long): PowerSample? {
        val timed = latestPower ?: return null
        val ageMillis = epochMillis - timed.epochMillis
        return timed.sample.takeIf { ageMillis in 0..config.powerSampleTimeoutMillis }
    }

    private fun LocationSample.toActivityPoint(power: PowerSample?): ActivityPoint {
        return ActivityPoint(
            time = unixSecondsToIso(epochMillis.floorDiv(1000L)),
            latitude = latitude,
            longitude = longitude,
            elevationMeters = elevationMeters,
            speedMetersPerSecond = speedMetersPerSecond,
            powerWatts = power?.watts,
            cadenceRpm = power?.cadenceRpm?.roundToInt(),
        )
    }

    private fun buildDocument(name: String?): ActivityDocument {
        val recordedSegments = segments
            .filter { it.isNotEmpty() }
            .map { ActivitySegment(points = it.toList()) }
        val startTime = recordedSegments.firstOrNull()?.points?.firstOrNull()?.time

        return ActivityDocument(
            metadata = ActivityMetadata(
                name = name,
                sport = config.sport,
                startTime = startTime,
            ),
            tracks = if (recordedSegments.isEmpty()) {
                emptyList()
            } else {
                listOf(ActivityTrack(name = name, segments = recordedSegments))
            },
        )
    }
}

data class TrackRecorderConfig(
    val sport: String = "cycling",
    val maxHorizontalAccuracyMeters: Double = 50.0,
    val powerSampleTimeoutMillis: Long = 5_000L,
)

enum class RecordingState {
    IDLE,
    RECORDING,
    PAUSED,
    STOPPED,
}

data class RecordingStats(
    val state: RecordingState,
    val elapsedMillis: Long,
    val distanceMeters: Double,
    val currentSpeedMetersPerSecond: Double?,
    val pointCount: Int,
    val currentPowerWatts: Int?,
    val currentCadenceRpm: Int?,
)

private data class TimedPowerSample(val sample: PowerSample, val epochMillis: Long)

data class RecordedActivity(
    val document: ActivityDocument,
    val stats: RecordingStats,
)
