package com.gpxeditor.android.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gpxeditor.android.recording.BleSensorPermissions
import com.gpxeditor.android.recording.HeartRateSensorController
import com.gpxeditor.shared.data.ble.HeartRateSensorConnectionState

@Composable
fun HeartRateSensorScreen(controller: HeartRateSensorController, onBackClick: () -> Unit) {
    val context = LocalContext.current
    val state by controller.state.collectAsState()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        if (BleSensorPermissions.allGranted(context)) controller.startScan()
    }

    DisposableEffect(Unit) {
        if (BleSensorPermissions.allGranted(context)) controller.startScan()
        onDispose(controller::closeScreen)
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Heart rate sensor",
                onBackClick = onBackClick,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

            state.selected?.let { selected ->
                Text("Selected: ${selected.name ?: selected.id}")
                TextButton(onClick = controller::forget) { Text("Forget sensor") }
            }

            when (val connection = state.connectionState) {
                is HeartRateSensorConnectionState.Connecting -> Text("Connecting…")
                is HeartRateSensorConnectionState.Connected -> Text("Connected", color = MaterialTheme.colorScheme.primary)
                is HeartRateSensorConnectionState.Failed -> Text(connection.message, color = MaterialTheme.colorScheme.error)
                else -> Unit
            }
            state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            if (!BleSensorPermissions.allGranted(context)) {
                Text("Bluetooth permission is required to find heart rate sensors.")
                Button(onClick = { permissionLauncher.launch(BleSensorPermissions.required()) }) {
                    Text("Allow Bluetooth")
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = controller::startScan, enabled = !state.isScanning) { Text("Scan") }
                    if (state.isScanning) CircularProgressIndicator()
                }
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(state.devices, key = { it.id }) { device ->
                        Column(
                            modifier = Modifier.fillMaxWidth().clickable { controller.select(device) }.padding(vertical = 14.dp),
                        ) {
                            Text(device.name ?: "Heart rate sensor", style = MaterialTheme.typography.titleMedium)
                            Text("RSSI ${device.rssi} dBm", style = MaterialTheme.typography.bodyMedium)
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
