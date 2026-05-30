package com.gpxeditor.shared.domain.imported.ports

interface GpxTrackFileStorage {
    suspend fun save(storageKey: String, content: String)
    suspend fun read(storageKey: String): String
    suspend fun delete(storageKey: String)
}
