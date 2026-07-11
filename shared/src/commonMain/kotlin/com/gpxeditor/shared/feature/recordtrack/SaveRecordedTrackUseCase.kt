package com.gpxeditor.shared.feature.recordtrack

import com.gpxeditor.shared.data.activity.ActivityDocumentJson
import com.gpxeditor.shared.domain.activity.ActivityDocument
import com.gpxeditor.shared.domain.imported.ImportedTrack
import com.gpxeditor.shared.domain.imported.ports.ActivityTrackFileStorage
import com.gpxeditor.shared.domain.imported.ports.ImportClock
import com.gpxeditor.shared.domain.imported.ports.ImportIdGenerator
import com.gpxeditor.shared.domain.imported.ports.ImportedTrackStore

/**
 * Saves a finished recording into the imported-track history through the
 * same storage pipeline as FIT imports (ActivityDocument JSON), so sensor
 * fields survive and detail/trim/edit/export flows work unchanged.
 */
class SaveRecordedTrackUseCase(
    private val fileStorage: ActivityTrackFileStorage,
    private val trackStore: ImportedTrackStore,
    private val idGenerator: ImportIdGenerator,
    private val clock: ImportClock,
) {
    suspend operator fun invoke(request: SaveRecordedTrackRequest): SaveRecordedTrackResult {
        val document = request.document
        if (document.pointCount == 0) {
            return SaveRecordedTrackResult.Failure("Recording does not contain any GPS points.")
        }

        val id = idGenerator.nextId()
        val importedTrack = ImportedTrack(
            id = id,
            displayName = displayNameFor(document),
            originalFileName = "Recorded with GPXFixer",
            importedAt = clock.nowIsoString(),
            storageKey = "tracks/${id.trim()}.activity.json",
            trackCount = document.tracks.size,
            pointCount = document.pointCount,
        )

        fileStorage.save(importedTrack.storageKey, ActivityDocumentJson.serialize(document))
        try {
            trackStore.add(importedTrack)
        } catch (throwable: Throwable) {
            fileStorage.delete(importedTrack.storageKey)
            throw throwable
        }

        return SaveRecordedTrackResult.Success(importedTrack)
    }

    private fun displayNameFor(document: ActivityDocument): String {
        document.metadata.name?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }

        // "2026-07-11T10:00:00Z" -> "Ride 2026-07-11 10:00"
        val startTime = document.metadata.startTime ?: return "Recorded track"
        return "Ride " + startTime
            .removeSuffix("Z")
            .replace('T', ' ')
            .substringBeforeLast(':')
    }
}

data class SaveRecordedTrackRequest(
    val document: ActivityDocument,
)

sealed interface SaveRecordedTrackResult {
    data class Success(
        val importedTrack: ImportedTrack,
    ) : SaveRecordedTrackResult

    data class Failure(
        val message: String,
    ) : SaveRecordedTrackResult
}
