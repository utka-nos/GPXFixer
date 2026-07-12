package com.gpxeditor.shared.data.ble

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HeartRateMeasurementParserTest {
    @Test
    fun parsesEightBitHeartRate() {
        val sample = HeartRateMeasurementParser().parse(bytes(0x00, 142))

        assertEquals(142, sample.bpm)
    }

    @Test
    fun parsesSixteenBitHeartRate() {
        val sample = HeartRateMeasurementParser().parse(bytes(0x01, 0x2C, 0x01)) // 300 bpm

        assertEquals(300, sample.bpm)
    }

    @Test
    fun ignoresTrailingOptionalFields() {
        // Sensor contact, energy expended, and RR intervals are present after the value.
        val flags = 0x02 or 0x04 or 0x08 or 0x10
        val sample = HeartRateMeasurementParser().parse(
            bytes(flags, 155, 0x34, 0x12, 0x00, 0x04),
        )

        assertEquals(155, sample.bpm)
    }

    @Test
    fun rejectsEmptyPacket() {
        assertFailsWith<HeartRateParseException> {
            HeartRateMeasurementParser().parse(ByteArray(0))
        }
    }

    @Test
    fun rejectsPacketShorterThanAnnouncedValue() {
        assertFailsWith<HeartRateParseException> {
            HeartRateMeasurementParser().parse(bytes(0x00))
        }
        assertFailsWith<HeartRateParseException> {
            HeartRateMeasurementParser().parse(bytes(0x01, 0x90))
        }
    }

    private fun bytes(vararg values: Int): ByteArray =
        ByteArray(values.size) { index -> values[index].toByte() }
}
