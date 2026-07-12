package com.gpxeditor.shared.data.ble

/** A heart-rate reading decoded from the Heart Rate Measurement characteristic (0x2A37). */
data class HeartRateSample(
    val bpm: Int,
)

/**
 * Decoder for Bluetooth Heart Rate Measurement notifications.
 *
 * The flags byte selects an 8-bit or 16-bit heart rate value; the optional
 * energy-expended and RR-interval fields that may follow are ignored.
 */
class HeartRateMeasurementParser {
    fun parse(bytes: ByteArray): HeartRateSample {
        if (bytes.isEmpty()) {
            throw HeartRateParseException("Heart Rate Measurement is empty.")
        }

        val flags = bytes[0].toInt() and 0xFF
        val bpm = if (flags and HEART_RATE_VALUE_16_BIT != 0) {
            requireLength(bytes, 3, "16-bit heart rate value")
            (bytes[1].toInt() and 0xFF) or ((bytes[2].toInt() and 0xFF) shl 8)
        } else {
            requireLength(bytes, 2, "8-bit heart rate value")
            bytes[1].toInt() and 0xFF
        }

        return HeartRateSample(bpm = bpm)
    }

    private fun requireLength(bytes: ByteArray, length: Int, field: String) {
        if (bytes.size < length) {
            throw HeartRateParseException("Heart Rate Measurement is truncated while reading $field.")
        }
    }

    private companion object {
        const val HEART_RATE_VALUE_16_BIT = 1 shl 0
    }
}

class HeartRateParseException(message: String) : IllegalArgumentException(message)
