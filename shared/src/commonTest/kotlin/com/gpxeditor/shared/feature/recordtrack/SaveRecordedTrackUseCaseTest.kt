package com.gpxeditor.shared.feature.recordtrack

import com.gpxeditor.shared.data.activity.ActivityDocumentJson
import com.gpxeditor.shared.data.activity.ActivityJsonParseResult
import com.gpxeditor.shared.domain.activity.ActivityDocument
import com.gpxeditor.shared.domain.activity.ActivityMetadata
import com.gpxeditor.shared.domain.activity.ActivityPoint
import com.gpxeditor.shared.domain.activity.ActivitySegment
import com.gpxeditor.shared.domain.activity.ActivityTrack
import com.gpxeditor.shared.domain.imported.ImportedTrack
import com.gpxeditor.shared.domain.imported.ports.GpxTrackFileStorage
import com.gpxeditor.shared.domain.imported.ports.ImportClock
import com.gpxeditor.shared.domain.imported.ports.ImportIdGenerator
import com.gpxeditor.shared.domain.imported.ports.ImportedTrackStore
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SaveRecordedTrackUseCaseTest {
    @Test
    fun savesRecordingAsActivityJsonTrack() = runSuspend {
        val fileStorage = FakeGpxTrackFileStorage()
        val trackStore = FakeImportedTrackStore()
        val useCase = SaveRecordedTrackUseCase(
            fileStorage = fileStorage,
            trackStore = trackStore,
            idGenerator = FixedImportIdGenerator("rec-1"),
            clock = FixedImportClock("2026-07-11T12:00:00Z"),
        )

        val result = useCase(SaveRecordedTrackRequest(recordedDocument()))

        val success = assertIs<SaveRecordedTrackResult.Success>(result)
        assertEquals("rec-1", success.importedTrack.id)
        assertEquals("Ride 2026-07-11 10:00", success.importedTrack.displayName)
        assertEquals("Recorded with GPXFixer", success.importedTrack.originalFileName)
        assertEquals("2026-07-11T12:00:00Z", success.importedTrack.importedAt)
        assertEquals("tracks/rec-1.activity.json", success.importedTrack.storageKey)
        assertEquals(1, success.importedTrack.trackCount)
        assertEquals(2, success.importedTrack.pointCount)
        assertEquals(listOf(success.importedTrack), trackStore.tracks)

        val savedContent = fileStorage.savedContent("tracks/rec-1.activity.json")
        val parsed = assertIs<ActivityJsonParseResult.Success>(ActivityDocumentJson.parse(savedContent!!))
        assertEquals(recordedDocument(), parsed.document)
    }

    @Test
    fun prefersMetadataNameForDisplayName() = runSuspend {
        val useCase = SaveRecordedTrackUseCase(
            fileStorage = FakeGpxTrackFileStorage(),
            trackStore = FakeImportedTrackStore(),
            idGenerator = FixedImportIdGenerator("rec-2"),
            clock = FixedImportClock("2026-07-11T12:00:00Z"),
        )

        val document = recordedDocument().let {
            it.copy(metadata = it.metadata.copy(name = "Evening loop"))
        }
        val result = useCase(SaveRecordedTrackRequest(document))

        val success = assertIs<SaveRecordedTrackResult.Success>(result)
        assertEquals("Evening loop", success.importedTrack.displayName)
    }

    @Test
    fun failsForRecordingWithoutPoints() = runSuspend {
        val fileStorage = FakeGpxTrackFileStorage()
        val trackStore = FakeImportedTrackStore()
        val useCase = SaveRecordedTrackUseCase(
            fileStorage = fileStorage,
            trackStore = trackStore,
            idGenerator = FixedImportIdGenerator("rec-3"),
            clock = FixedImportClock("2026-07-11T12:00:00Z"),
        )

        val result = useCase(SaveRecordedTrackRequest(ActivityDocument()))

        assertIs<SaveRecordedTrackResult.Failure>(result)
        assertTrue(fileStorage.savedFiles.isEmpty())
        assertTrue(trackStore.tracks.isEmpty())
    }

    @Test
    fun rollsBackSavedFileWhenStoreFails() = runSuspend {
        val fileStorage = FakeGpxTrackFileStorage()
        val useCase = SaveRecordedTrackUseCase(
            fileStorage = fileStorage,
            trackStore = FakeImportedTrackStore(failOnAdd = true),
            idGenerator = FixedImportIdGenerator("rec-4"),
            clock = FixedImportClock("2026-07-11T12:00:00Z"),
        )

        assertFailsWith<IllegalStateException> {
            useCase(SaveRecordedTrackRequest(recordedDocument()))
        }
        assertTrue(fileStorage.savedFiles.isEmpty())
    }

    private fun recordedDocument(): ActivityDocument {
        return ActivityDocument(
            metadata = ActivityMetadata(
                sport = "cycling",
                startTime = "2026-07-11T10:00:00Z",
            ),
            tracks = listOf(
                ActivityTrack(
                    segments = listOf(
                        ActivitySegment(
                            points = listOf(
                                ActivityPoint(
                                    time = "2026-07-11T10:00:00Z",
                                    latitude = 55.0,
                                    longitude = 37.0,
                                    elevationMeters = 200.0,
                                ),
                                ActivityPoint(
                                    time = "2026-07-11T10:00:01Z",
                                    latitude = 55.001,
                                    longitude = 37.0,
                                    speedMetersPerSecond = 8.5,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
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

        fun savedContent(storageKey: String): String? = savedFiles[storageKey]
    }

    private class FakeImportedTrackStore(
        private val failOnAdd: Boolean = false,
    ) : ImportedTrackStore {
        val tracks = mutableListOf<ImportedTrack>()

        override suspend fun getAll(): List<ImportedTrack> {
            return tracks
        }

        override suspend fun add(track: ImportedTrack) {
            if (failOnAdd) {
                throw IllegalStateException("Failed to save metadata")
            }

            tracks += track
        }

        override suspend fun remove(id: String) {
            tracks.removeAll { it.id == id }
        }
    }

    private class FixedImportIdGenerator(
        private val id: String,
    ) : ImportIdGenerator {
        override fun nextId(): String = id
    }

    private class FixedImportClock(
        private val value: String,
    ) : ImportClock {
        override fun nowIsoString(): String = value
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
