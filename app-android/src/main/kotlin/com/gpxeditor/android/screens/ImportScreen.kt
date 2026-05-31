package com.gpxeditor.android.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.gpxeditor.android.ImportScreenState
import com.gpxeditor.shared.domain.imported.ImportedTrack
import com.gpxeditor.shared.feature.trackdetail.TrackDetail

@Composable
fun ImportScreen(
    state: ImportScreenState,
    onImportClick: () -> Unit,
    onTrackClick: (ImportedTrack) -> Unit,
    onMapPreviewClick: (TrackDetail) -> Unit,
    onBackFromMap: () -> Unit,
    onBackFromDetail: () -> Unit,
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            state.selectedTrackMapDetail?.let { detail ->
                TrackMapFullScreen(
                    detail = detail,
                    onBackClick = onBackFromMap,
                )
                return@Surface
            }

            state.selectedTrackDetail?.let { detail ->
                TrackDetailScreen(
                    detail = detail,
                    onMapClick = { onMapPreviewClick(detail) },
                    onBackClick = onBackFromDetail,
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

@Composable
private fun EmptyHistory() {
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "No GPX tracks imported yet.",
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun ImportedTrackRow(
    track: ImportedTrack,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.small,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = track.displayName,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = track.originalFileName,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "${track.trackCount} tracks / ${track.pointCount} points",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "Imported at ${track.importedAt}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
