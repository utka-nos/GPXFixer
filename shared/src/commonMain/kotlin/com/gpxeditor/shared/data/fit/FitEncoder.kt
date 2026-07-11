package com.gpxeditor.shared.data.fit

import com.gpxeditor.shared.core.geo.haversineMeters
import com.gpxeditor.shared.core.time.utcOffsetSecondsAt
import com.gpxeditor.shared.domain.activity.ActivityDocument
import com.gpxeditor.shared.domain.activity.ActivityPoint
import kotlin.math.roundToLong

/**
 * Encodes an [ActivityDocument] into a valid FIT activity file with the message
 * sequence services like Strava require to finalize an upload: a `file_id`,
 * timer `event`s wrapping the `record` stream (one start/stop pair per segment,
 * so recording pauses survive), a single `lap`, a `session` carrying the
 * aggregate totals, and a closing `activity` with UTC and local timestamps.
 *
 * The record definition uses a fixed superset of the fields present anywhere in
 * the document; points that lack a value emit the FIT "invalid" sentinel,
 * exactly as real devices do. Session/lap aggregates are computed from the
 * points and omitted when the underlying data is absent. The summary messages
 * are only written when at least one point carries a timestamp — without time
 * data the file cannot describe a meaningful activity.
 */
object FitEncoder {
    private const val FILE_ID_LOCAL_TYPE = 0
    private const val RECORD_LOCAL_TYPE = 1
    private const val EVENT_LOCAL_TYPE = 2
    private const val LAP_LOCAL_TYPE = 3
    private const val SESSION_LOCAL_TYPE = 4
    private const val ACTIVITY_LOCAL_TYPE = 5

    private const val MESSAGE_FILE_ID = 0
    private const val MESSAGE_SESSION = 18
    private const val MESSAGE_LAP = 19
    private const val MESSAGE_RECORD = 20
    private const val MESSAGE_EVENT = 21
    private const val MESSAGE_ACTIVITY = 34

    // file_id: type=activity, manufacturer=development.
    private const val FILE_TYPE_ACTIVITY = 4L
    private const val MANUFACTURER_DEVELOPMENT = 255L

    // FIT `event` / `event_type` / `activity.type` enum values.
    private const val EVENT_TIMER = 0L
    private const val EVENT_SESSION = 8L
    private const val EVENT_LAP = 9L
    private const val EVENT_ACTIVITY = 26L
    private const val EVENT_TYPE_START = 0L
    private const val EVENT_TYPE_STOP = 1L
    private const val EVENT_TYPE_STOP_ALL = 4L
    private const val ACTIVITY_TYPE_MANUAL = 0L

    private const val FIT_EPOCH_OFFSET = 631065600L
    private const val INVALID_UINT32 = 0xFFFFFFFEL

    /**
     * [localTimeOffsetSeconds] is the offset used for the `activity` message's
     * `local_timestamp`; when `null` the device time zone at the end of the
     * activity is used. Tests pass an explicit value for determinism.
     */
    fun encode(document: ActivityDocument, localTimeOffsetSeconds: Long? = null): ByteArray {
        val segments = document.tracks
            .flatMap { it.segments }
            .map { it.points }
            .filter { it.isNotEmpty() }
        val points = segments.flatten()
        val summary = summarize(document.metadata.sport, segments)

        val body = FitWriter()
        writeFileId(body, points)

        val fields = RecordField.entries.filter { field -> points.any { field.rawValue(it) != null } }
        if (fields.isNotEmpty()) {
            writeRecordDefinition(body, fields)
            if (summary != null) {
                writeEventDefinition(body)
            }
            val spans = segments.map(::segmentSpanUnixSeconds)
            val lastTimedIndex = spans.indexOfLast { it != null }
            for ((index, segment) in segments.withIndex()) {
                val span = spans[index]
                if (span != null) {
                    writeTimerEvent(body, span.first, EVENT_TYPE_START)
                }
                for (point in segment) {
                    writeRecord(body, fields, point)
                }
                if (span != null) {
                    val eventType = if (index == lastTimedIndex) EVENT_TYPE_STOP_ALL else EVENT_TYPE_STOP
                    writeTimerEvent(body, span.second, eventType)
                }
            }
        }

        if (summary != null) {
            writeLap(body, summary)
            writeSession(body, summary)
            val offset = localTimeOffsetSeconds ?: utcOffsetSecondsAt(summary.endUnixSeconds)
            writeActivity(body, summary, offset)
        }

        return assemble(body.toByteArray())
    }

    private fun writeFileId(writer: FitWriter, points: List<ActivityPoint>) {
        writer.writeValue((0x40 or FILE_ID_LOCAL_TYPE).toLong(), 1) // definition header
        writer.writeValue(0, 1) // reserved
        writer.writeValue(0, 1) // architecture: little-endian
        writer.writeValue(MESSAGE_FILE_ID.toLong(), 2)
        writer.writeValue(3, 1) // field count
        writer.writeFieldDefinition(number = 0, size = 1, baseType = 0x00) // type, enum
        writer.writeFieldDefinition(number = 1, size = 2, baseType = 0x84) // manufacturer, uint16
        writer.writeFieldDefinition(number = 4, size = 4, baseType = 0x86) // time_created, uint32

        writer.writeValue(FILE_ID_LOCAL_TYPE.toLong(), 1) // data header
        writer.writeValue(FILE_TYPE_ACTIVITY, 1)
        writer.writeValue(MANUFACTURER_DEVELOPMENT, 2)
        val timeCreated = points.firstNotNullOfOrNull { it.time }
            ?.let(::isoToFitTimestamp)
            ?: INVALID_UINT32
        writer.writeValue(timeCreated, 4)
    }

    private fun writeRecordDefinition(writer: FitWriter, fields: List<RecordField>) {
        writer.writeValue((0x40 or RECORD_LOCAL_TYPE).toLong(), 1) // definition header
        writer.writeValue(0, 1) // reserved
        writer.writeValue(0, 1) // architecture: little-endian
        writer.writeValue(MESSAGE_RECORD.toLong(), 2)
        writer.writeValue(fields.size.toLong(), 1)
        for (field in fields) {
            writer.writeFieldDefinition(field.number, field.size, field.baseType)
        }
    }

    private fun writeRecord(writer: FitWriter, fields: List<RecordField>, point: ActivityPoint) {
        writer.writeValue(RECORD_LOCAL_TYPE.toLong(), 1) // data header
        for (field in fields) {
            writer.writeValue(field.rawValue(point) ?: field.invalid, field.size)
        }
    }

    private fun writeEventDefinition(writer: FitWriter) {
        writer.writeValue((0x40 or EVENT_LOCAL_TYPE).toLong(), 1) // definition header
        writer.writeValue(0, 1) // reserved
        writer.writeValue(0, 1) // architecture: little-endian
        writer.writeValue(MESSAGE_EVENT.toLong(), 2)
        writer.writeValue(4, 1) // field count
        writer.writeFieldDefinition(number = 253, size = 4, baseType = 0x86) // timestamp, uint32
        writer.writeFieldDefinition(number = 0, size = 1, baseType = 0x00) // event, enum
        writer.writeFieldDefinition(number = 1, size = 1, baseType = 0x00) // event_type, enum
        writer.writeFieldDefinition(number = 4, size = 1, baseType = 0x02) // event_group, uint8
    }

    private fun writeTimerEvent(writer: FitWriter, unixSeconds: Long, eventType: Long) {
        writer.writeValue(EVENT_LOCAL_TYPE.toLong(), 1) // data header
        writer.writeValue(unixToFitTimestamp(unixSeconds), 4)
        writer.writeValue(EVENT_TIMER, 1)
        writer.writeValue(eventType, 1)
        writer.writeValue(0, 1) // event_group
    }

    private fun writeLap(writer: FitWriter, summary: ActivitySummary) {
        val fields = buildList {
            add(MessageField(number = 254, baseType = 0x84, size = 2, value = 0)) // message_index
            add(MessageField(number = 253, baseType = 0x86, size = 4, value = unixToFitTimestamp(summary.endUnixSeconds)))
            add(MessageField(number = 0, baseType = 0x00, size = 1, value = EVENT_LAP)) // event
            add(MessageField(number = 1, baseType = 0x00, size = 1, value = EVENT_TYPE_STOP)) // event_type
            add(MessageField(number = 2, baseType = 0x86, size = 4, value = unixToFitTimestamp(summary.startUnixSeconds))) // start_time
            add(MessageField(number = 7, baseType = 0x86, size = 4, value = summary.elapsedMillis)) // total_elapsed_time
            add(MessageField(number = 8, baseType = 0x86, size = 4, value = summary.timerMillis)) // total_timer_time
            summary.distanceCentimeters?.let { add(MessageField(number = 9, baseType = 0x86, size = 4, value = it)) } // total_distance
            summary.avgSpeedRaw?.let { add(MessageField(number = 13, baseType = 0x84, size = 2, value = it)) }
            summary.maxSpeedRaw?.let { add(MessageField(number = 14, baseType = 0x84, size = 2, value = it)) }
            summary.avgHeartRate?.let { add(MessageField(number = 15, baseType = 0x02, size = 1, value = it)) }
            summary.maxHeartRate?.let { add(MessageField(number = 16, baseType = 0x02, size = 1, value = it)) }
            summary.avgCadence?.let { add(MessageField(number = 17, baseType = 0x02, size = 1, value = it)) }
            summary.maxCadence?.let { add(MessageField(number = 18, baseType = 0x02, size = 1, value = it)) }
            summary.avgPower?.let { add(MessageField(number = 19, baseType = 0x84, size = 2, value = it)) }
            summary.maxPower?.let { add(MessageField(number = 20, baseType = 0x84, size = 2, value = it)) }
            add(MessageField(number = 25, baseType = 0x00, size = 1, value = summary.sportCode)) // sport
        }
        writeSummaryMessage(writer, MESSAGE_LAP, LAP_LOCAL_TYPE, fields)
    }

    private fun writeSession(writer: FitWriter, summary: ActivitySummary) {
        val fields = buildList {
            add(MessageField(number = 254, baseType = 0x84, size = 2, value = 0)) // message_index
            add(MessageField(number = 253, baseType = 0x86, size = 4, value = unixToFitTimestamp(summary.endUnixSeconds)))
            add(MessageField(number = 0, baseType = 0x00, size = 1, value = EVENT_SESSION)) // event
            add(MessageField(number = 1, baseType = 0x00, size = 1, value = EVENT_TYPE_STOP)) // event_type
            add(MessageField(number = 2, baseType = 0x86, size = 4, value = unixToFitTimestamp(summary.startUnixSeconds))) // start_time
            add(MessageField(number = 5, baseType = 0x00, size = 1, value = summary.sportCode)) // sport
            add(MessageField(number = 7, baseType = 0x86, size = 4, value = summary.elapsedMillis)) // total_elapsed_time
            add(MessageField(number = 8, baseType = 0x86, size = 4, value = summary.timerMillis)) // total_timer_time
            summary.distanceCentimeters?.let { add(MessageField(number = 9, baseType = 0x86, size = 4, value = it)) } // total_distance
            summary.avgSpeedRaw?.let { add(MessageField(number = 14, baseType = 0x84, size = 2, value = it)) }
            summary.maxSpeedRaw?.let { add(MessageField(number = 15, baseType = 0x84, size = 2, value = it)) }
            summary.avgHeartRate?.let { add(MessageField(number = 16, baseType = 0x02, size = 1, value = it)) }
            summary.maxHeartRate?.let { add(MessageField(number = 17, baseType = 0x02, size = 1, value = it)) }
            summary.avgCadence?.let { add(MessageField(number = 18, baseType = 0x02, size = 1, value = it)) }
            summary.maxCadence?.let { add(MessageField(number = 19, baseType = 0x02, size = 1, value = it)) }
            summary.avgPower?.let { add(MessageField(number = 20, baseType = 0x84, size = 2, value = it)) }
            summary.maxPower?.let { add(MessageField(number = 21, baseType = 0x84, size = 2, value = it)) }
            add(MessageField(number = 25, baseType = 0x84, size = 2, value = 0)) // first_lap_index
            add(MessageField(number = 26, baseType = 0x84, size = 2, value = 1)) // num_laps
        }
        writeSummaryMessage(writer, MESSAGE_SESSION, SESSION_LOCAL_TYPE, fields)
    }

    private fun writeActivity(writer: FitWriter, summary: ActivitySummary, localTimeOffsetSeconds: Long) {
        val fields = listOf(
            MessageField(number = 253, baseType = 0x86, size = 4, value = unixToFitTimestamp(summary.endUnixSeconds)),
            MessageField(number = 0, baseType = 0x86, size = 4, value = summary.timerMillis), // total_timer_time
            MessageField(number = 1, baseType = 0x84, size = 2, value = 1), // num_sessions
            MessageField(number = 2, baseType = 0x00, size = 1, value = ACTIVITY_TYPE_MANUAL), // type
            MessageField(number = 3, baseType = 0x00, size = 1, value = EVENT_ACTIVITY), // event
            MessageField(number = 4, baseType = 0x00, size = 1, value = EVENT_TYPE_STOP), // event_type
            MessageField(
                number = 5, // local_timestamp
                baseType = 0x86,
                size = 4,
                value = unixToFitTimestamp(summary.endUnixSeconds + localTimeOffsetSeconds),
            ),
        )
        writeSummaryMessage(writer, MESSAGE_ACTIVITY, ACTIVITY_LOCAL_TYPE, fields)
    }

    /** Writes a definition message immediately followed by a single data message. */
    private fun writeSummaryMessage(
        writer: FitWriter,
        globalMessageNumber: Int,
        localType: Int,
        fields: List<MessageField>,
    ) {
        writer.writeValue((0x40 or localType).toLong(), 1) // definition header
        writer.writeValue(0, 1) // reserved
        writer.writeValue(0, 1) // architecture: little-endian
        writer.writeValue(globalMessageNumber.toLong(), 2)
        writer.writeValue(fields.size.toLong(), 1)
        for (field in fields) {
            writer.writeFieldDefinition(field.number, field.size, field.baseType)
        }
        writer.writeValue(localType.toLong(), 1) // data header
        for (field in fields) {
            writer.writeValue(field.value, field.size)
        }
    }

    private fun summarize(sport: String?, segments: List<List<ActivityPoint>>): ActivitySummary? {
        val spans = segments.mapNotNull(::segmentSpanUnixSeconds)
        if (spans.isEmpty()) {
            return null
        }
        val startUnixSeconds = spans.minOf { it.first }
        val endUnixSeconds = spans.maxOf { it.second }
        val timerSeconds = spans.sumOf { it.second - it.first }

        val points = segments.flatten()
        val distanceMeters = totalDistanceMeters(segments)
        val speeds = points.mapNotNull { it.speedMetersPerSecond }
        // FIT semantics: avg_speed = total_distance / total_timer_time.
        val avgSpeed = when {
            distanceMeters != null && timerSeconds > 0 -> distanceMeters / timerSeconds
            speeds.isNotEmpty() -> speeds.average()
            else -> null
        }
        val heartRates = points.mapNotNull { it.heartRateBpm }
        val cadences = points.mapNotNull { it.cadenceRpm }
        val powers = points.mapNotNull { it.powerWatts }

        return ActivitySummary(
            sportCode = FitSports.code(sport),
            startUnixSeconds = startUnixSeconds,
            endUnixSeconds = endUnixSeconds,
            elapsedMillis = ((endUnixSeconds - startUnixSeconds) * 1000L).coerceIn(0L, INVALID_UINT32),
            timerMillis = (timerSeconds * 1000L).coerceIn(0L, INVALID_UINT32),
            distanceCentimeters = distanceMeters
                ?.let { (it * 100.0).roundToLong().coerceIn(0L, INVALID_UINT32) },
            avgSpeedRaw = avgSpeed?.let(::speedToRaw),
            maxSpeedRaw = speeds.maxOrNull()?.let(::speedToRaw),
            avgHeartRate = heartRates.averageRaw(max = 0xFEL),
            maxHeartRate = heartRates.maxRaw(max = 0xFEL),
            avgCadence = cadences.averageRaw(max = 0xFEL),
            maxCadence = cadences.maxRaw(max = 0xFEL),
            avgPower = powers.averageRaw(max = 0xFFFEL),
            maxPower = powers.maxRaw(max = 0xFFFEL),
        )
    }

    /** First and last point timestamps of a segment, in Unix seconds. */
    private fun segmentSpanUnixSeconds(points: List<ActivityPoint>): Pair<Long, Long>? {
        val times = points.mapNotNull { point -> point.time?.let(::isoToUnixSeconds) }
        if (times.isEmpty()) {
            return null
        }
        return times.min() to times.max()
    }

    /**
     * Total distance covered, in meters. Each segment contributes the distance its
     * recorded cumulative counter advanced within the segment — not the absolute
     * counter value, which is wrong after a trim or when a new segment/source
     * resets the counter. Segments without recorded distance fall back to the
     * great-circle distance over their located points. Pause gaps between
     * segments never count.
     */
    private fun totalDistanceMeters(segments: List<List<ActivityPoint>>): Double? {
        var total = 0.0
        var hasDistance = false
        for (segment in segments) {
            val distance = recordedDistanceMeters(segment) ?: coordinateDistanceMeters(segment) ?: continue
            total += distance
            hasDistance = true
        }
        return total.takeIf { hasDistance }
    }

    /**
     * Distance the segment's cumulative distance counter advanced, as the sum of
     * positive deltas so a mid-segment counter reset does not go negative.
     */
    private fun recordedDistanceMeters(segment: List<ActivityPoint>): Double? {
        val distances = segment.mapNotNull { it.distanceMeters }
        if (distances.size < 2) {
            return null
        }
        return distances.zipWithNext().sumOf { (previous, next) -> (next - previous).coerceAtLeast(0.0) }
    }

    /** Cumulative great-circle distance over the segment's consecutive located points. */
    private fun coordinateDistanceMeters(segment: List<ActivityPoint>): Double? {
        var total = 0.0
        var hasLeg = false
        var previousLatitude: Double? = null
        var previousLongitude: Double? = null
        for (point in segment) {
            val latitude = point.latitude ?: continue
            val longitude = point.longitude ?: continue
            if (previousLatitude != null && previousLongitude != null) {
                total += haversineMeters(previousLatitude, previousLongitude, latitude, longitude)
                hasLeg = true
            }
            previousLatitude = latitude
            previousLongitude = longitude
        }
        return total.takeIf { hasLeg }
    }

    private fun speedToRaw(metersPerSecond: Double): Long =
        (metersPerSecond * 1000.0).roundToLong().coerceIn(0L, 0xFFFEL)

    private fun List<Int>.averageRaw(max: Long): Long? =
        takeIf { it.isNotEmpty() }?.average()?.roundToLong()?.coerceIn(0L, max)

    private fun List<Int>.maxRaw(max: Long): Long? =
        maxOrNull()?.toLong()?.coerceIn(0L, max)

    private fun assemble(body: ByteArray): ByteArray {
        val header = ByteArray(12)
        header[0] = 12 // header size
        header[1] = 0x20 // protocol version 2.0
        // profile version [2..3] left as 0
        val dataSize = body.size
        header[4] = (dataSize and 0xFF).toByte()
        header[5] = ((dataSize shr 8) and 0xFF).toByte()
        header[6] = ((dataSize shr 16) and 0xFF).toByte()
        header[7] = ((dataSize shr 24) and 0xFF).toByte()
        header[8] = '.'.code.toByte()
        header[9] = 'F'.code.toByte()
        header[10] = 'I'.code.toByte()
        header[11] = 'T'.code.toByte()

        val withoutCrc = header + body
        val crc = FitCrc.compute(withoutCrc, 0, withoutCrc.size)
        return withoutCrc + byteArrayOf((crc and 0xFF).toByte(), ((crc shr 8) and 0xFF).toByte())
    }

    private fun isoToFitTimestamp(iso: String): Long? {
        val unix = isoToUnixSeconds(iso) ?: return null
        return unixToFitTimestamp(unix)
    }

    private fun unixToFitTimestamp(unixSeconds: Long): Long =
        (unixSeconds - FIT_EPOCH_OFFSET).coerceIn(0L, INVALID_UINT32)

    /** A single field of a definition+data message pair: profile number, base type, size, raw value. */
    private class MessageField(
        val number: Int,
        val baseType: Int,
        val size: Int,
        val value: Long,
    )

    /** Aggregates for the `lap`/`session`/`activity` messages, already in raw FIT units. */
    private class ActivitySummary(
        val sportCode: Long,
        val startUnixSeconds: Long,
        val endUnixSeconds: Long,
        val elapsedMillis: Long,
        val timerMillis: Long,
        val distanceCentimeters: Long?,
        val avgSpeedRaw: Long?,
        val maxSpeedRaw: Long?,
        val avgHeartRate: Long?,
        val maxHeartRate: Long?,
        val avgCadence: Long?,
        val maxCadence: Long?,
        val avgPower: Long?,
        val maxPower: Long?,
    )

    /**
     * The fixed set of record fields the encoder can produce. Each knows how to turn
     * an [ActivityPoint] into its raw FIT integer (or `null` when the point lacks it)
     * and the sentinel to emit when a value is absent.
     */
    private enum class RecordField(
        val number: Int,
        val baseType: Int,
        val size: Int,
        val invalid: Long,
    ) {
        TIMESTAMP(253, 0x86, 4, INVALID_UINT32) {
            override fun rawValue(point: ActivityPoint): Long? = point.time?.let(::isoToFitTimestamp)
        },
        LATITUDE(0, 0x85, 4, 0x7FFFFFFFL) {
            override fun rawValue(point: ActivityPoint): Long? =
                if (point.latitude != null && point.longitude != null) {
                    degreesToSemicircles(point.latitude)
                } else {
                    null
                }
        },
        LONGITUDE(1, 0x85, 4, 0x7FFFFFFFL) {
            override fun rawValue(point: ActivityPoint): Long? =
                if (point.latitude != null && point.longitude != null) {
                    degreesToSemicircles(point.longitude)
                } else {
                    null
                }
        },
        ALTITUDE(2, 0x84, 2, 0xFFFFL) {
            override fun rawValue(point: ActivityPoint): Long? = point.elevationMeters
                ?.let { ((it + 500.0) * 5.0).roundToLong().coerceIn(0L, 0xFFFEL) }
        },
        HEART_RATE(3, 0x02, 1, 0xFFL) {
            override fun rawValue(point: ActivityPoint): Long? =
                point.heartRateBpm?.toLong()?.coerceIn(0L, 0xFEL)
        },
        CADENCE(4, 0x02, 1, 0xFFL) {
            override fun rawValue(point: ActivityPoint): Long? =
                point.cadenceRpm?.toLong()?.coerceIn(0L, 0xFEL)
        },
        DISTANCE(5, 0x86, 4, INVALID_UINT32) {
            override fun rawValue(point: ActivityPoint): Long? = point.distanceMeters
                ?.let { (it * 100.0).roundToLong().coerceIn(0L, INVALID_UINT32) }
        },
        SPEED(6, 0x84, 2, 0xFFFFL) {
            override fun rawValue(point: ActivityPoint): Long? = point.speedMetersPerSecond
                ?.let { (it * 1000.0).roundToLong().coerceIn(0L, 0xFFFEL) }
        },
        POWER(7, 0x84, 2, 0xFFFFL) {
            override fun rawValue(point: ActivityPoint): Long? =
                point.powerWatts?.toLong()?.coerceIn(0L, 0xFFFEL)
        },
        ;

        abstract fun rawValue(point: ActivityPoint): Long?
    }
}

private const val SEMICIRCLE_RANGE = 2147483648.0

private fun degreesToSemicircles(degrees: Double): Long =
    (degrees * SEMICIRCLE_RANGE / 180.0).roundToLong().coerceIn(-2147483648L, 2147483647L)

/** Minimal growable little-endian byte buffer for assembling FIT records. */
private class FitWriter {
    private val bytes = ArrayList<Byte>()

    fun writeValue(value: Long, size: Int) {
        for (i in 0 until size) {
            bytes.add(((value shr (8 * i)) and 0xFF).toByte())
        }
    }

    fun writeFieldDefinition(number: Int, size: Int, baseType: Int) {
        bytes.add(number.toByte())
        bytes.add(size.toByte())
        bytes.add(baseType.toByte())
    }

    fun toByteArray(): ByteArray = bytes.toByteArray()
}

/**
 * Parses an ISO-8601 UTC instant (`yyyy-MM-ddTHH:mm:ss`, optional fractional seconds
 * and trailing `Z`) into Unix epoch seconds. Returns `null` for anything it cannot
 * parse; non-UTC offsets are not supported and treated as UTC.
 */
private fun isoToUnixSeconds(iso: String): Long? {
    if (iso.length < 19) return null
    return try {
        val year = iso.substring(0, 4).toInt()
        if (iso[4] != '-' || iso[7] != '-') return null
        val month = iso.substring(5, 7).toInt()
        val day = iso.substring(8, 10).toInt()
        if (iso[10] != 'T' && iso[10] != ' ') return null
        val hour = iso.substring(11, 13).toInt()
        val minute = iso.substring(14, 16).toInt()
        val second = iso.substring(17, 19).toInt()
        daysFromCivil(year, month, day) * 86400L + hour * 3600L + minute * 60L + second
    } catch (exception: NumberFormatException) {
        null
    }
}

/** Days since 1970-01-01 for a civil date (Howard Hinnant's algorithm). */
private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
    val y = if (month <= 2) year - 1L else year.toLong()
    val era = (if (y >= 0) y else y - 399) / 400
    val yearOfEra = y - era * 400
    val monthOffset = if (month > 2) month - 3 else month + 9
    val dayOfYear = (153 * monthOffset + 2) / 5 + day - 1
    val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
    return era * 146097 + dayOfEra - 719468
}
