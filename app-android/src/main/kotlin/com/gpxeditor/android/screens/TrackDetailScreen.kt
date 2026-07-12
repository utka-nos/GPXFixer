package com.gpxeditor.android.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gpxeditor.shared.feature.trackdetail.TrackDetail

@Composable
fun TrackDetailScreen(
    detail: TrackDetail,
    statusMessage: String?,
    errorMessage: String?,
    onBackClick: () -> Unit,
    onRenameConfirm: (String) -> Unit,
    onTrimClick: () -> Unit,
    onEditClick: () -> Unit,
    onExportGpxClick: () -> Unit,
    onExportFitClick: () -> Unit,
) {
    var isMapFullScreen by remember { mutableStateOf(false) }
    var isPowerChartFullScreen by remember { mutableStateOf(false) }
    var isHeartRateChartFullScreen by remember { mutableStateOf(false) }
    var isSpeedChartFullScreen by remember { mutableStateOf(false) }
    var isRenameDialogVisible by remember { mutableStateOf(false) }

    if (isMapFullScreen) {
        TrackMapFullScreen(
            document = detail.document,
            onBackClick = { isMapFullScreen = false },
        )
        return
    }

    if (isPowerChartFullScreen) {
        TrackChartFullScreen(
            title = "Power",
            unit = "W",
            unitLong = "watts",
            samples = detail.powerSamples,
            onBackClick = { isPowerChartFullScreen = false },
        )
        return
    }

    if (isHeartRateChartFullScreen) {
        TrackChartFullScreen(
            title = "Heart rate",
            unit = "bpm",
            unitLong = "beats per minute",
            samples = detail.heartRateSamples,
            onBackClick = { isHeartRateChartFullScreen = false },
        )
        return
    }

    if (isSpeedChartFullScreen) {
        TrackChartFullScreen(
            title = "Speed",
            unit = "km/h",
            unitLong = "kilometers per hour",
            samples = detail.speedSamples,
            onBackClick = { isSpeedChartFullScreen = false },
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TrackDetailTopBar(
            onBackClick = onBackClick,
            onRenameClick = { isRenameDialogVisible = true },
            onTrimClick = onTrimClick,
            onEditClick = onEditClick,
            onExportGpxClick = onExportGpxClick,
            onExportFitClick = onExportFitClick,
        )

        Text(
            text = detail.importedTrack.displayName,
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = detail.importedTrack.originalFileName,
            style = MaterialTheme.typography.bodyMedium,
        )

        statusMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        TrackMapSection(
            document = detail.document,
            onOpenMap = { isMapFullScreen = true },
        )
        SummarySection(
            title = "Summary",
            importedAt = detail.importedTrack.importedAt,
            summary = detail.summary,
        )
        TrackChartSection(
            title = "Power",
            unit = "W",
            samples = detail.powerSamples,
            onOpenChart = { isPowerChartFullScreen = true },
        )
        TrackChartSection(
            title = "Heart rate",
            unit = "bpm",
            samples = detail.heartRateSamples,
            onOpenChart = { isHeartRateChartFullScreen = true },
        )
        TrackChartSection(
            title = "Speed",
            unit = "km/h",
            samples = detail.speedSamples,
            onOpenChart = { isSpeedChartFullScreen = true },
        )
        WarningsSection(warnings = detail.warnings)
        TrackSegmentsSection(segments = detail.segments)
    }

    if (isRenameDialogVisible) {
        RenameTrackDialog(
            currentName = detail.importedTrack.displayName,
            onConfirm = { newName ->
                isRenameDialogVisible = false
                onRenameConfirm(newName)
            },
            onDismiss = { isRenameDialogVisible = false },
        )
    }
}

@Composable
private fun RenameTrackDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename track") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Track name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name) },
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun TrackDetailTopBar(
    onBackClick: () -> Unit,
    onRenameClick: () -> Unit,
    onTrimClick: () -> Unit,
    onEditClick: () -> Unit,
    onExportGpxClick: () -> Unit,
    onExportFitClick: () -> Unit,
) {
    var isActionsMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(onClick = onBackClick) {
            Text("Back")
        }
        Spacer(modifier = Modifier.weight(1f))
        Box {
            Button(onClick = { isActionsMenuExpanded = true }) {
                Text("Actions")
            }
            DropdownMenu(
                expanded = isActionsMenuExpanded,
                onDismissRequest = { isActionsMenuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = {
                        isActionsMenuExpanded = false
                        onRenameClick()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Trim track") },
                    onClick = {
                        isActionsMenuExpanded = false
                        onTrimClick()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Edit") },
                    onClick = {
                        isActionsMenuExpanded = false
                        onEditClick()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Export GPX") },
                    onClick = {
                        isActionsMenuExpanded = false
                        onExportGpxClick()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Export FIT") },
                    onClick = {
                        isActionsMenuExpanded = false
                        onExportFitClick()
                    },
                )
            }
        }
    }
}
