package com.gpxeditor.shared.feature.importgpx

import com.gpxeditor.shared.data.gpx.GpxParseError
import com.gpxeditor.shared.data.gpx.GpxParseResult
import com.gpxeditor.shared.data.gpx.GpxParser
import com.gpxeditor.shared.domain.gpx.GpxDocument
import com.gpxeditor.shared.domain.imported.ImportedTrack
import com.gpxeditor.shared.domain.imported.ports.GpxTrackFileStorage
import com.gpxeditor.shared.domain.imported.ports.ImportClock
import com.gpxeditor.shared.domain.imported.ports.ImportIdGenerator
import com.gpxeditor.shared.domain.imported.ports.ImportedTrackStore

class ImportGpxTrackUseCase(
    private val fileStorage: GpxTrackFileStorage,
    private val trackStore: ImportedTrackStore,
    private val idGenerator: ImportIdGenerator,
    private val clock: ImportClock,
) {
    suspend operator fun invoke(request: ImportGpxTrackRequest): ImportGpxTrackResult {
        return when (val parseResult = GpxParser.parse(request.content)) {
            is GpxParseResult.Failure -> ImportGpxTrackResult.Failure(parseResult.error)
            is GpxParseResult.Success -> importParsedDocument(request, parseResult.document)
        }
    }

    private suspend fun importParsedDocument(
        request: ImportGpxTrackRequest,
        document: GpxDocument,
    ): ImportGpxTrackResult.Success {
        val id = idGenerator.nextId()
        val storageKey = storageKeyFor(id)
        val importedTrack = ImportedTrack(
            id = id,
            displayName = displayNameFor(request.originalFileName, document),
            originalFileName = request.originalFileName.trim(),
            importedAt = clock.nowIsoString(),
            storageKey = storageKey,
            trackCount = document.tracks.size,
            pointCount = document.pointCount,
        )

        fileStorage.save(storageKey, request.content)
        try {
            trackStore.add(importedTrack)
        } catch (throwable: Throwable) {
            fileStorage.delete(storageKey)
            throw throwable
        }

        return ImportGpxTrackResult.Success(importedTrack, document)
    }

    private fun storageKeyFor(id: String): String {
        return "tracks/${id.trim()}.gpx"
    }

    private fun displayNameFor(originalFileName: String, document: GpxDocument): String {
        return document.metadata?.name?.trim()?.takeIf { it.isNotEmpty() }
            ?: document.tracks.firstNotNullOfOrNull { it.name?.trim()?.takeIf(String::isNotEmpty) }
            ?: originalFileName.trim().removeSuffix(".gpx").removeSuffix(".GPX").takeIf { it.isNotEmpty() }
            ?: "Untitled track"
    }
}

data class ImportGpxTrackRequest(
    val originalFileName: String,
    val content: String,
)

sealed interface ImportGpxTrackResult {
    data class Success(
        val importedTrack: ImportedTrack,
        val document: GpxDocument,
    ) : ImportGpxTrackResult

    data class Failure(
        val error: GpxParseError,
    ) : ImportGpxTrackResult
}
