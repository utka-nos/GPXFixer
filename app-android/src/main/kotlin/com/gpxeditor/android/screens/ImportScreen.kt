package com.gpxeditor.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gpxeditor.shared.domain.gpx.GpxDocument
import com.gpxeditor.shared.domain.imported.ImportedTrack
import com.gpxeditor.shared.feature.edittrack.TrimGpxTrackResult

@Composable
fun ImportScreen(
    state: ImportScreenState,
    onImportClick: () -> Unit,
    onTrackClick: (ImportedTrack) -> Unit,
    onBackFromDetail: () -> Unit,
    onTrimTrack: () -> Unit,
    onEditTrack: () -> Unit,
    onExportTrack: () -> Unit,
    onBackFromTrim: () -> Unit,
    onPreviewTrim: (GpxDocument, Int, Int) -> TrimGpxTrackResult,
    onSaveTrimmedTrack: (GpxDocument) -> Unit,
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            state.trimTrackDetail?.let { detail ->
                TrackTrimScreen(
                    detail = detail,
                    isSaving = state.isLoadingTrackDetail,
                    statusMessage = state.statusMessage,
                    errorMessage = state.errorMessage,
                    onBackClick = onBackFromTrim,
                    onPreviewTrim = onPreviewTrim,
                    onSaveClick = onSaveTrimmedTrack,
                )
                return@Surface
            }

            state.selectedTrackDetail?.let { detail ->
                TrackDetailScreen(
                    detail = detail,
                    statusMessage = state.statusMessage,
                    errorMessage = state.errorMessage,
                    onBackClick = onBackFromDetail,
                    onTrimClick = onTrimTrack,
                    onEditClick = onEditTrack,
                    onExportClick = onExportTrack,
                )
                return@Surface
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "GPXFixer",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = "Imported tracks",
                    style = MaterialTheme.typography.titleMedium,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        enabled = !state.isImporting,
                        onClick = onImportClick,
                    ) {
                        Text(if (state.isImporting) "Importing" else "Import GPX")
                    }
                    if (state.isImporting) {
                        CircularProgressIndicator()
                    }
                    if (state.isLoadingTrackDetail) {
                        CircularProgressIndicator()
                    }
                }

                state.statusMessage?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                state.errorMessage?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                if (state.tracks.isEmpty()) {
                    EmptyHistory()
                } else {
                    state.tracks.forEach { track ->
                        ImportedTrackRow(
                            track = track,
                            onClick = { onTrackClick(track) },
                        )
                    }
                }
            }
        }
    }
}
