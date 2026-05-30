package com.gpxeditor.shared.data.gpx

import com.gpxeditor.shared.domain.gpx.GpxDocument
import com.gpxeditor.shared.domain.gpx.GpxMetadata
import com.gpxeditor.shared.domain.gpx.GpxTrack
import com.gpxeditor.shared.domain.gpx.GpxTrackPoint
import com.gpxeditor.shared.domain.gpx.GpxTrackSegment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class GpxParserTest {
    @Test
    fun parsesValidTrackWithMetadataAndPoints() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="GPXFixer">
                <metadata>
                    <name>Morning Ride</name>
                    <desc>City loop</desc>
                    <time>2026-05-31T08:00:00Z</time>
                </metadata>
                <trk>
                    <name>Main Track</name>
                    <desc>First segment</desc>
                    <trkseg>
                        <trkpt lat="41.7151" lon="44.8271">
                            <ele>512.4</ele>
                            <time>2026-05-31T08:01:00Z</time>
                            <name>Start</name>
                        </trkpt>
                        <trkpt lat="41.7160" lon="44.8280">
                            <ele>518.0</ele>
                            <time>2026-05-31T08:02:00Z</time>
                        </trkpt>
                    </trkseg>
                </trk>
            </gpx>
        """.trimIndent()

        val document = GpxParser.parseOrThrow(xml)

        assertEquals("1.1", document.version)
        assertEquals("GPXFixer", document.creator)
        assertEquals(GpxMetadata("Morning Ride", "City loop", "2026-05-31T08:00:00Z"), document.metadata)
        assertEquals(1, document.tracks.size)
        assertEquals(2, document.pointCount)

        val track = document.tracks.first()
        assertEquals("Main Track", track.name)
        assertEquals("First segment", track.description)
        assertEquals(1, track.segments.size)

        val firstPoint = track.segments.first().points.first()
        assertEquals(41.7151, firstPoint.latitude)
        assertEquals(44.8271, firstPoint.longitude)
        assertEquals(512.4, firstPoint.elevationMeters)
        assertEquals("2026-05-31T08:01:00Z", firstPoint.time)
        assertEquals("Start", firstPoint.name)
    }

    @Test
    fun parsesGpxWithoutOptionalFields() {
        val xml = """
            <gpx>
                <trk>
                    <trkseg>
                        <trkpt lat="41.7151" lon="44.8271" />
                    </trkseg>
                </trk>
            </gpx>
        """.trimIndent()

        val document = GpxParser.parseOrThrow(xml)
        val point = document.tracks.single().segments.single().points.single()

        assertEquals("1.1", document.version)
        assertNull(document.creator)
        assertNull(document.metadata)
        assertNull(document.tracks.single().name)
        assertNull(point.elevationMeters)
        assertNull(point.time)
    }

    @Test
    fun parsesMultipleSegments() {
        val xml = """
            <gpx version="1.1">
                <trk>
                    <trkseg>
                        <trkpt lat="41.0" lon="44.0" />
                    </trkseg>
                    <trkseg>
                        <trkpt lat="42.0" lon="45.0" />
                        <trkpt lat="43.0" lon="46.0" />
                    </trkseg>
                </trk>
            </gpx>
        """.trimIndent()

        val track = GpxParser.parseOrThrow(xml).tracks.single()

        assertEquals(2, track.segments.size)
        assertEquals(3, track.pointCount)
    }

    @Test
    fun supportsNamespacesEscapedTextCdataAndSelfClosingTags() {
        val xml = """
            <gpx:gpx xmlns:gpx="http://www.topografix.com/GPX/1/1" version="1.1" creator="A&amp;B">
                <gpx:metadata>
                    <gpx:name>Alice &amp; Bob &lt;Route&gt;</gpx:name>
                    <gpx:desc><![CDATA[Fast <safe> route]]></gpx:desc>
                </gpx:metadata>
                <gpx:trk>
                    <gpx:name />
                    <gpx:trkseg>
                        <gpx:trkpt lat="41.7151" lon="44.8271" />
                    </gpx:trkseg>
                </gpx:trk>
            </gpx:gpx>
        """.trimIndent()

        val document = GpxParser.parseOrThrow(xml)

        assertEquals("A&B", document.creator)
        assertEquals("Alice & Bob <Route>", document.metadata?.name)
        assertEquals("Fast <safe> route", document.metadata?.description)
        assertNull(document.tracks.single().name)
    }

    @Test
    fun returnsFailureForMissingLatitude() {
        val result = GpxParser.parse(
            """
                <gpx>
                    <trk>
                        <trkseg>
                            <trkpt lon="44.8271" />
                        </trkseg>
                    </trk>
                </gpx>
            """.trimIndent(),
        )

        val failure = assertIs<GpxParseResult.Failure>(result)
        assertEquals("Track point is missing 'lat' attribute", failure.error.message)
    }

    @Test
    fun returnsFailureForInvalidLongitude() {
        val result = GpxParser.parse(
            """
                <gpx>
                    <trk>
                        <trkseg>
                            <trkpt lat="41.7151" lon="east" />
                        </trkseg>
                    </trk>
                </gpx>
            """.trimIndent(),
        )

        val failure = assertIs<GpxParseResult.Failure>(result)
        assertEquals("Track point has invalid 'lon' value: east", failure.error.message)
    }

    @Test
    fun returnsFailureForOutOfRangeCoordinates() {
        val latitudeResult = GpxParser.parse(
            """
                <gpx>
                    <trk>
                        <trkseg>
                            <trkpt lat="91.0" lon="44.8271" />
                        </trkseg>
                    </trk>
                </gpx>
            """.trimIndent(),
        )
        val longitudeResult = GpxParser.parse(
            """
                <gpx>
                    <trk>
                        <trkseg>
                            <trkpt lat="41.7151" lon="181.0" />
                        </trkseg>
                    </trk>
                </gpx>
            """.trimIndent(),
        )

        assertEquals(
            "Track point 'lat' value is out of range: 91.0",
            assertIs<GpxParseResult.Failure>(latitudeResult).error.message,
        )
        assertEquals(
            "Track point 'lon' value is out of range: 181.0",
            assertIs<GpxParseResult.Failure>(longitudeResult).error.message,
        )
    }

    @Test
    fun returnsFailureForInvalidElevation() {
        val result = GpxParser.parse(
            """
                <gpx>
                    <trk>
                        <trkseg>
                            <trkpt lat="41.7151" lon="44.8271">
                                <ele>high</ele>
                            </trkpt>
                        </trkseg>
                    </trk>
                </gpx>
            """.trimIndent(),
        )

        val failure = assertIs<GpxParseResult.Failure>(result)
        assertEquals("Track point has invalid 'ele' value: high", failure.error.message)
    }

    @Test
    fun serializesDocumentAndParsesItBack() {
        val document = GpxDocument(
            creator = "GPXFixer & Tests",
            metadata = GpxMetadata(
                name = "Round <Trip>",
                description = "Export & import",
                time = "2026-05-31T08:00:00Z",
            ),
            tracks = listOf(
                GpxTrack(
                    name = "Track & One",
                    description = "Primary <segment>",
                    segments = listOf(
                        GpxTrackSegment(
                            points = listOf(
                                GpxTrackPoint(
                                    latitude = 41.7151,
                                    longitude = 44.8271,
                                    elevationMeters = 512.4,
                                    time = "2026-05-31T08:01:00Z",
                                    name = "Start & Go",
                                    description = "First <point>",
                                ),
                                GpxTrackPoint(
                                    latitude = 41.716,
                                    longitude = 44.828,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val serialized = GpxSerializer.serialize(document)
        val parsed = GpxParser.parseOrThrow(serialized)

        assertEquals(document, parsed)
    }
}
