package com.gpxeditor.shared.feature.renametrack

import com.gpxeditor.shared.domain.imported.ImportedTrack
import com.gpxeditor.shared.domain.imported.ports.ImportedTrackStore

class RenameTrackUseCase(
    private val trackStore: ImportedTrackStore,
) {
    suspend operator fun invoke(request: RenameTrackRequest): RenameTrackResult {
        val newDisplayName = request.newDisplayName.trim()
        if (newDisplayName.isEmpty()) {
            return RenameTrackResult.Failure("Track name cannot be empty")
        }

        val updatedTrack = request.track.copy(displayName = newDisplayName)
        if (updatedTrack.displayName != request.track.displayName) {
            trackStore.add(updatedTrack)
        }

        return RenameTrackResult.Success(updatedTrack)
    }
}

data class RenameTrackRequest(
    val track: ImportedTrack,
    val newDisplayName: String,
)

sealed interface RenameTrackResult {
    data class Success(
        val importedTrack: ImportedTrack,
    ) : RenameTrackResult

    data class Failure(
        val message: String,
    ) : RenameTrackResult
}
