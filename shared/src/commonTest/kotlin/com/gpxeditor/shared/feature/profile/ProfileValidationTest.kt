package com.gpxeditor.shared.feature.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileValidationTest {
    @Test
    fun acceptsWeightWithinSaneRange() {
        assertTrue(ProfileValidation.isValidWeight(72.5))
        assertFalse(ProfileValidation.isValidWeight(19.9))
        assertFalse(ProfileValidation.isValidWeight(400.1))
    }

    @Test
    fun acceptsBikeWeightWithinSaneRange() {
        assertTrue(ProfileValidation.isValidBikeWeight(9.0))
        assertTrue(ProfileValidation.isValidBikeWeight(3.0))
        assertTrue(ProfileValidation.isValidBikeWeight(50.0))
        assertFalse(ProfileValidation.isValidBikeWeight(2.9))
        assertFalse(ProfileValidation.isValidBikeWeight(50.1))
    }

    @Test
    fun acceptsBirthYearBetween1900AndCurrentYear() {
        assertTrue(ProfileValidation.isValidBirthYear(1990, currentYear = 2026))
        assertTrue(ProfileValidation.isValidBirthYear(2026, currentYear = 2026))
        assertFalse(ProfileValidation.isValidBirthYear(2027, currentYear = 2026))
        assertFalse(ProfileValidation.isValidBirthYear(1899, currentYear = 2026))
    }

    @Test
    fun acceptsFtpWithinSaneRange() {
        assertTrue(ProfileValidation.isValidFtp(250))
        assertFalse(ProfileValidation.isValidFtp(29))
        assertFalse(ProfileValidation.isValidFtp(2001))
    }

    @Test
    fun acceptsAscendingHeartRateBounds() {
        assertNull(ProfileValidation.validateHeartRateBounds(listOf(114, 133, 152, 171)))
    }

    @Test
    fun rejectsNonAscendingBounds() {
        assertEquals(
            ZoneBoundsError.NOT_ASCENDING,
            ProfileValidation.validateHeartRateBounds(listOf(114, 152, 133, 171)),
        )
        assertEquals(
            ZoneBoundsError.NOT_ASCENDING,
            ProfileValidation.validatePowerBounds(listOf(138, 188, 188, 263, 300, 375)),
        )
    }

    @Test
    fun rejectsBoundsOutsideSaneRange() {
        assertEquals(
            ZoneBoundsError.OUT_OF_RANGE,
            ProfileValidation.validateHeartRateBounds(listOf(30, 133, 152, 171)),
        )
        assertEquals(
            ZoneBoundsError.OUT_OF_RANGE,
            ProfileValidation.validatePowerBounds(listOf(138, 188, 225, 263, 300, 3001)),
        )
    }
}
