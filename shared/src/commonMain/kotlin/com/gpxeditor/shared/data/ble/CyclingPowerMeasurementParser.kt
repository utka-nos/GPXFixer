package com.gpxeditor.shared.data.ble

/** A power-meter reading decoded from the Cycling Power Measurement characteristic (0x2A63). */
data class PowerSample(
    val watts: Int,
    val cadenceRpm: Double?,
)

/**
 * Stateful decoder for Bluetooth Cycling Power Measurement notifications.
 *
 * Cadence needs two crank events, so the first packet containing crank data has a null cadence.
 * Counter and event-time rollover are handled as unsigned 16-bit arithmetic.
 */
class CyclingPowerMeasurementParser {
    private var previousCrankData: CrankData? = null

    fun parse(bytes: ByteArray): PowerSample {
        val reader = LittleEndianReader(bytes)
        val flags = reader.readUInt16("flags")
        val watts = reader.readSInt16("instantaneous power")

        if (flags and PEDAL_POWER_BALANCE_PRESENT != 0) {
            reader.skip(1, "pedal power balance")
        }
        if (flags and ACCUMULATED_TORQUE_PRESENT != 0) {
            reader.skip(2, "accumulated torque")
        }
        if (flags and WHEEL_REVOLUTION_DATA_PRESENT != 0) {
            reader.skip(6, "wheel revolution data")
        }

        val cadence = if (flags and CRANK_REVOLUTION_DATA_PRESENT != 0) {
            val current = CrankData(
                revolutions = reader.readUInt16("cumulative crank revolutions"),
                eventTime = reader.readUInt16("last crank event time"),
            )
            cadence(previousCrankData, current).also { previousCrankData = current }
        } else {
            null
        }

        return PowerSample(watts = watts, cadenceRpm = cadence)
    }

    fun reset() {
        previousCrankData = null
    }

    private fun cadence(previous: CrankData?, current: CrankData): Double? {
        if (previous == null) return null

        val revolutionDelta = unsigned16Delta(current.revolutions, previous.revolutions)
        val timeDelta = unsigned16Delta(current.eventTime, previous.eventTime)
        if (revolutionDelta == 0 || timeDelta == 0) return null

        return revolutionDelta * SECONDS_PER_MINUTE * EVENT_TIME_TICKS_PER_SECOND / timeDelta
    }

    private fun unsigned16Delta(current: Int, previous: Int): Int =
        (current - previous) and UINT16_MASK

    private data class CrankData(val revolutions: Int, val eventTime: Int)

    private companion object {
        const val PEDAL_POWER_BALANCE_PRESENT = 1 shl 0
        const val ACCUMULATED_TORQUE_PRESENT = 1 shl 2
        const val WHEEL_REVOLUTION_DATA_PRESENT = 1 shl 4
        const val CRANK_REVOLUTION_DATA_PRESENT = 1 shl 5
        const val UINT16_MASK = 0xFFFF
        const val SECONDS_PER_MINUTE = 60.0
        const val EVENT_TIME_TICKS_PER_SECOND = 1024.0
    }
}

class CyclingPowerParseException(message: String) : IllegalArgumentException(message)

private class LittleEndianReader(private val bytes: ByteArray) {
    private var position = 0

    fun readUInt16(field: String): Int {
        requireAvailable(2, field)
        val value = (bytes[position].toInt() and 0xFF) or
            ((bytes[position + 1].toInt() and 0xFF) shl 8)
        position += 2
        return value
    }

    fun readSInt16(field: String): Int {
        val unsigned = readUInt16(field)
        return if (unsigned and 0x8000 != 0) unsigned - 0x10000 else unsigned
    }

    fun skip(count: Int, field: String) {
        requireAvailable(count, field)
        position += count
    }

    private fun requireAvailable(count: Int, field: String) {
        if (position + count > bytes.size) {
            throw CyclingPowerParseException(
                "Cycling Power Measurement is truncated while reading $field.",
            )
        }
    }
}
