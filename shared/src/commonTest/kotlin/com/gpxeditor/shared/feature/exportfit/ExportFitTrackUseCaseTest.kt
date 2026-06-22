package com.gpxeditor.shared.feature.exportfit

import com.gpxeditor.shared.data.fit.FitActivityDecoder
import com.gpxeditor.shared.data.fit.FitEncoder
import com.gpxeditor.shared.data.fit.FitSourceStore
import com.gpxeditor.shared.domain.activity.ActivityDocument
import com.gpxeditor.shared.domain.activity.ActivityPoint
import com.gpxeditor.shared.domain.activity.ActivitySegment
import com.gpxeditor.shared.domain.activity.ActivityTrack
import com.gpxeditor.shared.domain.fit.FitDecodeResult
import com.gpxeditor.shared.domain.imported.ImportedTrack
import com.gpxeditor.shared.domain.imported.ports.ActivityTrackFileStorage
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalEncodingApi::class)
class ExportFitTrackUseCaseTest {
    @Test
    fun patchesOriginalWhenSourceIsAvailable() = runSuspend {
        val original = FitEncoder.encode(twoPointDocument())
        val storage = FakeStorage()
        storage.savedFiles[FitSourceStore.keyFor("track-1")] = Base64.encode(original)

        // The user deleted the second point and moved the first.
        val edited = ActivityDocument(
            tracks = listOf(
                ActivityTrack(
                    segments = listOf(
                        ActivitySegment(
                            points = listOf(
                                ActivityPoint(latitude = 42.5, longitude = 45.5, sourceIndex = 0),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val bytes = ExportFitTrackUseCase(storage).encode(trackWithId("track-1"), edited)
        val result = FitActivityDecoder().decode(bytes)

        val success = assertIs<FitDecodeResult.Success>(result)
        val points = success.document.tracks.single().segments.single().points
        assertEquals(1, points.size)
        assertEquals(42.5, points.single().latitude!!, absoluteTolerance = 1e-4)
        assertEquals(45.5, points.single().longitude!!, absoluteTolerance = 1e-4)
    }

    @Test
    fun fallsBackToEncodingWhenSourceMissing() = runSuspend {
        val storage = FakeStorage()
        val document = twoPointDocument()

        val bytes = ExportFitTrackUseCase(storage).encode(trackWithId("track-1"), document)
        val result = FitActivityDecoder().decode(bytes)

        val success = assertIs<FitDecodeResult.Success>(result)
        assertEquals(2, success.document.tracks.single().segments.single().points.size)
    }

    private fun twoPointDocument(): ActivityDocument {
        return ActivityDocument(
            tracks = listOf(
                ActivityTrack(
                    segments = listOf(
                        ActivitySegment(
                            points = listOf(
                                ActivityPoint(time = "2020-01-01T00:00:00Z", latitude = 41.0, longitude = 44.0, sourceIndex = 0),
                                ActivityPoint(time = "2020-01-01T00:00:01Z", latitude = 41.1, longitude = 44.1, sourceIndex = 1),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    private fun trackWithId(id: String): ImportedTrack {
        return ImportedTrack(
            id = id,
            displayName = "Track",
            originalFileName = "track.fit",
            importedAt = "2020-01-01T00:00:00Z",
            storageKey = "tracks/$id.activity.json",
            trackCount = 1,
            pointCount = 1,
        )
    }

    private class FakeStorage : ActivityTrackFileStorage {
        val savedFiles = mutableMapOf<String, String>()

        override suspend fun save(storageKey: String, content: String) {
            savedFiles[storageKey] = content
        }

        override suspend fun read(storageKey: String): String {
            return savedFiles[storageKey] ?: error("Missing $storageKey")
        }

        override suspend fun delete(storageKey: String) {
            savedFiles.remove(storageKey)
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
