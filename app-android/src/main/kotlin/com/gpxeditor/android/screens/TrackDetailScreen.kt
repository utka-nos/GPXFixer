package com.gpxeditor.android

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
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
    onTrimClick: () -> Unit,
    onEditClick: () -> Unit,
    onExportClick: () -> Unit,
) {
    var isMapFullScreen by remember { mutableStateOf(false) }

    if (isMapFullScreen) {
        TrackMapFullScreen(
            document = detail.document,
            onBackClick = { isMapFullScreen = false },
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
            onTrimClick = onTrimClick,
            onEditClick = onEditClick,
            onExportClick = onExportClick,
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
        WarningsSection(warnings = detail.warnings)
        TrackSegmentsSection(segments = detail.segments)
    }
}

@Composable
private fun TrackDetailTopBar(
    onBackClick: () -> Unit,
    onTrimClick: () -> Unit,
    onEditClick: () -> Unit,
    onExportClick: () -> Unit,
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
                    text = { Text("Export") },
                    onClick = {
                        isActionsMenuExpanded = false
                        onExportClick()
                    },
                )
            }
        }
    }
}
