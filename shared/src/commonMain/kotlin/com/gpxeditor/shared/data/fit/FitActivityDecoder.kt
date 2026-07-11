package com.gpxeditor.shared.data.fit

import com.gpxeditor.shared.core.time.unixSecondsToIso
import com.gpxeditor.shared.domain.fit.FitActivityDocument
import com.gpxeditor.shared.domain.fit.FitActivityMetadata
import com.gpxeditor.shared.domain.fit.FitActivityPoint
import com.gpxeditor.shared.domain.fit.FitActivitySegment
import com.gpxeditor.shared.domain.fit.FitActivityTrack
import com.gpxeditor.shared.domain.fit.FitDecodeError
import com.gpxeditor.shared.domain.fit.FitDecodeResult
import com.gpxeditor.shared.domain.fit.FitFileDecoder

/**
 * Pure-Kotlin [FitFileDecoder]. Validates the FIT header and CRC, decodes the
 * record stream into messages, and maps `record`/`session`/`file_id`/`sport`
 * messages into a [FitActivityDocument]. All `record` messages become points of
 * a single track/segment in stream order.
 */
class FitActivityDecoder : FitFileDecoder {
    override fun decode(bytes: ByteArray): FitDecodeResult {
        return try {
            FitDecodeResult.Success(decodeOrThrow(bytes))
        } catch (exception: FitDecodeException) {
            FitDecodeResult.Failure(FitDecodeError(exception.message ?: "Failed to decode FIT file."))
        }
    }

    private fun decodeOrThrow(bytes: ByteArray): FitActivityDocument {
        if (bytes.size < MIN_FILE_SIZE) {
            throw FitDecodeException("FIT file is too small.")
        }

        val headerSize = bytes[0].toInt() and 0xFF
        if (headerSize < MIN_HEADER_SIZE || headerSize > bytes.size) {
            throw FitDecodeException("Unsupported FIT header size.")
        }
        if (!hasFitSignature(bytes)) {
            throw FitDecodeException("Not a FIT file.")
        }

        val dataSize = readUInt32(bytes, 4)
        val dataEnd = headerSize + dataSize
        if (dataEnd + CRC_SIZE > bytes.size) {
            throw FitDecodeException("FIT file is truncated.")
        }

        validateHeaderCrc(bytes, headerSize)
        validateFileCrc(bytes, dataEnd)

        val messages = FitMessageDecoder(bytes, headerSize, dataEnd).decode()
        return toDocument(messages)
    }

    private fun hasFitSignature(bytes: ByteArray): Boolean {
        return bytes[8].toInt() == '.'.code &&
            bytes[9].toInt() == 'F'.code &&
            bytes[10].toInt() == 'I'.code &&
            bytes[11].toInt() == 'T'.code
    }

    private fun validateHeaderCrc(bytes: ByteArray, headerSize: Int) {
        if (headerSize < 14) {
            return
        }
        val expected = readUInt16(bytes, 12)
        if (expected != 0 && FitCrc.compute(bytes, 0, 12) != expected) {
            throw FitDecodeException("Invalid FIT header CRC.")
        }
    }

    private fun validateFileCrc(bytes: ByteArray, dataEnd: Int) {
        val expected = readUInt16(bytes, dataEnd)
        if (FitCrc.compute(bytes, 0, dataEnd) != expected) {
            throw FitDecodeException("Invalid FIT CRC.")
        }
    }

    private fun toDocument(messages: List<FitMessage>): FitActivityDocument {
        val points = messages
            .filter { it.globalMessageNumber == MESSAGE_RECORD }
            .map(::toPoint)
        val tracks = if (points.isEmpty()) {
            emptyList()
        } else {
            listOf(FitActivityTrack(segments = listOf(FitActivitySegment(points = points))))
        }
        return FitActivityDocument(
            metadata = toMetadata(messages, points),
            tracks = tracks,
        )
    }

    private fun toPoint(message: FitMessage): FitActivityPoint {
        val elevation = message.field(FIELD_ENHANCED_ALTITUDE)?.intValue()
            ?: message.field(FIELD_ALTITUDE)?.intValue()
        val speed = message.field(FIELD_ENHANCED_SPEED)?.intValue()
            ?: message.field(FIELD_SPEED)?.intValue()
        return FitActivityPoint(
            time = message.field(FIELD_TIMESTAMP)?.intValue()?.let(::fitTimestampToIso),
            latitude = message.field(FIELD_POSITION_LAT)?.intValue()?.let(::semicirclesToDegrees),
            longitude = message.field(FIELD_POSITION_LONG)?.intValue()?.let(::semicirclesToDegrees),
            elevationMeters = elevation?.let { it / ALTITUDE_SCALE - ALTITUDE_OFFSET },
            heartRateBpm = message.field(FIELD_HEART_RATE)?.intValue()?.toInt(),
            powerWatts = message.field(FIELD_POWER)?.intValue()?.toInt(),
            cadenceRpm = message.field(FIELD_CADENCE)?.intValue()?.toInt(),
            distanceMeters = message.field(FIELD_DISTANCE)?.intValue()?.let { it / DISTANCE_SCALE },
            speedMetersPerSecond = speed?.let { it / SPEED_SCALE },
        )
    }

    private fun toMetadata(messages: List<FitMessage>, points: List<FitActivityPoint>): FitActivityMetadata {
        val fileId = messages.firstOrNull { it.globalMessageNumber == MESSAGE_FILE_ID }
        val sport = messages.firstOrNull { it.globalMessageNumber == MESSAGE_SPORT }
        val session = messages.firstOrNull { it.globalMessageNumber == MESSAGE_SESSION }

        val sportCode = session?.field(FIELD_SESSION_SPORT)?.intValue()
            ?: sport?.field(FIELD_SPORT_SPORT)?.intValue()
        val startTimestamp = points.firstNotNullOfOrNull { it.time }
            ?: session?.field(FIELD_SESSION_START_TIME)?.intValue()?.let(::fitTimestampToIso)
            ?: fileId?.field(FIELD_FILE_ID_TIME_CREATED)?.intValue()?.let(::fitTimestampToIso)

        return FitActivityMetadata(
            name = sport?.field(FIELD_SPORT_NAME)?.stringValue(),
            sport = sportCode?.let(::sportName),
            startTime = startTimestamp,
            source = fileId?.field(FIELD_FILE_ID_PRODUCT_NAME)?.stringValue()
                ?: fileId?.field(FIELD_FILE_ID_MANUFACTURER)?.intValue()?.let(::manufacturerName),
        )
    }

    private fun semicirclesToDegrees(semicircles: Long): Double = semicircles * SEMICIRCLE_TO_DEGREES

    private fun sportName(code: Long): String = SPORTS[code.toInt()] ?: "sport_$code"

    private fun manufacturerName(code: Long): String? = MANUFACTURERS[code.toInt()]

    private fun fitTimestampToIso(timestamp: Long): String = unixSecondsToIso(timestamp + FIT_EPOCH_OFFSET)

    private fun readUInt16(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun readUInt32(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }

    private companion object {
        const val MIN_HEADER_SIZE = 12
        const val CRC_SIZE = 2
        const val MIN_FILE_SIZE = MIN_HEADER_SIZE + CRC_SIZE

        const val MESSAGE_FILE_ID = 0
        const val MESSAGE_SPORT = 12
        const val MESSAGE_SESSION = 18
        const val MESSAGE_RECORD = 20

        const val FIELD_TIMESTAMP = 253
        const val FIELD_POSITION_LAT = 0
        const val FIELD_POSITION_LONG = 1
        const val FIELD_ALTITUDE = 2
        const val FIELD_HEART_RATE = 3
        const val FIELD_CADENCE = 4
        const val FIELD_DISTANCE = 5
        const val FIELD_SPEED = 6
        const val FIELD_POWER = 7
        const val FIELD_ENHANCED_SPEED = 73
        const val FIELD_ENHANCED_ALTITUDE = 78

        const val FIELD_FILE_ID_MANUFACTURER = 1
        const val FIELD_FILE_ID_TIME_CREATED = 4
        const val FIELD_FILE_ID_PRODUCT_NAME = 8

        const val FIELD_SPORT_SPORT = 0
        const val FIELD_SPORT_NAME = 3

        const val FIELD_SESSION_START_TIME = 2
        const val FIELD_SESSION_SPORT = 5

        const val SEMICIRCLE_TO_DEGREES = 180.0 / 2147483648.0
        const val ALTITUDE_SCALE = 5.0
        const val ALTITUDE_OFFSET = 500.0
        const val DISTANCE_SCALE = 100.0
        const val SPEED_SCALE = 1000.0

        // Seconds between the Unix epoch and the FIT epoch (1989-12-31T00:00:00Z).
        const val FIT_EPOCH_OFFSET = 631065600L

        val SPORTS = mapOf(
            0 to "generic",
            1 to "running",
            2 to "cycling",
            5 to "swimming",
            11 to "walking",
            12 to "cross_country_skiing",
            13 to "alpine_skiing",
            14 to "snowboarding",
            15 to "rowing",
            17 to "hiking",
        )

        val MANUFACTURERS = mapOf(
            1 to "Garmin",
            263 to "Wahoo Fitness",
            265 to "Suunto",
        )
    }
}
