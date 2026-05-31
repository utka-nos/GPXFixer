package com.gpxeditor.shared.feature.edittrack

import com.gpxeditor.shared.data.gpx.GpxParser
import com.gpxeditor.shared.data.gpx.GpxSerializer
import com.gpxeditor.shared.domain.gpx.GpxDocument
import com.gpxeditor.shared.domain.gpx.GpxMetadata
import com.gpxeditor.shared.domain.gpx.GpxTrack
import com.gpxeditor.shared.domain.gpx.GpxTrackPoint
import com.gpxeditor.shared.domain.gpx.GpxTrackSegment
import com.gpxeditor.shared.feature.trackdetail.GpxCoordinate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MoveGpxTrackPointUseCaseTest {
    @Test
    fun movesPointByGlobalIndexAndKeepsPointData() {
        val result = MoveGpxTrackPointUseCase()(
            MoveGpxTrackPointRequest(
                document = documentWithMultipleTracksAndSegments(),
                pointIndex = 2,
                latitude = 41.7151,
                longitude = 44.8271,
            ),
        )

        val success = assertIs<MoveGpxTrackPointResult.Success>(result)
        val movedPoint = success.document.tracks
            .first()
            .segments
            .last()
            .points
            .single()

        assertEquals(2, success.movedPointIndex)
        assertEquals(41.7151, movedPoint.latitude)
        assertEquals(44.8271, movedPoint.longitude)
        assertEquals(12.0, movedPoint.elevationMeters)
        assertEquals("2026-05-31T08:02:00Z", movedPoint.time)
        assertEquals("p2", movedPoint.name)
        assertEquals(4, success.document.pointCount)
        assertEquals(4, success.summary.pointCount)
        assertEquals(GpxCoordinate(0.0, 0.0), success.summary.startCoordinate)
    }

    @Test
    fun keepsMetadataAndProducesSerializableDocument() {
        val success = assertIs<MoveGpxTrackPointResult.Success>(
            MoveGpxTrackPointUseCase()(
                MoveGpxTrackPointRequest(
                    document = documentWithMultipleTracksAndSegments(),
                    pointIndex = 1,
                    latitude = 10.0,
                    longitude = 20.0,
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
    fun returnsFailureForInvalidInput() {
        val useCase = MoveGpxTrackPointUseCase()
        val document = documentWithMultipleTracksAndSegments()

        assertEquals(
            "Point index must be zero or greater.",
            failureMessage(useCase, document, pointIndex = -1, latitude = 0.0, longitude = 0.0),
        )
        assertEquals(
            "Point index is outside the GPX document point range.",
            failureMessage(useCase, document, pointIndex = 4, latitude = 0.0, longitude = 0.0),
        )
        assertEquals(
            "Latitude must be between -90 and 90 degrees.",
            failureMessage(useCase, document, pointIndex = 0, latitude = 91.0, longitude = 0.0),
        )
        assertEquals(
            "Longitude must be between -180 and 180 degrees.",
            failureMessage(useCase, document, pointIndex = 0, latitude = 0.0, longitude = 181.0),
        )
    }

    private fun failureMessage(
        useCase: MoveGpxTrackPointUseCase,
        document: GpxDocument,
        pointIndex: Int,
        latitude: Double,
        longitude: Double,
    ): String {
        val result = useCase(
            MoveGpxTrackPointRequest(
                document = document,
                pointIndex = pointIndex,
                latitude = latitude,
                longitude = longitude,
            ),
        )

        return assertIs<MoveGpxTrackPointResult.Failure>(result).error.message
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
                    ),
                ),
                GpxTrack(
                    name = "Extra Track",
                    segments = listOf(
                        GpxTrackSegment(points = listOf(point("p3", 0.003, "03"))),
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
            elevationMeters = 10.0 + minute.toInt(),
            time = "2026-05-31T08:$minute:00Z",
            name = name,
        )
    }
}
