package com.gpxeditor.shared.feature.importgpx

import com.gpxeditor.shared.data.activity.ActivityDocumentJson
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImportGpxTrackUseCaseTest {
    @Test
    fun importsValidGpxAndSavesFileAndMetadata() = runSuspend {
        val fileStorage = FakeGpxTrackFileStorage()
        val trackStore = FakeImportedTrackStore()
        val useCase = ImportGpxTrackUseCase(
            fileStorage = fileStorage,
            trackStore = trackStore,
            idGenerator = FixedImportIdGenerator("track-1"),
            clock = FixedImportClock("2026-05-31T10:00:00Z"),
        )
        val content = """
            <gpx version="1.1" creator="GPXFixer">
                <metadata>
                    <name>Evening Route</name>
                </metadata>
                <trk>
                    <name>Fallback Name</name>
                    <trkseg>
                        <trkpt lat="41.7151" lon="44.8271" />
                        <trkpt lat="41.7160" lon="44.8280" />
                    </trkseg>
                </trk>
            </gpx>
        """.trimIndent()

        val result = useCase(
            ImportGpxTrackRequest(
                originalFileName = "route.gpx",
                content = content,
            ),
        )

        val success = assertIs<ImportGpxTrackResult.Success>(result)
        assertEquals("track-1", success.importedTrack.id)
        assertEquals("Evening Route", success.importedTrack.displayName)
        assertEquals("route.gpx", success.importedTrack.originalFileName)
        assertEquals("2026-05-31T10:00:00Z", success.importedTrack.importedAt)
        assertEquals("tracks/track-1.activity.json", success.importedTrack.storageKey)
        assertEquals(1, success.importedTrack.trackCount)
        assertEquals(2, success.importedTrack.pointCount)
        val savedDocument = ActivityDocumentJson.parseOrThrow(
            fileStorage.savedContent("tracks/track-1.activity.json") ?: "",
        )
        assertEquals("Evening Route", savedDocument.metadata.name)
        assertEquals("GPXFixer", savedDocument.metadata.source)
        assertEquals(2, savedDocument.pointCount)
        assertEquals(listOf(success.importedTrack), trackStore.tracks)
    }

    @Test
    fun returnsFailureForInvalidGpxWithoutSavingAnything() = runSuspend {
        val fileStorage = FakeGpxTrackFileStorage()
        val trackStore = FakeImportedTrackStore()
        val useCase = ImportGpxTrackUseCase(
            fileStorage = fileStorage,
            trackStore = trackStore,
            idGenerator = FixedImportIdGenerator("track-1"),
            clock = FixedImportClock("2026-05-31T10:00:00Z"),
        )

        val result = useCase(
            ImportGpxTrackRequest(
                originalFileName = "broken.gpx",
                content = "<gpx><trk><trkseg><trkpt lon=\"44.8271\" /></trkseg></trk></gpx>",
            ),
        )

        val failure = assertIs<ImportGpxTrackResult.Failure>(result)
        assertEquals("Track point is missing 'lat' attribute", failure.error.message)
        assertTrue(fileStorage.savedFiles.isEmpty())
        assertTrue(trackStore.tracks.isEmpty())
    }

    @Test
    fun rollsBackSavedFileWhenMetadataStoreFails() {
        val fileStorage = FakeGpxTrackFileStorage()
        val trackStore = FakeImportedTrackStore(failOnAdd = true)
        val useCase = ImportGpxTrackUseCase(
            fileStorage = fileStorage,
            trackStore = trackStore,
            idGenerator = FixedImportIdGenerator("track-1"),
            clock = FixedImportClock("2026-05-31T10:00:00Z"),
        )

        assertFailsWith<IllegalStateException> {
            runSuspend {
                useCase(
                    ImportGpxTrackRequest(
                        originalFileName = "route.gpx",
                        content = "<gpx><trk><trkseg><trkpt lat=\"41.7151\" lon=\"44.8271\" /></trkseg></trk></gpx>",
                    ),
                )
            }
        }

        assertNull(fileStorage.savedContent("tracks/track-1.activity.json"))
        assertTrue(trackStore.tracks.isEmpty())
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
