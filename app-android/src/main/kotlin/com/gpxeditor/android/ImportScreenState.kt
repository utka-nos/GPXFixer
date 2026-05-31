package com.gpxeditor.android

import com.gpxeditor.shared.domain.imported.ImportedTrack
import com.gpxeditor.shared.feature.trackdetail.TrackDetail

data class ImportScreenState(
    val tracks: List<ImportedTrack> = emptyList(),
    val isImporting: Boolean = false,
    val isLoadingTrackDetail: Boolean = false,
    val selectedTrackDetail: TrackDetail? = null,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
)
