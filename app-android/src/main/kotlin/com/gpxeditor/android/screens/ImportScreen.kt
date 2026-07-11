package com.gpxeditor.android.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gpxeditor.android.EmptyHistory
import com.gpxeditor.android.ImportScreenState
import com.gpxeditor.android.ImportedTrackRow
import com.gpxeditor.android.RecordingScreen
import com.gpxeditor.android.TrackDetailScreen
import com.gpxeditor.android.TrackTrimScreen
import com.gpxeditor.shared.domain.activity.ActivityDocument
import com.gpxeditor.shared.domain.imported.ImportedTrack
import com.gpxeditor.shared.feature.edittrack.DeleteGpxTrackPointResult
import com.gpxeditor.shared.feature.edittrack.MoveGpxTrackPointResult
import com.gpxeditor.shared.feature.edittrack.TrimGpxTrackResult

@Composable
fun ImportScreen(
    state: ImportScreenState,
    onImportClick: () -> Unit,
    onTrackClick: (ImportedTrack) -> Unit,
    onDeleteTrack: (ImportedTrack) -> Unit,
    onBackFromDetail: () -> Unit,
    onRenameTrack: (String) -> Unit,
    onTrimTrack: () -> Unit,
    onEditTrack: () -> Unit,
    onExportTrackAsGpx: () -> Unit,
    onExportTrackAsFit: () -> Unit,
    onBackFromTrim: () -> Unit,
    onPreviewTrim: (ActivityDocument, Int, Int) -> TrimGpxTrackResult,
    onSaveTrimmedTrack: (ActivityDocument) -> Unit,
    onBackFromEdit: () -> Unit,
    onDeleteTrackPoint: (ActivityDocument, Int) -> DeleteGpxTrackPointResult,
    onMoveTrackPoint: (ActivityDocument, Int, Double, Double) -> MoveGpxTrackPointResult,
    onSaveEditedTrack: (ActivityDocument) -> Unit,
) {
    var trackPendingDelete by remember { mutableStateOf<ImportedTrack?>(null) }
    var isRecordingOpen by remember { mutableStateOf(false) }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            if (isRecordingOpen) {
                RecordingScreen(onBackClick = { isRecordingOpen = false })
                return@Surface
            }

            state.editTrackDetail?.let { detail ->
                TrackEditScreen(
                    detail = detail,
                    isSaving = state.isLoadingTrackDetail,
                    errorMessage = state.errorMessage,
                    onBackClick = onBackFromEdit,
                    onDeletePoint = onDeleteTrackPoint,
                    onMovePoint = onMoveTrackPoint,
                    onSaveClick = onSaveEditedTrack,
                )
                return@Surface
            }

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
                    onRenameConfirm = onRenameTrack,
                    onTrimClick = onTrimTrack,
                    onEditClick = onEditTrack,
                    onExportGpxClick = onExportTrackAsGpx,
                    onExportFitClick = onExportTrackAsFit,
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
                    Button(onClick = { isRecordingOpen = true }) {
                        Text("Record")
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
                            onDeleteClick = { trackPendingDelete = track },
                        )
                    }
                }
            }

            trackPendingDelete?.let { track ->
                AlertDialog(
                    onDismissRequest = { trackPendingDelete = null },
                    title = { Text("Delete track?") },
                    text = { Text("\"${track.displayName}\" will be removed permanently.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                trackPendingDelete = null
                                onDeleteTrack(track)
                            },
                        ) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { trackPendingDelete = null }) {
                            Text("Cancel")
                        }
                    },
                )
            }
        }
    }
}
