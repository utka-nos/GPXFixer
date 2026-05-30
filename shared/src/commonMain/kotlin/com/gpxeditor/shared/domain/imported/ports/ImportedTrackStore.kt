package com.gpxeditor.shared.domain.imported.ports

import com.gpxeditor.shared.domain.imported.ImportedTrack

interface ImportedTrackStore {
    suspend fun getAll(): List<ImportedTrack>
    suspend fun add(track: ImportedTrack)
    suspend fun remove(id: String)
}
