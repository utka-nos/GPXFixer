package com.gpxeditor.shared.data.profile

import com.gpxeditor.shared.domain.profile.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface UserProfileStorage {
    fun read(): String?
    fun write(content: String?)
}

/**
 * Persists the profile in the same small JSON-file style as other app storage and
 * exposes it as a [StateFlow] so screens react to changes without a restart.
 */
class UserProfileRepository(private val storage: UserProfileStorage) {
    private val state = MutableStateFlow(loadInitial())

    val profile: StateFlow<UserProfile> = state.asStateFlow()

    fun save(profile: UserProfile) {
        storage.write(if (profile == UserProfile.EMPTY) null else UserProfileJson.encode(profile))
        state.value = profile
    }

    private fun loadInitial(): UserProfile =
        storage.read()?.let(UserProfileJson::decode) ?: UserProfile.EMPTY
}
