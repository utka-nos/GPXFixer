package com.gpxeditor.shared.data.gpx

import com.gpxeditor.shared.domain.gpx.GpxDocument
import com.gpxeditor.shared.domain.gpx.GpxMetadata
import com.gpxeditor.shared.domain.gpx.GpxTrack
import com.gpxeditor.shared.domain.gpx.GpxTrackPoint

object GpxSerializer {
    fun serialize(document: GpxDocument): String {
        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            append("""<gpx version="${document.version.escapeXmlAttribute()}"""")
            document.creator?.let { append(""" creator="${it.escapeXmlAttribute()}"""") }
            append(""" xmlns="http://www.topografix.com/GPX/1/1">""")
            appendLine()

            document.metadata?.let { appendMetadata(it) }
            document.tracks.forEach { appendTrack(it) }

            appendLine("</gpx>")
        }
    }

    private fun StringBuilder.appendMetadata(metadata: GpxMetadata) {
        if (metadata.name == null && metadata.description == null && metadata.time == null) return

        appendLine("  <metadata>")
        appendTextElement("name", metadata.name, indent = "    ")
        appendTextElement("desc", metadata.description, indent = "    ")
        appendTextElement("time", metadata.time, indent = "    ")
        appendLine("  </metadata>")
    }

    private fun StringBuilder.appendTrack(track: GpxTrack) {
        appendLine("  <trk>")
        appendTextElement("name", track.name, indent = "    ")
        appendTextElement("desc", track.description, indent = "    ")
        track.segments.forEach { segment ->
            appendLine("    <trkseg>")
            segment.points.forEach { appendTrackPoint(it) }
            appendLine("    </trkseg>")
        }
        appendLine("  </trk>")
    }

    private fun StringBuilder.appendTrackPoint(point: GpxTrackPoint) {
        append("      <trkpt lat=\"")
        append(point.latitude)
        append("\" lon=\"")
        append(point.longitude)
        appendLine("\">")
        appendTextElement("ele", point.elevationMeters?.toString(), indent = "        ")
        appendTextElement("time", point.time, indent = "        ")
        appendTextElement("name", point.name, indent = "        ")
        appendTextElement("desc", point.description, indent = "        ")
        appendLine("      </trkpt>")
    }

    private fun StringBuilder.appendTextElement(
        tagName: String,
        value: String?,
        indent: String,
    ) {
        if (value == null) return

        append(indent)
        append("<")
        append(tagName)
        append(">")
        append(value.escapeXmlText())
        append("</")
        append(tagName)
        appendLine(">")
    }

    private fun String.escapeXmlText(): String {
        return buildString(length) {
            this@escapeXmlText.forEach { char ->
                when (char) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    else -> append(char)
                }
            }
        }
    }

    private fun String.escapeXmlAttribute(): String {
        return buildString(length) {
            this@escapeXmlAttribute.forEach { char ->
                when (char) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&apos;")
                    else -> append(char)
                }
            }
        }
    }
}
