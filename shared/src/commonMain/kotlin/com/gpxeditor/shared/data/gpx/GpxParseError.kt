package com.gpxeditor.shared.data.gpx

import com.gpxeditor.shared.domain.gpx.GpxDocument

data class GpxParseError(
    val message: String,
    val line: Int? = null,
    val column: Int? = null,
)

class GpxParseException(
    val error: GpxParseError,
) : IllegalArgumentException(
    buildString {
        append(error.message)
        if (error.line != null && error.column != null) {
            append(" at ")
            append(error.line)
            append(":")
            append(error.column)
        }
    },
)

sealed interface GpxParseResult {
    data class Success(val document: GpxDocument) : GpxParseResult
    data class Failure(val error: GpxParseError) : GpxParseResult
}
