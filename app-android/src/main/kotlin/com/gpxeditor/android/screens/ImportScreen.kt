package com.gpxeditor.android.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gpxeditor.android.ImportScreenState
import com.gpxeditor.android.recording.HeartRateSensorController
import com.gpxeditor.android.recording.PowerSensorController
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
    onRecordingClosed: () -> Unit,
    onRestoreRecording: () -> Unit,
    onDiscardRecording: () -> Unit,
    powerSensorController: PowerSensorController,
    heartRateSensorController: HeartRateSensorController,
) {
    var trackPendingDelete by remember { mutableStateOf<ImportedTrack?>(null) }
    var isRecordingOpen by remember { mutableStateOf(false) }
    var isSensorsOpen by remember { mutableStateOf(false) }

    MaterialTheme {
        if (isRecordingOpen) {
            RecordingScreen(
                onBackClick = {
                    isRecordingOpen = false
                    onRecordingClosed()
                },
            )
            return@MaterialTheme
        }

        if (isSensorsOpen) {
            SensorsScreen(
                powerSensorController = powerSensorController,
                heartRateSensorController = heartRateSensorController,
                onBackClick = { isSensorsOpen = false },
            )
            return@MaterialTheme
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
            return@MaterialTheme
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
            return@MaterialTheme
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
            return@MaterialTheme
        }

        Scaffold(
            topBar = {
                AppTopBar(
                    title = "GPXFixer",
                    actions = {
                        IconButton(
                            enabled = !state.isImporting,
                            onClick = onImportClick,
                        ) {
                            Icon(
                                imageVector = Icons.Default.UploadFile,
                                contentDescription = "Import track",
                            )
                        }
                        IconButton(onClick = { isRecordingOpen = true }) {
                            Icon(
                                imageVector = Icons.Default.FiberManualRecord,
                                contentDescription = "Record track",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                        IconButton(onClick = { isSensorsOpen = true }) {
                            Icon(
                                imageVector = Icons.Default.Sensors,
                                contentDescription = "Sensors",
                            )
                        }
                    },
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (state.isImporting || state.isLoadingTrackDetail) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
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

                Text(
                    text = "Imported tracks",
                    style = MaterialTheme.typography.titleMedium,
                )

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
        }

        state.recoveredRecording?.let { recovered ->
            AlertDialog(
                onDismissRequest = onDiscardRecording,
                title = { Text("Unfinished recording found") },
                text = {
                    Text(
                        "A recording was interrupted before it could be saved: " +
                            "${recovered.stats.pointCount} points, " +
                            "${formatDistance(recovered.stats.distanceMeters)}. Restore it?",
                    )
                },
                confirmButton = {
                    TextButton(onClick = onRestoreRecording) {
                        Text("Restore")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDiscardRecording) {
                        Text("Discard", color = MaterialTheme.colorScheme.error)
                    }
                },
            )
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
