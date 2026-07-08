package com.gpxeditor.shared.feature.deletetrack

import com.gpxeditor.shared.data.fit.FitSourceStore
import com.gpxeditor.shared.domain.imported.ImportedTrack
import com.gpxeditor.shared.domain.imported.ports.GpxTrackFileStorage
import com.gpxeditor.shared.domain.imported.ports.ImportedTrackStore
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeleteImportedTrackUseCaseTest {
    @Test
    fun removesMetadataAndStoredFiles() = runSuspend {
        val fileStorage = FakeGpxTrackFileStorage()
        val trackStore = FakeImportedTrackStore()
        val track = importedTrack("track-1")
        val otherTrack = importedTrack("track-2")
        trackStore.tracks += listOf(track, otherTrack)
        fileStorage.savedFiles[track.storageKey] = "activity json"
        fileStorage.savedFiles[FitSourceStore.keyFor(track.id)] = "fit source"
        fileStorage.savedFiles[otherTrack.storageKey] = "other activity json"
        val useCase = DeleteImportedTrackUseCase(
            fileStorage = fileStorage,
            trackStore = trackStore,
        )

        useCase(track)

        assertEquals(listOf(otherTrack), trackStore.tracks)
        assertNull(fileStorage.savedFiles[track.storageKey])
        assertNull(fileStorage.savedFiles[FitSourceStore.keyFor(track.id)])
        assertEquals("other activity json", fileStorage.savedFiles[otherTrack.storageKey])
    }

    @Test
    fun deletesTrackWithoutFitSource() = runSuspend {
        val fileStorage = FakeGpxTrackFileStorage()
        val trackStore = FakeImportedTrackStore()
        val track = importedTrack("track-1")
        trackStore.tracks += track
        fileStorage.savedFiles[track.storageKey] = "activity json"
        val useCase = DeleteImportedTrackUseCase(
            fileStorage = fileStorage,
            trackStore = trackStore,
        )

        useCase(track)

        assertTrue(trackStore.tracks.isEmpty())
        assertTrue(fileStorage.savedFiles.isEmpty())
    }

    private fun importedTrack(id: String): ImportedTrack {
        return ImportedTrack(
            id = id,
            displayName = "Track $id",
            originalFileName = "$id.gpx",
            importedAt = "2026-05-31T10:00:00Z",
            storageKey = "tracks/$id.activity.json",
            trackCount = 1,
            pointCount = 2,
        )
    }

    private class FakeGpxTrackFileStorage : GpxTrackFileStorage {
        val savedFiles = mutableMapOf<String, String>()

        override suspend fun save(storageKey: String, content: String) {
            savedFiles[storageKey] = content
        }

        override suspend fun read(storageKey: String): String {
            return savedFiles.getValue(storageKey)
        }

        override suspend fun delete(storageKey: String) {
            savedFiles.remove(storageKey)
        }
    }

    private class FakeImportedTrackStore : ImportedTrackStore {
        val tracks = mutableListOf<ImportedTrack>()

        override suspend fun getAll(): List<ImportedTrack> {
            return tracks
        }

        override suspend fun add(track: ImportedTrack) {
            tracks += track
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
