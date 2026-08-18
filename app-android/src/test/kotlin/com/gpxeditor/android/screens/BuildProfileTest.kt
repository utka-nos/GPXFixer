package com.gpxeditor.android.screens

import com.gpxeditor.shared.domain.profile.HeartRateZones
import com.gpxeditor.shared.domain.profile.PowerZones
import com.gpxeditor.shared.domain.profile.Sex
import com.gpxeditor.shared.domain.profile.UserProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BuildProfileTest {
    private val emptyHeartRateBounds = List(HeartRateZones.BOUNDARY_COUNT) { "" }
    private val emptyPowerBounds = List(PowerZones.BOUNDARY_COUNT) { "" }

    private fun build(
        weightText: String = "",
        bikeWeightText: String = "",
        sex: Sex? = null,
        birthYearText: String = "",
        heartRateBoundTexts: List<String> = emptyHeartRateBounds,
        ftpText: String = "",
        powerBoundTexts: List<String> = emptyPowerBounds,
    ) = buildProfile(
        weightText = weightText,
        bikeWeightText = bikeWeightText,
        sex = sex,
        birthYearText = birthYearText,
        heartRateBoundTexts = heartRateBoundTexts,
        ftpText = ftpText,
        powerBoundTexts = powerBoundTexts,
        currentYear = 2026,
    )

    @Test
    fun blankFieldsProduceEmptyProfile() {
        val result = build()

        assertTrue(result.errors.isEmpty())
        assertEquals(UserProfile.EMPTY, result.profile)
    }

    @Test
    fun parsesAllFields() {
        val result = build(
            weightText = "72,5",
            bikeWeightText = "8,4",
            sex = Sex.FEMALE,
            birthYearText = "1990",
            heartRateBoundTexts = listOf("114", "133", "152", "171"),
            ftpText = "250",
            powerBoundTexts = listOf("138", "188", "225", "263", "300", "375"),
        )

        assertTrue(result.errors.isEmpty())
        assertEquals(
            UserProfile(
                weightKg = 72.5,
                bikeWeightKg = 8.4,
                sex = Sex.FEMALE,
                birthYear = 1990,
                heartRateZones = HeartRateZones(listOf(114, 133, 152, 171)),
                ftpWatts = 250,
                powerZones = PowerZones(listOf(138, 188, 225, 263, 300, 375)),
            ),
            result.profile,
        )
    }

    @Test
    fun reportsInvalidWeightAndBirthYear() {
        val result = build(weightText = "abc", birthYearText = "2050")

        assertNull(result.profile)
        assertEquals(setOf(ProfileField.WEIGHT, ProfileField.BIRTH_YEAR), result.errors.keys)
    }

    @Test
    fun reportsBikeWeightOutsideItsOwnRange() {
        val result = build(bikeWeightText = "60")

        assertNull(result.profile)
        assertEquals(setOf(ProfileField.BIKE_WEIGHT), result.errors.keys)
    }

    @Test
    fun leavesBikeWeightUnsetWhenBlank() {
        val result = build(weightText = "72.5")

        assertTrue(result.errors.isEmpty())
        assertNull(result.profile?.bikeWeightKg)
    }

    @Test
    fun rejectsPartiallyFilledZoneBoundaries() {
        val result = build(heartRateBoundTexts = listOf("114", "", "152", "171"))

        assertNull(result.profile)
        assertEquals(setOf(ProfileField.HEART_RATE_ZONES), result.errors.keys)
    }

    @Test
    fun rejectsNonAscendingZoneBoundaries() {
        val result = build(powerBoundTexts = listOf("138", "188", "150", "263", "300", "375"))

        assertNull(result.profile)
        assertEquals(setOf(ProfileField.POWER_ZONES), result.errors.keys)
    }
}
