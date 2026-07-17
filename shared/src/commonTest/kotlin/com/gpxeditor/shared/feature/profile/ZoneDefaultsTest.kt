package com.gpxeditor.shared.feature.profile

import kotlin.test.Test
import kotlin.test.assertEquals

class ZoneDefaultsTest {
    @Test
    fun estimatesMaxHeartRateAs220MinusAge() {
        assertEquals(220 - 36, ZoneDefaults.estimatedMaxHeartRate(birthYear = 1990, currentYear = 2026))
    }

    @Test
    fun derivesFiveHeartRateZonesFromMaxHeartRate() {
        val zones = ZoneDefaults.heartRateZonesFromMax(maxHeartRateBpm = 190)

        assertEquals(listOf(114, 133, 152, 171), zones.upperBoundsBpm)
    }

    @Test
    fun derivesCogganPowerZonesFromFtp() {
        val zones = ZoneDefaults.powerZonesFromFtp(ftpWatts = 250)

        assertEquals(listOf(138, 188, 225, 263, 300, 375), zones.upperBoundsWatts)
    }

    @Test
    fun derivedZonesAreValidForRealisticInputs() {
        for (maxHeartRate in 100..ProfileValidation.MAX_HEART_RATE_BPM) {
            assertEquals(
                null,
                ProfileValidation.validateHeartRateBounds(
                    ZoneDefaults.heartRateZonesFromMax(maxHeartRate).upperBoundsBpm,
                ),
            )
        }
        for (ftp in ProfileValidation.MIN_FTP_WATTS..ProfileValidation.MAX_FTP_WATTS) {
            assertEquals(
                null,
                ProfileValidation.validatePowerBounds(ZoneDefaults.powerZonesFromFtp(ftp).upperBoundsWatts),
            )
        }
    }
}
