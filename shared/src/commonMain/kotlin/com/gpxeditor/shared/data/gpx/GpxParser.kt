package com.gpxeditor.shared.data.gpx

import com.gpxeditor.shared.domain.gpx.GpxDocument
import com.gpxeditor.shared.domain.gpx.GpxMetadata
import com.gpxeditor.shared.domain.gpx.GpxTrack
import com.gpxeditor.shared.domain.gpx.GpxTrackPoint
import com.gpxeditor.shared.domain.gpx.GpxTrackSegment

object GpxParser {
    fun parse(xml: String): GpxParseResult {
        return try {
            GpxParseResult.Success(parseOrThrow(xml))
        } catch (exception: GpxParseException) {
            GpxParseResult.Failure(exception.error)
        }
    }

    fun parseOrThrow(xml: String): GpxDocument {
        val root = SimpleXmlParser(xml).parse()
        if (root.localName != "gpx") {
            throw GpxParseException(
                GpxParseError("Expected <gpx> root element, got <${root.name}>"),
            )
        }

        val tracks = root.children
            .filter { it.localName == "trk" }
            .map(::parseTrack)

        return GpxDocument(
            version = root.attributes["version"] ?: "1.1",
            creator = root.attributes["creator"],
            metadata = root.child("metadata")?.let(::parseMetadata),
            tracks = tracks,
        )
    }

    private fun parseMetadata(element: XmlElement): GpxMetadata {
        return GpxMetadata(
            name = element.childText("name"),
            description = element.childText("desc"),
            time = element.childText("time"),
        )
    }

    private fun parseTrack(element: XmlElement): GpxTrack {
        return GpxTrack(
            name = element.childText("name"),
            description = element.childText("desc"),
            segments = element.children
                .filter { it.localName == "trkseg" }
                .map(::parseTrackSegment),
        )
    }

    private fun parseTrackSegment(element: XmlElement): GpxTrackSegment {
        return GpxTrackSegment(
            points = element.children
                .filter { it.localName == "trkpt" }
                .map(::parseTrackPoint),
        )
    }

    private fun parseTrackPoint(element: XmlElement): GpxTrackPoint {
        val latitude = element.requiredCoordinate("lat", -90.0, 90.0)
        val longitude = element.requiredCoordinate("lon", -180.0, 180.0)

        return GpxTrackPoint(
            latitude = latitude,
            longitude = longitude,
            elevationMeters = element.optionalDouble("ele"),
            time = element.childText("time"),
            name = element.childText("name"),
            description = element.childText("desc"),
        )
    }

    private fun XmlElement.requiredCoordinate(
        name: String,
        minValue: Double,
        maxValue: Double,
    ): Double {
        val rawValue = attributes[name]
            ?: throw GpxParseException(GpxParseError("Track point is missing '$name' attribute"))
        val value = rawValue.toDoubleOrNull()
            ?: throw GpxParseException(GpxParseError("Track point has invalid '$name' value: $rawValue"))

        if (value !in minValue..maxValue) {
            throw GpxParseException(
                GpxParseError("Track point '$name' value is out of range: $rawValue"),
            )
        }

        return value
    }

    private fun XmlElement.optionalDouble(localName: String): Double? {
        val rawValue = childText(localName) ?: return null
        return rawValue.toDoubleOrNull()
            ?: throw GpxParseException(GpxParseError("Track point has invalid '$localName' value: $rawValue"))
    }
}
