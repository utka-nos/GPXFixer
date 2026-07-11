package com.gpxeditor.shared.feature.recordtrack

/**
 * Line format for the crash-safe recording journal and its recovery.
 *
 * While recording, the platform appends one line per event (start, pause,
 * resume, accepted GPS point). If the process dies mid-recording, [recover]
 * replays the surviving lines through a fresh [TrackRecorder], so recovery
 * shares all recording logic. Malformed lines — e.g. a torn last write —
 * are skipped.
 */
object RecordingJournal {
    fun startLine(epochMillis: Long): String = "$START_EVENT $epochMillis"

    fun pauseLine(epochMillis: Long): String = "$PAUSE_EVENT $epochMillis"

    fun resumeLine(epochMillis: Long): String = "$RESUME_EVENT $epochMillis"

    fun pointLine(sample: LocationSample): String = buildString {
        append(POINT_EVENT)
        append(' ').append(sample.epochMillis)
        append(' ').append(sample.latitude)
        append(' ').append(sample.longitude)
        append(' ').append(sample.elevationMeters ?: NULL_FIELD)
        append(' ').append(sample.horizontalAccuracyMeters ?: NULL_FIELD)
        append(' ').append(sample.speedMetersPerSecond ?: NULL_FIELD)
    }

    /** Rebuilds the interrupted recording, or returns null if no recording was started. */
    fun recover(content: String): RecordedActivity? {
        var recorder: TrackRecorder? = null
        var lastEventMillis = 0L

        content.lineSequence().forEach { rawLine ->
            val parts = rawLine.trim().split(' ')
            when (parts.firstOrNull()) {
                START_EVENT -> {
                    if (recorder != null) return@forEach
                    val millis = parts.getOrNull(1)?.toLongOrNull() ?: return@forEach
                    recorder = TrackRecorder().also { it.start(millis) }
                    lastEventMillis = millis
                }

                PAUSE_EVENT -> {
                    val activeRecorder = recorder ?: return@forEach
                    val millis = parts.getOrNull(1)?.toLongOrNull() ?: return@forEach
                    if (activeRecorder.state == RecordingState.RECORDING) {
                        activeRecorder.pause(millis)
                        lastEventMillis = millis
                    }
                }

                RESUME_EVENT -> {
                    val activeRecorder = recorder ?: return@forEach
                    val millis = parts.getOrNull(1)?.toLongOrNull() ?: return@forEach
                    if (activeRecorder.state == RecordingState.PAUSED) {
                        activeRecorder.resume(millis)
                        lastEventMillis = millis
                    }
                }

                POINT_EVENT -> {
                    val activeRecorder = recorder ?: return@forEach
                    val sample = parsePoint(parts) ?: return@forEach
                    if (activeRecorder.onLocation(sample)) {
                        lastEventMillis = sample.epochMillis
                    }
                }
            }
        }

        return recorder?.stop(atEpochMillis = lastEventMillis)
    }

    private fun parsePoint(parts: List<String>): LocationSample? {
        if (parts.size < 7) return null

        return LocationSample(
            epochMillis = parts[1].toLongOrNull() ?: return null,
            latitude = parts[2].toDoubleOrNull() ?: return null,
            longitude = parts[3].toDoubleOrNull() ?: return null,
            elevationMeters = (parseOptionalDouble(parts[4]) ?: return null).value,
            horizontalAccuracyMeters = (parseOptionalDouble(parts[5]) ?: return null).value,
            speedMetersPerSecond = (parseOptionalDouble(parts[6]) ?: return null).value,
        )
    }

    // Wraps the parsed value so an absent field ("-") stays distinguishable
    // from a number that failed to parse (torn write).
    private data class OptionalDouble(val value: Double?)

    private fun parseOptionalDouble(text: String): OptionalDouble? {
        if (text == NULL_FIELD) return OptionalDouble(null)
        return text.toDoubleOrNull()?.let(::OptionalDouble)
    }

    private const val START_EVENT = "START"
    private const val PAUSE_EVENT = "PAUSE"
    private const val RESUME_EVENT = "RESUME"
    private const val POINT_EVENT = "POINT"
    private const val NULL_FIELD = "-"
}
