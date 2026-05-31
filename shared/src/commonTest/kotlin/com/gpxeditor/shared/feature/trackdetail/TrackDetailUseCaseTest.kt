package com.gpxeditor.shared.feature.trackdetail

import com.gpxeditor.shared.domain.imported.ImportedTrack
import com.gpxeditor.shared.domain.imported.ports.GpxTrackFileStorage
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrackDetailUseCaseTest {
    @Test
    fun loadsSavedTrackAndBuildsSummary() = runSuspend {
        val storage = FakeGpxTrackFileStorage(
            "tracks/track-1.gpx" to """
                <gpx version="1.1" creator="GPXFixer">
                    <metadata>
                        <name>Morning Ride</name>
                    </metadata>
                    <trk>
                        <trkseg>
                            <trkpt lat="0.0" lon="0.0">
                                <ele>10.0</ele>
                                <time>2026-05-31T08:00:00Z</time>
                            </trkpt>
                            <trkpt lat="0.0" lon="0.001">
                                <ele>18.0</ele>
                                <time>2026-05-31T08:01:00Z</time>
                            </trkpt>
                            <trkpt lat="0.0" lon="0.002">
                                <ele>12.0</ele>
                                <time>2026-05-31T08:02:00Z</time>
                            </trkpt>
                        </trkseg>
                    </trk>
                </gpx>
            """.trimIndent(),
        )
        val useCase = TrackDetailUseCase(storage)

        val result = useCase(importedTrack())

        val detail = assertIs<TrackDetailResult.Success>(result).detail
        assertEquals("Morning Ride", detail.document.metadata?.name)
        assertEquals(1, detail.summary.trackCount)
        assertEquals(1, detail.summary.segmentCount)
        assertEquals(3, detail.summary.pointCount)
        assertEquals(8.0, detail.summary.totalAscentMeters)
        assertEquals(6.0, detail.summary.totalDescentMeters)
        assertEquals(10.0, detail.summary.minElevationMeters)
        assertEquals(18.0, detail.summary.maxElevationMeters)
        assertEquals("2026-05-31T08:00:00Z", detail.summary.startTime)
        assertEquals("2026-05-31T08:02:00Z", detail.summary.endTime)
        assertEquals(GpxCoordinate(0.0, 0.0), detail.summary.startCoordinate)
        assertEquals(GpxCoordinate(0.0, 0.002), detail.summary.endCoordinate)
        assertTrue(abs(detail.summary.distanceMeters - 222.4) < 1.0)
        assertTrue(detail.warnings.isEmpty())

        assertEquals(1, detail.segments.size)
        assertEquals(1, detail.segments.single().index)
        assertEquals(3, detail.segments.single().pointCount)
        assertTrue(abs(detail.segments.single().distanceMeters - 222.4) < 1.0)
    }

    @Test
    fun reportsWarningsForSparseTrackData() = runSuspend {
        val storage = FakeGpxTrackFileStorage(
            "tracks/track-1.gpx" to """
                <gpx>
                    <trk>
                        <trkseg>
                            <trkpt lat="41.0" lon="44.0" />
                        </trkseg>
                        <trkseg />
                    </trk>
                </gpx>
            """.trimIndent(),
        )
        val useCase = TrackDetailUseCase(storage)

        val detail = assertIs<TrackDetailResult.Success>(useCase(importedTrack())).detail

        assertNull(detail.summary.totalAscentMeters)
        assertNull(detail.summary.totalDescentMeters)
        assertNull(detail.summary.minElevationMeters)
        assertNull(detail.summary.maxElevationMeters)
        assertEquals(
            listOf(
                "Some segments are empty.",
                "No elevation data found.",
                "No timestamp data found.",
            ),
            detail.warnings,
        )
    }

    @Test
    fun returnsFailureForCorruptedSavedGpx() = runSuspend {
        val storage = FakeGpxTrackFileStorage(
            "tracks/track-1.gpx" to "<gpx><trk><trkseg><trkpt lon=\"44.0\" /></trkseg></trk></gpx>",
        )
        val useCase = TrackDetailUseCase(storage)

        val failure = assertIs<TrackDetailResult.Failure>(useCase(importedTrack()))

        assertEquals("Track point is missing 'lat' attribute", failure.error.message)
    }

    private fun importedTrack(): ImportedTrack {
        return ImportedTrack(
            id = "track-1",
            displayName = "Morning Ride",
            originalFileName = "ride.gpx",
            importedAt = "2026-05-31T10:00:00Z",
            storageKey = "tracks/track-1.gpx",
            trackCount = 1,
            pointCount = 3,
        )
    }

    private class FakeGpxTrackFileStorage(
        vararg files: Pair<String, String>,
    ) : GpxTrackFileStorage {
        private val savedFiles = files.toMap().toMutableMap()

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
