package com.gpxeditor.shared.domain.fit

interface FitFileDecoder {
    fun decode(bytes: ByteArray): FitDecodeResult
}

sealed interface FitDecodeResult {
    data class Success(
        val document: FitActivityDocument,
    ) : FitDecodeResult

    data class Failure(
        val error: FitDecodeError,
    ) : FitDecodeResult
}

data class FitDecodeError(
    val message: String,
)
