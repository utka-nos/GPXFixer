package com.gpxeditor.android

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gpxeditor.android.recording.RecordingPermissions
import com.gpxeditor.android.recording.TrackRecordingService
import com.gpxeditor.shared.feature.recordtrack.RecordingState
import com.gpxeditor.shared.feature.recordtrack.RecordingStats

@Composable
fun RecordingScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val stats by TrackRecordingService.stats.collectAsState()
    var permissionsGranted by remember { mutableStateOf(RecordingPermissions.allGranted(context)) }
    var showStopConfirmation by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissionsGranted = RecordingPermissions.allGranted(context)
        if (permissionsGranted) {
            TrackRecordingService.start(context)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row {
            Button(onClick = onBackClick) {
                Text("Back")
            }
        }

        Text(
            text = "Record track",
            style = MaterialTheme.typography.headlineMedium,
        )

        val currentStats = stats
        if (currentStats == null) {
            IdleContent(
                permissionsGranted = permissionsGranted,
                onStartClick = {
                    if (RecordingPermissions.allGranted(context)) {
                        permissionsGranted = true
                        TrackRecordingService.start(context)
                    } else {
                        permissionLauncher.launch(RecordingPermissions.required())
                    }
                },
            )
        } else {
            ActiveRecordingContent(
                stats = currentStats,
                onPauseClick = { TrackRecordingService.pause(context) },
                onResumeClick = { TrackRecordingService.resume(context) },
                onStopClick = { showStopConfirmation = true },
            )
        }
    }

    if (showStopConfirmation) {
        AlertDialog(
            onDismissRequest = { showStopConfirmation = false },
            title = { Text("Stop recording?") },
            text = { Text("The recording will be finished.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showStopConfirmation = false
                        TrackRecordingService.stop(context)
                    },
                ) {
                    Text("Stop", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showStopConfirmation = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun IdleContent(
    permissionsGranted: Boolean,
    onStartClick: () -> Unit,
) {
    if (!permissionsGranted) {
        Text(
            text = "GPXFixer needs location access to record your track " +
                "and notification access to show recording progress.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    Button(onClick = onStartClick) {
        Text("Start recording")
    }
}

@Composable
private fun ActiveRecordingContent(
    stats: RecordingStats,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onStopClick: () -> Unit,
) {
    Text(
        text = formatDuration(stats.elapsedMillis),
        style = MaterialTheme.typography.displayLarge,
    )

    StatRow(label = "Distance", value = formatDistance(stats.distanceMeters))
    StatRow(label = "Speed", value = formatSpeed(stats.currentSpeedMetersPerSecond))
    StatRow(label = "Points", value = stats.pointCount.toString())

    when {
        stats.state == RecordingState.PAUSED -> {
            Text(
                text = "Paused",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        stats.pointCount == 0 -> {
            Text(
                text = "Searching for GPS signal…",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (stats.state == RecordingState.PAUSED) {
            Button(onClick = onResumeClick) {
                Text("Resume")
            }
        } else {
            Button(onClick = onPauseClick) {
                Text("Pause")
            }
        }

        Button(
            onClick = onStopClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) {
            Text("Stop")
        }
    }
}

@Composable
private fun StatRow(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
