package com.gpxeditor.shared.data.ble

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface PowerSensor {
    val connectionState: StateFlow<PowerSensorConnectionState>
    val samples: Flow<PowerSample>

    /** Scans for the first Cycling Power Service advertiser and connects to it. */
    suspend fun connect()

    suspend fun disconnect()
}

sealed interface PowerSensorConnectionState {
    data object Disconnected : PowerSensorConnectionState
    data object Scanning : PowerSensorConnectionState
    data class Connecting(val sensorName: String?) : PowerSensorConnectionState
    data class Connected(val sensorName: String?) : PowerSensorConnectionState
    data class Failed(val message: String) : PowerSensorConnectionState
}
