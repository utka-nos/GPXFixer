package com.gpxeditor.shared.data.profile

import com.gpxeditor.shared.domain.profile.HeartRateZones
import com.gpxeditor.shared.domain.profile.PowerZones
import com.gpxeditor.shared.domain.profile.Sex
import com.gpxeditor.shared.domain.profile.UserProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UserProfileJsonTest {
    private val fullProfile = UserProfile(
        weightKg = 72.5,
        sex = Sex.FEMALE,
        birthYear = 1990,
        heartRateZones = HeartRateZones(listOf(114, 133, 152, 171)),
        ftpWatts = 250,
        powerZones = PowerZones(listOf(138, 188, 225, 263, 300, 375)),
    )

    @Test
    fun roundTripsFullProfile() {
        assertEquals(fullProfile, UserProfileJson.decode(UserProfileJson.encode(fullProfile)))
    }

    @Test
    fun roundTripsPartialProfile() {
        val profile = UserProfile(weightKg = 80.0, ftpWatts = 200)

        assertEquals(profile, UserProfileJson.decode(UserProfileJson.encode(profile)))
    }

    @Test
    fun roundTripsEmptyProfile() {
        assertEquals(UserProfile.EMPTY, UserProfileJson.decode(UserProfileJson.encode(UserProfile.EMPTY)))
    }

    @Test
    fun decodeIgnoresUnknownSexValue() {
        assertEquals(
            UserProfile(birthYear = 1990),
            UserProfileJson.decode("{\"sex\":\"OTHER\",\"birthYear\":1990}"),
        )
    }

    @Test
    fun decodeRejectsMalformedJson() {
        assertNull(UserProfileJson.decode(""))
        assertNull(UserProfileJson.decode("not json"))
        assertNull(UserProfileJson.decode("{\"weightKg\":}"))
        assertNull(UserProfileJson.decode("{\"weightKg\":72.5"))
    }

    @Test
    fun decodeRejectsWrongBoundaryCount() {
        assertNull(UserProfileJson.decode("{\"heartRateZoneUpperBoundsBpm\":[114,133,152]}"))
        assertNull(UserProfileJson.decode("{\"powerZoneUpperBoundsWatts\":[138,188]}"))
    }
}
