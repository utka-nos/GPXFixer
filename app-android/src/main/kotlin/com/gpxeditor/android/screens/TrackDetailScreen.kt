package com.gpxeditor.android.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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

    Scaffold(
        topBar = {
            AppTopBar(
                title = detail.importedTrack.displayName,
                onBackClick = onBackClick,
                actions = {
                    TrackActionsMenu(
                        onRenameClick = { isRenameDialogVisible = true },
                        onTrimClick = onTrimClick,
                        onEditClick = onEditClick,
                        onExportGpxClick = onExportGpxClick,
                        onExportFitClick = onExportFitClick,
                    )
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
            Text(
                text = detail.importedTrack.originalFileName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun TrackActionsMenu(
    onRenameClick: () -> Unit,
    onTrimClick: () -> Unit,
    onEditClick: () -> Unit,
    onExportGpxClick: () -> Unit,
    onExportFitClick: () -> Unit,
) {
    var isExpanded by remember { mutableStateOf(false) }

    IconButton(onClick = { isExpanded = true }) {
        Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = "Track actions",
        )
    }
    DropdownMenu(
        expanded = isExpanded,
        onDismissRequest = { isExpanded = false },
    ) {
        TrackActionItem(
            text = "Rename",
            icon = Icons.Default.DriveFileRenameOutline,
            onClick = onRenameClick,
            closeMenu = { isExpanded = false },
        )
        TrackActionItem(
            text = "Trim track",
            icon = Icons.Default.ContentCut,
            onClick = onTrimClick,
            closeMenu = { isExpanded = false },
        )
        TrackActionItem(
            text = "Edit points",
            icon = Icons.Default.EditLocationAlt,
            onClick = onEditClick,
            closeMenu = { isExpanded = false },
        )
        TrackActionItem(
            text = "Export GPX",
            icon = Icons.Default.Map,
            onClick = onExportGpxClick,
            closeMenu = { isExpanded = false },
        )
        TrackActionItem(
            text = "Export FIT",
            icon = Icons.Default.Watch,
            onClick = onExportFitClick,
            closeMenu = { isExpanded = false },
        )
    }
}

@Composable
private fun TrackActionItem(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    closeMenu: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(text) },
        leadingIcon = { Icon(imageVector = icon, contentDescription = null) },
        onClick = {
            closeMenu()
            onClick()
        },
    )
}
