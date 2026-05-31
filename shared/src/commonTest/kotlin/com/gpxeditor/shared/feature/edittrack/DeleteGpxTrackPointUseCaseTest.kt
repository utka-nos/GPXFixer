package com.gpxeditor.shared.feature.edittrack

import com.gpxeditor.shared.data.gpx.GpxParser
import com.gpxeditor.shared.data.gpx.GpxSerializer
import com.gpxeditor.shared.domain.gpx.GpxDocument
import com.gpxeditor.shared.domain.gpx.GpxMetadata
import com.gpxeditor.shared.domain.gpx.GpxTrack
import com.gpxeditor.shared.domain.gpx.GpxTrackPoint
import com.gpxeditor.shared.domain.gpx.GpxTrackSegment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DeleteGpxTrackPointUseCaseTest {
    @Test
    fun deletesPointByGlobalIndexAndRecalculatesSummary() {
        val useCase = DeleteGpxTrackPointUseCase()

        val result = useCase(
            DeleteGpxTrackPointRequest(
                document = documentWithMultipleTracksAndSegments(),
                pointIndex = 3,
            ),
        )

        val success = assertIs<DeleteGpxTrackPointResult.Success>(result)

        assertEquals(3, success.deletedPointIndex)
        assertEquals(5, success.document.pointCount)
        assertEquals(
            listOf("p0", "p1", "p2", "p4", "p5"),
            success.document.tracks
                .flatMap { it.segments }
                .flatMap { it.points }
                .map { it.name },
        )
        assertEquals(2, success.document.tracks.size)
        assertEquals(3, success.document.tracks.first().segments.size)
        assertEquals(5, success.summary.pointCount)
        assertEquals("2026-05-31T08:00:00Z", success.summary.startTime)
        assertEquals("2026-05-31T08:05:00Z", success.summary.endTime)
    }

    @Test
    fun removesEmptySegmentsAndTracks() {
        val useCase = DeleteGpxTrackPointUseCase()

        val success = assertIs<DeleteGpxTrackPointResult.Success>(
            useCase(
                DeleteGpxTrackPointRequest(
                    document = documentWithSinglePointExtraTrack(),
                    pointIndex = 2,
                ),
            ),
        )

        assertEquals(2, success.document.pointCount)
        assertEquals(1, success.document.tracks.size)
        assertEquals("Main Track", success.document.tracks.single().name)
        assertEquals(1, success.document.tracks.single().segments.size)
    }

    @Test
    fun keepsMetadataAndProducesSerializableDocument() {
        val useCase = DeleteGpxTrackPointUseCase()

        val success = assertIs<DeleteGpxTrackPointResult.Success>(
            useCase(
                DeleteGpxTrackPointRequest(
                    document = documentWithMultipleTracksAndSegments(),
                    pointIndex = 1,
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
    fun returnsFailureForInvalidIndexes() {
        val useCase = DeleteGpxTrackPointUseCase()
        val document = documentWithMultipleTracksAndSegments()

        assertEquals(
            "Point index must be zero or greater.",
            failureMessage(useCase, document, pointIndex = -1),
        )
        assertEquals(
            "Point index is outside the GPX document point range.",
            failureMessage(useCase, document, pointIndex = 6),
        )
    }

    @Test
    fun returnsFailureForDocumentWithoutPoints() {
        val result = DeleteGpxTrackPointUseCase()(
            DeleteGpxTrackPointRequest(
                document = GpxDocument(tracks = listOf(GpxTrack())),
                pointIndex = 0,
            ),
        )

        val failure = assertIs<DeleteGpxTrackPointResult.Failure>(result)

        assertEquals("Cannot delete a point from an empty GPX document.", failure.error.message)
    }

    private fun failureMessage(
        useCase: DeleteGpxTrackPointUseCase,
        document: GpxDocument,
        pointIndex: Int,
    ): String {
        val result = useCase(
            DeleteGpxTrackPointRequest(
                document = document,
                pointIndex = pointIndex,
            ),
        )

        return assertIs<DeleteGpxTrackPointResult.Failure>(result).error.message
    }

    private fun documentWithSinglePointExtraTrack(): GpxDocument {
        return documentWithMultipleTracksAndSegments().copy(
            tracks = listOf(
                GpxTrack(
                    name = "Main Track",
                    segments = listOf(
                        GpxTrackSegment(points = listOf(point("p0", 0.0, "00"), point("p1", 0.001, "01"))),
                    ),
                ),
                GpxTrack(
                    name = "Extra Track",
                    segments = listOf(
                        GpxTrackSegment(points = listOf(point("p2", 0.002, "02"))),
                    ),
                ),
            ),
        )
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
                    segments = listOf(
                        GpxTrackSegment(points = listOf(point("p0", 0.0, "00"), point("p1", 0.001, "01"))),
                        GpxTrackSegment(points = listOf(point("p2", 0.002, "02"))),
                        GpxTrackSegment(points = listOf(point("p3", 0.003, "03"), point("p4", 0.004, "04"))),
                    ),
                ),
                GpxTrack(
                    name = "Extra Track",
                    segments = listOf(
                        GpxTrackSegment(points = listOf(point("p5", 0.005, "05"))),
                    ),
                ),
            ),
        )
    }

    private fun point(
        name: String,
        longitude: Double,
        minute: String,
    ): GpxTrackPoint {
        return GpxTrackPoint(
            latitude = 0.0,
            longitude = longitude,
            elevationMeters = 10.0,
            time = "2026-05-31T08:$minute:00Z",
            name = name,
        )
    }
}
