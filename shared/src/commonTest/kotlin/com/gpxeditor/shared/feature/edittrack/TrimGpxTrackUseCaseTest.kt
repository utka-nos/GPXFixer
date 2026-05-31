package com.gpxeditor.shared.feature.edittrack

import com.gpxeditor.shared.data.gpx.GpxParser
import com.gpxeditor.shared.data.gpx.GpxSerializer
import com.gpxeditor.shared.domain.gpx.GpxDocument
import com.gpxeditor.shared.domain.gpx.GpxMetadata
import com.gpxeditor.shared.domain.gpx.GpxTrack
import com.gpxeditor.shared.domain.gpx.GpxTrackPoint
import com.gpxeditor.shared.domain.gpx.GpxTrackSegment
import com.gpxeditor.shared.feature.trackdetail.GpxCoordinate
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TrimGpxTrackUseCaseTest {
    @Test
    fun trimsDocumentByGlobalPointRangeAndRecalculatesSummary() {
        val useCase = TrimGpxTrackUseCase()

        val result = useCase(
            TrimGpxTrackRequest(
                document = documentWithMultipleTracksAndSegments(),
                startPointIndex = 1,
                endPointIndexInclusive = 4,
            ),
        )

        val success = assertIs<TrimGpxTrackResult.Success>(result)
        assertEquals(4, success.removedPointCount)
        assertEquals(1, success.document.tracks.size)
        assertEquals("Main Track", success.document.tracks.single().name)
        assertEquals(2, success.document.tracks.single().segments.size)
        assertEquals(
            listOf("p1", "p2", "p3", "p4"),
            success.document.tracks
                .single()
                .segments
                .flatMap { it.points }
                .map { it.name },
        )

        assertEquals(1, success.summary.trackCount)
        assertEquals(2, success.summary.segmentCount)
        assertEquals(4, success.summary.pointCount)
        assertEquals(4.0, success.summary.totalAscentMeters)
        assertEquals(2.0, success.summary.totalDescentMeters)
        assertEquals(11.0, success.summary.minElevationMeters)
        assertEquals(14.0, success.summary.maxElevationMeters)
        assertEquals("2026-05-31T08:01:00Z", success.summary.startTime)
        assertEquals("2026-05-31T08:04:00Z", success.summary.endTime)
        assertEquals(GpxCoordinate(0.0, 0.001), success.summary.startCoordinate)
        assertEquals(GpxCoordinate(0.0, 0.004), success.summary.endCoordinate)
        assertTrue(abs(success.summary.distanceMeters - 222.4) < 1.0)
    }

    @Test
    fun keepsMetadataAndProducesSerializableDocument() {
        val useCase = TrimGpxTrackUseCase()

        val success = assertIs<TrimGpxTrackResult.Success>(
            useCase(
                TrimGpxTrackRequest(
                    document = documentWithMultipleTracksAndSegments(),
                    startPointIndex = 2,
                    endPointIndexInclusive = 3,
                ),
            ),
        )

        assertEquals("1.1", success.document.version)
        assertEquals("GPXFixer", success.document.creator)
        assertEquals("Morning Ride", success.document.metadata?.name)

        val parsed = GpxParser.parseOrThrow(GpxSerializer.serialize(success.document))

        assertEquals(success.document, parsed)
    }

    @Test
    fun keepsSinglePointWhenRangeContainsOnePoint() {
        val useCase = TrimGpxTrackUseCase()

        val success = assertIs<TrimGpxTrackResult.Success>(
            useCase(
                TrimGpxTrackRequest(
                    document = documentWithMultipleTracksAndSegments(),
                    startPointIndex = 3,
                    endPointIndexInclusive = 3,
                ),
            ),
        )

        assertEquals(1, success.document.pointCount)
        assertEquals("p3", success.document.tracks.single().segments.single().points.single().name)
        assertEquals(0.0, success.summary.distanceMeters)
        assertEquals(0.0, success.summary.totalAscentMeters)
        assertEquals(0.0, success.summary.totalDescentMeters)
    }

    @Test
    fun returnsFailureForInvalidRanges() {
        val useCase = TrimGpxTrackUseCase()
        val document = documentWithMultipleTracksAndSegments()

        assertEquals(
            "Start point index must be zero or greater.",
            failureMessage(useCase, document, startPointIndex = -1, endPointIndexInclusive = 2),
        )
        assertEquals(
            "End point index must be greater than or equal to start point index.",
            failureMessage(useCase, document, startPointIndex = 3, endPointIndexInclusive = 2),
        )
        assertEquals(
            "End point index is outside the GPX document point range.",
            failureMessage(useCase, document, startPointIndex = 0, endPointIndexInclusive = 8),
        )
    }

    @Test
    fun returnsFailureForDocumentWithoutPoints() {
        val result = TrimGpxTrackUseCase()(
            TrimGpxTrackRequest(
                document = GpxDocument(tracks = listOf(GpxTrack())),
                startPointIndex = 0,
                endPointIndexInclusive = 0,
            ),
        )

        val failure = assertIs<TrimGpxTrackResult.Failure>(result)

        assertEquals("Cannot trim GPX document without track points.", failure.error.message)
    }

    private fun failureMessage(
        useCase: TrimGpxTrackUseCase,
        document: GpxDocument,
        startPointIndex: Int,
        endPointIndexInclusive: Int,
    ): String {
        val result = useCase(
            TrimGpxTrackRequest(
                document = document,
                startPointIndex = startPointIndex,
                endPointIndexInclusive = endPointIndexInclusive,
            ),
        )

        return assertIs<TrimGpxTrackResult.Failure>(result).error.message
    }

    private fun documentWithMultipleTracksAndSegments(): GpxDocument {
        return GpxDocument(
            version = "1.1",
            creator = "GPXFixer",
            metadata = GpxMetadata(
                name = "Morning Ride",
                description = "City loop",
                time = "2026-05-31T08:00:00Z",
            ),
            tracks = listOf(
                GpxTrack(
                    name = "Main Track",
                    description = "Primary",
                    segments = listOf(
                        GpxTrackSegment(
                            points = listOf(
                                point(name = "p0", longitude = 0.0, elevation = 10.0, minute = "00"),
                                point(name = "p1", longitude = 0.001, elevation = 11.0, minute = "01"),
                                point(name = "p2", longitude = 0.002, elevation = 14.0, minute = "02"),
                            ),
                        ),
                        GpxTrackSegment(
                            points = listOf(
                                point(name = "p3", longitude = 0.003, elevation = 12.0, minute = "03"),
                                point(name = "p4", longitude = 0.004, elevation = 13.0, minute = "04"),
                            ),
                        ),
                    ),
                ),
                GpxTrack(
                    name = "Extra Track",
                    segments = listOf(
                        GpxTrackSegment(
                            points = listOf(
                                point(name = "p5", longitude = 0.005, elevation = 15.0, minute = "05"),
                                point(name = "p6", longitude = 0.006, elevation = 16.0, minute = "06"),
                                point(name = "p7", longitude = 0.007, elevation = 17.0, minute = "07"),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    private fun point(
        name: String,
        longitude: Double,
        elevation: Double,
        minute: String,
    ): GpxTrackPoint {
        return GpxTrackPoint(
            latitude = 0.0,
            longitude = longitude,
            elevationMeters = elevation,
            time = "2026-05-31T08:$minute:00Z",
            name = name,
        )
    }
}
