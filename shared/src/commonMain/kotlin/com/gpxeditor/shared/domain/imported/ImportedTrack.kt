package com.gpxeditor.shared.domain.imported

data class ImportedTrack(
    val id: String,
    val displayName: String,
    val originalFileName: String,
    val importedAt: String,
    val storageKey: String,
    val trackCount: Int,
    val pointCount: Int,
)
