package com.gpxeditor.shared.feature.importfit

import com.gpxeditor.shared.data.activity.ActivityDocumentJson
import com.gpxeditor.shared.domain.fit.FitActivityDocument
import com.gpxeditor.shared.domain.fit.FitActivityMetadata
import com.gpxeditor.shared.domain.fit.FitActivityPoint
import com.gpxeditor.shared.domain.fit.FitActivitySegment
import com.gpxeditor.shared.domain.fit.FitActivityTrack
import com.gpxeditor.shared.domain.fit.FitDecodeError
import com.gpxeditor.shared.domain.fit.FitDecodeResult
import com.gpxeditor.shared.domain.fit.FitFileDecoder
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

class ImportFitTrackUseCaseTest {
    @Test
    fun importsDecodedFitActivityAsGpxTrack() = runSuspend {
        val fileStorage = FakeGpxTrackFileStorage()
        val trackStore = FakeImportedTrackStore()
        val useCase = ImportFitTrackUseCase(
            fitFileDecoder = FixedFitFileDecoder(
                FitDecodeResult.Success(
                    FitActivityDocument(
                        metadata = FitActivityMetadata(
                            name = "Morning Ride",
                            sport = "cycling",
                            startTime = "2026-05-31T08:00:00Z",
                            source = "Apple Watch",
                        ),
                        tracks = listOf(
                            FitActivityTrack(
                                segments = listOf(
                                    FitActivitySegment(
                                        points = listOf(
                                            FitActivityPoint(
                                                time = "2026-05-31T08:01:00Z",
                                                latitude = 41.7151,
                                                longitude = 44.8271,
                                                elevationMeters = 512.4,
                                                heartRateBpm = 142,
                                                powerWatts = 210,
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            fileStorage = fileStorage,
            trackStore = trackStore,
            idGenerator = FixedImportIdGenerator("track-1"),
            clock = FixedImportClock("2026-05-31T10:00:00Z"),
        )

        val result = useCase(
            ImportFitTrackRequest(
                originalFileName = "ride.fit",
                content = byteArrayOf(1, 2, 3),
            ),
        )

        val success = assertIs<ImportFitTrackResult.Success>(result)
        assertEquals("track-1", success.importedTrack.id)
        assertEquals("Morning Ride", success.importedTrack.displayName)
        assertEquals("ride.fit", success.importedTrack.originalFileName)
        assertEquals("tracks/track-1.activity.json", success.importedTrack.storageKey)
        assertEquals(1, success.importedTrack.trackCount)
        assertEquals(1, success.importedTrack.pointCount)
        assertEquals(listOf(success.importedTrack), trackStore.tracks)

        val savedDocument = ActivityDocumentJson.parseOrThrow(
            fileStorage.savedContent("tracks/track-1.activity.json") ?: "",
        )
        assertEquals("Apple Watch", savedDocument.metadata.source)
        assertEquals("Morning Ride", savedDocument.metadata.name)
        assertEquals(41.7151, savedDocument.tracks.single().segments.single().points.single().latitude)
    }

    @Test
    fun returnsFailureWhenDecoderFails() = runSuspend {
        val fileStorage = FakeGpxTrackFileStorage()
        val trackStore = FakeImportedTrackStore()
        val useCase = ImportFitTrackUseCase(
            fitFileDecoder = FixedFitFileDecoder(FitDecodeResult.Failure(FitDecodeError("Invalid FIT CRC."))),
            fileStorage = fileStorage,
            trackStore = trackStore,
            idGenerator = FixedImportIdGenerator("track-1"),
            clock = FixedImportClock("2026-05-31T10:00:00Z"),
        )

        val result = useCase(ImportFitTrackRequest("broken.fit", byteArrayOf()))

        val failure = assertIs<ImportFitTrackResult.Failure>(result)
        assertEquals("Invalid FIT CRC.", failure.error.message)
        assertTrue(fileStorage.savedFiles.isEmpty())
        assertTrue(trackStore.tracks.isEmpty())
    }

    @Test
    fun returnsFailureWhenDecodedFitHasNoGpsPoints() = runSuspend {
        val fileStorage = FakeGpxTrackFileStorage()
        val trackStore = FakeImportedTrackStore()
        val useCase = ImportFitTrackUseCase(
            fitFileDecoder = FixedFitFileDecoder(
                FitDecodeResult.Success(
                    FitActivityDocument(
                        tracks = listOf(
                            FitActivityTrack(
                                segments = listOf(
                                    FitActivitySegment(
                                        points = listOf(FitActivityPoint(heartRateBpm = 120)),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            fileStorage = fileStorage,
            trackStore = trackStore,
            idGenerator = FixedImportIdGenerator("track-1"),
            clock = FixedImportClock("2026-05-31T10:00:00Z"),
        )

        val result = useCase(ImportFitTrackRequest("ride.fit", byteArrayOf(1)))

        val failure = assertIs<ImportFitTrackResult.Failure>(result)
        assertEquals("FIT file does not contain GPS track points.", failure.error.message)
        assertTrue(fileStorage.savedFiles.isEmpty())
        assertTrue(trackStore.tracks.isEmpty())
    }

    @Test
    fun rollsBackSavedGpxWhenMetadataStoreFails() {
        val fileStorage = FakeGpxTrackFileStorage()
        val trackStore = FakeImportedTrackStore(failOnAdd = true)
        val useCase = ImportFitTrackUseCase(
            fitFileDecoder = FixedFitFileDecoder(
                FitDecodeResult.Success(
                    FitActivityDocument(
                        tracks = listOf(
                            FitActivityTrack(
                                segments = listOf(
                                    FitActivitySegment(
                                        points = listOf(FitActivityPoint(latitude = 41.7151, longitude = 44.8271)),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            fileStorage = fileStorage,
            trackStore = trackStore,
            idGenerator = FixedImportIdGenerator("track-1"),
            clock = FixedImportClock("2026-05-31T10:00:00Z"),
        )

        assertFailsWith<IllegalStateException> {
            runSuspend {
                useCase(ImportFitTrackRequest("ride.fit", byteArrayOf(1)))
            }
        }

        assertNull(fileStorage.savedContent("tracks/track-1.activity.json"))
        assertTrue(trackStore.tracks.isEmpty())
    }

    private class FixedFitFileDecoder(
        private val result: FitDecodeResult,
    ) : FitFileDecoder {
        override fun decode(bytes: ByteArray): FitDecodeResult = result
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
