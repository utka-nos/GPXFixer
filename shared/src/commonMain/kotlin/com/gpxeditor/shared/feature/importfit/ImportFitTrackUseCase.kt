package com.gpxeditor.shared.feature.importfit

import com.gpxeditor.shared.data.activity.ActivityDocumentJson
import com.gpxeditor.shared.data.activity.ActivityFitMapper
import com.gpxeditor.shared.data.fit.FitSourceStore
import com.gpxeditor.shared.domain.fit.FitDecodeError
import com.gpxeditor.shared.domain.fit.FitDecodeResult
import com.gpxeditor.shared.domain.fit.FitFileDecoder
import com.gpxeditor.shared.domain.activity.ActivityDocument
import com.gpxeditor.shared.domain.imported.ImportedTrack
import com.gpxeditor.shared.domain.imported.ports.ActivityTrackFileStorage
import com.gpxeditor.shared.domain.imported.ports.ImportClock
import com.gpxeditor.shared.domain.imported.ports.ImportIdGenerator
import com.gpxeditor.shared.domain.imported.ports.ImportedTrackStore
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class ImportFitTrackUseCase(
    private val fitFileDecoder: FitFileDecoder,
    private val fileStorage: ActivityTrackFileStorage,
    private val trackStore: ImportedTrackStore,
    private val idGenerator: ImportIdGenerator,
    private val clock: ImportClock,
) {
    suspend operator fun invoke(request: ImportFitTrackRequest): ImportFitTrackResult {
        return when (val decodeResult = fitFileDecoder.decode(request.content)) {
            is FitDecodeResult.Failure -> ImportFitTrackResult.Failure(decodeResult.error)
            is FitDecodeResult.Success -> importDecodedDocument(request, decodeResult)
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun importDecodedDocument(
        request: ImportFitTrackRequest,
        decodeResult: FitDecodeResult.Success,
    ): ImportFitTrackResult {
        val activityDocument = ActivityFitMapper.fromFitActivity(decodeResult.document)
        if (activityDocument.pointCount == 0 || !activityDocument.hasGpsPoints()) {
            return ImportFitTrackResult.Failure(
                FitDecodeError("FIT file does not contain GPS track points."),
            )
        }

        val id = idGenerator.nextId()
        val storageKey = storageKeyFor(id)
        val content = ActivityDocumentJson.serialize(activityDocument)
        val importedTrack = ImportedTrack(
            id = id,
            displayName = displayNameFor(request.originalFileName, activityDocument),
            originalFileName = request.originalFileName.trim(),
            importedAt = clock.nowIsoString(),
            storageKey = storageKey,
            trackCount = activityDocument.tracks.size,
            pointCount = activityDocument.pointCount,
        )

        val sourceKey = FitSourceStore.keyFor(id)
        fileStorage.save(storageKey, content)
        fileStorage.save(sourceKey, Base64.encode(request.content))
        try {
            trackStore.add(importedTrack)
        } catch (throwable: Throwable) {
            fileStorage.delete(storageKey)
            fileStorage.delete(sourceKey)
            throw throwable
        }

        return ImportFitTrackResult.Success(
            importedTrack = importedTrack,
            document = activityDocument,
        )
    }

    private fun storageKeyFor(id: String): String {
        return "tracks/${id.trim()}.activity.json"
    }

    private fun displayNameFor(originalFileName: String, document: ActivityDocument): String {
        return document.metadata.name?.trim()?.takeIf { it.isNotEmpty() }
            ?: document.tracks.firstNotNullOfOrNull { it.name?.trim()?.takeIf(String::isNotEmpty) }
            ?: originalFileName.trim()
                .removeSuffix(".fit")
                .removeSuffix(".FIT")
                .takeIf { it.isNotEmpty() }
            ?: "Untitled track"
    }

    private fun ActivityDocument.hasGpsPoints(): Boolean {
        return tracks.any { track ->
            track.segments.any { segment ->
                segment.points.any { point -> point.latitude != null && point.longitude != null }
            }
        }
    }
}

data class ImportFitTrackRequest(
    val originalFileName: String,
    val content: ByteArray,
)

sealed interface ImportFitTrackResult {
    data class Success(
        val importedTrack: ImportedTrack,
        val document: ActivityDocument,
    ) : ImportFitTrackResult

    data class Failure(
        val error: FitDecodeError,
    ) : ImportFitTrackResult
}
