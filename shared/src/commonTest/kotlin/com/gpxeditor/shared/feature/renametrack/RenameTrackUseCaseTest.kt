package com.gpxeditor.shared.feature.renametrack

import com.gpxeditor.shared.domain.imported.ImportedTrack
import com.gpxeditor.shared.domain.imported.ports.ImportedTrackStore
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RenameTrackUseCaseTest {
    @Test
    fun renamesTrackAndUpdatesStore() = runSuspend {
        val trackStore = FakeImportedTrackStore()
        val useCase = RenameTrackUseCase(trackStore)
        val track = importedTrack(displayName = "Morning Ride")

        val result = useCase(
            RenameTrackRequest(
                track = track,
                newDisplayName = "Evening Ride",
            ),
        )

        val success = assertIs<RenameTrackResult.Success>(result)
        assertEquals("Evening Ride", success.importedTrack.displayName)
        assertEquals(track.copy(displayName = "Evening Ride"), success.importedTrack)
        assertEquals(listOf(success.importedTrack), trackStore.tracks)
    }

    @Test
    fun trimsWhitespaceAroundNewName() = runSuspend {
        val trackStore = FakeImportedTrackStore()
        val useCase = RenameTrackUseCase(trackStore)

        val result = useCase(
            RenameTrackRequest(
                track = importedTrack(displayName = "Morning Ride"),
                newDisplayName = "  Evening Ride  ",
            ),
        )

        val success = assertIs<RenameTrackResult.Success>(result)
        assertEquals("Evening Ride", success.importedTrack.displayName)
    }

    @Test
    fun returnsFailureForBlankNameWithoutTouchingStore() = runSuspend {
        val trackStore = FakeImportedTrackStore()
        val useCase = RenameTrackUseCase(trackStore)

        val result = useCase(
            RenameTrackRequest(
                track = importedTrack(displayName = "Morning Ride"),
                newDisplayName = "   ",
            ),
        )

        val failure = assertIs<RenameTrackResult.Failure>(result)
        assertEquals("Track name cannot be empty", failure.message)
        assertTrue(trackStore.tracks.isEmpty())
    }

    @Test
    fun skipsStoreUpdateWhenNameIsUnchanged() = runSuspend {
        val trackStore = FakeImportedTrackStore()
        val useCase = RenameTrackUseCase(trackStore)
        val track = importedTrack(displayName = "Morning Ride")

        val result = useCase(
            RenameTrackRequest(
                track = track,
                newDisplayName = "Morning Ride",
            ),
        )

        val success = assertIs<RenameTrackResult.Success>(result)
        assertEquals(track, success.importedTrack)
        assertTrue(trackStore.tracks.isEmpty())
    }

    private fun importedTrack(displayName: String): ImportedTrack {
        return ImportedTrack(
            id = "track-1",
            displayName = displayName,
            originalFileName = "route.gpx",
            importedAt = "2026-05-31T10:00:00Z",
            storageKey = "tracks/track-1.activity.json",
            trackCount = 1,
            pointCount = 2,
        )
    }

    private class FakeImportedTrackStore : ImportedTrackStore {
        val tracks = mutableListOf<ImportedTrack>()

        override suspend fun getAll(): List<ImportedTrack> {
            return tracks
        }

        override suspend fun add(track: ImportedTrack) {
            tracks.removeAll { it.id == track.id }
            tracks.add(index = 0, element = track)
        }

        override suspend fun remove(id: String) {
            tracks.removeAll { it.id == id }
        }
    }
}

private fun <T> runSuspend(block: suspend () -> T): T {
    var value: T? = null
    var failure: Throwable? = null

    block.startCoroutine(
        object : Continuation<T> {
            override val context = EmptyCoroutineContext

            override fun resumeWith(result: Result<T>) {
                result
                    .onSuccess { value = it }
                    .onFailure { failure = it }
            }
        },
    )

    failure?.let { throw it }
    @Suppress("UNCHECKED_CAST")
    return value as T
}
