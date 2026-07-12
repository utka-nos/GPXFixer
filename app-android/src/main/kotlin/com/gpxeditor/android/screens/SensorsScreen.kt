package com.gpxeditor.android.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gpxeditor.android.recording.HeartRateSensorController
import com.gpxeditor.android.recording.PowerSensorController

/** Entry point to the per-sensor pairing screens. */
@Composable
fun SensorsScreen(
    powerSensorController: PowerSensorController,
    heartRateSensorController: HeartRateSensorController,
    onBackClick: () -> Unit,
) {
    var openSensor by remember { mutableStateOf<SensorDestination?>(null) }
    val powerState by powerSensorController.state.collectAsState()
    val heartRateState by heartRateSensorController.state.collectAsState()

    when (openSensor) {
        SensorDestination.POWER -> {
            PowerSensorScreen(
                controller = powerSensorController,
                onBackClick = { openSensor = null },
            )
            return
        }

        SensorDestination.HEART_RATE -> {
            HeartRateSensorScreen(
                controller = heartRateSensorController,
                onBackClick = { openSensor = null },
            )
            return
        }

        null -> Unit
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row {
            Button(onClick = onBackClick) { Text("Back") }
        }
        Text("Sensors", style = MaterialTheme.typography.headlineMedium)

        SensorEntry(
            title = "Power sensor",
            selectedName = powerState.selected?.let { it.name ?: it.id },
            onClick = { openSensor = SensorDestination.POWER },
        )
        SensorEntry(
            title = "Heart rate sensor",
            selectedName = heartRateState.selected?.let { it.name ?: it.id },
            onClick = { openSensor = SensorDestination.HEART_RATE },
        )
    }
}

@Composable
private fun SensorEntry(
    title: String,
    selectedName: String?,
    onClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Button(onClick = onClick) { Text(title) }
        Text(
            text = selectedName?.let { "Selected: $it" } ?: "No sensor selected",
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private enum class SensorDestination {
    POWER,
    HEART_RATE,
}
