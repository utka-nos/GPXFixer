package com.gpxeditor.android.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import android.util.Log
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gpxeditor.android.recording.RecordingPermissions
import com.gpxeditor.android.recording.TrackRecordingService
import com.gpxeditor.shared.feature.recordtrack.RecordingState
import com.gpxeditor.shared.feature.recordtrack.RecordingStats
import com.gpxeditor.shared.feature.recordtrack.RoutePoint
import com.gpxeditor.shared.feature.trackdetail.TrackChartSample
import com.gpxeditor.shared.data.ble.HeartRateSensorRecordingStatus
import com.gpxeditor.shared.data.ble.PowerSensorRecordingStatus

@Composable
fun RecordingScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val stats by TrackRecordingService.stats.collectAsState()
    val routeSegments by TrackRecordingService.routeSegments.collectAsState()
    val powerChartSamples by TrackRecordingService.powerChartSamples.collectAsState()
    val powerSensorStatus by TrackRecordingService.powerSensorStatus.collectAsState()
    val heartRateSensorStatus by TrackRecordingService.heartRateSensorStatus.collectAsState()
    var permissionsGranted by remember { mutableStateOf(RecordingPermissions.allGranted(context)) }
    var showStopConfirmation by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        // An empty result or immediate denial without a dialog means the permission
        // is permanently denied and can only be granted from system settings.
        Log.i(RecordingPermissions.TAG, "Permission dialog result: $results")
        RecordingPermissions.logStatus(context, "permission result")
        permissionsGranted = RecordingPermissions.allGranted(context)
        if (permissionsGranted) {
            TrackRecordingService.start(context)
        } else {
            Log.w(
                RecordingPermissions.TAG,
                "Recording not started, missing: ${RecordingPermissions.missing(context)}",
            )
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
            val saveMessage by TrackRecordingService.lastSaveMessage.collectAsState()
            saveMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            IdleContent(
                permissionsGranted = permissionsGranted,
                onStartClick = {
                    RecordingPermissions.logStatus(context, "start click")
                    if (RecordingPermissions.allGranted(context)) {
                        permissionsGranted = true
                        Log.i(RecordingPermissions.TAG, "Starting recording service")
                        TrackRecordingService.start(context)
                    } else {
                        Log.i(
                            RecordingPermissions.TAG,
                            "Requesting permissions: ${RecordingPermissions.missing(context)}",
                        )
                        permissionLauncher.launch(RecordingPermissions.required())
                    }
                },
            )
        } else {
            ActiveRecordingContent(
                stats = currentStats,
                routeSegments = routeSegments,
                powerChartSamples = powerChartSamples,
                powerSensorStatus = powerSensorStatus,
                heartRateSensorStatus = heartRateSensorStatus,
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
    routeSegments: List<List<RoutePoint>>,
    powerChartSamples: List<TrackChartSample>,
    powerSensorStatus: PowerSensorRecordingStatus,
    heartRateSensorStatus: HeartRateSensorRecordingStatus,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onStopClick: () -> Unit,
) {
    LiveRecordingMap(
        segments = routeSegments,
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp),
    )

    Text(
        text = formatDuration(stats.elapsedMillis),
        style = MaterialTheme.typography.displayLarge,
    )

    StatRow(label = "Distance", value = formatDistance(stats.distanceMeters))
    StatRow(label = "Speed", value = formatSpeed(stats.currentSpeedMetersPerSecond))
    StatRow(label = "Points", value = stats.pointCount.toString())
    StatRow(label = "Power", value = stats.currentPowerWatts?.let { "$it W" } ?: "—")
    StatRow(label = "Cadence", value = stats.currentCadenceRpm?.let { "$it rpm" } ?: "—")
    StatRow(label = "Heart rate", value = stats.currentHeartRateBpm?.let { "$it bpm" } ?: "—")
    LivePowerChartSection(samples = powerChartSamples)
    Text(
        text = when (powerSensorStatus) {
            PowerSensorRecordingStatus.CONNECTED -> "Power sensor connected"
            PowerSensorRecordingStatus.RECONNECTING -> "Power sensor reconnecting…"
            PowerSensorRecordingStatus.NOT_CONNECTED -> "Power sensor not connected"
            PowerSensorRecordingStatus.NOT_CONFIGURED -> "No power sensor configured"
        },
        color = if (powerSensorStatus == PowerSensorRecordingStatus.CONNECTED) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.secondary
        },
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        text = when (heartRateSensorStatus) {
            HeartRateSensorRecordingStatus.CONNECTED -> "Heart rate sensor connected"
            HeartRateSensorRecordingStatus.RECONNECTING -> "Heart rate sensor reconnecting…"
            HeartRateSensorRecordingStatus.NOT_CONNECTED -> "Heart rate sensor not connected"
            HeartRateSensorRecordingStatus.NOT_CONFIGURED -> "No heart rate sensor configured"
        },
        color = if (heartRateSensorStatus == HeartRateSensorRecordingStatus.CONNECTED) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.secondary
        },
        style = MaterialTheme.typography.bodyMedium,
    )

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
