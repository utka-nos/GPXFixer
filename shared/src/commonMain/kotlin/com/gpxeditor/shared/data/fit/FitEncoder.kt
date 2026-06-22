package com.gpxeditor.shared.data.fit

import com.gpxeditor.shared.domain.activity.ActivityDocument
import com.gpxeditor.shared.domain.activity.ActivityPoint
import kotlin.math.roundToLong

/**
 * Encodes an [ActivityDocument] into a valid FIT activity file: a 12-byte header,
 * a `file_id` message, one `record` message per point, and a trailing CRC.
 *
 * The record definition uses a fixed superset of the fields present anywhere in the
 * document; points that lack a value emit the FIT "invalid" sentinel, exactly as
 * real devices do. Only the fields modelled by [ActivityPoint] are written, so this
 * is not a byte-for-byte round-trip of an imported FIT yet — it preserves the data
 * the editor works with (GPS, elevation, time, heart rate, cadence, power,
 * distance, speed).
 */
object FitEncoder {
    private const val FILE_ID_LOCAL_TYPE = 0
    private const val RECORD_LOCAL_TYPE = 1
    private const val MESSAGE_FILE_ID = 0
    private const val MESSAGE_RECORD = 20

    // file_id: type=activity, manufacturer=development.
    private const val FILE_TYPE_ACTIVITY = 4L
    private const val MANUFACTURER_DEVELOPMENT = 255L

    private const val FIT_EPOCH_OFFSET = 631065600L
    private const val INVALID_UINT32 = 0xFFFFFFFEL

    fun encode(document: ActivityDocument): ByteArray {
        val points = document.tracks
            .flatMap { it.segments }
            .flatMap { it.points }

        val body = FitWriter()
        writeFileId(body, points)

        val fields = RecordField.entries.filter { field -> points.any { field.rawValue(it) != null } }
        if (fields.isNotEmpty()) {
            writeRecordDefinition(body, fields)
            for (point in points) {
                writeRecord(body, fields, point)
            }
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
        return (unix - FIT_EPOCH_OFFSET).coerceIn(0L, INVALID_UINT32)
    }

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
