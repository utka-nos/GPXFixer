package com.gpxeditor.android.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Sensors",
                onBackClick = onBackClick,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SensorEntry(
                title = "Power sensor",
                icon = Icons.Default.Bolt,
                selectedName = powerState.selected?.let { it.name ?: it.id },
                onClick = { openSensor = SensorDestination.POWER },
            )
            SensorEntry(
                title = "Heart rate sensor",
                icon = Icons.Default.MonitorHeart,
                selectedName = heartRateState.selected?.let { it.name ?: it.id },
                onClick = { openSensor = SensorDestination.HEART_RATE },
            )
        }
    }
}

@Composable
private fun SensorEntry(
    title: String,
    icon: ImageVector,
    selectedName: String?,
    onClick: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = {
                Text(selectedName?.let { "Selected: $it" } ?: "No sensor selected")
            },
            leadingContent = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            trailingContent = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                )
            },
        )
    }
}

private enum class SensorDestination {
    POWER,
    HEART_RATE,
}
