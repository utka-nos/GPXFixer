package com.gpxeditor.shared.data.activity

import com.gpxeditor.shared.domain.activity.ActivityDocument
import com.gpxeditor.shared.domain.activity.ActivityMetadata
import com.gpxeditor.shared.domain.activity.ActivityPoint
import com.gpxeditor.shared.domain.activity.ActivitySegment
import com.gpxeditor.shared.domain.activity.ActivityTrack

object ActivityDocumentJson {
    fun serialize(document: ActivityDocument): String {
        return buildString {
            append("{")
            append("\"metadata\":")
            appendMetadata(document.metadata)
            append(",\"tracks\":[")
            document.tracks.forEachIndexed { index, track ->
                if (index > 0) append(",")
                appendTrack(track)
            }
            append("]}")
        }
    }

    fun parse(json: String): ActivityJsonParseResult {
        return try {
            ActivityJsonParseResult.Success(parseOrThrow(json))
        } catch (exception: ActivityJsonParseException) {
            ActivityJsonParseResult.Failure(exception.message ?: "Invalid activity JSON")
        }
    }

    fun parseOrThrow(json: String): ActivityDocument {
        val root = JsonParser(json).parse() as? JsonValue.Object
            ?: throw ActivityJsonParseException("Expected activity JSON object")

        return ActivityDocument(
            metadata = root.obj("metadata")?.toMetadata() ?: ActivityMetadata(),
            tracks = root.array("tracks")?.mapNotNull { value ->
                (value as? JsonValue.Object)?.toTrack()
            } ?: emptyList(),
        )
    }

    private fun StringBuilder.appendMetadata(metadata: ActivityMetadata) {
        append("{")
        appendStringProperty("name", metadata.name, isFirst = true)
        appendStringProperty("description", metadata.description)
        appendStringProperty("sport", metadata.sport)
        appendStringProperty("startTime", metadata.startTime)
        appendStringProperty("source", metadata.source)
        append("}")
    }

    private fun StringBuilder.appendTrack(track: ActivityTrack) {
        append("{")
        appendStringProperty("name", track.name, isFirst = true)
        appendStringProperty("description", track.description)
        append(",\"segments\":[")
        track.segments.forEachIndexed { index, segment ->
            if (index > 0) append(",")
            appendSegment(segment)
        }
        append("]}")
    }

    private fun StringBuilder.appendSegment(segment: ActivitySegment) {
        append("{\"points\":[")
        segment.points.forEachIndexed { index, point ->
            if (index > 0) append(",")
            appendPoint(point)
        }
        append("]}")
    }

    private fun StringBuilder.appendPoint(point: ActivityPoint) {
        append("{")
        appendStringProperty("time", point.time, isFirst = true)
        appendNumberProperty("latitude", point.latitude)
        appendNumberProperty("longitude", point.longitude)
        appendNumberProperty("elevationMeters", point.elevationMeters)
        appendStringProperty("name", point.name)
        appendStringProperty("description", point.description)
        appendNumberProperty("heartRateBpm", point.heartRateBpm)
        appendNumberProperty("powerWatts", point.powerWatts)
        appendNumberProperty("cadenceRpm", point.cadenceRpm)
        appendNumberProperty("distanceMeters", point.distanceMeters)
        appendNumberProperty("speedMetersPerSecond", point.speedMetersPerSecond)
        appendNumberProperty("sourceIndex", point.sourceIndex)
        append("}")
    }

    private fun StringBuilder.appendStringProperty(
        name: String,
        value: String?,
        isFirst: Boolean = false,
    ) {
        if (!isFirst) append(",")
        append("\"")
        append(name)
        append("\":")
        if (value == null) {
            append("null")
        } else {
            append("\"")
            append(value.escapeJson())
            append("\"")
        }
    }

    private fun StringBuilder.appendNumberProperty(name: String, value: Number?) {
        append(",\"")
        append(name)
        append("\":")
        append(value?.toString() ?: "null")
    }

    private fun String.escapeJson(): String {
        return buildString(length) {
            this@escapeJson.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
        }
    }

    private fun JsonValue.Object.toMetadata(): ActivityMetadata {
        return ActivityMetadata(
            name = string("name"),
            description = string("description"),
            sport = string("sport"),
            startTime = string("startTime"),
            source = string("source"),
        )
    }

    private fun JsonValue.Object.toTrack(): ActivityTrack {
        return ActivityTrack(
            name = string("name"),
            description = string("description"),
            segments = array("segments")?.mapNotNull { value ->
                (value as? JsonValue.Object)?.toSegment()
            } ?: emptyList(),
        )
    }

    private fun JsonValue.Object.toSegment(): ActivitySegment {
        return ActivitySegment(
            points = array("points")?.mapNotNull { value ->
                (value as? JsonValue.Object)?.toPoint()
            } ?: emptyList(),
        )
    }

    private fun JsonValue.Object.toPoint(): ActivityPoint {
        return ActivityPoint(
            time = string("time"),
            latitude = double("latitude"),
            longitude = double("longitude"),
            elevationMeters = double("elevationMeters"),
            name = string("name"),
            description = string("description"),
            heartRateBpm = int("heartRateBpm"),
            powerWatts = int("powerWatts"),
            cadenceRpm = int("cadenceRpm"),
            distanceMeters = double("distanceMeters"),
            speedMetersPerSecond = double("speedMetersPerSecond"),
            sourceIndex = int("sourceIndex"),
        )
    }

    private fun JsonValue.Object.obj(name: String): JsonValue.Object? = values[name] as? JsonValue.Object
    private fun JsonValue.Object.array(name: String): List<JsonValue>? = (values[name] as? JsonValue.Array)?.values
    private fun JsonValue.Object.string(name: String): String? = (values[name] as? JsonValue.StringValue)?.value
    private fun JsonValue.Object.double(name: String): Double? = (values[name] as? JsonValue.NumberValue)?.value
    private fun JsonValue.Object.int(name: String): Int? = double(name)?.toInt()
}

sealed interface ActivityJsonParseResult {
    data class Success(val document: ActivityDocument) : ActivityJsonParseResult
    data class Failure(val message: String) : ActivityJsonParseResult
}

private class ActivityJsonParseException(message: String) : RuntimeException(message)

private sealed interface JsonValue {
    data class Object(val values: Map<String, JsonValue>) : JsonValue
    data class Array(val values: List<JsonValue>) : JsonValue
    data class StringValue(val value: String) : JsonValue
    data class NumberValue(val value: Double) : JsonValue
    data object NullValue : JsonValue
}

private class JsonParser(
    private val input: String,
) {
    private var index = 0

    fun parse(): JsonValue {
        val value = parseValue()
        skipWhitespace()
        if (index != input.length) {
            fail("Unexpected trailing content")
        }
        return value
    }

    private fun parseValue(): JsonValue {
        skipWhitespace()
        return when (peek()) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> JsonValue.StringValue(parseString())
            'n' -> {
                consumeLiteral("null")
                JsonValue.NullValue
            }
            else -> parseNumber()
        }
    }

    private fun parseObject(): JsonValue.Object {
        expect('{')
        skipWhitespace()
        val values = mutableMapOf<String, JsonValue>()
        if (consumeIf('}')) return JsonValue.Object(values)

        while (true) {
            skipWhitespace()
            val key = parseString()
            skipWhitespace()
            expect(':')
            values[key] = parseValue()
            skipWhitespace()
            if (consumeIf('}')) break
            expect(',')
        }

        return JsonValue.Object(values)
    }

    private fun parseArray(): JsonValue.Array {
        expect('[')
        skipWhitespace()
        val values = mutableListOf<JsonValue>()
        if (consumeIf(']')) return JsonValue.Array(values)

        while (true) {
            values += parseValue()
            skipWhitespace()
            if (consumeIf(']')) break
            expect(',')
        }

        return JsonValue.Array(values)
    }

    private fun parseString(): String {
        expect('"')
        return buildString {
            while (true) {
                if (index >= input.length) fail("Unterminated string")
                when (val char = input[index++]) {
                    '"' -> return@buildString
                    '\\' -> append(parseEscape())
                    else -> append(char)
                }
            }
        }
    }

    private fun parseEscape(): Char {
        if (index >= input.length) fail("Unterminated escape sequence")
        return when (val char = input[index++]) {
            '"' -> '"'
            '\\' -> '\\'
            '/' -> '/'
            'b' -> '\b'
            'f' -> '\u000C'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            else -> fail("Unsupported escape sequence: \\$char")
        }
    }

    private fun parseNumber(): JsonValue.NumberValue {
        val start = index
        if (peek() == '-') index += 1
        consumeDigits()
        if (peekOrNull() == '.') {
            index += 1
            consumeDigits()
        }
        val exponent = peekOrNull()
        if (exponent == 'e' || exponent == 'E') {
            index += 1
            val sign = peekOrNull()
            if (sign == '+' || sign == '-') index += 1
            consumeDigits()
        }

        val rawValue = input.substring(start, index)
        val value = rawValue.toDoubleOrNull() ?: fail("Invalid number: $rawValue")
        return JsonValue.NumberValue(value)
    }

    private fun consumeDigits() {
        val start = index
        while (peekOrNull()?.isDigit() == true) {
            index += 1
        }
        if (start == index) fail("Expected digit")
    }

    private fun consumeLiteral(literal: String) {
        if (!input.startsWith(literal, index)) {
            fail("Expected '$literal'")
        }
        index += literal.length
    }

    private fun expect(char: Char) {
        skipWhitespace()
        if (peek() != char) fail("Expected '$char'")
        index += 1
    }

    private fun consumeIf(char: Char): Boolean {
        skipWhitespace()
        if (peekOrNull() != char) return false
        index += 1
        return true
    }

    private fun skipWhitespace() {
        while (peekOrNull()?.isWhitespace() == true) {
            index += 1
        }
    }

    private fun peek(): Char = peekOrNull() ?: fail("Unexpected end of JSON")
    private fun peekOrNull(): Char? = input.getOrNull(index)

    private fun fail(message: String): Nothing {
        throw ActivityJsonParseException(message)
    }
}
