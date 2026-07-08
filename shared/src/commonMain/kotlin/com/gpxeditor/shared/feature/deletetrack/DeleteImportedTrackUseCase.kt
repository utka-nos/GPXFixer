package com.gpxeditor.shared.feature.deletetrack

import com.gpxeditor.shared.data.fit.FitSourceStore
import com.gpxeditor.shared.domain.imported.ImportedTrack
import com.gpxeditor.shared.domain.imported.ports.ActivityTrackFileStorage
import com.gpxeditor.shared.domain.imported.ports.ImportedTrackStore

class DeleteImportedTrackUseCase(
    private val fileStorage: ActivityTrackFileStorage,
    private val trackStore: ImportedTrackStore,
) {
    suspend operator fun invoke(track: ImportedTrack) {
        // Metadata goes first: if a file delete fails afterwards, the track
        // disappears from the list and only orphan files are left behind.
        trackStore.remove(track.id)
        fileStorage.delete(track.storageKey)
        fileStorage.delete(FitSourceStore.keyFor(track.id))
    }
}
