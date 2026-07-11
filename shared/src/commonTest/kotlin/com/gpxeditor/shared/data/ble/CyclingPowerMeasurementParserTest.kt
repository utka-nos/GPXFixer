package com.gpxeditor.shared.data.ble

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class CyclingPowerMeasurementParserTest {
    @Test
    fun parsesPowerAndCalculatesCadenceFromConsecutiveCrankEvents() {
        val parser = CyclingPowerMeasurementParser()

        val first = parser.parse(measurement(watts = 250, revolutions = 123, eventTime = 2_048))
        val second = parser.parse(measurement(watts = 265, revolutions = 124, eventTime = 2_560))

        assertEquals(250, first.watts)
        assertNull(first.cadenceRpm)
        assertEquals(265, second.watts)
        assertEquals(120.0, second.cadenceRpm)
    }

    @Test
    fun parsesSignedPowerWithoutCrankData() {
        val sample = CyclingPowerMeasurementParser().parse(
            bytes(0x00, 0x00, 0x9C, 0xFF), // -100 W
        )

        assertEquals(-100, sample.watts)
        assertNull(sample.cadenceRpm)
    }

    @Test
    fun locatesCrankDataAfterOtherOptionalFields() {
        val parser = CyclingPowerMeasurementParser()
        // Pedal balance, accumulated torque, wheel data, and crank data are present.
        val flags = 0x01 or 0x04 or 0x10 or 0x20
        val prefix = bytes(
            flags, 0x00,
            0x2C, 0x01, // 300 W
            0x80, // pedal balance
            0x34, 0x12, // accumulated torque
            0x78, 0x56, 0x34, 0x12, 0x00, 0x04, // wheel data
        )

        val first = parser.parse(prefix + bytes(10, 0, 0, 4))
        val second = parser.parse(prefix + bytes(11, 0, 0, 6))

        assertNull(first.cadenceRpm)
        assertEquals(120.0, second.cadenceRpm)
    }

    @Test
    fun handlesCrankAndEventTimeRollover() {
        val parser = CyclingPowerMeasurementParser()

        parser.parse(measurement(watts = 200, revolutions = 0xFFFF, eventTime = 0xFE00))
        val sample = parser.parse(measurement(watts = 205, revolutions = 0, eventTime = 0))

        assertEquals(120.0, sample.cadenceRpm)
    }

    @Test
    fun returnsNullCadenceWhenEventTimeDoesNotAdvance() {
        val parser = CyclingPowerMeasurementParser()

        parser.parse(measurement(watts = 200, revolutions = 20, eventTime = 1_000))
        val sample = parser.parse(measurement(watts = 200, revolutions = 21, eventTime = 1_000))

        assertNull(sample.cadenceRpm)
    }

    @Test
    fun rejectsPacketsShorterThanMandatoryFields() {
        assertFailsWith<CyclingPowerParseException> {
            CyclingPowerMeasurementParser().parse(bytes(0x00, 0x00, 0xFA))
        }
    }

    @Test
    fun rejectsPacketWhoseFlagsAnnounceMissingCrankData() {
        assertFailsWith<CyclingPowerParseException> {
            CyclingPowerMeasurementParser().parse(bytes(0x20, 0x00, 0xFA, 0x00, 0x01, 0x00))
        }
    }

    @Test
    fun resetDropsPreviousCrankEvent() {
        val parser = CyclingPowerMeasurementParser()
        parser.parse(measurement(watts = 200, revolutions = 1, eventTime = 100))

        parser.reset()
        val sample = parser.parse(measurement(watts = 200, revolutions = 2, eventTime = 200))

        assertNull(sample.cadenceRpm)
    }

    private fun measurement(watts: Int, revolutions: Int, eventTime: Int): ByteArray = bytes(
        0x20, 0x00,
        watts and 0xFF, (watts shr 8) and 0xFF,
        revolutions and 0xFF, (revolutions shr 8) and 0xFF,
        eventTime and 0xFF, (eventTime shr 8) and 0xFF,
    )

    private fun bytes(vararg values: Int): ByteArray =
        ByteArray(values.size) { index -> values[index].toByte() }
}
