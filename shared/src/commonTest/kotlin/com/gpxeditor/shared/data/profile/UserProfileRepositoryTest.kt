package com.gpxeditor.shared.data.profile

import com.gpxeditor.shared.domain.profile.HeartRateZones
import com.gpxeditor.shared.domain.profile.Sex
import com.gpxeditor.shared.domain.profile.UserProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private class FakeUserProfileStorage(var content: String? = null) : UserProfileStorage {
    override fun read(): String? = content
    override fun write(content: String?) {
        this.content = content
    }
}

class UserProfileRepositoryTest {
    private val profile = UserProfile(
        weightKg = 72.5,
        bikeWeightKg = 8.4,
        sex = Sex.MALE,
        birthYear = 1990,
        heartRateZones = HeartRateZones(listOf(114, 133, 152, 171)),
    )

    @Test
    fun startsEmptyWhenNothingIsStored() {
        assertEquals(UserProfile.EMPTY, UserProfileRepository(FakeUserProfileStorage()).profile.value)
    }

    @Test
    fun savePersistsAndUpdatesTheFlow() {
        val storage = FakeUserProfileStorage()
        val repository = UserProfileRepository(storage)

        repository.save(profile)

        assertEquals(profile, repository.profile.value)
        assertEquals(profile, UserProfileRepository(storage).profile.value)
    }

    @Test
    fun savingEmptyProfileClearsStorage() {
        val storage = FakeUserProfileStorage()
        val repository = UserProfileRepository(storage)
        repository.save(profile)

        repository.save(UserProfile.EMPTY)

        assertNull(storage.content)
        assertEquals(UserProfile.EMPTY, repository.profile.value)
    }

    @Test
    fun startsEmptyWhenStoredContentIsCorrupted() {
        assertEquals(
            UserProfile.EMPTY,
            UserProfileRepository(FakeUserProfileStorage("garbage")).profile.value,
        )
    }
}
